#!/usr/bin/env bash
#
# Tests scripts/bump-version.sh against throwaway fixture repositories.
#
# Usage: scripts/bump-version.test.sh [name-filter]

set -uo pipefail

# Same reason the script under test sets it: byte-oriented grep, plus a stable `sort` order for the
# changed-file assertion.
export LC_ALL=C

# Fixtures must not see the caller's repository or configuration. A git hook runs with GIT_DIR and
# GIT_INDEX_FILE pointing at the real repository, and a global config can carry core.hooksPath,
# init.templateDir, or commit.gpgsign.
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_COMMON_DIR GIT_PREFIX
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
BUMP="$SCRIPT_DIR/bump-version.sh"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/bump-version-test.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

PASSED=0
FAILED=0

# ---------------------------------------------------------------------------
# Runner and reporting
# ---------------------------------------------------------------------------

fail() {
  CASE_FAILED=1
  printf 'not ok %s: %s\n' "$CASE" "$1" >&2
}

run_case() {
  CASE=$1
  CASE_DIR="$WORK/$CASE"
  CASE_FAILED=0
  UNTRACKED=""
  RUN_FROM=""
  if ! declare -F "case_$CASE" > /dev/null; then
    FAILED=$((FAILED + 1))
    printf 'not ok %s: no case_%s function defined\n' "$CASE" "$CASE" >&2
    return
  fi
  make_fixture
  "case_$CASE"
  local case_status=$?
  if [ "$case_status" -ne 0 ] && [ "$CASE_FAILED" -eq 0 ]; then
    fail "case_$CASE exited with status $case_status"
  fi
  if [ "$CASE_FAILED" -eq 0 ]; then
    PASSED=$((PASSED + 1))
    printf 'ok %s\n' "$CASE"
  else
    FAILED=$((FAILED + 1))
  fi
}

# ---------------------------------------------------------------------------
# Fixture helpers
# ---------------------------------------------------------------------------

write_file() {
  # $1 = path (relative to CASE_DIR), stdin = content
  local path="$CASE_DIR/$1"
  mkdir -p "$(dirname -- "$path")"
  cat > "$path"
}

append_file() {
  # $1 = path (relative to CASE_DIR), stdin = content to append
  cat >> "$CASE_DIR/$1"
}

make_fixture() {
  mkdir -p "$CASE_DIR"
  git -C "$CASE_DIR" init -q -b main

  write_file build.gradle <<'EOF'
group = "com.example.widgets"

tasks.register("verify") {
    group = "verification"
}
EOF

  write_file settings.gradle <<'EOF'
rootProject.name = "widgets"

include "alpha-core"
include "beta"
EOF

  write_file gradle.properties <<'EOF'
repoUrl=https://example.invalid/widgets
version=0.1.0-SNAPSHOT
EOF

  write_file README.md <<'EOF'
Release coordinates:

    com.example.widgets:alpha-core:0.0.9
    com.example.widgets:beta:0.0.9
com.example.widgets:alpha-core:0.0.9

Jars:

    beta-0.0.9.jar
    alpha-core-0.0.9.jar
    jline-3.30.5.jar
    gradle-wrapper.jar
beta-0.0.9.jar
EOF

  write_file RELEASE.md <<'EOF'
Consuming snapshots:

    com.example.widgets:alpha-core:0.1.0-SNAPSHOT
EOF

  mkdir -p "$CASE_DIR/gradle/wrapper"
  printf 'PK\x03\x04\x00version=0.1.0-SNAPSHOT\x00beta-0.0.9.jar\x00com.example.widgets:beta:0.0.9\x00' \
    > "$CASE_DIR/gradle/wrapper/gradle-wrapper.jar"
}

use_real_repo_docs() {
  cp "$REPO_ROOT/build.gradle" "$CASE_DIR/build.gradle"
  cp "$REPO_ROOT/settings.gradle" "$CASE_DIR/settings.gradle"
  cp "$REPO_ROOT/gradle.properties" "$CASE_DIR/gradle.properties"
  cp "$REPO_ROOT/README.md" "$CASE_DIR/README.md"
  cp "$REPO_ROOT/RELEASE.md" "$CASE_DIR/RELEASE.md"
}

# ---------------------------------------------------------------------------
# Invocation
# ---------------------------------------------------------------------------

bump() {
  if [ -n "$UNTRACKED" ]; then
    git -C "$CASE_DIR" add -A -f -- . ":(exclude)$UNTRACKED" > /dev/null
  else
    git -C "$CASE_DIR" add -A -f > /dev/null
  fi
  rm -rf "$CASE_DIR.orig"
  cp -R "$CASE_DIR" "$CASE_DIR.orig"
  STDOUT=$(cd "$CASE_DIR/$RUN_FROM" && "$BUMP" "$@" 2> "$CASE_DIR.stderr")
  STATUS=$?
  STDERR=$(cat "$CASE_DIR.stderr")
  OUTPUT="$STDOUT$STDERR"
}

# ---------------------------------------------------------------------------
# Assertions
# ---------------------------------------------------------------------------

assert_status() {
  if [ "$STATUS" -ne "$1" ]; then
    fail "expected exit status $1, got $STATUS (output: $OUTPUT)"
  fi
}

assert_status_nonzero() {
  if [ "$STATUS" -eq 0 ]; then
    fail "expected a non-zero exit status, got 0 (output: $OUTPUT)"
  fi
}

assert_output_contains() {
  case "$OUTPUT" in
    *"$1"*) ;;
    *) fail "expected output to contain: $1" ;;
  esac
}

assert_output_lacks() {
  case "$OUTPUT" in
    *"$1"*) fail "expected output to lack: $1" ;;
    *) ;;
  esac
}

assert_stdout_contains() {
  case "$STDOUT" in
    *"$1"*) ;;
    *) fail "expected stdout to contain: $1" ;;
  esac
}

assert_stderr_contains() {
  case "$STDERR" in
    *"$1"*) ;;
    *) fail "expected stderr to contain: $1" ;;
  esac
}

assert_file_contains() {
  if ! grep -qF -- "$2" "$CASE_DIR/$1"; then
    fail "expected $1 to contain: $2"
  fi
}

assert_file_lacks() {
  if grep -qF -- "$2" "$CASE_DIR/$1"; then
    fail "expected $1 to lack: $2"
  fi
}

assert_file_matches() {
  if ! grep -qE -- "$2" "$CASE_DIR/$1"; then
    fail "expected $1 to match: $2"
  fi
}

assert_unchanged() {
  if ! cmp -s "$CASE_DIR/$1" "$CASE_DIR.orig/$1"; then
    fail "expected $1 to be byte-identical to its pre-bump content"
  fi
}

assert_changed_files() {
  # No arguments means "assert nothing changed": printf '%s\n' with no operands emits a bare
  # newline, which command substitution then strips, so `expected` is the empty string.
  local expected actual
  expected=$(printf '%s\n' "$@" | sort)
  actual=$(git -C "$CASE_DIR" diff --name-only | sort)
  if [ "$expected" != "$actual" ]; then
    fail "expected changed files [$*], got [$(printf '%s ' $actual)]"
  fi
}

assert_rejected() {
  # $1 = expected stderr fragment
  assert_status 1
  assert_stderr_contains "$1"
  assert_changed_files
}

# ---------------------------------------------------------------------------
# Cases
# ---------------------------------------------------------------------------

case_release_bump_rewrites_release_coordinates() {
  bump 0.1.0
  assert_status 0
  assert_file_contains README.md "com.example.widgets:alpha-core:0.1.0"
  assert_file_contains README.md "com.example.widgets:beta:0.1.0"
  assert_file_contains gradle.properties "version=0.1.0"
  assert_output_contains "Updated version: 0.1.0-SNAPSHOT -> 0.1.0"
  assert_stdout_contains "Updated version:"
  assert_file_matches README.md '^com\.example\.widgets:alpha-core:0\.1\.0'
  assert_changed_files README.md gradle.properties
}

case_release_bump_leaves_snapshot_coordinates() {
  bump 0.1.0
  assert_status 0
  assert_unchanged RELEASE.md
  assert_file_contains RELEASE.md "com.example.widgets:alpha-core:0.1.0-SNAPSHOT"
}

case_snapshot_bump_rewrites_snapshot_coordinates() {
  bump 0.2.0-SNAPSHOT
  assert_status 0
  assert_file_contains RELEASE.md "com.example.widgets:alpha-core:0.2.0-SNAPSHOT"
  assert_unchanged README.md
  assert_changed_files RELEASE.md gradle.properties
}

case_release_bump_rewrites_own_jar_filename() {
  bump 0.1.0
  assert_status 0
  assert_file_contains README.md "beta-0.1.0.jar"
  assert_file_lacks README.md "beta-0.0.9.jar"
  assert_file_contains README.md "alpha-core-0.1.0.jar"
  assert_file_lacks README.md "alpha-core-0.0.9.jar"
  assert_file_matches README.md '^beta-0\.1\.0\.jar'
}

case_release_bump_leaves_foreign_jar_filename() {
  bump 0.1.0
  assert_status 0
  assert_file_contains README.md "jline-3.30.5.jar"
  assert_file_contains README.md "gradle-wrapper.jar"
}

case_mixed_release_and_snapshot_line() {
  append_file README.md <<'EOF'
Use "com.example.widgets:beta:0.0.9" unless you want the -SNAPSHOT feed.
EOF
  bump 0.1.0
  assert_status 0
  # Pins documented intent: a line naming both a release coordinate and the literal string
  # "-SNAPSHOT" is skipped whole by the `/-SNAPSHOT/!` address, not an accident to fix.
  assert_file_contains README.md 'com.example.widgets:beta:0.0.9" unless you want the -SNAPSHOT feed.'
  # The skip is per-line, not per-file: the other coordinate lines in the same README still move.
  assert_file_contains README.md "com.example.widgets:beta:0.1.0"
}

case_binary_files_untouched() {
  bump 0.1.0
  assert_status 0
  assert_unchanged gradle/wrapper/gradle-wrapper.jar
  assert_changed_files README.md gradle.properties
}

case_settings_gradle_include_forms() {
  write_file settings.gradle <<'EOF'
rootProject.name = "widgets"

include(':alpha-core')
include "beta"
include 'gamma:delta'
EOF
  append_file README.md <<'EOF'
    delta-0.0.9.jar
EOF
  bump 0.1.0
  assert_status 0
  assert_file_contains README.md "alpha-core-0.1.0.jar"
  assert_file_contains README.md "beta-0.1.0.jar"
  assert_file_contains README.md "delta-0.1.0.jar"
}

case_duplicate_group_ids_rejected() {
  write_file build.gradle <<'EOF'
group = "com.example.widgets"
group = "com.example.other"
EOF
  bump 0.1.0
  assert_stderr_contains "com.example.widgets"
  assert_stderr_contains "com.example.other"
  assert_rejected "declares more than one group id"
}

case_no_group_id_rejected() {
  write_file build.gradle <<'EOF'
tasks.register("verify") {
    group = "verification"
}
EOF
  bump 0.1.0
  assert_rejected "could not read a group id"
}

case_same_version_rejected() {
  bump 0.1.0-SNAPSHOT
  assert_rejected "current version and new version are the same"
}

case_missing_version_key_rejected() {
  write_file gradle.properties <<'EOF'
repoUrl=https://example.invalid/widgets
EOF
  bump 0.1.0
  assert_rejected "could not read current version from gradle.properties"
}

case_duplicate_version_keys_rejected() {
  write_file gradle.properties <<'EOF'
repoUrl=https://example.invalid/widgets
version=0.1.0-SNAPSHOT
version=0.2.0-SNAPSHOT
EOF
  bump 0.1.0
  assert_stderr_contains "0.1.0-SNAPSHOT"
  assert_stderr_contains "0.2.0-SNAPSHOT"
  assert_rejected "declares more than one version key"
}

case_current_version_absent_rejected() {
  bump 9.9.9 8.8.8
  assert_rejected "current version '8.8.8' not found in any tracked file"
}

case_matched_but_unrewritable_exits_nonzero() {
  write_file README.md <<'EOF'
Historic note: 0.5.0 was the first tag.
EOF
  write_file RELEASE.md <<'EOF'
Nothing to see here.
EOF
  bump 0.9.0-SNAPSHOT 0.5.0
  assert_stdout_contains "Found 1 occurrence(s)"
  assert_rejected "No files were changed"
}

case_no_modules_rejected() {
  write_file settings.gradle <<'EOF'
rootProject.name = "widgets"
EOF
  bump 0.1.0
  assert_rejected "could not read any module names from settings.gradle"
}

case_requires_repo_root() {
  RUN_FROM=sub
  mkdir -p "$CASE_DIR/sub"
  bump 0.1.0
  assert_status_nonzero
  assert_changed_files
}

case_explicit_current_version_argument() {
  bump 0.2.0 0.0.9
  assert_status 0
  assert_file_contains README.md "com.example.widgets:alpha-core:0.2.0"
  assert_file_contains README.md "beta-0.2.0.jar"
  assert_unchanged gradle.properties
  assert_output_lacks "Detected current version"
  assert_changed_files README.md
}

case_exact_version_forms_rewritten() {
  write_file docs/sample-pom.xml <<'EOF'
<project>
    <version>0.1.0-SNAPSHOT</version>
</project>
EOF
  append_file build.gradle <<'EOF'
version = "0.1.0-SNAPSHOT"
EOF
  bump 0.2.0-SNAPSHOT
  assert_status 0
  assert_file_contains docs/sample-pom.xml "<version>0.2.0-SNAPSHOT</version>"
  assert_file_contains build.gradle 'version = "0.2.0-SNAPSHOT"'
  assert_file_contains gradle.properties "version=0.2.0-SNAPSHOT"
}

case_untracked_file_untouched() {
  UNTRACKED=NOTES.md
  write_file NOTES.md <<'EOF'
Internal notes: version=0.1.0-SNAPSHOT
EOF
  bump 0.1.0
  assert_status 0
  assert_unchanged NOTES.md
  assert_changed_files README.md gradle.properties
}

case_missing_argument_rejected() {
  bump
  assert_rejected "Usage: bump-version.sh"
}

case_module_name_as_suffix_of_another_word() {
  append_file README.md <<'EOF'
See my-beta-1.2.3.jar for the fork.
notmy.com.example.widgets:beta:1.2.3
EOF
  bump 0.1.0
  assert_status 0
  assert_file_contains README.md "my-beta-1.2.3.jar"
  assert_file_contains README.md "notmy.com.example.widgets:beta:1.2.3"
  assert_file_contains README.md "beta-0.1.0.jar"
  assert_file_contains README.md "com.example.widgets:beta:0.1.0"
}

case_coordinate_without_version_untouched() {
  append_file README.md <<'EOF'
See {@code com.example.widgets:beta} for details.
EOF
  bump 0.1.0
  assert_status 0
  assert_file_contains README.md "See {@code com.example.widgets:beta} for details."
}

case_real_repo_docs_track_the_documented_invariants() {
  use_real_repo_docs
  bump 9.9.9
  assert_status 0
  assert_file_contains README.md "org.coordinatekit.foundation:cli-brand:9.9.9"
  assert_file_contains README.md "conventions-9.9.9.jar"
  assert_unchanged RELEASE.md
}

case_real_repo_snapshot_bump_leaves_release_docs() {
  use_real_repo_docs
  bump 9.9.9-SNAPSHOT
  assert_status 0
  assert_unchanged README.md
  assert_file_contains RELEASE.md "org.coordinatekit.foundation:cli-brand:9.9.9-SNAPSHOT"
  assert_file_contains gradle.properties "version=9.9.9-SNAPSHOT"
}

# ---------------------------------------------------------------------------
# Case list and driver
# ---------------------------------------------------------------------------

CASES="
release_bump_rewrites_release_coordinates
release_bump_leaves_snapshot_coordinates
snapshot_bump_rewrites_snapshot_coordinates
release_bump_rewrites_own_jar_filename
release_bump_leaves_foreign_jar_filename
mixed_release_and_snapshot_line
binary_files_untouched
settings_gradle_include_forms
duplicate_group_ids_rejected
no_group_id_rejected
same_version_rejected
missing_version_key_rejected
duplicate_version_keys_rejected
current_version_absent_rejected
matched_but_unrewritable_exits_nonzero
no_modules_rejected
requires_repo_root
explicit_current_version_argument
exact_version_forms_rewritten
untracked_file_untouched
missing_argument_rejected
module_name_as_suffix_of_another_word
coordinate_without_version_untouched
real_repo_docs_track_the_documented_invariants
real_repo_snapshot_bump_leaves_release_docs
"

FILTER="${1:-}"

MATCHED=0
for name in $CASES; do
  if [ -n "$FILTER" ]; then
    case "$name" in
      *"$FILTER"*) ;;
      *) continue ;;
    esac
  fi
  MATCHED=$((MATCHED + 1))
  run_case "$name"
done

if [ -n "$FILTER" ] && [ "$MATCHED" -eq 0 ]; then
  printf "no case matches filter '%s'\n" "$FILTER" >&2
  exit 1
fi

printf '%d passed, %d failed\n' "$PASSED" "$FAILED"
[ "$FAILED" -eq 0 ]

#!/usr/bin/env bash
#
# Bumps the project version across all tracked files.
#
# Usage:
#   scripts/bump-version.sh <new-version> [<current-version>]
#
# If <current-version> is omitted, it is read from gradle.properties.

set -euo pipefail

# Ensure sed handles all byte sequences (binary files in git ls-files)
export LC_ALL=C

NEW_VERSION="${1:?Usage: bump-version.sh <new-version> [<current-version>]}"
CURRENT_VERSION="${2:-}"

# Read the published group from build.gradle. Task definitions also assign `group` ("verification",
# "documentation"), so the pattern accepts only a dotted group id, and a conflict is an error rather
# than a silent pick of whichever came first.
GROUP=$(sed -nE 's/^[[:space:]]*group[[:space:]]*=[[:space:]]*"([A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+)"[[:space:]]*$/\1/p' build.gradle | sort -u)
case "$GROUP" in
  "")
    echo "Error: could not read a group id (e.g. org.example.foo) from build.gradle" >&2
    exit 1
    ;;
  *$'\n'*)
    echo "Error: build.gradle declares more than one group id:" >&2
    echo "$GROUP" | sed 's/^/  /' >&2
    exit 1
    ;;
esac
echo "Detected group: $GROUP"

# Read the module names from settings.gradle. The jar-filename rewrite below is anchored to them so
# it never touches a dependency jar that a doc happens to name.
MODULES=$(sed -nE "s/^[[:space:]]*include[[:space:]]*\(?[[:space:]]*['\"]:?([A-Za-z0-9._:-]+)['\"].*/\1/p" settings.gradle |
  sed -E 's/.*://' | sort -u)
if [ -z "$MODULES" ]; then
  echo "Error: could not read any module names from settings.gradle" >&2
  exit 1
fi

if [ -z "$CURRENT_VERSION" ]; then
  CURRENT_VERSION=$(grep '^version=' gradle.properties | cut -d= -f2 || true)
  case "$CURRENT_VERSION" in
    "")
      echo "Error: could not read current version from gradle.properties" >&2
      exit 1
      ;;
    *$'\n'*)
      echo "Error: gradle.properties declares more than one version key:" >&2
      echo "$CURRENT_VERSION" | sed 's/^/  /' >&2
      exit 1
      ;;
  esac
  echo "Detected current version: $CURRENT_VERSION"
fi

if [ "$CURRENT_VERSION" = "$NEW_VERSION" ]; then
  echo "Error: current version and new version are the same ($CURRENT_VERSION)" >&2
  exit 1
fi

# Escape dots for use in regex
ESCAPED_CURRENT=$(printf '%s' "$CURRENT_VERSION" | sed 's/\./\\./g')
ESCAPED_GROUP=$(printf '%s' "$GROUP" | sed 's/\./\\./g')
ESCAPED_MODULES=$(printf '%s' "$MODULES" | sed 's/\./\\./g' | paste -sd'|' -)

# This script's own test file is excluded by path. Its fixture repositories declare a version key
# and its assertions name expected output coordinates and jar filenames; those literals are test
# data, not project versions, and rewriting them silently breaks the suite. The exclusion is this
# one exact path, so any other script stays in scope. Keep version literals out of this file's own
# comments for the same reason.
SELF_TEST=scripts/bump-version.test.sh

# Restrict every rewrite step to tracked text files. Binary files (e.g. the tracked
# gradle-wrapper.jar) can byte-match these patterns by coincidence, and sed -i risks
# corrupting them; `grep -I` skips anything it detects as binary.
tracked_text_files() {
  # `--null` rather than `-Z`: BSD grep (macOS) accepts -Z but silently ignores it, so
  # -Z leaves entries newline-separated and xargs -0 reads them as one giant filename.
  git ls-files -z -- . ":(exclude)$SELF_TEST" | xargs -0 grep -Il --null '' 2>/dev/null || true
}

# Verify the current version exists in at least one tracked text file
if ! tracked_text_files | xargs -0 grep -l "$CURRENT_VERSION" > /dev/null 2>&1; then
  echo "Error: current version '$CURRENT_VERSION' not found in any tracked file" >&2
  exit 1
fi

# Count matches before replacing
MATCH_COUNT=$(tracked_text_files | xargs -0 grep -c "$CURRENT_VERSION" 2>/dev/null | awk -F: '{s+=$NF} END {print s}')
echo "Found $MATCH_COUNT occurrence(s) of '$CURRENT_VERSION' to evaluate"

# Detect sed in-place flag (macOS requires '' argument, Linux does not)
if sed --version > /dev/null 2>&1; then
  SED_INPLACE=(sed -i -E)
else
  SED_INPLACE=(sed -i '' -E)
fi

# `version=`, `version = "…"`, and `<version>…</version>` always track the exact current version,
# whether that is a release or a `-SNAPSHOT`, so these patterns stay anchored to CURRENT_VERSION.
SED_ARGS=(
  -e "s/version = \"$ESCAPED_CURRENT\"/version = \"$NEW_VERSION\"/g"
  -e "s/version=$ESCAPED_CURRENT/version=$NEW_VERSION/g"
  -e "s|<version>$ESCAPED_CURRENT</version>|<version>$NEW_VERSION</version>|g"
)

# Published coordinates and jar filenames (README) name the last release, so they are matched by
# "any version" rather than CURRENT_VERSION and are only rewritten when bumping to a release. The
# `/-SNAPSHOT/!` address keeps them off lines that document a snapshot coordinate, which would
# otherwise be swallowed by the version pattern and turned into a release coordinate. The jar rule
# is anchored to this project's own module names so it never touches a dependency jar that a doc
# happens to name. The `(^|[^A-Za-z0-9._-])` left-hand guard keeps a forked or foreign name (e.g.
# `my-conventions-1.2.3.jar`, `notmy.org.coordinatekit.foundation:x:1.0`) from being caught by the
# alternation matching only a suffix of it.
if [[ "$NEW_VERSION" != *-SNAPSHOT ]]; then
  SED_ARGS+=(
    -e "/-SNAPSHOT/!s/(^|[^A-Za-z0-9._-])($ESCAPED_GROUP:[A-Za-z0-9._-]+:)[0-9][A-Za-z0-9.+-]*/\1\2$NEW_VERSION/g"
    -e "/-SNAPSHOT/!s/(^|[^A-Za-z0-9._-])($ESCAPED_MODULES)-[0-9][A-Za-z0-9.+-]*\.jar/\1\2-$NEW_VERSION.jar/g"
  )
else
  # Snapshot coordinates (RELEASE.md) name the in-development version, so they are rewritten on the
  # bump back to the next `-SNAPSHOT` and left alone by a release bump.
  SED_ARGS+=(
    -e "s/(^|[^A-Za-z0-9._-])($ESCAPED_GROUP:[A-Za-z0-9._-]+:)[0-9][A-Za-z0-9.+-]*-SNAPSHOT/\1\2$NEW_VERSION/g"
  )
fi

# Perform targeted replacements across all tracked text files
tracked_text_files | xargs -0 "${SED_INPLACE[@]}" "${SED_ARGS[@]}"

# Report what changed
CHANGED_FILES=$(git diff --name-only)
if [ -z "$CHANGED_FILES" ]; then
  echo "No files were changed. The current version may not match any known patterns." >&2
  exit 1
fi

echo "Updated version: $CURRENT_VERSION -> $NEW_VERSION"
echo "Changed files:"
echo "$CHANGED_FILES" | sed 's/^/  /'

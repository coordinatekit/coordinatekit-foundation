# AGENTS.md

## Repository

coordinatekit-foundation is the base layer CoordinateKit's other repositories build on. Anything more than one repository needs is implemented here once and consumed as a published library, never copied. Two modules live here, both under the group `org.coordinatekit.foundation`.

`cli-brand` publishes `Banner`, which renders the brand art as a string via `render(boolean ansiEnabled)`. The module depends only on JLine (`implementation`); where the banner prints and the ANSI decision belong to the consumer (README has the recipe). That dependency is the aggregate `org.jline:jline` jar on purpose, not `jline-terminal` plus a chosen provider: it bundles every terminal provider, so JLine selects the one matching the consumer's JDK at run time instead of this module pinning a native backend the consumer should choose. Keep the aggregate.

`conventions` publishes two files and no code: the Eclipse formatter profile and the Apache-2.0 license header. It is not a Gradle plugin and configures nothing on its own. A consumer reads whichever entry it wants out of the jar and wires it into its own Spotless setup. README.md has the recipe.

## Commands

JDK 21, Gradle 9.4 through the wrapper.

```
./gradlew check                 # compile, tests, Error Prone, Spotless check
./gradlew test
./gradlew :cli-brand:test --tests '*BannerTest'
./gradlew :cli-brand:test --tests '*BannerTest.markSegments*'   # single test method
./gradlew spotlessApply
./gradlew aggregateJacocoReport # build/reports/jacoco/aggregateJacocoReport/html/index.html
./gradlew aggregateJavadoc      # runs with -Werror; any Javadoc warning is a failure
```

Run `pre-commit install` once per clone. The hooks run prettier, `./gradlew spotlessApply`, and `./gradlew check` before every commit, and enforce Conventional Commits on the message. A commit is slow and arrives pre-verified.

## Build layout

The root `build.gradle` owns all module wiring, and a subproject's own `build.gradle` holds nothing but its dependencies. `cli-brand/build.gradle` is four lines. The wiring comes in two tiers: every subproject gets Spotless and `java-library`, and `codeProjects`, meaning every subproject except `conventions`, also gets JaCoCo, Error Prone, JSpecify as `api`, SLF4J as `implementation`, and JUnit. `conventions` sits out because it has no sources to compile, test, or measure.

A new module needs an `include` in `settings.gradle` and a `build.gradle` naming its own dependencies. Everything else is inherited. Its package has to be the group plus the module name with dashes turned into dots, so `cli-brand` lives in `org.coordinatekit.foundation.cli.brand`, because `aggregateJavadoc` derives each module's Javadoc group pattern from its name. A name segment that should stay uppercase in the group title goes in the `acronyms` set in `build.gradle`.

The repository formats itself against `conventions`' files by path rather than by resolving the published jar, so the build never depends on its own artifact. Consumers use the jar.

Dependency versions live in `gradle/libs.versions.toml`, grouped under comments that state an update policy for each group. The `api` coordinates move conservatively because they are on the published surface, and test dependencies track latest. The project version lives in `gradle.properties`, and `scripts/bump-version.sh` rewrites it across every tracked file, matching `version=`, `version = "…"`, `<version>…</version>`, and `<project>:<module>:<version>` coordinates. README examples stay correct as long as they keep that coordinate form.

Error Prone 2.48 needs `-XDaddTypeAnnotationsToSymbol=true` on JDK 21 and `net.ltgt.errorprone` 4.1.0 does not pass it, which is what the `JavaCompile` block in `build.gradle` is for. Removing it breaks compilation.

`.gitignore` ignores `*.md` and `.*` wholesale and re-allows a named list, so a new Markdown file or dotfile at the root has to join that list before it can be committed.

## Java conventions

Formatting is Spotless with the Eclipse profile from `conventions`: four spaces, 120 columns for code, 100 for comments, and the license header on every `.java` file. Run `spotlessApply` instead of hand-formatting. A region that has to keep its own shape can be fenced with `// spotless:off` and `// spotless:on`, or with the Eclipse `@formatter:off` tag.

Null safety is JSpecify. `@NullMarked` goes on the package in `package-info.java`, and `@Nullable` marks the exceptions.

Members are ordered alphabetically within their kind: nested types, then fields, then constructors, then methods. Another order is allowed where it carries meaning and the Javadoc says so. `Banner.LAYOUTS` runs richest to leanest because `compose` renders the first rung that fits the terminal.

Javadoc covers private and package-private members too, and `aggregateJavadoc` runs with `-Werror`, so a missing `@param` fails CI.

Banner art belongs in `src/main/resources` as a `.txt` resource rather than in a Java text block: it needs no escaping, diffs as plain text, and art tooling can edit it. Nothing preserves trailing whitespace — Spotless trims it from sources and the pre-commit hook from everything else — so art must never depend on it; padding is applied at render time, as `Banner.rightPad` does.

Code that touches a terminal or the environment splits into a thin private gatherer and a pure package-private decider, so a test can drive the decision with no terminal involved. `Banner.colorMode(boolean, String, Integer, String)`, `Banner.terminalWidth(int, String)`, and `Banner.compose` are the shape to copy.

## Tests

JUnit Jupiter. A parameterized case is a record named `<Something>Parameters` carrying a `name` field, with a `static Stream<…> methodName()` provider directly above the `@MethodSource @ParameterizedTest` of the same name. Test methods are named `member__scenario`, as in `colorMode__ladder`. Bodies are segmented with `// ARRANGE //`, `// ACT //`, and `// ASSERT //`. Assertions describe structure, such as whether the mark is present or which SGR form was emitted, rather than pinning exact art, so the art can be redrawn without rewriting the tests. One exception is deliberate: `BannerTest.mark__widthPinned` pins the mark's width to catch an accidental change to `mark.txt`.

## CI and releases

Pull requests run tests with a JaCoCo coverage comment, Javadoc, and Spotless. The Spotless job applies formatting and pushes a `style:` commit on same-repo pull requests, and only checks on forks. Pull request titles are linted as Conventional Commits with a lowercase subject.

Releasing takes two steps. The Prepare Release workflow, dispatched manually with a version, runs `bump-version.sh`, generates `CHANGELOG.md` through the git-changelog plugin against a temporary tag, and opens a `chore: prepare release <version>` pull request. Pushing the `v<version>` tag afterwards builds and cuts a GitHub release whose notes are the matching `CHANGELOG.md` section.

No `maven-publish` wiring exists yet, so nothing publishes the coordinates README documents.

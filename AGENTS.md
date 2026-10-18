# AGENTS.md

## Repository

coordinatekit-foundation is the base layer CoordinateKit's other repositories build on. Anything more than one repository needs is implemented here once and consumed as a published library, never copied. Ten modules live here, all under the group `org.coordinatekit.foundation`:

- `architecture` publishes `FoundationArchitecture`, the ArchUnit rules a module runs from one `ArchitectureTest`.
- `cli-brand` publishes `Banner`, which renders the brand art.
- `conventions` publishes the Eclipse formatter profile and the license header, as two files and no code.
- `changelog-gradle` is the plugin that configures git-changelog with CoordinateKit's template.
- `aggregate-javadoc-gradle` is the plugin that registers a root `aggregateJavadoc` task.
- `concordance`, `concordance-errorprone`, and `concordance-gradle` are the member-order rule below: its annotations, the Error Prone check that enforces it, and the plugin that wires the check in.
- `third-party-licenses-gradle` is the plugin that checks dependency licenses and renders `THIRD-PARTY-LICENSES.txt`.
- `version-references-gradle` is the plugin that rewrites the version references in a build's documents at release time.

Each module's design and its traps are documented where they apply, not here. Read the class Javadoc of the plugin or main class before changing a module, and the class Javadoc of a test before changing that suite. The root `build.gradle` and `buildSrc/build.gradle` comment every block of wiring with the reason it is there, and `gradle/libs.versions.toml` states the update policy for each group. README.md has every module's consumer recipe, and RELEASE.md the release flow, its credentials, and its failure recovery.

## Commands

JDK 21, Gradle through the wrapper.

```
./gradlew check                 # compile, tests, Error Prone, Spotless check, POM validation
./gradlew test
./gradlew :cli-brand:test --tests '*BannerTest'
./gradlew :cli-brand:test --tests '*BannerTest.markSegments*'   # single test method
./gradlew spotlessApply
./gradlew :concordance-gradle:test --tests '*ConcordancePluginTest'  # runs a real Gradle build
./gradlew :aggregate-javadoc-gradle:test   # runs a real Gradle build
./gradlew :version-references-gradle:test    # runs a real Gradle build
./gradlew aggregateJacocoReport # build/reports/jacoco/aggregateJacocoReport/html/index.html
./gradlew aggregateJavadoc      # runs with -Werror; any Javadoc warning is a failure
```

Run `pre-commit install` once per clone. The hooks run prettier, `./gradlew spotlessApply`, and `./gradlew check` before every commit, and enforce Conventional Commits on the message. A commit is slow and arrives pre-verified.

## Build layout

The root `build.gradle` owns all module wiring. A subproject's own `build.gradle` holds its `description` and its dependencies, plus a `gradlePlugin { plugins { ... } }` block if it publishes a plugin, and nothing else. Every subproject gets Spotless, `java-library`, and the Maven Central publishing setup; every one except `conventions`, which has no sources, also gets JaCoCo, Error Prone, JSpecify as `api`, and JUnit.

A new module needs an `include` in `settings.gradle` and a `build.gradle` setting its `description` and naming its dependencies. Everything else is inherited, including its place in the single Central deployment. Its package has to be the group plus the module name with dashes turned into dots, so `cli-brand` lives in `org.coordinatekit.foundation.cli.brand`, because `aggregateJavadoc` derives each module's Javadoc group from its name. A name segment whose display form is not the segment capitalised, such as `cli` or `errorprone`, goes in the `wordForms` map of the root `build.gradle`'s `aggregateJavadoc` block.

`buildSrc` compiles a second copy of `changelog-gradle`, `aggregate-javadoc-gradle`, and `version-references-gradle`, so the root build applies them from the working tree. Edit the real module, never anything under `buildSrc`. Because of that second compilation, `aggregate-javadoc-gradle` may use only the Gradle API and JSpecify, `version-references-gradle` only those and JGit, and neither may use Concordance's annotations.

gradle-release writes the project version in `gradle.properties`, and `rewriteVersionReferences` moves every other version reference across the tracked files in the release commit. Examples in README.md and RELEASE.md keep themselves current only in the forms the rewrite recognises; the comment at the top of README.md says which.

`.gitignore` ignores `*.md` and `.*` wholesale and re-allows a named list, so a new Markdown file or dotfile has to join that list before it can be committed. Without it, `git add` skips the file silently.

## Java conventions

Formatting is Spotless with the Eclipse profile from `conventions`: four spaces, 120 columns for code, 100 for comments, and the license header on every `.java` file. Run `spotlessApply` instead of hand-formatting. A region that has to keep its own shape can be fenced with `// spotless:off` and `// spotless:on`, or with the Eclipse `@formatter:off` tag.

Null safety is JSpecify. `@NullMarked` goes on the package in `package-info.java`, and `@Nullable` marks the exceptions.

Member order is Concordance, the rule this repository also publishes: enum constants, then constants (`static final` fields), then fields, then constructors, then methods, with each category sorted alphabetically and case-insensitively inside itself. Constructors are the one exception, running by ascending parameter count, and static and instance methods sort as one sequence rather than in two blocks. Nested types can sit anywhere in a type, and their own members are checked like any other type's. The build runs the check over main and test sources alike, so a misplaced member fails the build with a message naming both members involved. Sorting test methods by name groups them by the member under test, because of the `member__scenario` naming, and a `@MethodSource` provider and its test share a name, so they stay adjacent.

Another order is allowed where it carries meaning, and it is declared rather than merely commented. `@IntentionalOrder(members = {...}, reason = "...")` on a type frees whole categories of its members, as `MemberCategory` uses on its own constants. `@IgnoreOrder(reason = "...")` on one member takes it out of the check entirely, so the members either side of it compare with each other. Both require a reason. A logger field and a JUnit lifecycle method need neither: the build lists `org.slf4j.Logger` and the four Jupiter lifecycle annotations as exemptions.

Javadoc covers private and package-private members too, and `aggregateJavadoc` runs with `-Werror`, so a missing `@param` fails CI.

Code that touches a terminal, the environment, or Git splits into a thin gatherer and a pure package-private decider, so a test can drive the decision with no terminal, environment, or repository involved. `Banner.colorMode(boolean, String, Integer, String)`, `Banner.terminalWidth(int, String)`, and `Banner.compose` are the shape to copy.

## Tests

JUnit Jupiter. A parameterized case is a record named `<Something>Parameters` carrying a `name` field, with a `static Stream<…> methodName()` provider directly above the `@MethodSource @ParameterizedTest` of the same name. Test methods are named `member__scenario`, as in `colorMode__ladder`. Bodies are segmented with `// ARRANGE //`, `// ACT //`, and `// ASSERT //`. Assertions describe structure rather than pinning exact output, so the output can change without rewriting the tests.

Every plugin module tests its plugin through TestKit builds as well as `ProjectBuilder` projects. Each TestKit fixture writes the `testKit.fixtureJvmArgs` system property into its `gradle.properties` as `org.gradle.jvmargs`, or the fixture's coverage never reaches the report; copy an existing suite's `writeFixture`.

## CI and releases

Pull requests run tests with a JaCoCo coverage comment, Javadoc, and Spotless. The Spotless job applies formatting and pushes a `style:` commit on same-repo pull requests, and only checks on forks. Pull request titles are linted as Conventional Commits with a lowercase subject.

Releasing is the dispatched `release.yml` workflow. Its `publish` job goes live on Maven Central with no undo, so read RELEASE.md before changing the release or publishing wiring.

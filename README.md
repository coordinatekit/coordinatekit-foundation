# coordinatekit-foundation

The base layer CoordinateKit's projects build on. Functionality more than one repository needs is implemented here once and consumed as a published library.

It includes `cli-brand`, the brand banner CoordinateKit's command-line tools print; `changelog-gradle`, the Gradle plugin that generates `CHANGELOG.md` from conventional commits; `conventions`, the Eclipse formatter profile and license header CoordinateKit's Java sources are formatted against; Concordance, the member-order rule those sources follow, which takes three modules of its own; two Gradle plugins that aggregate a multi-module build's Javadoc and JaCoCo coverage; `third-party-licenses-gradle`, the plugin that checks a build's dependency licenses and renders its third-party attribution file; and `bump-version-gradle`, the plugin that moves a build's version across every tracked file that names it. Every jar comes straight from Maven Central; see [RELEASE.md](RELEASE.md) for how a release ships and how to depend on a `-SNAPSHOT` build instead.

## CLI brand

`Banner` renders the globe mark and wordmark, choosing a layout that fits the terminal's width and dropping color when output is piped or whenever the caller's ANSI decision says no, however the consumer computes it: `NO_COLOR`, a `--ansi` flag, or anything else. Add it as an ordinary dependency.

```groovy
dependencies {
    implementation "org.coordinatekit.foundation:cli-brand:0.3.0"
}
```

`render` returns the art as a string, so printing it is the whole integration; where it appears and the ANSI decision belong to the consumer:

```java
System.out.print(new Banner().render(ansiEnabled));
```

## Changelog

`changelog-gradle` publishes a Gradle plugin, applied under the id `org.coordinatekit.foundation.changelog`, that generates `CHANGELOG.md` from conventional commits. It applies [git-changelog](https://github.com/tomasbjerre/git-changelog-gradle-plugin) and configures its `gitChangelog` task with CoordinateKit's template, which lists Breaking Changes, Deprecated, Features, Bug Fixes, Performance, Build, and Reverts for each release, linking every entry to its pull request or, for a commit that landed without one, to the commit. A squash-merged commit's trailing `(#NN)` becomes the pull request link, and a `DEPRECATED:` footer files a commit under Deprecated.

git-changelog publishes to the Gradle Plugin Portal and this plugin to Maven Central, so `settings.gradle` has to name both among its plugin repositories before the `plugins` block can resolve either:

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The links are built from `repoUrl` in `gradle.properties`.

```groovy
plugins {
    id "org.coordinatekit.foundation.changelog" version "0.3.0"
}
```

```properties
repoUrl=https://github.com/coordinatekit/crf
```

`./gradlew gitChangelog` then rewrites `CHANGELOG.md` from the whole history. A build that sets no `repoUrl` fails when the task runs. The task renders into its own temporary directory, and the plugin moves the result over `CHANGELOG.md` only once it exists, so a failed run fails the build and leaves the previous file untouched, even when git-changelog itself only logs the error and finishes green.

A repository whose earlier releases predate conventional commits starts the changelog after the last of them and carries those releases in a Markdown file that is appended after the generated ones. Both properties are optional:

```groovy
foundationChangelog {
    fromRevision = "v0.1.0"
    initialRelease = file(".infra/changelog_initial_release.md")
}
```

## Concordance

Concordance is the member-order rule CoordinateKit's Java sources follow. A type declares its enum constants, then its constants, then its fields, then its constructors, then its methods, and each category is sorted alphabetically and case-insensitively inside itself. Constructors are the one exception, running by ascending parameter count. Nested types can sit anywhere in a type, and their own members are checked like any other type's. An Error Prone check enforces the rule and a Gradle plugin configures the check, so a consuming build writes no `-Xep` flags of its own.

| Module                                                | What it holds                                                                                 |
| ----------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `org.coordinatekit.foundation:concordance`            | The two exemption annotations and `MemberCategory`. The only one a consumer compiles against. |
| `org.coordinatekit.foundation:concordance-errorprone` | The check. It belongs on Error Prone's own classpath, which the plugin arranges.              |
| `org.coordinatekit.foundation:concordance-gradle`     | The Gradle plugin, applied under the id `org.coordinatekit.foundation.concordance`.           |

The plugin publishes to Maven Central rather than to the Gradle Plugin Portal, so `settings.gradle` has to name Central among its plugin repositories before the `plugins` block can resolve it:

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The build then applies it beside `net.ltgt.errorprone`. The plugin reacts to that one rather than applying it, so the Error Prone version, the `error_prone_core` coordinate, and the compiler arguments all stay yours; a build that forgets the line fails with a message naming it. The check jar is resolved at the plugin's own version, so the two are always the same release. The check is built against Error Prone 2.50.0; consuming a significantly older or newer release may cause binary incompatibilities.

```groovy
plugins {
    id "net.ltgt.errorprone" version "5.1.0"
    id "org.coordinatekit.foundation.concordance" version "0.3.0"
}

dependencies {
    errorprone "com.google.errorprone:error_prone_core:2.50.0"
    compileOnly "org.coordinatekit.foundation:concordance:0.3.0"
    testCompileOnly "org.coordinatekit.foundation:concordance:0.3.0"
}

concordance {
    scaffoldingFieldTypes = ["org.slf4j.Logger"]
    lifecycleAnnotations = [
        "org.junit.jupiter.api.BeforeAll", "org.junit.jupiter.api.BeforeEach",
        "org.junit.jupiter.api.AfterEach", "org.junit.jupiter.api.AfterAll",
    ]
}
```

Both lists are empty by default and matched by fully qualified name against resolved types, so nothing is exempt unless a build says so. A field of a scaffolding type, such as a logger, and a method carrying a lifecycle annotation are invisible to the check, and the members on either side of one compare with each other. `severity` is the third property and defaults to `ERROR`, because a build that applies the plugin has adopted the rule; the check's own declared severity is `WARNING`, so it never breaks a build it arrives in unasked.

Two annotations grant the exemptions a build cannot express as configuration, and both require a reason. `@IntentionalOrder(members = {...}, reason = "...")` on a type frees whole categories of its members, for a type whose declaration order carries meaning the alphabet would destroy, such as a width ladder running richest to leanest. `@IgnoreOrder(reason = "...")` on a single member takes it out of the check entirely.

## Aggregate Javadoc and JaCoCo

A multi-module build publishes one set of Javadoc pages and one coverage report, not one per module. Two Gradle plugins register the root project's `aggregateJavadoc` and `aggregateJacocoReport` tasks, so a repository takes the wiring as a plugin instead of copying it into its own build script.

| Module                                                  | What it holds                                                                     |
| ------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `org.coordinatekit.foundation:aggregate-javadoc-gradle` | The plugin applied under the id `org.coordinatekit.foundation.aggregate-javadoc`. |
| `org.coordinatekit.foundation:aggregate-jacoco-gradle`  | The plugin applied under the id `org.coordinatekit.foundation.aggregate-jacoco`.  |

Both plugins publish to Maven Central rather than to the Gradle Plugin Portal, so `settings.gradle` has to name Central among its plugin repositories. For `-SNAPSHOT` versions, see [RELEASE.md](RELEASE.md#consuming-snapshots):

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The root project applies whichever plugins it wants and configures them in a block each:

```groovy
plugins {
    id "org.coordinatekit.foundation.aggregate-javadoc" version "0.3.0"
    id "org.coordinatekit.foundation.aggregate-jacoco" version "0.3.0"
}

repositories {
    mavenCentral()
}

aggregateJavadoc {
    title = "Example"
    links = ["https://docs.oracle.com/en/java/javase/21/docs/api/"]
    wordForms = ["cli": "CLI"]
}

aggregateJacoco {
    projects = subprojects.findAll { it.name != "docs" }
}
```

Each block has a `projects` property naming the modules the task covers. It defaults to every subproject that applies `java` for Javadoc and every subproject that applies `jacoco` for the coverage report, so the block is only needed to narrow or widen that. `aggregateJavadoc.title` is the one required property, and a build that leaves it out fails at the end of evaluation with a message naming it. `links` and `wordForms` are empty by default.

`aggregateJavadoc` writes to `build/docs/aggregateJavadoc` and fails on any Javadoc warning. Each module gets a tab of its own, so its package has to be the module's own group plus its name with the dashes turned into dots: `cli-brand` in group `org.example` lives in `org.example.cli.brand`. Javadoc puts a package in the first group whose pattern matches it, so the plugin registers the longest module name first. Without that, the pattern for `concordance` would also claim the packages of `concordance-gradle`. A tab is titled by the module name's segments, each capitalised, and `wordForms` overrides the ones that should not be, such as `cli` for `CLI`.

The task's classpath is the compile classpath of every selected module, so a module the selection depends on is built but not documented. Anything else a build needs on the task goes through `tasks.named`, which defers the configuration until the task is realized:

```groovy
tasks.named("aggregateJavadoc", Javadoc) {
    options.addMultilineStringsOption("-add-exports").value = ["jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"]
}
```

The settings that depend on the selection are read once every project has been evaluated, so realizing the task early, as `allprojects { tasks.withType(Javadoc) { } }` does, is safe. The title, the links, and the groups come only from the `aggregateJavadoc` block, and a `tasks.named` action that sets the title is overwritten. The Javadoc tool comes from the root project's toolchain, so a build that sets a toolchain only in `subprojects { }` will run the task with the daemon's JDK.

`aggregateJacocoReport` writes XML and HTML to `build/reports/jacoco/aggregateJacocoReport`. It runs each selected module's `test` task and reads the execution data that module's own `jacocoTestReport` reads, so any extra data file a build adds there reaches the aggregate too. A selected module without a `jacocoTestReport` task fails the build with a message naming it and `aggregateJacoco.projects`. JaCoCo's Ant tasks resolve against the root project's own repositories, which is why the example declares `mavenCentral()` there.

## Conventions

The jar holds two entries and no code:

```
org/coordinatekit/foundation/conventions/eclipse_java_coordinatekit.xml
org/coordinatekit/foundation/conventions/license_header.txt
```

It is not a Gradle plugin and it configures nothing on its own. A consumer loads whichever file it wants and wires it into its own build, so it keeps control of its formatting setup and can adopt one file without the other.

Declare a configuration to hold the jar, then read the formatter profile out of it:

```groovy
configurations {
    conventions
}

dependencies {
    conventions "org.coordinatekit.foundation:conventions:0.3.0"
}

def conventionsBase = "org/coordinatekit/foundation/conventions"

spotless {
    java {
        eclipse("4.21").configXml(resources.text.fromArchiveEntry(
                configurations.conventions.singleFile,
                "$conventionsBase/eclipse_java_coordinatekit.xml").asString())
        licenseHeaderFile file("config/license_header.txt")
        target "src/**/*.java"
        trimTrailingWhitespace()
        toggleOffOn()
    }
}
```

Reading the entry as a string rather than resolving it as a file matters. `configFile` resolves its argument during configuration, which would make every invocation of the consumer's build, including `./gradlew tasks`, resolve the jar as a file dependency. `configXml` and `licenseHeader` take content directly, so a single `asString()` covers it.

The example points `licenseHeaderFile` at the consumer's own file rather than at `license_header.txt` from the jar. The bundled header names CoordinateKit's copyright holder and Apache-2.0, so it suits this repository's own projects and nothing else. Take it only if that is genuinely your header.

### Loading the files without Gradle

Nothing about the jar assumes Gradle or Spotless. Both entries are plain text and can be unpacked with any zip tool, checked into a repository, or fed to a different formatter:

```
unzip -j conventions-0.3.0.jar 'org/coordinatekit/foundation/conventions/*' -d config/
```

## Third-party licenses

`org.coordinatekit.foundation:third-party-licenses-gradle` is a Gradle plugin, applied under the id `org.coordinatekit.foundation.third-party-licenses`, that checks the licenses of a build's runtime dependencies and renders the `THIRD-PARTY-LICENSES.txt` its distribution ships. It applies and configures the [dependency-license-report](https://github.com/jk1/Gradle-License-Report) plugin itself, so a consuming build never touches that plugin's own block. The plugin holds logic only. The license texts, the allowlist, the normalizer bundle, the license overrides, and the copyright notices are the consumer's compliance decisions and stay in the consumer's repository.

The plugin itself publishes to Maven Central, but the license report plugin it applies publishes only to the Gradle Plugin Portal, so `settings.gradle` needs both repositories in its plugin resolution:

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The build applies the plugin beside `application` and describes its licenses in a `thirdPartyLicenses` block:

```groovy
plugins {
    id "application"
    id "org.coordinatekit.foundation.third-party-licenses" version "0.3.0"
}

thirdPartyLicenses {
    excludeGroups = ["org.example"]
    excludeBoms = true
    licenseOverrides = ["org.example.legacy:legacy-core": "MIT License"]
    copyrightNotices = ["org.example.legacy:legacy-core": "Copyright (c) 2009 Example Authors"]
    licenses {
        register("Apache License, Version 2.0") {
            text = file("licenses/Apache-2.0.txt")
            includeNoticeFile = true
        }
        register("MIT License") {
            text = file("licenses/MIT.txt")
            noticeRequired = true
        }
        register("Eclipse Public License - v 2.0") {
            text = file("licenses/EPL-2.0.txt")
            reciprocalLabel = "the Eclipse Public License"
        }
    }
}
```

Modules are keyed `group:artifact`. Each license is registered by the normalized name the license report gives it, and only `text` is required. `noticeRequired` fails the build for a dependency under that license with no entry in `copyrightNotices`, which MIT and the BSD licenses need because keeping the copyright notice is their obligation. `includeNoticeFile` copies a dependency's own notice file out of its jar—searched for in any of `META-INF/NOTICE`, `NOTICE`, `META-INF/NOTICE.txt`, `NOTICE.txt`, `META-INF/NOTICE.md`, or `NOTICE.md` (case-insensitive)—which Apache 2.0 asks for. `reciprocalLabel` marks a license as reciprocal or copyleft and names its family in the source-availability sentence of the preamble, which lists exactly the families present among the dependencies. `excludeBoms` is `false` by default.

Two files sit beside the build script by default, and both locations can be changed through `allowedLicensesFile` and `normalizerBundleFile`:

- `allowed-licenses.json` lists the license each runtime dependency may carry, in the format of the license report's `checkLicense` task. An entry with an empty `moduleLicense` allows a dependency whose POM declares none, and a `licenseOverrides` entry supplies the license name for the attribution (but the allowlist check still validates the POM's declared license, so the override does not avoid the need for an allowlist entry for what the POM actually declares). When a dependency declares several licenses, the attribution names the first one the allowlist approves for that module, preferring a registered one. The allowlist cannot choose between two licenses it approves, so `licenseOverrides` is the way to pick one. Every non-empty `moduleLicense` has to be satisfied by a registered license, and the project fails at evaluation if one is not. As in the license report, a `moduleLicense` is a regular expression that must match a whole registered name, and `.*` matches anything, so `Apache.*` is satisfied by a registered `Apache License, Version 2.0`. The file is read as leniently as the report reads it, so comments, unquoted keys, and single-quoted strings are fine.
- `license-normalizer-bundle.json` maps the spellings POMs use onto the names the rest of the configuration uses. It is merged with the license report's built-in bundle. When the file is absent, no normalizer runs.

`checkLicense` runs as part of `check` and fails on a dependency whose license is not allowed. `generateThirdPartyLicenses` renders the attribution into `build/reports/third-party-licenses` and, under the `application` plugin, adds it to the main distribution. It fails on a dependency whose license is not registered and on a required copyright notice that is missing, and both messages name the block that fixes them. Both tasks read `runtimeClasspath`, so the `java` plugin has to be applied.

The preamble of the rendered file says each dependency ships as a separate jar under `lib/`, and the source-availability sentence relies on that. It suits a distribution that lays its jars out under `lib/`, as `application` does, and does not suit a fat jar.

## Bump version

`org.coordinatekit.foundation:bump-version-gradle` is a Gradle plugin, applied under the id `org.coordinatekit.foundation.bump-version`, that moves a build's version across every tracked file that names it. It replaces the version-bumping script each CoordinateKit repository used to copy, so a new document format becomes a pull request here rather than a local edit in each. Every anchor comes from the Gradle model: the root project's group, the names of its subprojects, and the root project's own name. A repository configures only the paths to leave alone.

The plugin publishes to Maven Central and needs nothing from the Gradle Plugin Portal, so `settings.gradle` needs only `mavenCentral()` among its plugin repositories. For `-SNAPSHOT` versions, see [RELEASE.md](RELEASE.md#consuming-snapshots):

```groovy
pluginManagement {
    repositories {
        mavenCentral()
    }
}
```

The plugin goes on the root project, and only there. Applying it to a subproject fails the build.

```groovy
plugins {
    id "org.coordinatekit.foundation.bump-version" version "0.4.0-SNAPSHOT"
}

group = "com.example.widgets"
version = "1.2.0-SNAPSHOT"
```

The version can instead be a `version=` entry in `gradle.properties`. Either way the task reads it from the build, and it reads the group the same way, so both can be set after the `plugins` block.

```
./gradlew bumpVersion --to=<version>
```

The task lists the files Git tracks under the root project, skips the binary ones, and rewrites each place a file names the current version. `--from=<version>` names the current version when it is not the build's, for a build that declares none. A build that tracks a file as data, such as a test fixture or an archived document, leaves it out with `exclude`:

```groovy
foundationVersion {
    exclude "docs/archive/**", "src/test/resources/**"
}
```

Globs are relative to the root project and use `/`. `*` matches within one path segment, `?` matches one character, and `**` matches across segments. A leading `**/` also matches at the root, and a glob ending in `/` excludes everything under that directory.

### What it rewrites

Each rule is anchored to the group, the module names, or the root project's name, so a dependency a document happens to name is left alone. With the group `com.example.widgets`, a module `alpha-core`, and a root project `widgets`, the forms are:

| Form                 | Example                                                                                             |
| -------------------- | --------------------------------------------------------------------------------------------------- |
| Build version        | `version=1.2.0-SNAPSHOT`, `version = "1.2.0-SNAPSHOT"`, or a POM `<version>` outside any dependency |
| Coordinate           | `com.example.widgets:alpha-core:1.1.0`                                                              |
| Maven dependency     | a `<dependency>` block with that group and a module as its `artifactId`                             |
| Jar filename         | `alpha-core-1.1.0.jar`, `alpha-core-1.1.0-sources.jar`                                              |
| Plugin id            | `id "com.example.widgets.gizmo" version "1.1.0"`, in Groovy or Kotlin form                          |
| Archive name         | `widgets-1.1.0.tar.gz`, or a `widgets-1.1.0/` directory inside one                                  |
| Printed version line | `widgets 1.1.0 (build 7)`                                                                           |

A version held in a property, such as `${widgets.version}`, is never rewritten.

Two invariants keep the examples in a repository's documents self-maintaining. The forms that name the last release, which are the coordinates, Maven dependencies, jar filenames, plugin ids, archive names, and version lines, are rewritten when the new version is a release and are left alone when it is a `-SNAPSHOT`, so a document keeps advertising the version that was just released while `main` moves on. Snapshot coordinates and Maven dependencies, which name the version in development, work the other way: a bump to a `-SNAPSHOT` rewrites them and a release bump leaves them alone. A line that shows a `-SNAPSHOT` is never turned into a release by the first group. The exception is a jar filename, plugin id, archive name, or version line written at exactly the current `-SNAPSHOT`, which the bump moves along with the version, so the release bump turns an example written at the snapshot into the release.

### The check

Before it writes anything, the task looks for a line that names the project and that no rule recognised. A line is reported when it contains a coordinate of the project's group, a plugin id of that group, a module jar filename, or a root archive name, shows a version other than the new one, and is of the kind the bump moves. The build fails listing each `file:line`, and no file is written:

```
These lines name the project with a version that no rule recognises. Nothing was written.
  docs/setup.md:14
Add a rule to coordinatekit-foundation, or leave the file out with foundationVersion { exclude "<path>" }.
```

The same refusal covers a current version that appears in no tracked file, a bump that changes no file, and a build whose own version declaration the bump did not find. The check cannot see a version held in a map notation, a Gradle declaration split across lines, or a property, so those need the file reviewed by hand.

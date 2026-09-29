# coordinatekit-foundation

The base layer CoordinateKit's projects build on. Functionality more than one repository needs is implemented here once and consumed as a published library.

It includes `cli-brand`, the brand banner CoordinateKit's command-line tools print; `changelog-gradle`, the Gradle plugin that generates `CHANGELOG.md` from conventional commits; `conventions`, the Eclipse formatter profile and license header CoordinateKit's Java sources are formatted against; and Concordance, the member-order rule those sources follow, which takes three modules of its own. Every jar comes straight from Maven Central; see [RELEASE.md](RELEASE.md) for how a release ships and how to depend on a `-SNAPSHOT` build instead.

## CLI brand

`Banner` renders the globe mark and wordmark, choosing a layout that fits the terminal's width and dropping color when output is piped or whenever the caller's ANSI decision says no, however the consumer computes it: `NO_COLOR`, a `--ansi` flag, or anything else. Add it as an ordinary dependency.

```groovy
dependencies {
    implementation "org.coordinatekit.foundation:cli-brand:0.2.0"
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
    id "org.coordinatekit.foundation.changelog" version "0.2.0"
}
```

```properties
repoUrl=https://github.com/coordinatekit/crf
```

`./gradlew gitChangelog` then rewrites `CHANGELOG.md` from the whole history. A build that sets no `repoUrl` fails when the task runs. git-changelog itself logs a rendering failure and finishes green, so the plugin deletes the file before the task and fails the build afterwards if the task did not write it, putting the previous file back.

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
    id "org.coordinatekit.foundation.concordance" version "0.2.0"
}

dependencies {
    errorprone "com.google.errorprone:error_prone_core:2.50.0"
    compileOnly "org.coordinatekit.foundation:concordance:0.2.0"
    testCompileOnly "org.coordinatekit.foundation:concordance:0.2.0"
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
    conventions "org.coordinatekit.foundation:conventions:0.2.0"
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
unzip -j conventions-0.2.0.jar 'org/coordinatekit/foundation/conventions/*' -d config/
```

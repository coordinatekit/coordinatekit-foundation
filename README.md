# coordinatekit-foundation

The base layer CoordinateKit's projects build on. Functionality more than one repository needs is implemented here once and consumed as a published library.

It includes `cli-brand`, the brand banner CoordinateKit's command-line tools print; `conventions`, the Eclipse formatter profile and license header CoordinateKit's Java sources are formatted against; and Concordance, the member-order rule those sources follow, which takes three modules of its own. Every jar comes straight from Maven Central; see [RELEASE.md](RELEASE.md) for how a release ships and how to depend on a `-SNAPSHOT` build instead.

## CLI brand

`Banner` renders the globe mark and wordmark, choosing a layout that fits the terminal's width and dropping color when output is piped or whenever the caller's ANSI decision says no, however the consumer computes it: `NO_COLOR`, a `--ansi` flag, or anything else. Add it as an ordinary dependency.

```groovy
dependencies {
    implementation "org.coordinatekit.foundation:cli-brand:0.1.0"
}
```

`render` returns the art as a string, so printing it is the whole integration; where it appears and the ANSI decision belong to the consumer:

```java
System.out.print(new Banner().render(ansiEnabled));
```

## Concordance

Concordance is the member-order rule CoordinateKit's Java sources follow. A type declares its enum constants, then its nested types, then its constants, then its fields, then its constructors, then its methods, and each category is sorted alphabetically and case-insensitively inside itself. Constructors are the one exception, running by ascending parameter count. An Error Prone check enforces the rule and a Gradle plugin configures the check, so a consuming build writes no `-Xep` flags of its own.

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
    conventions "org.coordinatekit.foundation:conventions:0.1.0"
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
unzip -j conventions-0.1.0.jar 'org/coordinatekit/foundation/conventions/*' -d config/
```

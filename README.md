# coordinatekit-foundation

The base layer CoordinateKit's projects build on. Functionality more than one repository needs is implemented here once and consumed as a published library.

It includes two modules: `cli-brand`, the brand banner CoordinateKit's command-line tools print, and `conventions`, the Eclipse formatter profile and license header CoordinateKit's Java sources are formatted against. Both jars come straight from Maven Central; see [RELEASE.md](RELEASE.md) for how a release ships and how to depend on a `-SNAPSHOT` build instead.

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

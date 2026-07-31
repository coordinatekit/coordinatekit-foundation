# Release

This repository publishes `cli-brand` and `conventions` to Maven Central through the [Sonatype Central Portal](https://central.sonatype.com). Tagging a release publishes both modules there as a single deployment and cuts a GitHub release; every push to `main` publishes `-SNAPSHOT` artifacts of whatever is currently in flight.

Both paths run off the `com.gradleup.nmcp.aggregation` plugin, applied to the root project, which collects each subproject's publication into one bundle rather than uploading per module. A `v*` tag runs `publishAggregationToCentralPortal`, signed and released; because there is only one bundle, Central validates and publishes both modules as a unit, so a partial release where one module goes live and the other fails validation cannot happen. The task also polls the Portal until the deployment reaches `VALIDATED` or fails the build if it reaches `FAILED`, rather than reporting success on upload alone. A push to `main` runs `publishAggregationToCentralSnapshots` whenever `gradle.properties` is on a `-SNAPSHOT`, unsigned, uploading the same aggregated bundle straight to the Portal's snapshot endpoint. Signing is gated on `isPublishingToCentral`, true only when `nmcpPublishAggregationToCentralPortal` is in the task graph, so `./gradlew build`, `publishToMavenLocal`, and snapshot uploads all run without a GPG key.

## One-time setup

The `org.coordinatekit` namespace is already verified on the Central Portal, and a GPG signing key and Portal user token already exist for this project. Setting up a new environment from scratch would mean verifying the namespace (proving ownership of the `coordinatekit.org` domain via a DNS TXT record Sonatype provides), generating a Portal user token from the Central Portal account settings, and generating a GPG key with `gpg --full-generate-key` followed by `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>` so the public key is resolvable when someone verifies a signed artifact.

Four repository secrets carry those credentials into CI: `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` (the Portal user token), and `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` (the signing key). `GPG_PRIVATE_KEY` is the ASCII-armored private key text itself, passed straight to Gradle's `useInMemoryPgpKeys`, not a base64 encoding of it.

## Publishing locally

`./gradlew publishAggregationToCentralPortal` and `./gradlew publishAggregationToCentralSnapshots` both read credentials from Gradle properties: `centralPortalUsername`, `centralPortalPassword`, `signingKey`, `signingPassword`. Put them in `~/.gradle/gradle.properties` (never in this repository), or supply them as `ORG_GRADLE_PROJECT_centralPortalUsername`-style environment variables, matching what CI does. If `signingKey` is absent, signing falls back to `useGpgCmd()` and shells out to a local `gpg` installation with whatever key it already has configured, so a local publish works with either a key pasted into Gradle properties or an ordinarily configured GPG keyring.

## The release flow

Dispatch the "Prepare Release" workflow manually with the target version. It runs `scripts/bump-version.sh` and regenerates `CHANGELOG.md`, which the git-changelog plugin builds against a temporary tag so the unreleased commits land under the new version, then opens a `chore: prepare release <version>` pull request. Merge that pull request, then push the matching `v<version>` tag. The tag push builds the project, publishes both modules to Central through `publishAggregationToCentralPortal` as one deployment, waits for Central to validate it, and, only if that publish succeeds, cuts the GitHub release from the matching `CHANGELOG.md` section. Publishing runs with `publishingType = "AUTOMATIC"`, so a validated deployment goes live on Central without a manual click on the Portal, and without an undo: the first real tag push is the live test of the whole pipeline.

After the tag ships, bump `gradle.properties` back to the next `-SNAPSHOT` so `main` resumes publishing snapshots of the next round of work: `scripts/bump-version.sh <next>-SNAPSHOT`. The script skips its coordinate and jar-filename rewrites whenever the target version ends in `-SNAPSHOT`, so README keeps advertising the version that was just released rather than the in-development one, and instead rewrites the "Consuming snapshots" coordinate below to the new `-SNAPSHOT` version, so that example always names the version `main` is currently publishing.

## Consuming snapshots

Sibling repositories that want to track unreleased work add the Portal's snapshot repository and depend on the `-SNAPSHOT` coordinate:

```groovy
repositories {
    maven { url = "https://central.sonatype.com/repository/maven-snapshots/" }
}

dependencies {
    implementation "org.coordinatekit.foundation:cli-brand:0.2.0-SNAPSHOT"
}
```

# Release

This repository publishes `aggregate-javadoc-gradle`, `changelog-gradle`, `cli-brand`, `concordance`, `concordance-errorprone`, `concordance-gradle`, `conventions`, `third-party-licenses-gradle`, and `version-references-gradle` to Maven Central through the [Sonatype Central Portal](https://central.sonatype.com). Dispatching the Release workflow publishes all nine there as a single deployment, tags the release, and cuts a GitHub release; every push to `main` publishes `-SNAPSHOT` artifacts of whatever is currently in flight.

Both paths run off the `com.gradleup.nmcp.aggregation` plugin, applied to the root project, which collects each subproject's publication into one bundle rather than uploading per module. The Release workflow runs `publishAggregationToCentralPortal`, signed and released, as the last of the build tasks `./gradlew release` runs; because there is only one bundle, Central validates and publishes every module as a unit, so a partial release where one module goes live and another fails validation cannot happen. Each of the four plugin modules contributes two publications rather than one, its jar and the marker that lets a `plugins` block resolve the plugin by id, and both ride in the same bundle. The task also polls the Portal until the deployment reaches `VALIDATED` or fails the build if it reaches `FAILED`, rather than reporting success on upload alone. A push to `main` runs `publishAggregationToCentralSnapshots` whenever `gradle.properties` is on a `-SNAPSHOT`, unsigned, uploading the same aggregated bundle straight to the Portal's snapshot endpoint. Signing is gated on `isPublishingToCentral`, true only when `nmcpPublishAggregationToCentralPortal` is in the task graph, so `./gradlew build`, `publishToMavenLocal`, and snapshot uploads all run without a GPG key.

## One-time setup

The `org.coordinatekit` namespace is already verified on the Central Portal, and a GPG signing key and Portal user token already exist for this project. Setting up a new environment from scratch would mean verifying the namespace (proving ownership of the `coordinatekit.org` domain via a DNS TXT record Sonatype provides), generating a Portal user token from the Central Portal account settings, and generating a GPG key with `gpg --full-generate-key` followed by `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>` so the public key is resolvable when someone verifies a signed artifact.

Four repository secrets carry those credentials into CI: `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` (the Portal user token), and `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` (the signing key). `GPG_PRIVATE_KEY` is the ASCII-armored private key text itself, passed straight to Gradle's `useInMemoryPgpKeys`, not a base64 encoding of it. The Release workflow reads all four, so `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` must exist as repository or organization secrets before its first run.

The `main` ruleset requires a pull request, and only an organization admin bypasses it. The `release-tags` ruleset blocks tag creation for everyone but organization and repository admins. `./gradlew release` pushes two commits to `main` and one tag, which the workflow's own `GITHUB_TOKEN` cannot do under those rules, and a push made with it would not trigger `snapshot.yml` either. A GitHub App installation token can do both, and it lives for one hour, so nothing long-lived is stored for pushing. The App is set up once, for the organization:

1. Create the App under the organization's settings, Developer settings, GitHub Apps. Its name becomes the bot identity on the commits, as `<name>[bot]`. Give it a homepage URL, uncheck the webhook, and set the `Contents` repository permission to read and write. `Metadata` read is added automatically, and nothing else is needed, because the release commits never touch `.github/workflows`, the one case that would need `Workflows` write. Restrict installation to this organization.
2. Generate a private key on the App's page. It downloads a PEM file.
3. Install the App on the organization, scoped to the repositories that release this way.
4. Store the App's numeric id and the PEM text as organization secrets named `RELEASE_APP_ID` and `RELEASE_APP_PRIVATE_KEY`, granted to those repositories.
5. Add the App as a bypass actor on the `main` ruleset and the `release-tags` ruleset, choosing it by name in each ruleset's bypass list. The `release-branches` ruleset is untouched.

The private key rotates by generating a new one on the App's page and updating the secret. A leaked token can push to the installed repositories' contents for at most an hour and nothing else. The workflow looks up the bot's user id with the App token and configures the address as `<id>+<slug>[bot]@users.noreply.github.com`, the form GitHub uses to link the commits to the App's profile and avatar. The commits are made with the `git` CLI and are not signed, so GitHub shows them as unverified whatever the address says.

## Publishing locally

`./gradlew publishAggregationToCentralPortal` and `./gradlew publishAggregationToCentralSnapshots` both read credentials from Gradle properties: `centralPortalUsername`, `centralPortalPassword`, `signingKey`, `signingPassword`. Put them in `~/.gradle/gradle.properties` (never in this repository), or supply them as `ORG_GRADLE_PROJECT_centralPortalUsername`-style environment variables, matching what CI does. If `signingKey` is absent, signing falls back to `useGpgCmd()` and shells out to a local `gpg` installation with whatever key it already has configured, so a local publish works with either a key pasted into Gradle properties or an ordinarily configured GPG keyring.

## The release flow

Dispatch the Release workflow manually with three inputs: `release`, the version to ship, such as `0.4.0`; `next`, the version development moves to afterwards, written without `-SNAPSHOT`; and `dry_run`, which defaults to false. One job runs `./gradlew release` with the App's token, and the `release` task does the rest:

1. It checks that the working tree is clean and that `main` is level with `origin`.
2. It strips `-SNAPSHOT` from `gradle.properties` and runs `rewriteVersionReferences`, which moves the version references in the documents to the release.
3. It runs the build tasks in a second nested build, with the release version in effect: `check`, `aggregateJavadoc`, `gitChangelog`, and `publishAggregationToCentralPortal`, in that order. `gitChangelog` renders the unreleased commits under the release version without a tag, so `CHANGELOG.md` is part of the release commit.
4. It commits as `chore: release 'v<release>'.`, tags `v<release>`, writes `<next>-SNAPSHOT` into `gradle.properties`, commits as `chore: start 'v<next>-SNAPSHOT'.`, and pushes both commits and the tag.

The workflow then cuts the GitHub release from the matching `CHANGELOG.md` section. Both commits are `chore:`, which the changelog template does not render, so they never appear in a later release's notes. The snapshot bump commit is pushed with the App's token, so it triggers `snapshot.yml` and `main` publishes the next `-SNAPSHOT` as it does after any other push.

Publishing runs with `publishingType = "AUTOMATIC"`, so a validated deployment goes live on Central without a manual click on the Portal, and without an undo. The order of the build tasks puts everything that can fail cheaply before the one step that cannot be repeated. A failure in `check`, the Javadoc, or the changelog template stops the release before Central is touched, and so does a failed publish. In both cases no commit and no tag exist, and `gradle.properties` is restored, so there is nothing to undo and the workflow can be dispatched again. A fresh checkout starts each run, so the rewritten documents do not carry over. A failure after Central has accepted the deployment can only be the tag push or the second commit. The artifacts are then published with no tag on `main`, and the runner's commits are gone with its checkout. The recovery is to run the same release locally from a clean `main` with `-PreleaseDryRun=true`, which makes the commits and the tag without publishing or pushing, and then push both commits and the tag by hand.

The release runs with `--no-configuration-cache`. gradle-release applies under the configuration cache, but its tasks still hold `Project` references when they execute, and the flag propagates into both nested builds. Every other invocation keeps the configuration cache on.

A dry run is dispatched with `dry_run` set, or locally with `-PreleaseDryRun=true`. It runs the whole sequence, rewrites the documents, regenerates `CHANGELOG.md`, and makes both commits and the tag, but it drops `publishAggregationToCentralPortal` from the build tasks and sets `pushToRemote = false`, so neither Central nor `origin` is touched. The workflow still prints the release notes, and skips the GitHub release. The first dispatch after any change to the flow should be a dry run.

The "Consuming snapshots" section below carries a coordinate and never a plugin id. A plugin id is rewritten to the release whatever version it shows, so a snapshot plugin id would move to the release in the release commit and the example would stop naming a snapshot.

## Consuming snapshots

Sibling repositories that want to track unreleased work add the Portal's snapshot repository and depend on the `-SNAPSHOT` coordinate:

```groovy
repositories {
    maven { url = "https://central.sonatype.com/repository/maven-snapshots/" }
}

dependencies {
    implementation "org.coordinatekit.foundation:cli-brand:0.4.0-SNAPSHOT"
}
```

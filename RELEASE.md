# Release

This repository publishes `aggregate-javadoc-gradle`, `architecture`, `changelog-gradle`, `cli-brand`, `concordance`, `concordance-errorprone`, `concordance-gradle`, `conventions`, `third-party-licenses-gradle`, and `version-references-gradle` to Maven Central through the [Sonatype Central Portal](https://central.sonatype.com). Dispatching the Release workflow publishes every module there as a single deployment, tags the release, and cuts a GitHub release; every push to `main` publishes `-SNAPSHOT` artifacts of whatever is currently in flight.

Both paths run off the `com.gradleup.nmcp.aggregation` plugin, applied to the root project, which collects each subproject's publication into one bundle rather than uploading per module. The Release workflow runs `publishAggregationToCentralPortal`, signed, from the release tag it has just created locally. The bundle is uploaded as `USER_MANAGED`, so the task polls the Portal until the deployment reaches `VALIDATED` and fails the build if it reaches `FAILED`, naming Central's errors. Central has therefore checked every POM, signature, and credential before anything is pushed, and `nmcpPublishDeployment` makes the deployment live only after the push. Because there is only one bundle, Central validates and publishes every module as a unit, so a partial release where one module goes live and another fails validation cannot happen. Each plugin module contributes two publications rather than one, its jar and the marker that lets a `plugins` block resolve the plugin by id, and both ride in the same bundle. A push to `main` runs `publishAggregationToCentralSnapshots` whenever `gradle.properties` is on a `-SNAPSHOT`, unsigned, uploading the same aggregated bundle straight to the Portal's snapshot endpoint. The `com.vanniktech.maven.publish.base` plugin creates each module's publications and fills their POMs from the `POM_*` properties in `gradle.properties`. It requires a signature for any publish of a version that is not a `-SNAPSHOT` and requires none for a snapshot, so `./gradlew build`, `publishToMavenLocal` on a snapshot version, and snapshot uploads all run without a GPG key.

## One-time setup

The `org.coordinatekit` namespace is already verified on the Central Portal, and a GPG signing key and Portal user token already exist for this project. Setting up a new environment from scratch would mean verifying the namespace (proving ownership of the `coordinatekit.org` domain via a DNS TXT record Sonatype provides), generating a Portal user token from the Central Portal account settings, and generating a GPG key with `gpg --full-generate-key` followed by `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>` so the public key is resolvable when someone verifies a signed artifact.

Four repository secrets carry those credentials into CI: `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` (the Portal user token), and `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` (the signing key). `GPG_PRIVATE_KEY` is the ASCII-armored private key text itself, not a base64 encoding of it. The workflow passes it as the `signingInMemoryKey` property, which reaches Gradle's `useInMemoryPgpKeys` unchanged. The Release workflow reads all four, so `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` must exist as repository or organization secrets before its first run.

The `main` ruleset requires a pull request, and only an organization admin bypasses it. The `release-tags` ruleset blocks tag creation for everyone but organization and repository admins. The workflow pushes two commits to `main` and one tag in a single atomic push, which its own `GITHUB_TOKEN` cannot do under those rules, and a push made with it would not trigger `snapshot.yml` either. A GitHub App installation token can do both, and it lives for one hour, so nothing long-lived is stored for pushing. The App is set up once, for the organization:

1. Create the App under the organization's settings, Developer settings, GitHub Apps. Its name becomes the bot identity on the commits, as `<name>[bot]`. Give it a homepage URL, uncheck the webhook, and set the `Contents` repository permission to read and write. `Metadata` read is added automatically, and nothing else is needed, because the release commits never touch `.github/workflows`, the one case that would need `Workflows` write. Restrict installation to this organization.
2. Generate a private key on the App's page. It downloads a PEM file.
3. Install the App on the organization, scoped to the repositories that release this way.
4. Store the App's Client ID, shown on its General page as a string beginning `Iv`, and the PEM text as organization secrets named `RELEASE_APP_CLIENT_ID` and `RELEASE_APP_PRIVATE_KEY`, granted to those repositories.
5. Add the App as a bypass actor on the `main` ruleset and the `release-tags` ruleset, choosing it by name in each ruleset's bypass list. The `release-branches` ruleset is untouched.

The private key rotates by generating a new one on the App's page and updating the secret. A leaked token can push to the installed repositories' contents for at most an hour and nothing else. The workflow looks up the bot's user id with the App token and configures the address as `<id>+<slug>[bot]@users.noreply.github.com`, the form GitHub uses to link the commits to the App's profile and avatar. The commits are made with the `git` CLI and are not signed, so GitHub shows them as unverified whatever the address says.

## Publishing locally

`./gradlew publishAggregationToCentralPortal` and `./gradlew publishAggregationToCentralSnapshots` both read credentials from Gradle properties: `centralPortalUsername`, `centralPortalPassword`, `signingInMemoryKey`, `signingInMemoryKeyPassword`. Put them in `~/.gradle/gradle.properties` (never in this repository), or supply them as `ORG_GRADLE_PROJECT_centralPortalUsername`-style environment variables, matching what CI does. If `signingInMemoryKey` is absent, a release version falls back to `useGpgCmd()` and shells out to a local `gpg` installation with whatever key it already has configured, so a local publish works with either a key pasted into Gradle properties or an ordinarily configured GPG keyring. Without that property, a `-SNAPSHOT` version is not signed at all.

## The release flow

Dispatch the Release workflow manually with three inputs: `release`, the version to ship, such as `0.4.0`; `next`, the version development moves to afterwards, written without `-SNAPSHOT`; and `dry_run`, which defaults to false. The workflow runs one job. It first runs `./gradlew release` with the App's token, and the `release` task prepares the release locally without pushing anything:

1. It checks that the working tree is clean and that `main` is level with `origin`.
2. It strips `-SNAPSHOT` from `gradle.properties` and runs `rewriteVersionReferences`, which moves the version references in the documents to the release.
3. It runs the build tasks in a second nested build, with the release version in effect: `check`, `aggregateJavadoc`, and `gitChangelog`. `gitChangelog` renders the unreleased commits under the release version without a tag, so `CHANGELOG.md` is part of the release commit.
4. It commits as `chore: release 'v<release>'.`, tags `v<release>`, writes `<next>-SNAPSHOT` into `gradle.properties`, and commits as `chore: start 'v<next>-SNAPSHOT'.`. The build sets `pushToRemote = false`, so all three stay on the runner.

The job then extracts the release notes from the matching `CHANGELOG.md` section, failing if the section is empty, and checks out the tag. It runs `publishAggregationToCentralPortal` from the tag, which uploads the bundle and waits for Central to validate it, and records the deployment id from the task's log. Only then does it push both commits and the tag with `git push --atomic`, so `origin` gets all three or none of them. If the push is rejected, the job drops the deployment through the Central API instead of publishing it. Once the push succeeds it runs `nmcpPublishDeployment` for that deployment id, which makes the release live on Central without a manual click on the Portal and without an undo, and last it cuts the GitHub release from the notes with the App's token. Both commits are `chore:`, which the changelog template does not render, so they never appear in a later release's notes. The push is made with the App's token, so it triggers `snapshot.yml`, once, on the snapshot bump at its tip, and `main` publishes the next `-SNAPSHOT` as it does after any other push.

Publishing goes live only in the last two steps, after the push. What a failure leaves behind depends on where it happens:

- A failure in `check`, the Javadoc, the changelog template, the release notes, or the upload leaves nothing on `origin` and nothing live, so the workflow can be dispatched again. A fresh checkout starts each run, so the rewritten documents do not carry over. If the upload got as far as creating a deployment, drop that deployment in the Portal first, because the job drops nothing when validation fails or times out, when the log has no deployment id, or when the run is cancelled during the upload. The Central credentials and `GPG_PRIVATE_KEY` are used for the first time in the upload, so a bad token, signing key, or POM shows up here, before the tag is pushed to `origin`.
- A rejected push leaves nothing on `origin`. The likeliest cause is a pull request merged to `main` while the build ran. The job checks that the tag is not on `origin` and drops the `VALIDATED` deployment itself, so the next dispatch, which builds on top of the merge, can follow straight away. Drop the deployment in the Portal only if the drop step failed.
- A failure in `nmcpPublishDeployment` leaves the tag and `main` on `origin` and the deployment `VALIDATED`. The job keeps that deployment, because the tag on `origin` points at it. Publish it from the Portal, or with `./gradlew nmcpPublishDeployment -PnmcpDeploymentId=<id>` and the Central credentials, then create the GitHub release from the tag by hand.
- A failure in `Create GitHub Release` after `nmcpPublishDeployment` succeeded, such as the App token being refused, leaves the version live. Create the GitHub release from the tag by hand.

"Re-run failed jobs" does not apply to this workflow. After the push it would run gradle-release again against a tag that already exists. Before the push it starts from the original commit, so it fails if `main` has moved since, and it leaves the first attempt's `VALIDATED` deployment behind. Dispatch the workflow again instead.

The release runs with `--no-configuration-cache`. gradle-release applies under the configuration cache, but its tasks still hold `Project` references when they execute, and the flag propagates into both nested builds. Every other invocation, the upload and publish steps included, keeps the configuration cache on.

A dry run is dispatched with `dry_run` set. The job runs the sequence through the upload: it rewrites the documents, regenerates `CHANGELOG.md`, makes both commits and the tag, prints the release notes, and uploads the bundle to Central, which validates it and prints the deployment id. It skips the push, the publish, and the GitHub release, so `origin` is untouched and nothing goes live. Once the notes and the deployment id are printed, the job drops the `VALIDATED` deployment through the Central API, and the run fails if the drop fails, leaving the deployment to be dropped in the Portal. The dry run signs with the real GPG key and uploads with the real Central token. The first dispatch after any change to the flow, and after any bump of `nmcp`, should be a dry run. The upload step reads the deployment id from the `Nmcp: deployment bundle '…' uploaded.` line in the task's log, which `nmcp` does not version, so a bump that rewords it fails the release with "no deployment id in the upload log". The drop step is the one place the workflow calls the Central API without `nmcp`. It sends `DELETE /api/v1/publisher/deployment/{id}` with `curl`, using the same Bearer token `nmcp` builds from the Portal user token.

A local run never pushes or publishes either, because the build always sets `pushToRemote = false`. It needs a clean `main` level with `origin`:

```bash
./gradlew release --no-configuration-cache \
  -Prelease.useAutomaticVersion=true \
  -Prelease.releaseVersion=0.4.0 \
  -Prelease.newVersion=0.5.0-SNAPSHOT
```

The run commits through the repository's pre-commit hooks, which include `./gradlew check`, and it leaves two commits and a tag in the clone. Discard them afterwards with `git tag -d v0.4.0` and `git reset --hard origin/main`.

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

/*
 * Copyright 2025-present Andy Marek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.coordinatekit.foundation.changelog.gradle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import se.bjurr.gitchangelog.plugin.gradle.GitChangelogTask;
import se.bjurr.gitchangelog.plugin.gradle.HelperParam;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Unit and functional tests for {@link ChangelogPlugin}. The conventions the plugin sets on the
 * {@code gitChangelog} task are read straight off it through {@link ProjectBuilder}, and the
 * guard's decisions are driven as plain statics over files in a temporary directory. Everything
 * that only a real build can show, the template rendering a history, the guard's actions wrapping
 * the task, and the configuration cache reusing both, runs through TestKit against a throwaway git
 * repository.
 *
 * <p>
 * JGit writes that repository's history with pinned dates, so the rendered release headings do not
 * depend on when or where the test runs. The project directory is also the repository, which is
 * where git-changelog looks by default.
 *
 * <p>
 * The fixture builds run in daemons that the test JVM's coverage agent cannot reach. When the test
 * task sets {@code testKit.fixtureJvmArgs}, {@code writeFixture} passes it on through each
 * fixture's {@code gradle.properties}, which is how the build records their coverage. Without it,
 * as under an IDE's own test runner, the fixtures run the same and record nothing.
 */
class ChangelogPluginTest {
    /**
     * One repository URL that the guard has to reject.
     *
     * @param name what the case shows
     * @param repoUrl the configured value, {@code null} for none
     */
    private record BlankRepoUrlParameters(String name, String repoUrl) {}

    /** The id the plugin is applied under. */
    private static final String PLUGIN_ID = "org.coordinatekit.foundation.changelog";

    /** The repository URL every fixture build takes its links from. */
    private static final String REPO_URL = "https://github.com/example/fixture";

    @Test
    void apply__appliesGitChangelogAndRegistersTheExtension() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);

        // ASSERT //
        assertTrue(project.getPlugins().hasPlugin("se.bjurr.gitchangelog.git-changelog-gradle-plugin"));
        assertFalse(project.getExtensions().getByType(ChangelogExtension.class).getRepoUrl().isPresent());
    }

    @Test
    void apply__failsWithoutRepoUrl(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(directory, null, "");
        Path changelog = Files.writeString(directory.resolve("CHANGELOG.md"), "previous");

        // ACT //
        BuildResult result = runner(directory).withArguments("gitChangelog").buildAndFail();

        // ASSERT //
        assertTrue(result.getOutput().contains("repoUrl="), result.getOutput());
        assertEquals("previous", Files.readString(changelog), "a rejected run leaves the file alone");
    }

    @Test
    void apply__flowsFromRevisionFromTheExtension() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(PLUGIN_ID);
        ChangelogExtension extension = project.getExtensions().getByType(ChangelogExtension.class);

        GitChangelogTask task = gitChangelog(project);
        boolean unsetBefore = !task.fromRevision.isPresent();

        // ACT //
        extension.getFromRevision().set("v0.1.0");

        // ASSERT //
        assertTrue(unsetBefore);
        assertEquals("v0.1.0", task.fromRevision.get());
    }

    @Test
    void apply__rendersTaggedHistory(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, REPO_URL, "");
        String fixHash = writeHistory(directory);

        // ACT //
        runner(directory).withArguments("gitChangelog").build();

        // ASSERT //
        String changelog = Files.readString(directory.resolve("CHANGELOG.md"));
        String hashless = changelog.replaceAll("\\(\\[[0-9a-f]+\\]\\([^)]*/commit/[0-9a-f]{40}\\)\\)", "(HASH)");
        String latest = releaseBody(hashless, "0.2.0");
        String first = releaseBody(hashless, "0.1.0");
        assertTrue(latest.startsWith("## [0.2.0] - 2026-01-05\n"), changelog);
        assertTrue(first.startsWith("## [0.1.0] - 2026-01-01\n"), changelog);

        String breaking = sectionBody(latest, "BREAKING CHANGES");
        assertTrue(breaking.contains("- drop the legacy widget (HASH)\n  the widget no longer exists\n"), changelog);
        assertFalse(breaking.contains("gadget"), changelog);

        String deprecated = sectionBody(latest, "Deprecated");
        assertTrue(
                deprecated.contains("- replace the gadget (HASH)\n  the gadget goes away next release\n"),
                changelog
        );
        assertFalse(deprecated.contains("widget"), changelog);

        String features = sectionBody(latest, "Features");
        assertTrue(features.contains("- add the widget ([#12](" + REPO_URL + "/pull/12))\n"), changelog);
        assertFalse(features.contains("gadget"), "the refactor commit files only under Deprecated:\n" + changelog);
        assertFalse(features.contains("mend"), changelog);

        String fixes = sectionBody(latest, "Bug Fixes");
        assertTrue(fixes.contains("- mend the widget (HASH)\n"), changelog);
        assertFalse(fixes.contains("add the widget"), changelog);
        assertTrue(changelog.contains("](" + REPO_URL + "/commit/" + fixHash + "))"), changelog);

        assertTrue(sectionBody(first, "Features").contains("- seed the widget (HASH)\n"), changelog);
        assertFalse(first.contains("mend the widget"), changelog);
        assertFalse(changelog.contains("start the repository"), "chore commits have no section");
        assertTrue(latest.contains("[0.2.0]: " + REPO_URL + "/releases/tag/v0.2.0"), changelog);
        assertFalse(changelog.contains("(#12)"), "the pull request reference moves out of the description");
    }

    @Test
    void apply__restoresThePreviousFileOnAReusedConfigurationCache(@TempDir Path directory)
            throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, REPO_URL, """

                foundationChangelog {
                    initialRelease = file("initial.md")
                }
                """);
        writeHistory(directory);
        Path initial = Files.writeString(directory.resolve("initial.md"), "## [0.0.1] - earlier\n");
        GradleRunner runner = runner(directory).withArguments("gitChangelog", "--configuration-cache");
        runner.build();
        Path changelog = directory.resolve("CHANGELOG.md");
        String rendered = Files.readString(changelog);
        // The file is read when the template renders, so swapping it leaves the cached graph valid.
        Files.delete(initial);
        Files.createDirectory(initial);

        // ACT //
        BuildResult result = runner.buildAndFail();

        // ASSERT //
        assertTrue(result.getOutput().contains("Reusing configuration cache"), result.getOutput());
        assertTrue(result.getOutput().contains("did not write"), result.getOutput());
        assertEquals(rendered, Files.readString(changelog), "the backup survives the cache round trip");
    }

    @Test
    void apply__restoresThePreviousFileWhenRenderFails(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, REPO_URL, """

                foundationChangelog {
                    initialRelease = file("initial.md")
                }
                """);
        writeHistory(directory);
        Files.createDirectory(directory.resolve("initial.md"));
        Path changelog = Files.writeString(directory.resolve("CHANGELOG.md"), "previous");

        // ACT //
        BuildResult result = runner(directory).withArguments("gitChangelog").buildAndFail();

        // ASSERT //
        assertTrue(result.getOutput().contains("did not write"), result.getOutput());
        assertEquals("previous", Files.readString(changelog), "a failed render loses nothing");
    }

    @Test
    void apply__reusesTheConfigurationCache(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, REPO_URL, "");
        writeHistory(directory);
        GradleRunner runner = runner(directory).withArguments("gitChangelog", "--configuration-cache");

        // ACT //
        runner.build();
        BuildResult result = runner.build();

        // ASSERT //
        assertTrue(result.getOutput().contains("Reusing configuration cache"), result.getOutput());
        String changelog = Files.readString(directory.resolve("CHANGELOG.md"));
        assertTrue(changelog.contains("## [0.2.0]"), "the guard still ran on the reused graph:\n" + changelog);
    }

    @Test
    void apply__setsTheTaskConventions() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);
        GitChangelogTask task = gitChangelog(project);

        // ASSERT //
        assertTrue(task.templateContent.get().contains("## [{{subString name 1}}]"));
        assertEquals("yyyy-MM-dd", task.dateFormat.get());
        assertEquals(
                List.of(
                        "description",
                        "pullRequest",
                        "ifContainsDeprecated",
                        "ifCommitDeprecated",
                        "repoUrl",
                        "initialRelease"
                ),
                task.handlebarsHelpers.get().stream().map(HelperParam::getName).toList()
        );
    }

    @Test
    void apply__startsAfterFromRevisionAndAppendsInitialRelease(@TempDir Path directory)
            throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, REPO_URL, """

                foundationChangelog {
                    fromRevision = "v0.1.0"
                    initialRelease = file("initial.md")
                }
                """);
        writeHistory(directory);
        Files.writeString(directory.resolve("initial.md"), "## [0.0.1] - earlier\n");

        // ACT //
        runner(directory).withArguments("gitChangelog").build();

        // ASSERT //
        String changelog = Files.readString(directory.resolve("CHANGELOG.md"));
        assertTrue(changelog.contains("## [0.2.0]"), changelog);
        assertFalse(changelog.contains("seed the widget"), changelog);
        assertTrue(changelog.stripTrailing().endsWith("## [0.0.1] - earlier"), changelog);
    }

    @Test
    void apply__takesTheRepoUrlFromAGradleProperty(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Files.writeString(directory.resolve("gradle.properties"), "repoUrl=https://github.com/coordinatekit/crf\n");
        Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);

        // ASSERT //
        assertEquals(
                "https://github.com/coordinatekit/crf",
                project.getExtensions().getByType(ChangelogExtension.class).getRepoUrl().get()
        );
    }

    /**
     * Writes a file and commits it under the fixed identity for the given day.
     *
     * @param git the repository to commit to
     * @param message the full commit message, footers included
     * @param day the day of January 2026 the commit is dated
     * @return the full hash of the new commit
     * @throws GitAPIException if the commit fails
     * @throws IOException if the file cannot be written
     */
    private static String commit(Git git, String message, int day) throws GitAPIException, IOException {
        Files.writeString(git.getRepository().getWorkTree().toPath().resolve("history.txt"), message);
        git.add().addFilepattern("history.txt").call();
        PersonIdent person = person(day);
        return git.commit().setMessage(message).setAuthor(person).setCommitter(person).setSign(false).call().name();
    }

    private static GitChangelogTask gitChangelog(Project project) {
        return (GitChangelogTask) project.getTasks().getByName("gitChangelog");
    }

    /**
     * Returns the identity every fixture commit and tag carries. The time is noon UTC, which renders as
     * the same {@code yyyy-MM-dd} date in whatever zone the daemon runs.
     *
     * @param day the day of January 2026
     * @return the identity dated at noon UTC on that day
     */
    private static PersonIdent person(int day) {
        return new PersonIdent(
                "Fixture",
                "fixture@example.com",
                Instant.parse("2026-01-%02dT12:00:00Z".formatted(day)),
                ZoneOffset.UTC
        );
    }

    static Stream<BlankRepoUrlParameters> prepare__blankRepoUrl() {
        return Stream.of(
                new BlankRepoUrlParameters("absent", null),
                new BlankRepoUrlParameters("empty", ""),
                new BlankRepoUrlParameters("whitespace", "  ")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void prepare__blankRepoUrl(BlankRepoUrlParameters parameters, @TempDir Path directory) throws IOException {
        // ARRANGE //
        Path changelog = Files.writeString(directory.resolve("CHANGELOG.md"), "kept");

        // ACT //
        GradleException thrown = assertThrows(
                GradleException.class,
                () -> ChangelogPlugin.prepare(parameters.repoUrl(), changelog)
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains("repoUrl="), thrown.getMessage());
        assertEquals("kept", Files.readString(changelog), "a rejected run leaves the file alone");
    }

    @Test
    void prepare__returnsNothingWhenThereIsNoFile(@TempDir Path directory) {
        // ARRANGE //
        Path changelog = directory.resolve("CHANGELOG.md");

        // ACT //
        byte[] previous = ChangelogPlugin.prepare("https://github.com/coordinatekit/crf", changelog);

        // ASSERT //
        assertNull(previous);
    }

    @Test
    void prepare__setsAnExistingFileAside(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path changelog = Files.writeString(directory.resolve("CHANGELOG.md"), "old");

        // ACT //
        byte[] previous = ChangelogPlugin.prepare("https://github.com/coordinatekit/crf", changelog);

        // ASSERT //
        assertArrayEquals("old".getBytes(StandardCharsets.UTF_8), previous);
        assertFalse(Files.exists(changelog));
    }

    /**
     * Returns the text of one release, from its heading to the next release's heading or the end.
     *
     * @param changelog the rendered changelog
     * @param version the release's version, without the brackets
     * @return the release's heading line and everything under it
     */
    private static String releaseBody(String changelog, String version) {
        return slice(changelog, "## [" + version + "]", "\n## [");
    }

    /**
     * Returns a runner for a fixture build, with the plugin under test and the git-changelog plugin it
     * applies on the same classpath.
     *
     * @param directory the fixture's project directory
     * @return a runner rooted there
     */
    private static GradleRunner runner(Path directory) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath();
    }

    /**
     * Returns the text of one section within a release, from its heading to the next section's heading
     * or the end.
     *
     * @param release the text of one release, as {@link #releaseBody} returns it
     * @param heading the section's heading, without the leading {@code ###}
     * @return the section's heading line and everything under it
     */
    private static String sectionBody(String release, String heading) {
        return slice(release, "### " + heading, "\n### ");
    }

    /**
     * Cuts a span out of the text, failing the test when its start is missing.
     *
     * @param text the text to cut from
     * @param start the marker the span begins with, which the span includes
     * @param end the marker the span stops before, or the end of the text when it does not follow
     * @return the span
     */
    private static String slice(String text, String start, String end) {
        int from = text.indexOf(start);
        assertTrue(from >= 0, () -> "no " + start + " in:\n" + text);
        int to = text.indexOf(end, from + start.length());
        return to < 0 ? text.substring(from) : text.substring(from, to);
    }

    /**
     * Tags the current commit with an annotated tag dated for the given day.
     *
     * @param git the repository to tag
     * @param name the tag name
     * @param day the day of January 2026 the tag is dated
     * @throws GitAPIException if the tag fails
     */
    private static void tag(Git git, String name, int day) throws GitAPIException {
        git.tag().setName(name).setMessage(name).setTagger(person(day)).setAnnotated(true).setSigned(false).call();
    }

    @Test
    void template__ships() {
        // ACT //
        String template = ChangelogPlugin.template();

        // ASSERT //
        assertTrue(template.startsWith("# Changelog"));
        assertTrue(template.contains("{{{repoUrl}}}/releases/tag/"));
        assertFalse(template.contains("__REPO_URL__"));
    }

    @Test
    void verify__acceptsAWrittenFile(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path changelog = Files.writeString(directory.resolve("CHANGELOG.md"), "new");

        // ACT //
        ChangelogPlugin.verify(changelog, "old".getBytes(StandardCharsets.UTF_8));

        // ASSERT //
        assertEquals("new", Files.readString(changelog));
    }

    @Test
    void verify__failsWithoutOutputAndRestoresThePreviousFile(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path changelog = directory.resolve("CHANGELOG.md");

        // ACT //
        GradleException thrown = assertThrows(
                GradleException.class,
                () -> ChangelogPlugin.verify(changelog, "old".getBytes(StandardCharsets.UTF_8))
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains("did not write " + changelog), thrown.getMessage());
        assertEquals("old", Files.readString(changelog));
    }

    @Test
    void verify__failsWithoutOutputWhenThereWasNoPreviousFile(@TempDir Path directory) {
        // ARRANGE //
        Path changelog = directory.resolve("CHANGELOG.md");

        // ACT //
        assertThrows(GradleException.class, () -> ChangelogPlugin.verify(changelog, null));

        // ASSERT //
        assertFalse(Files.exists(changelog));
    }

    /**
     * Writes a consumer build that applies the plugin, followed by the given text. The build gets a
     * {@code gradle.properties} carrying {@code repoUrl}, and the daemon arguments from
     * {@code testKit.fixtureJvmArgs} when the test task sets it.
     *
     * @param directory the project directory to write into
     * @param repoUrl the {@code repoUrl} property, or {@code null} to leave it out
     * @param block Gradle script appended after the {@code plugins} block, such as a
     *        {@code foundationChangelog} block
     * @throws IOException if the fixture cannot be written
     */
    private static void writeFixture(Path directory, @Nullable String repoUrl, String block) throws IOException {
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = \"fixture\"\n");
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id "%s"
                }
                """.formatted(PLUGIN_ID) + block);

        Properties properties = new Properties();
        if (repoUrl != null) {
            properties.setProperty("repoUrl", repoUrl);
        }
        String jvmArgs = System.getProperty("testKit.fixtureJvmArgs");
        if (jvmArgs != null) {
            properties.setProperty("org.gradle.jvmargs", jvmArgs);
        }
        try (OutputStream file = Files.newOutputStream(directory.resolve("gradle.properties"))) {
            properties.store(file, null);
        }
    }

    /**
     * Turns the directory into a git repository with two tagged releases. {@code v0.1.0} holds a single
     * feature on top of a first commit that the changelog has no section for, so that the tag does not
     * sit on a root commit, which git-changelog includes even when it is the exclusive lower bound of
     * {@code fromRevision}. {@code v0.2.0} adds a feature with a pull request reference, a fix without
     * one, a breaking change, and a deprecation, the last two carrying their footers.
     *
     * @param directory the project directory, which becomes the repository
     * @return the full hash of the fix commit
     * @throws GitAPIException if any git operation fails
     * @throws IOException if a file cannot be written
     */
    private static String writeHistory(Path directory) throws GitAPIException, IOException {
        try (Git git = Git.init().setDirectory(directory.toFile()).setInitialBranch("main").call()) {
            commit(git, "chore: start the repository", 1);
            commit(git, "feat: seed the widget", 1);
            tag(git, "v0.1.0", 1);
            commit(git, "feat: add the widget (#12)", 2);
            String fixHash = commit(git, "fix: mend the widget", 3);
            commit(git, "feat!: drop the legacy widget\n\nBREAKING CHANGE: the widget no longer exists", 4);
            commit(git, "refactor: replace the gadget\n\nDEPRECATED: the gadget goes away next release", 5);
            tag(git, "v0.2.0", 5);
            return fixHash;
        }
    }
}

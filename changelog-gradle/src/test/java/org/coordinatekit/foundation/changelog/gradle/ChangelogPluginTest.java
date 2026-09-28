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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import se.bjurr.gitchangelog.plugin.gradle.GitChangelogTask;
import se.bjurr.gitchangelog.plugin.gradle.HelperParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tests for {@link ChangelogPlugin}. The conventions the plugin sets on the {@code gitChangelog}
 * task are read straight off it through {@link ProjectBuilder}, and the guard's decisions are
 * driven as plain statics over files in a temporary directory. Whether the whole thing renders a
 * real changelog is left to this repository's own {@code gitChangelog} run, which applies the
 * plugin from source.
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

    private static GitChangelogTask gitChangelog(Project project) {
        return (GitChangelogTask) project.getTasks().getByName("gitChangelog");
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
}

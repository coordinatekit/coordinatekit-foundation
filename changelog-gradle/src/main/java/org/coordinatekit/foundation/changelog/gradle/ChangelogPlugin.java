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

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;
import se.bjurr.gitchangelog.plugin.gradle.GitChangelogTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Applies the git-changelog Gradle plugin and configures its {@code gitChangelog} task with
 * CoordinateKit's defaults: the template, the section list, the date format, and the helpers the
 * template calls. A consumer's whole integration is the {@code plugins} block, {@code repoUrl} in
 * {@code gradle.properties}, and an optional {@code foundationChangelog} block.
 *
 * <p>
 * This plugin applies git-changelog rather than reacting to it, because the template only renders
 * against that plugin's task and a build has no reason to choose its own version of it. The
 * dependency is {@code implementation}, so it reaches the published POM.
 *
 * <p>
 * git-changelog's task fails the build on a template that cannot render, but it only logs a
 * repository or file error and still finishes green, leaving nothing written. The task is therefore
 * wrapped in a guard: the task renders into its temporary directory, and only once that file exists
 * does the guard move it over {@code CHANGELOG.md}, failing the build when it does not. Either way
 * a failed run leaves the previous {@code CHANGELOG.md} untouched.
 *
 * @see ChangelogExtension
 */
public class ChangelogPlugin implements Plugin<Project> {
    /** The name of the changelog file, both in the project directory and in the staging directory. */
    private static final String CHANGELOG_FILE = "CHANGELOG.md";

    /** The format of the date shown beside each release. */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    /** The name of the block a build configures the changelog through. */
    private static final String EXTENSION_NAME = "foundationChangelog";

    /** The id of the plugin this one applies. */
    private static final String GIT_CHANGELOG_PLUGIN_ID = "se.bjurr.gitchangelog.git-changelog-gradle-plugin";

    /** The name of the Gradle property that supplies the repository URL. */
    private static final String REPO_URL_PROPERTY = "repoUrl";

    /** The name of the task git-changelog registers. */
    private static final String TASK_NAME = "gitChangelog";

    /** The template's name, resolved beside this class. */
    private static final String TEMPLATE_RESOURCE = "changelog.hbs";

    /** Instantiated by Gradle when a build applies the plugin. */
    public ChangelogPlugin() {}

    @Override
    public void apply(Project project) {
        ChangelogExtension extension = project.getExtensions().create(EXTENSION_NAME, ChangelogExtension.class);
        extension.getRepoUrl().convention(project.getProviders().gradleProperty(REPO_URL_PROPERTY));

        project.getPluginManager().apply(GIT_CHANGELOG_PLUGIN_ID);
        String template = template();
        project.getTasks().named(TASK_NAME, GitChangelogTask.class, task -> {
            task.templateContent.convention(template);
            task.dateFormat.convention(DATE_FORMAT);
            task.fromRevision.convention(extension.getFromRevision());
            task.handlebarsHelpers
                    .addAll(ChangelogHelpers.helpers(extension.getRepoUrl(), extension.getInitialRelease()));
            guard(task, extension.getRepoUrl(), extension.getInitialRelease(), project.file(CHANGELOG_FILE));
        });
    }

    /**
     * Points the task at a staging file and wraps its action so that only a written changelog reaches
     * the project directory.
     *
     * @param task the {@code gitChangelog} task
     * @param repoUrl the repository URL, checked before the task runs
     * @param initialRelease the initial release file, checked before the task runs
     * @param changelog the {@code CHANGELOG.md} the staged file replaces
     */
    private static void guard(
            GitChangelogTask task,
            Provider<String> repoUrl,
            Provider<RegularFile> initialRelease,
            File changelog
    ) {
        // A plain File rather than the task's property, so the actions capture nothing the
        // configuration cache would have to serialize along with the project.
        File staged = new File(task.getTemporaryDir(), CHANGELOG_FILE);
        task.file.set(staged);
        task.doFirst(first -> {
            RegularFile initial = initialRelease.getOrNull();
            prepare(repoUrl.getOrNull(), initial == null ? null : initial.getAsFile().toPath(), staged.toPath());
        });
        task.doLast(last -> publish(staged.toPath(), changelog.toPath()));
    }

    /**
     * Checks the configuration and clears the staging file before the task runs. Deleting a staged file
     * left by an earlier run is what makes a failed run detectable: {@link #publish} would otherwise
     * find that file and move it into place.
     *
     * @param repoUrl the repository URL, or {@code null} when none is configured
     * @param initialRelease the file appended after the last release, or {@code null} when none is
     *        configured
     * @param staged where the task writes the changelog
     * @throws GradleException if {@code repoUrl} is missing or blank, {@code initialRelease} is set but
     *         does not exist, or the staging file cannot be cleared
     */
    static void prepare(@Nullable String repoUrl, @Nullable Path initialRelease, Path staged) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new GradleException(
                    "foundationChangelog needs a repository URL for its links. Add repoUrl=https://github.com/<owner>/<repo>"
                            + " to gradle.properties, or set foundationChangelog.repoUrl."
            );
        }
        if (initialRelease != null && !Files.exists(initialRelease)) {
            throw new GradleException(
                    "foundationChangelog.initialRelease points at " + initialRelease
                            + ", which does not exist. Create the file or remove the setting."
            );
        }
        try {
            Files.createDirectories(staged.getParent());
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            throw new GradleException("Could not clear " + staged + " before regenerating the changelog.", e);
        }
    }

    /**
     * Moves the staged changelog over the project's, failing when the task did not write one.
     *
     * @param staged where the task should have written the changelog
     * @param changelog the file the staged changelog replaces
     * @throws GradleException if the staged file is missing after the task ran, or cannot be moved
     */
    static void publish(Path staged, Path changelog) {
        if (!Files.exists(staged)) {
            throw new GradleException(
                    "gitChangelog did not write " + changelog
                            + "; git-changelog logged the cause above, and the previous file is untouched."
            );
        }
        try {
            Files.move(staged, changelog, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GradleException("Could not move " + staged + " to " + changelog + ".", e);
        }
    }

    /**
     * Reads the changelog template that ships beside this class.
     *
     * @return the template text
     * @throws UncheckedIOException if the resource cannot be read
     */
    static String template() {
        try (InputStream in = ChangelogPlugin.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + TEMPLATE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + TEMPLATE_RESOURCE, e);
        }
    }
}

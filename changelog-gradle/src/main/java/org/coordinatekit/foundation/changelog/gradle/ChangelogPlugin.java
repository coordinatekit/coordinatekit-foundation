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
import org.gradle.api.provider.Property;
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
import java.util.concurrent.atomic.AtomicReference;

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
 * git-changelog's task logs every exception and still finishes green, so a template that fails to
 * render leaves the old {@code CHANGELOG.md} in place and nothing in the build says so. The task is
 * therefore wrapped in a guard: it sets the file aside and deletes it before the task runs, and
 * fails the build afterwards if the task did not write it back, restoring the old file so that a
 * failed run loses nothing.
 *
 * @see ChangelogExtension
 */
public class ChangelogPlugin implements Plugin<Project> {
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
            guard(task, extension.getRepoUrl(), extension.getInitialRelease());
        });
    }

    /**
     * Wraps the task's action so that a run that writes nothing fails the build.
     *
     * @param task the {@code gitChangelog} task
     * @param repoUrl the repository URL, checked before the task runs
     * @param initialRelease the initial release file, checked before the task runs
     */
    private static void guard(GitChangelogTask task, Provider<String> repoUrl, Provider<RegularFile> initialRelease) {
        // Captured rather than read from the task inside the actions, which the configuration cache
        // would otherwise have to serialize along with the project.
        Property<File> output = task.file;
        AtomicReference<byte @Nullable []> previous = new AtomicReference<>();
        task.doFirst(first -> {
            RegularFile initial = initialRelease.getOrNull();
            previous.set(
                    prepare(
                            repoUrl.getOrNull(),
                            initial == null ? null : initial.getAsFile().toPath(),
                            output.get().toPath()
                    )
            );
        });
        task.doLast(last -> verify(output.get().toPath(), previous.get()));
    }

    /**
     * Sets the changelog aside before the task runs. Deleting it is what makes a failed run detectable:
     * git-changelog would otherwise leave the previous file where {@link #verify} looks.
     *
     * @param repoUrl the repository URL, or {@code null} when none is configured
     * @param initialRelease the file appended after the last release, or {@code null} when none is
     *        configured
     * @param changelog where the task writes the changelog
     * @return the file's previous content, or {@code null} when it did not exist
     * @throws GradleException if {@code repoUrl} is missing or blank, {@code initialRelease} is set but
     *         does not exist, or the file cannot be replaced
     */
    static byte @Nullable [] prepare(@Nullable String repoUrl, @Nullable Path initialRelease, Path changelog) {
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
            if (!Files.exists(changelog)) {
                return null;
            }
            byte[] content = Files.readAllBytes(changelog);
            Files.delete(changelog);
            return content;
        } catch (IOException e) {
            throw new GradleException("Could not set " + changelog + " aside before regenerating it.", e);
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

    /**
     * Checks that the task wrote the changelog, and puts the previous content back when it did not.
     *
     * @param changelog where the task should have written the changelog
     * @param previous the content {@link #prepare} set aside, or {@code null} when there was none
     * @throws GradleException if the file is missing after the task ran
     */
    static void verify(Path changelog, byte @Nullable [] previous) {
        if (Files.exists(changelog)) {
            return;
        }
        try {
            if (previous != null) {
                Files.write(changelog, previous);
            }
        } catch (IOException e) {
            throw new GradleException(
                    "gitChangelog did not write " + changelog + " and the previous file could not be restored.",
                    e
            );
        }
        throw new GradleException(
                "gitChangelog did not write " + changelog + "; git-changelog logged the cause above."
        );
    }
}

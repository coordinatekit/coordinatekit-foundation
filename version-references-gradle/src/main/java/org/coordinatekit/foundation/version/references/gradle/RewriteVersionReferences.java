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
package org.coordinatekit.foundation.version.references.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Rewrites the version references in every tracked file that names the project, given the version
 * being released and the one that follows it. The task only gathers: it asks Git which files are
 * tracked, reads them, and hands their text to {@link RewritePlan}, which decides everything, then
 * writes the files that changed. Every refusal happens before the first write, so a refused rewrite
 * leaves the tree as it was. A write that fails partway is not undone, and the files written before
 * it stay rewritten.
 *
 * <p>
 * Files are read and written as ISO-8859-1, so a byte that is not valid UTF-8 comes back unchanged.
 * The task never touches the {@code Project} at execution time, which is what lets it run from the
 * configuration cache.
 *
 * @see VersionReferencesPlugin
 */
@UntrackedTask(because = "It rewrites tracked files in place, and no declared input describes them")
public abstract class RewriteVersionReferences extends DefaultTask {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public RewriteVersionReferences() {}

    /**
     * The globs of tracked files to leave alone.
     *
     * @return the exclusion globs
     */
    @Input
    public abstract ListProperty<String> getExcludes();

    /**
     * The subproject names that a module jar filename is anchored to.
     *
     * @return the subproject names
     */
    @Input
    public abstract ListProperty<String> getModules();

    /**
     * The version in development after the release, which a snapshot coordinate moves to. It is
     * optional so that a missing value fails with this task's own message and not Gradle's.
     *
     * @return the next version, ending in {@code -SNAPSHOT}
     */
    @Input
    @Optional
    @Option(option = "next", description = "The -SNAPSHOT version that follows the release.")
    public abstract Property<String> getNext();

    /**
     * The group of the root project, which a published coordinate and a plugin id are anchored to. It
     * is not called {@code getGroup} because {@code Task} already has that.
     *
     * @return the group
     */
    @Input
    public abstract Property<String> getProjectGroup();

    /**
     * The version being released, which a release coordinate, a jar name, a plugin id, and every other
     * form that advertises a release move to. It is optional so that a missing value fails with this
     * task's own message and not Gradle's.
     *
     * @return the release version, not ending in {@code -SNAPSHOT}
     */
    @Input
    @Optional
    @Option(option = "release", description = "The version being released.")
    public abstract Property<String> getRelease();

    /**
     * The directory the rewrite reads from, which is the root project's.
     *
     * @return the root project directory
     */
    @Internal
    public abstract DirectoryProperty getRootDirectory();

    /**
     * The root project's name, which an archive name and a printed version line are anchored to.
     *
     * @return the root project's name
     */
    @Input
    public abstract Property<String> getRootProjectName();

    /**
     * Reads the tracked text files that are not excluded.
     *
     * @param root the project directory
     * @param tracked the paths Git tracks under {@code root}
     * @param excludes the exclusion globs
     * @return the files' text, decoded as ISO-8859-1, by path
     */
    private static SortedMap<String, String> read(Path root, SortedSet<String> tracked, List<String> excludes) {
        SortedMap<String, String> texts = new TreeMap<>();
        List<Pattern> patterns = TrackedFiles.excludePatterns(excludes);
        for (String path : tracked) {
            if (TrackedFiles.isExcluded(path, patterns)) {
                continue;
            }
            try {
                byte[] content = Files.readAllBytes(root.resolve(path));
                if (!TrackedFiles.isBinary(content)) {
                    texts.put(path, new String(content, StandardCharsets.ISO_8859_1));
                }
            } catch (IOException e) {
                throw new GradleException("Could not read " + path + ". Nothing was written.", e);
            }
        }
        return texts;
    }

    /**
     * Rewrites the tracked files to the release version and the next version.
     *
     * @throws GradleException if the rewrite is refused or a file cannot be written. A refusal writes
     *         nothing, and a failed write names the files already written.
     */
    @TaskAction
    public void rewrite() {
        ReleaseVersions versions = ReleaseVersions.of(getRelease().getOrNull(), getNext().getOrNull());
        Anchors anchors = Anchors.of(getProjectGroup().get(), getRootProjectName().get(), getModules().get());
        Path root = getRootDirectory().get().getAsFile().toPath();

        SortedSet<String> tracked = TrackedFiles.list(root);
        for (String glob : TrackedFiles.unusedGlobs(tracked, getExcludes().get())) {
            getLogger().warn("The exclude '{}' matches no tracked file, so it protects nothing.", glob);
        }

        RewritePlan plan = RewritePlan.plan(read(root, tracked, getExcludes().get()), anchors, versions);
        plan.requireApplicable(versions);
        if (!plan.changedFiles().isEmpty()) {
            write(root, plan);
        }
        getLogger().lifecycle(plan.report(versions));
    }

    /**
     * Writes the files a plan changes.
     *
     * @param root the project directory
     * @param plan the plan
     * @throws GradleException if a file cannot be written, naming the files written before it
     */
    private static void write(Path root, RewritePlan plan) {
        List<String> written = new ArrayList<>();
        plan.changedFiles().forEach((path, rewrite) -> {
            try {
                Files.writeString(root.resolve(path), rewrite.text(), StandardCharsets.ISO_8859_1);
                written.add(path);
            } catch (IOException e) {
                throw new GradleException(
                        "Could not write " + path + ". Already written: " + (written.isEmpty() ? "nothing" : written)
                                + ".",
                        e
                );
            }
        });
    }
}

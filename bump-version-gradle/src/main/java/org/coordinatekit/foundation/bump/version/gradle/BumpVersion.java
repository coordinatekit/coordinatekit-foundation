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
package org.coordinatekit.foundation.bump.version.gradle;

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
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Moves the build's version across every tracked file that names it. The task only gathers: it asks
 * Git which files are tracked, reads them, and hands their text to {@link BumpPlan}, which decides
 * everything, then writes the files that changed. Every refusal happens before the first write, so
 * a refused bump leaves the tree as it was. A write that fails partway is not undone, and the files
 * written before it stay bumped.
 *
 * <p>
 * Files are read and written as ISO-8859-1, so a byte that is not valid UTF-8 comes back unchanged.
 * The task never touches the {@code Project} at execution time, which is what lets it run from the
 * configuration cache.
 *
 * @see BumpVersionPlugin
 */
@UntrackedTask(because = "It rewrites tracked files in place, and no declared input describes them")
public abstract class BumpVersion extends DefaultTask {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public BumpVersion() {}

    /**
     * Rewrites the tracked files from the current version to the new one.
     *
     * @throws GradleException if the bump is refused or a file cannot be written. A refusal writes
     *         nothing, and a failed write names the files already written.
     */
    @TaskAction
    public void bump() {
        VersionBump bump = VersionBump.of(getTo().getOrNull(), getFrom().getOrNull(), getBuildVersion().get());
        Anchors anchors = Anchors.of(getProjectGroup().get(), getRootProjectName().get(), getModules().get());
        Path root = getRootDirectory().get().getAsFile().toPath();

        BumpPlan plan = BumpPlan.plan(read(root, getExcludes().get()), anchors, bump);
        getLogger().lifecycle("Found {} occurrence(s) of '{}' to evaluate", plan.occurrences(), bump.current());
        plan.requireApplicable(bump);
        write(root, plan);
        getLogger().lifecycle(plan.report(bump));
    }

    /**
     * The version the build reports, which a bump moves from unless {@link #getFrom()} is given.
     *
     * @return the build's version
     */
    @Input
    public abstract Property<String> getBuildVersion();

    /**
     * The globs of tracked files to leave alone.
     *
     * @return the exclusion globs
     */
    @Input
    public abstract ListProperty<String> getExcludes();

    /**
     * The version to move from, when it is not the build's.
     *
     * @return the current version, if given
     */
    @Input
    @Optional
    @Option(option = "from", description = "The version the files name now, when it differs from the build's version.")
    public abstract Property<String> getFrom();

    /**
     * The subproject names that a module jar filename is anchored to.
     *
     * @return the subproject names
     */
    @Input
    public abstract ListProperty<String> getModules();

    /**
     * The group of the root project, which a published coordinate and a plugin id are anchored to. It
     * is not called {@code getGroup} because {@code Task} already has that.
     *
     * @return the group
     */
    @Input
    public abstract Property<String> getProjectGroup();

    /**
     * The directory the bump reads from, which is the root project's.
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
     * The version to write. It is optional so that a missing value fails with this task's own message
     * and not Gradle's.
     *
     * @return the new version
     */
    @Input
    @Optional
    @Option(option = "to", description = "The version to write into every tracked file that names the current one.")
    public abstract Property<String> getTo();

    /**
     * Reads the tracked text files that are not excluded.
     *
     * @param root the project directory
     * @param excludes the exclusion globs
     * @return the files' text, decoded as ISO-8859-1, by path
     */
    private static SortedMap<String, String> read(Path root, List<String> excludes) {
        SortedMap<String, String> texts = new TreeMap<>();
        List<Pattern> patterns = TrackedFiles.excludePatterns(excludes);
        for (String path : TrackedFiles.list(root)) {
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
     * Writes the files a plan changes.
     *
     * @param root the project directory
     * @param plan the plan
     * @throws GradleException if a file cannot be written, naming the files written before it
     */
    private static void write(Path root, BumpPlan plan) {
        List<String> written = new ArrayList<>();
        plan.changedFiles().forEach((path, rewrite) -> {
            try {
                Files.write(root.resolve(path), rewrite.text().getBytes(StandardCharsets.ISO_8859_1));
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

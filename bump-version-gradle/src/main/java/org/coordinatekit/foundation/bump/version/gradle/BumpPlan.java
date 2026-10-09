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

import org.coordinatekit.foundation.bump.version.gradle.Rewriter.Edit;
import org.coordinatekit.foundation.bump.version.gradle.Rewriter.FileRewrite;
import org.coordinatekit.foundation.bump.version.gradle.Rewriter.Form;
import org.gradle.api.GradleException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Everything a bump would do, worked out before anything is written. Planning reads each file's
 * text and nothing else, so every decision to refuse a bump, and the whole rewrite, can be made and
 * tested without a file system.
 *
 * @param changedFiles the rewrites that have at least one edit, by path
 * @param unmatched the lines each file has that name the project but that no rule matched, by path,
 *        for the files that have any
 * @param occurrences how many times the current version appears across all the files
 */
record BumpPlan(
        SortedMap<String, FileRewrite> changedFiles,
        SortedMap<String, List<Integer>> unmatched,
        int occurrences
) {
    BumpPlan {
        changedFiles = Collections.unmodifiableSortedMap(new TreeMap<>(changedFiles));
        unmatched = Collections.unmodifiableSortedMap(new TreeMap<>(unmatched));
    }

    /**
     * Counts the non-overlapping occurrences of a string.
     *
     * @param text the text to search
     * @param needle the string to find
     * @return how many times it appears
     */
    private static int count(String text, String needle) {
        int count = 0;
        for (int from = text.indexOf(needle); from >= 0; from = text.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }

    /**
     * Whether some changed file declares the build's own version.
     *
     * @param changed the changed files
     * @return {@code true} if one has an edit to a {@code version=} or {@code version =} declaration
     */
    private static boolean declaresVersion(SortedMap<String, FileRewrite> changed) {
        return changed.values()
                .stream()
                .flatMap(rewrite -> rewrite.edits().stream())
                .map(Edit::form)
                .anyMatch(form -> form == Form.VERSION_PROPERTY || form == Form.VERSION_ASSIGNMENT);
    }

    /**
     * Plans a bump over a set of files.
     *
     * @param texts the files' text, by path
     * @param anchors the names the rules are anchored to
     * @param bump the bump
     * @return the plan
     */
    static BumpPlan plan(SortedMap<String, String> texts, Anchors anchors, VersionBump bump) {
        Rewriter rewriter = new Rewriter(anchors, bump);
        SortedMap<String, FileRewrite> changed = new TreeMap<>();
        SortedMap<String, List<Integer>> unmatched = new TreeMap<>();
        int occurrences = 0;
        for (Map.Entry<String, String> file : texts.entrySet()) {
            FileRewrite rewrite = rewriter.rewrite(file.getValue());
            if (rewrite.changed()) {
                changed.put(file.getKey(), rewrite);
            }
            if (!rewrite.unmatched().isEmpty()) {
                unmatched.put(file.getKey(), rewrite.unmatched());
            }
            occurrences += count(file.getValue(), bump.current());
        }
        return new BumpPlan(changed, unmatched, occurrences);
    }

    /**
     * Describes the bump once it has been written.
     *
     * @param bump the bump
     * @return the new version line, then the changed files with their edit counts
     */
    String report(VersionBump bump) {
        StringBuilder report = new StringBuilder("Updated version: " + bump.current() + " -> " + bump.next());
        report.append("\nChanged files:");
        changedFiles().forEach(
                (path, rewrite) -> report.append("\n  ")
                        .append(path)
                        .append(" (")
                        .append(rewrite.edits().size())
                        .append(')')
        );
        return report.toString();
    }

    /**
     * Refuses a bump that would leave the repository half moved.
     *
     * <p>
     * It fails, in this order, when the current version appears in no file, when a line names the
     * project but no rule recognised it, when no file would change, and when no file declares the
     * build's own version. The unrecognised lines come before the last two because they say where to
     * look, which those two cannot. The last check is skipped when the current version was given with
     * {@code --from}, as then the build's version is not what is being moved.
     *
     * @param bump the bump
     * @throws GradleException for the first condition above that holds
     */
    void requireApplicable(VersionBump bump) {
        if (occurrences == 0) {
            throw new GradleException("The current version '" + bump.current() + "' is not in any tracked file.");
        }
        if (!unmatched.isEmpty()) {
            StringBuilder message = new StringBuilder(
                    "These lines name the project with a version that no rule recognises. Nothing was written."
            );
            unmatched.forEach(
                    (path, lines) -> lines.forEach(line -> message.append("\n  ").append(path).append(':').append(line))
            );
            message.append(
                    "\nAdd a rule to coordinatekit-foundation, or leave the file out with foundationVersion { exclude \"<path>\" }."
            );
            throw new GradleException(message.toString());
        }
        if (changedFiles.isEmpty()) {
            throw new GradleException(
                    "No files were changed. The current version '" + bump.current()
                            + "' appears, but no rule recognises where."
            );
        }
        if (bump.currentFromBuild() && !declaresVersion(changedFiles)) {
            throw new GradleException(
                    "No file declares the build's version '" + bump.current()
                            + "' as version=... or version = \"...\", so the build would still report it after the bump."
                            + " Declare it in gradle.properties or the root build, or pass --from if it lives elsewhere."
            );
        }
    }
}

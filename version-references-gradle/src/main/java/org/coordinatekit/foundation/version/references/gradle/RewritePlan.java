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

import org.coordinatekit.foundation.version.references.gradle.Rewriter.FileRewrite;
import org.gradle.api.GradleException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Everything a rewrite would do, worked out before anything is written. Planning reads each file's
 * text and nothing else, so every decision to refuse a rewrite, and the whole rewrite, can be made
 * and tested without a file system.
 *
 * @param changedFiles the rewrites that have at least one edit, by path
 * @param unmatched the lines each file has that name the project but that no rule matched, by path,
 *        for the files that have any
 * @param occurrences how many times the release version appears across all the files
 */
record RewritePlan(
        SortedMap<String, FileRewrite> changedFiles,
        SortedMap<String, List<Integer>> unmatched,
        int occurrences
) {
    RewritePlan {
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
     * Plans a rewrite over a set of files.
     *
     * <p>
     * The release version is counted as a substring, so it also turns up inside the same version with
     * {@code -SNAPSHOT} after it, which is what a manual run before the release tooling strips the
     * suffix sees.
     *
     * @param texts the files' text, by path
     * @param anchors the names the rules are anchored to
     * @param versions the release version and the next version
     * @return the plan
     */
    static RewritePlan plan(SortedMap<String, String> texts, Anchors anchors, ReleaseVersions versions) {
        Rewriter rewriter = new Rewriter(anchors, versions);
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
            occurrences += count(file.getValue(), versions.release());
        }
        return new RewritePlan(changed, unmatched, occurrences);
    }

    /**
     * Describes the rewrite once it has been written.
     *
     * @param versions the release version and the next version
     * @return a line naming both versions, then the changed files with their edit counts
     */
    String report(ReleaseVersions versions) {
        StringBuilder report = new StringBuilder(
                "Rewrote version references: release " + versions.release() + ", next " + versions.next()
        );
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
     * Refuses a rewrite that would leave the repository half moved.
     *
     * <p>
     * It fails, in this order, when the release version appears in no file, when a line names the
     * project but no rule recognised it, and when no file would change. The unrecognised lines come
     * before the last check because they say where to look, which that one cannot. The first check
     * catches a {@code --release} that names the wrong version.
     *
     * @param versions the release version and the next version
     * @throws GradleException for the first condition above that holds
     */
    void requireApplicable(ReleaseVersions versions) {
        if (occurrences == 0) {
            throw new GradleException("The release version '" + versions.release() + "' is not in any tracked file.");
        }
        if (!unmatched.isEmpty()) {
            StringBuilder message = new StringBuilder(
                    "These lines name the project with a version that no rule recognises. Nothing was written."
            );
            unmatched.forEach(
                    (path, lines) -> lines.forEach(line -> message.append("\n  ").append(path).append(':').append(line))
            );
            message.append(
                    "\nAdd a rule to coordinatekit-foundation, or leave the file out with foundationVersionReferences { exclude \"<path>\" }."
            );
            throw new GradleException(message.toString());
        }
        if (changedFiles.isEmpty()) {
            throw new GradleException(
                    "No files were changed. The release version '" + versions.release()
                            + "' appears, but no rule recognises where."
            );
        }
    }
}

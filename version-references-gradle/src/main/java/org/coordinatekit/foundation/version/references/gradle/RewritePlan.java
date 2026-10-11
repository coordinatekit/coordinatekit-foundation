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
 */
record RewritePlan(SortedMap<String, FileRewrite> changedFiles, SortedMap<String, List<Integer>> unmatched) {
    RewritePlan {
        changedFiles = Collections.unmodifiableSortedMap(new TreeMap<>(changedFiles));
        unmatched = Collections.unmodifiableSortedMap(new TreeMap<>(unmatched));
    }

    /**
     * Plans a rewrite over a set of files.
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
        for (Map.Entry<String, String> file : texts.entrySet()) {
            FileRewrite rewrite = rewriter.rewrite(file.getValue());
            if (rewrite.changed()) {
                changed.put(file.getKey(), rewrite);
            }
            if (!rewrite.unmatched().isEmpty()) {
                unmatched.put(file.getKey(), rewrite.unmatched());
            }
        }
        return new RewritePlan(changed, unmatched);
    }

    /**
     * Describes the rewrite once it has been written.
     *
     * @param versions the release version and the next version
     * @return a line naming both versions, then the changed files with their edit counts, or a single
     *         line saying nothing needed rewriting when no file changed
     */
    String report(ReleaseVersions versions) {
        if (changedFiles.isEmpty()) {
            return "Version references already match release " + versions.release() + " and next " + versions.next()
                    + ". Nothing to rewrite.";
        }
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
     * It fails only when a line names the project but no rule recognised it. A plan with nothing to
     * change is applicable, so a rewrite run a second time succeeds.
     *
     * @param versions the release version and the next version
     * @throws GradleException if any file has an unrecognised line
     */
    void requireApplicable(ReleaseVersions versions) {
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
    }
}

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Tests {@link RewritePlan}, the decisions the task makes before it writes anything. Files are
 * strings in a map, so each refusal is driven without a file system.
 */
class RewritePlanTest {
    /**
     * A set of files the plan has to refuse.
     *
     * @param name what the case shows
     * @param files the files' text, by path
     * @param message text the failure has to contain
     */
    private record RefusedParameters(String name, SortedMap<String, String> files, String message) {}

    /** The names every rule is anchored to. */
    private static final Anchors ANCHORS = Anchors.of("com.example.widgets", "widgets", List.of("alpha-core", "beta"));

    /** The snapshot that follows the release. */
    private static final String NEXT = "2.1.0-SNAPSHOT";

    /** The version being released. */
    private static final String RELEASE = "2.0.0";

    /** The pair every case rewrites under. */
    private static final ReleaseVersions VERSIONS = ReleaseVersions.of(RELEASE, NEXT);

    /**
     * Builds a file map from alternating paths and texts.
     *
     * @param pathsAndTexts a path, then its text, repeated
     * @return the files, by path
     */
    private static SortedMap<String, String> files(String... pathsAndTexts) {
        SortedMap<String, String> files = new TreeMap<>();
        for (int i = 0; i < pathsAndTexts.length; i += 2) {
            files.put(pathsAndTexts[i], pathsAndTexts[i + 1]);
        }
        return files;
    }

    @Test
    void plan__countsTheReleaseVersionInsideASnapshotOfIt() {
        // ACT //
        RewritePlan plan = RewritePlan.plan(
                files("a.txt", "2.0.0-SNAPSHOT 2.0.0\n", "b.txt", "one 2.0.0-SNAPSHOT\n", "c.txt", "none\n"),
                ANCHORS,
                VERSIONS
        );

        // ASSERT //
        assertEquals(3, plan.occurrences());
    }

    @Test
    void plan__separatesChangedFilesFromFilesWithUnmatchedLines() {
        // ACT //
        RewritePlan plan = RewritePlan.plan(
                files(
                        "good.md",
                        "com.example.widgets:beta:1.0.0\n",
                        "bad.md",
                        "com.example.widgets:beta:1.0.0.Final\n"
                ),
                ANCHORS,
                VERSIONS
        );

        // ASSERT //
        assertEquals(List.of("bad.md"), List.copyOf(plan.unmatched().keySet()));
        assertEquals(List.of("good.md"), List.copyOf(plan.changedFiles().keySet()));
    }

    @Test
    void report__namesBothVersionsAndTheChangedFilesWithTheirCounts() {
        // ARRANGE //
        RewritePlan plan = RewritePlan.plan(
                files(
                        "README.md",
                        "com.example.widgets:beta:1.0.0\ncom.example.widgets:alpha-core:1.0.0\n",
                        "RELEASE.md",
                        "com.example.widgets:beta:2.0.0-SNAPSHOT\n",
                        "NOTES.md",
                        "nothing\n"
                ),
                ANCHORS,
                VERSIONS
        );

        // ACT //
        String report = plan.report(VERSIONS);

        // ASSERT //
        assertEquals(
                "Rewrote version references: release 2.0.0, next 2.1.0-SNAPSHOT\nChanged files:\n  README.md (2)\n  RELEASE.md (1)",
                report
        );
    }

    @Test
    void requireApplicable__acceptsASoundPlan() {
        // ARRANGE //
        RewritePlan plan = RewritePlan.plan(
                files("gradle.properties", "version=2.0.0-SNAPSHOT\n", "README.md", "com.example.widgets:beta:1.0.0\n"),
                ANCHORS,
                VERSIONS
        );

        // ACT / ASSERT //
        assertDoesNotThrow(() -> plan.requireApplicable(VERSIONS));
    }

    @Test
    void requireApplicable__namesTheRemedyForUnmatchedLines() {
        // ARRANGE //
        RewritePlan plan = RewritePlan.plan(
                files(
                        "gradle.properties",
                        "version=2.0.0-SNAPSHOT\n",
                        "a.md",
                        "com.example.widgets:beta:1.0.0.Final\n"
                ),
                ANCHORS,
                VERSIONS
        );

        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> plan.requireApplicable(VERSIONS));

        // ASSERT //
        assertTrue(thrown.getMessage().contains("Nothing was written"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("foundationVersionReferences { exclude"), thrown.getMessage());
    }

    static Stream<RefusedParameters> requireApplicable__refused() {
        return Stream.of(
                new RefusedParameters(
                        "release version in no file",
                        files("gradle.properties", "version=3.0.0-SNAPSHOT\n"),
                        "'2.0.0' is not in any tracked file"
                ),
                new RefusedParameters(
                        "release version where no rule reads it",
                        files("NOTES.md", "Working towards 2.0.0.\n"),
                        "No files were changed"
                ),
                new RefusedParameters(
                        "line that names the project and no rule reads",
                        files(
                                "gradle.properties",
                                "version=2.0.0-SNAPSHOT\n",
                                "README.md",
                                "ok\ncom.example.widgets:beta:1.0.0.Final\n"
                        ),
                        "README.md:2"
                ),
                new RefusedParameters(
                        "unrecognised line wins over no change",
                        files("README.md", "com.example.widgets:beta:1.0.0.Final\n", "NOTES.md", "2.0.0\n"),
                        "README.md:1"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void requireApplicable__refused(RefusedParameters parameters) {
        // ARRANGE //
        RewritePlan plan = RewritePlan.plan(parameters.files(), ANCHORS, VERSIONS);

        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> plan.requireApplicable(VERSIONS));

        // ASSERT //
        assertTrue(thrown.getMessage().contains(parameters.message()), thrown.getMessage());
    }
}

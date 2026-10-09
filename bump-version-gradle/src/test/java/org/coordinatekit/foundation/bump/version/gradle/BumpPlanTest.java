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
 * Tests {@link BumpPlan}, the decisions the task makes before it writes anything. Files are strings
 * in a map, so each refusal is driven without a file system.
 */
class BumpPlanTest {
    /**
     * A set of files the plan has to refuse.
     *
     * @param name what the case shows
     * @param from the current version
     * @param fromBuild whether the current version came from the build
     * @param files the files' text, by path
     * @param message text the failure has to contain
     */
    private record RefusedParameters(
            String name,
            String from,
            boolean fromBuild,
            SortedMap<String, String> files,
            String message
    ) {}

    /** The names every rule is anchored to. */
    private static final Anchors ANCHORS = Anchors.of("com.example.widgets", "widgets", List.of("alpha-core", "beta"));

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
    void plan__countsOccurrencesAcrossFiles() {
        // ARRANGE //
        VersionBump bump = VersionBump.of("2.0.0", "2.0.0-SNAPSHOT", "unused");

        // ACT //
        BumpPlan plan = BumpPlan.plan(
                files("a.txt", "2.0.0-SNAPSHOT 2.0.0-SNAPSHOT\n", "b.txt", "one 2.0.0-SNAPSHOT\n", "c.txt", "none\n"),
                ANCHORS,
                bump
        );

        // ASSERT //
        assertEquals(3, plan.occurrences());
    }

    @Test
    void plan__keepsOnlyFilesWithUnmatchedLinesInTheirReport() {
        // ARRANGE //
        VersionBump bump = VersionBump.of("2.0.0", "2.0.0-SNAPSHOT", "unused");

        // ACT //
        BumpPlan plan = BumpPlan.plan(
                files(
                        "good.md",
                        "com.example.widgets:beta:1.0.0\n",
                        "bad.md",
                        "com.example.widgets:beta:1.0.0.Final\n"
                ),
                ANCHORS,
                bump
        );

        // ASSERT //
        assertEquals(List.of("bad.md"), List.copyOf(plan.unmatched().keySet()));
        assertEquals(List.of("good.md"), List.copyOf(plan.changedFiles().keySet()));
    }

    @Test
    void report__namesTheChangedFilesWithTheirCounts() {
        // ARRANGE //
        VersionBump bump = VersionBump.of("2.0.0", "2.0.0-SNAPSHOT", "unused");
        BumpPlan plan = BumpPlan.plan(
                files(
                        "gradle.properties",
                        "version=2.0.0-SNAPSHOT\n",
                        "README.md",
                        "com.example.widgets:beta:1.0.0\ncom.example.widgets:alpha-core:1.0.0\n",
                        "NOTES.md",
                        "nothing\n"
                ),
                ANCHORS,
                bump
        );

        // ACT //
        String report = plan.report(bump);

        // ASSERT //
        assertEquals(
                "Updated version: 2.0.0-SNAPSHOT -> 2.0.0\nChanged files:\n  README.md (2)\n  gradle.properties (1)",
                report
        );
    }

    @Test
    void requireApplicable__acceptsAnExplicitCurrentVersionWithoutADeclaration() {
        // ARRANGE //
        VersionBump bump = VersionBump.of("2.0.0", "1.0.0", "unused");
        BumpPlan plan = BumpPlan.plan(files("README.md", "com.example.widgets:beta:1.0.0\n"), ANCHORS, bump);

        // ACT / ASSERT //
        assertDoesNotThrow(() -> plan.requireApplicable(bump));
    }

    @Test
    void requireApplicable__acceptsASoundBump() {
        // ARRANGE //
        VersionBump bump = VersionBump.of("2.0.0", "2.0.0-SNAPSHOT", "unused");
        BumpPlan plan = BumpPlan.plan(
                files("gradle.properties", "version=2.0.0-SNAPSHOT\n", "README.md", "com.example.widgets:beta:1.0.0\n"),
                ANCHORS,
                bump
        );

        // ACT / ASSERT //
        assertDoesNotThrow(() -> plan.requireApplicable(bump));
    }

    @Test
    void requireApplicable__namesTheRemedyForUnmatchedLines() {
        // ARRANGE //
        VersionBump bump = VersionBump.of("2.0.0", "2.0.0-SNAPSHOT", "unused");
        BumpPlan plan = BumpPlan.plan(
                files(
                        "gradle.properties",
                        "version=2.0.0-SNAPSHOT\n",
                        "a.md",
                        "com.example.widgets:beta:1.0.0.Final\n"
                ),
                ANCHORS,
                bump
        );

        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> plan.requireApplicable(bump));

        // ASSERT //
        assertTrue(thrown.getMessage().contains("Nothing was written"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("foundationVersion { exclude"), thrown.getMessage());
    }

    static Stream<RefusedParameters> requireApplicable__refused() {
        return Stream.of(
                new RefusedParameters(
                        "current version in no file",
                        "2.0.0-SNAPSHOT",
                        true,
                        files("gradle.properties", "version=3.0.0-SNAPSHOT\n"),
                        "'2.0.0-SNAPSHOT' is not in any tracked file"
                ),
                new RefusedParameters(
                        "current version where no rule reads it",
                        "2.0.0-SNAPSHOT",
                        true,
                        files("NOTES.md", "Working towards 2.0.0-SNAPSHOT.\n"),
                        "No files were changed"
                ),
                new RefusedParameters(
                        "build version declared nowhere",
                        "2.0.0-SNAPSHOT",
                        true,
                        files("README.md", "com.example.widgets:beta:1.0.0\n", "NOTES.md", "2.0.0-SNAPSHOT\n"),
                        "No file declares the build's version"
                ),
                new RefusedParameters(
                        "line that names the project and no rule reads",
                        "2.0.0-SNAPSHOT",
                        true,
                        files(
                                "gradle.properties",
                                "version=2.0.0-SNAPSHOT\n",
                                "README.md",
                                "ok\ncom.example.widgets:beta:1.0.0.Final\n"
                        ),
                        "README.md:2"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void requireApplicable__refused(RefusedParameters parameters) {
        // ARRANGE //
        VersionBump bump = VersionBump
                .of("2.0.0", parameters.fromBuild() ? null : parameters.from(), parameters.from());
        BumpPlan plan = BumpPlan.plan(parameters.files(), ANCHORS, bump);

        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> plan.requireApplicable(bump));

        // ASSERT //
        assertTrue(thrown.getMessage().contains(parameters.message()), thrown.getMessage());
    }
}

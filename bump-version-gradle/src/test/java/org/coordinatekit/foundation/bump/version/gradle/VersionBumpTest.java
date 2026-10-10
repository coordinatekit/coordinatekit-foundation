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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Tests {@link VersionBump}, which turns the task's options and the build's version into one move
 * or refuses to.
 */
class VersionBumpTest {
    /**
     * One set of arguments the bump has to reject.
     *
     * @param name what the case shows
     * @param to the value of {@code --to}
     * @param from the value of {@code --from}
     * @param buildVersion the version the build reports
     * @param message text the failure has to contain
     */
    private record RejectedParameters(
            String name,
            @Nullable String to,
            @Nullable String from,
            String buildVersion,
            String message
    ) {}

    @Test
    void currentIsSnapshot__followsTheSuffix() {
        // ARRANGE //
        VersionBump snapshot = VersionBump.of("1.0.0", null, "0.9.0-SNAPSHOT");
        VersionBump release = VersionBump.of("1.1.0-SNAPSHOT", null, "1.0.0");

        // ASSERT //
        assertTrue(snapshot.currentIsSnapshot());
        assertFalse(release.currentIsSnapshot());
    }

    @Test
    void nextIsRelease__followsTheSuffix() {
        // ASSERT //
        assertTrue(VersionBump.of("1.0.0", null, "0.9.0").nextIsRelease());
        assertFalse(VersionBump.of("1.1.0-SNAPSHOT", null, "1.0.0").nextIsRelease());
    }

    @Test
    void of__readsTheCurrentVersionFromTheBuild() {
        // ACT //
        VersionBump bump = VersionBump.of("1.0.0", null, "0.9.0-SNAPSHOT");

        // ASSERT //
        assertEquals("0.9.0-SNAPSHOT", bump.current());
        assertEquals("1.0.0", bump.next());
        assertTrue(bump.currentFromBuild());
    }

    static Stream<RejectedParameters> of__rejected() {
        return Stream.of(
                new RejectedParameters("missing target", null, null, "1.0.0", "--to"),
                new RejectedParameters("blank target", " ", null, "1.0.0", "--to"),
                new RejectedParameters(
                        "target that is no version",
                        "latest",
                        null,
                        "1.0.0",
                        "'latest' is not a version"
                ),
                new RejectedParameters("target with a stray dot", "1..2", null, "1.0.0", "is not a version"),
                new RejectedParameters("target with a trailing space", "1.2.3 ", null, "1.0.0", "is not a version"),
                new RejectedParameters("build without a version", "1.0.0", null, "unspecified", "no version"),
                new RejectedParameters("blank build version", "1.0.0", null, "", "no version"),
                new RejectedParameters(
                        "current that is no version",
                        "1.0.0",
                        "main",
                        "0.1.0",
                        "'main' is not a version"
                ),
                new RejectedParameters("same version", "1.0.0", null, "1.0.0", "are the same"),
                new RejectedParameters("same version given with --from", "1.0.0", "1.0.0", "0.5.0", "are the same")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void of__rejected(RejectedParameters parameters) {
        // ACT //
        GradleException thrown = assertThrows(
                GradleException.class,
                () -> VersionBump.of(parameters.to(), parameters.from(), parameters.buildVersion())
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains(parameters.message()), thrown.getMessage());
    }

    @Test
    void of__usesFromInsteadOfTheBuild() {
        // ACT //
        VersionBump bump = VersionBump.of("1.0.0", "0.5.0", "unspecified");

        // ASSERT //
        assertEquals("0.5.0", bump.current());
        assertFalse(bump.currentFromBuild());
    }
}

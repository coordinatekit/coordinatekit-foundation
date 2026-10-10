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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Tests {@link ReleaseVersions}, which turns the task's two options into one pair of versions or
 * refuses to.
 */
class ReleaseVersionsTest {
    /**
     * One pair of arguments the rewrite has to reject.
     *
     * @param name what the case shows
     * @param release the value of {@code --release}
     * @param next the value of {@code --next}
     * @param message text the failure has to contain
     */
    private record RejectedParameters(String name, @Nullable String release, @Nullable String next, String message) {}

    @Test
    void of__acceptsAPair() {
        // ACT //
        ReleaseVersions versions = ReleaseVersions.of("1.0.0", "1.1.0-SNAPSHOT");

        // ASSERT //
        assertEquals("1.0.0", versions.release());
        assertEquals("1.1.0-SNAPSHOT", versions.next());
    }

    @Test
    void of__namesTheOptionsAndThePropertiesWhenAVersionIsMissing() {
        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> ReleaseVersions.of(null, null));

        // ASSERT //
        assertTrue(thrown.getMessage().contains("--release"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("--next"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("release.releaseVersion"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("release.newVersion"), thrown.getMessage());
    }

    static Stream<RejectedParameters> of__rejected() {
        return Stream.of(
                new RejectedParameters("missing release", null, "1.1.0-SNAPSHOT", "--release"),
                new RejectedParameters("blank release", " ", "1.1.0-SNAPSHOT", "--release"),
                new RejectedParameters("missing next", "1.0.0", null, "--next"),
                new RejectedParameters("blank next", "1.0.0", "", "--next"),
                new RejectedParameters(
                        "release that is no version",
                        "latest",
                        "1.1.0-SNAPSHOT",
                        "'latest' is not a version"
                ),
                new RejectedParameters("release with a stray dot", "1..2", "1.1.0-SNAPSHOT", "is not a version"),
                new RejectedParameters("release with a trailing space", "1.2.3 ", "1.3.0-SNAPSHOT", "is not a version"),
                new RejectedParameters(
                        "next that is no version",
                        "1.0.0",
                        "soon-SNAPSHOT",
                        "'soon-SNAPSHOT' is not a version"
                ),
                new RejectedParameters(
                        "release that is a snapshot",
                        "1.0.0-SNAPSHOT",
                        "1.1.0-SNAPSHOT",
                        "ends in -SNAPSHOT"
                ),
                new RejectedParameters("next that is a release", "1.0.0", "1.1.0", "does not end in -SNAPSHOT")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void of__rejected(RejectedParameters parameters) {
        // ACT //
        GradleException thrown = assertThrows(
                GradleException.class,
                () -> ReleaseVersions.of(parameters.release(), parameters.next())
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains(parameters.message()), thrown.getMessage());
    }
}

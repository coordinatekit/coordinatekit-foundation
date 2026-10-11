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

import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * The two versions one rewrite carries: the release being cut and the snapshot that follows it. A
 * form that advertises a release moves to the first, and a form that tracks the version in
 * development moves to the second. A mention that already shows a snapshot where a release belongs
 * is caught up in the same pass.
 *
 * @param release the version being released, which never ends in {@code -SNAPSHOT}
 * @param next the version in development after the release, which always ends in {@code -SNAPSHOT}
 */
record ReleaseVersions(String release, String next) {
    /** The suffix that marks a version as one in development. */
    static final String SNAPSHOT = "-SNAPSHOT";

    /** A version as a regular expression, to be placed in a group of a larger pattern. */
    static final String VERSION_PATTERN = "[0-9]+(?:\\.[0-9]+)*(?:[-+][A-Za-z0-9]+(?:\\.[0-9]+)*)*";

    /** The shape a version has to have, dot separated numbers and optional qualifiers. */
    private static final Pattern VERSION_SHAPE = Pattern.compile(VERSION_PATTERN);

    /**
     * Validates the two versions of one rewrite before any file is read.
     *
     * @param release the version being released, as passed to {@code --release}
     * @param next the version in development after it, as passed to {@code --next}
     * @return the pair
     * @throws GradleException if either version is missing or is not a version, if {@code release} ends
     *         in {@code -SNAPSHOT}, or if {@code next} does not
     */
    static ReleaseVersions of(@Nullable String release, @Nullable String next) {
        if (release == null || release.isBlank() || next == null || next.isBlank()) {
            throw new GradleException(
                    "Pass both versions with --release and --next, as in: ./gradlew rewriteVersionReferences"
                            + " --release=<version> --next=<version>-SNAPSHOT. Inside a gradle-release build they default"
                            + " to the release.releaseVersion and release.newVersion properties."
            );
        }
        for (String version : new String[] {release, next}) {
            if (!VERSION_SHAPE.matcher(version).matches()) {
                throw new GradleException(
                        "'" + version + "' is not a version. Use dotted numbers with an optional qualifier."
                );
            }
        }
        if (release.endsWith(SNAPSHOT)) {
            throw new GradleException("The release version '" + release + "' ends in " + SNAPSHOT + ".");
        }
        if (!next.endsWith(SNAPSHOT)) {
            throw new GradleException("The next version '" + next + "' does not end in " + SNAPSHOT + ".");
        }
        return new ReleaseVersions(release, next);
    }
}

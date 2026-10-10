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

import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * One validated move from the current version to a new one. Whether the new version is a snapshot
 * decides which forms a bump rewrites: a release bump moves the published coordinates, jar names,
 * and plugin ids that name the last release, and a snapshot bump moves the snapshot coordinates
 * that name the version in development.
 *
 * @param current the version the files name now
 * @param next the version to write
 * @param currentFromBuild whether {@code current} came from the build, as opposed to {@code --from}
 */
record VersionBump(String current, String next, boolean currentFromBuild) {

    /** The suffix that marks a version as one in development. */
    static final String SNAPSHOT = "-SNAPSHOT";

    /** A version as a regular expression, to be placed in a group of a larger pattern. */
    static final String VERSION_PATTERN = "[0-9]+(?:\\.[0-9]+)*(?:[-+][A-Za-z0-9]+(?:\\.[0-9]+)*)*";

    /** The shape a version has to have, dot separated numbers and optional qualifiers. */
    private static final Pattern VERSION_SHAPE = Pattern.compile(VERSION_PATTERN);

    /**
     * Whether the current version is one in development.
     *
     * @return {@code true} if the current version ends in {@code -SNAPSHOT}
     */
    boolean currentIsSnapshot() {
        return current.endsWith(SNAPSHOT);
    }

    /**
     * Whether the new version is a release.
     *
     * @return {@code true} if the new version does not end in {@code -SNAPSHOT}
     */
    boolean nextIsRelease() {
        return !next.endsWith(SNAPSHOT);
    }

    /**
     * Validates the arguments of one bump.
     *
     * @param to the new version, as passed to {@code --to}
     * @param from the current version, as passed to {@code --from}, or {@code null} to use the build's
     * @param buildVersion the version the build reports
     * @return the bump
     * @throws GradleException if {@code to} is missing or is not a version, if no current version can
     *         be determined, or if the current and new versions are the same
     */
    static VersionBump of(@Nullable String to, @Nullable String from, String buildVersion) {
        if (to == null || to.isBlank()) {
            throw new GradleException("Pass the new version with --to, as in: ./gradlew bumpVersion --to=<version>");
        }
        if (!VERSION_SHAPE.matcher(to).matches()) {
            throw new GradleException("'" + to + "' is not a version. Use dotted numbers with an optional qualifier.");
        }
        boolean fromBuild = from == null || from.isBlank();
        String current = fromBuild ? buildVersion : from;
        if (fromBuild && (current.isBlank() || current.equals("unspecified"))) {
            throw new GradleException(
                    "The build declares no version to move from. Set version in gradle.properties or the root build,"
                            + " or pass the current version with --from."
            );
        }
        if (!VERSION_SHAPE.matcher(current).matches()) {
            throw new GradleException("The current version '" + current + "' is not a version.");
        }
        if (current.equals(to)) {
            throw new GradleException("The current version and the new version are the same (" + to + ").");
        }
        return new VersionBump(current, to, fromBuild);
    }
}

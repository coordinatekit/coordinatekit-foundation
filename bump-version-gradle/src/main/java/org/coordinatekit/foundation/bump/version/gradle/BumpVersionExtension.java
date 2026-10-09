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

import org.gradle.api.provider.ListProperty;

/**
 * The {@code foundationVersion} block a build configures the version bump through. A build with
 * nothing to leave alone needs no block at all.
 *
 * <pre>
 * foundationVersion {
 *     exclude "docs/archive/**", "src/test/resources/**"
 * }
 * </pre>
 *
 * @see BumpVersionPlugin
 */
public abstract class BumpVersionExtension {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public BumpVersionExtension() {}

    /**
     * Adds globs of tracked files the bump leaves alone.
     *
     * @param patterns the globs to add, in the form {@link #getExcludes()} describes
     */
    public void exclude(String... patterns) {
        getExcludes().addAll(patterns);
    }

    /**
     * The globs of tracked files the bump leaves alone, relative to the root project and written with
     * {@code /}. It is empty by default. A file that holds version-like text as data, such as a test
     * fixture or an archived document, belongs here.
     *
     * @return the exclusion globs
     */
    public abstract ListProperty<String> getExcludes();
}

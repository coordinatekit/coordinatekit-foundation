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
package org.coordinatekit.foundation.changelog.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code foundationChangelog} block a build configures the changelog through. A build that sets
 * {@code repoUrl} in {@code gradle.properties} needs no block at all; the properties below cover
 * what differs from one repository to the next.
 *
 * <pre>
 * foundationChangelog {
 *     fromRevision = "v0.1.0"
 *     initialRelease = file(".infra/changelog_initial_release.md")
 * }
 * </pre>
 *
 * @see ChangelogPlugin
 */
public abstract class ChangelogExtension {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public ChangelogExtension() {}

    /**
     * The tag or commit the changelog starts from, exclusive. Unset by default, which renders the whole
     * history. A repository whose earlier releases predate conventional commits sets it to the last
     * such tag and carries those releases in {@link #getInitialRelease()}.
     *
     * @return the revision to start the changelog after
     */
    public abstract Property<String> getFromRevision();

    /**
     * A Markdown file appended after the last generated release, for the history that
     * {@link #getFromRevision()} cuts off. Its trailing whitespace is dropped, and nothing is appended
     * while the property is unset. A file that is set but does not exist fails the build.
     *
     * @return the file holding the pre-conventional-commits releases
     */
    public abstract RegularFileProperty getInitialRelease();

    /**
     * The repository's web address, without a trailing slash, such as
     * {@code https://github.com/coordinatekit/crf}. The template builds every pull request, commit, and
     * release link from it. It defaults to the {@code repoUrl} Gradle property, which the publishing
     * setup already reads for the POM, and a build that sets neither fails when {@code gitChangelog}
     * runs.
     *
     * @return the repository URL the changelog's links point at
     */
    public abstract Property<String> getRepoUrl();
}

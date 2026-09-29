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

/**
 * The Gradle plugin that generates CoordinateKit's changelog from conventional commits.
 *
 * <p>
 * {@link org.coordinatekit.foundation.changelog.gradle.ChangelogPlugin} is applied under the id
 * {@code org.coordinatekit.foundation.changelog}. It applies the git-changelog Gradle plugin and
 * configures its {@code gitChangelog} task with CoordinateKit's template, section list, and
 * helpers, so a build sets only what differs per repository, in the
 * {@link org.coordinatekit.foundation.changelog.gradle.ChangelogExtension}, the typed
 * {@code foundationChangelog} block.
 *
 * <p>
 * git-changelog publishes to the Gradle Plugin Portal and not to Maven Central, so a consumer's
 * {@code settings.gradle} needs both {@code mavenCentral()} and {@code gradlePluginPortal()} in its
 * {@code pluginManagement} repositories before the {@code plugins} block can resolve this plugin.
 * README.md has the recipe.
 */
@NullMarked
package org.coordinatekit.foundation.changelog.gradle;

import org.jspecify.annotations.NullMarked;

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
 * The Gradle plugin that configures Concordance, the member-order rule CoordinateKit's Java sources
 * follow.
 *
 * <p>
 * {@link org.coordinatekit.foundation.concordance.gradle.ConcordancePlugin} is applied under the id
 * {@code org.coordinatekit.foundation.concordance}. It reacts to {@code net.ltgt.errorprone} rather
 * than applying it, adds the check jar at its own version to the {@code errorprone} configuration,
 * and configures every Java compilation from the
 * {@link org.coordinatekit.foundation.concordance.gradle.ConcordanceExtension}, the typed
 * {@code concordance} block a build sets its severity and its exemptions in.
 *
 * <p>
 * The plugin publishes to Maven Central rather than to the Gradle Plugin Portal, so a consumer's
 * {@code settings.gradle} needs {@code mavenCentral()} in its {@code pluginManagement} repositories
 * before the {@code plugins} block can resolve it. README.md has the recipe.
 */
@NullMarked
package org.coordinatekit.foundation.concordance.gradle;

import org.jspecify.annotations.NullMarked;

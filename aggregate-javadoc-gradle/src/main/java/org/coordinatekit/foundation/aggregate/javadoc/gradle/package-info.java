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
 * The Gradle plugin that gathers the Javadoc of a multi-module build into one set of pages, with a
 * tab for each module.
 *
 * <p>
 * {@link org.coordinatekit.foundation.aggregate.javadoc.gradle.AggregateJavadocPlugin} is applied
 * to the root project under the id {@code org.coordinatekit.foundation.aggregate-javadoc}. It
 * registers the {@code aggregateJavadoc} task and configures it from the
 * {@link org.coordinatekit.foundation.aggregate.javadoc.gradle.AggregateJavadocExtension}, the
 * typed {@code aggregateJavadoc} block a build sets its title, links, and module word forms in.
 *
 * <p>
 * The plugin publishes to Maven Central rather than to the Gradle Plugin Portal, so a consumer's
 * {@code settings.gradle} needs {@code mavenCentral()} in its {@code pluginManagement} repositories
 * before the {@code plugins} block can resolve it. README.md has the recipe.
 */
@NullMarked
package org.coordinatekit.foundation.aggregate.javadoc.gradle;

import org.jspecify.annotations.NullMarked;

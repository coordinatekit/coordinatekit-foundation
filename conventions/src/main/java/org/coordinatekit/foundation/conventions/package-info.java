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
 * The Eclipse formatter profile and Apache-2.0 license header CoordinateKit's Java sources are
 * formatted against.
 *
 * <p>
 * This package holds no code. Its two entries, {@code eclipse_java_coordinatekit.xml} and
 * {@code license_header.txt}, live under {@code src/main/resources} and are the whole content of
 * the published jar. It is not a Gradle plugin and configures nothing on its own; a consumer reads
 * whichever entry it wants and wires it into its own build. See README.md for the recipe.
 *
 * <p>
 * This package declaration deliberately carries no {@code @NullMarked}: an annotated package
 * declaration compiles to a {@code package-info.class}, which would put a class file in a jar that
 * is meant to hold only the two resources above. Leaving the declaration bare keeps the binary jar
 * exactly as it is while still giving the sources and javadoc jars real content to publish.
 */
package org.coordinatekit.foundation.conventions;

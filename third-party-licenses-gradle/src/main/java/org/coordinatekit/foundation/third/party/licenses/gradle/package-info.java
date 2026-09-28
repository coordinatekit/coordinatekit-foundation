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
 * The Gradle plugin that checks a build's runtime dependency licenses and renders the third-party
 * attribution file its distribution ships.
 *
 * <p>
 * {@link org.coordinatekit.foundation.third.party.licenses.gradle.ThirdPartyLicensesPlugin} is
 * applied under the id {@code org.coordinatekit.foundation.third-party-licenses}. It applies and
 * configures the {@code com.github.jk1.dependency-license-report} plugin, fails {@code check} on a
 * license the build's allowlist does not permit, and adds a
 * {@link org.coordinatekit.foundation.third.party.licenses.gradle.GenerateThirdPartyLicenses} task
 * that renders {@code THIRD-PARTY-LICENSES.txt}. The build supplies every license fact through the
 * {@link org.coordinatekit.foundation.third.party.licenses.gradle.ThirdPartyLicensesExtension}, the
 * typed {@code thirdPartyLicenses} block, and the plugin supplies only the logic.
 *
 * <p>
 * The plugin depends on the license report plugin, which publishes to the Gradle Plugin Portal and
 * not to Maven Central, so a consumer's {@code settings.gradle} needs both {@code mavenCentral()}
 * and {@code gradlePluginPortal()} in its {@code pluginManagement} repositories. README.md has the
 * recipe.
 */
@NullMarked
package org.coordinatekit.foundation.third.party.licenses.gradle;

import org.jspecify.annotations.NullMarked;

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
package org.coordinatekit.foundation.third.party.licenses.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code thirdPartyLicenses} block a build configures the dependency license check and the
 * attribution file through. The plugin ships logic only: the license texts, the allowlist, the
 * normalizer bundle, the overrides, and the copyright notices all belong to the build that applies
 * it, because they are that build's compliance decisions.
 *
 * <pre>
 * thirdPartyLicenses {
 *     excludeGroups = ['org.example']
 *     licenseOverrides = ['org.example.legacy:legacy-core': 'MIT License']
 *     copyrightNotices = ['org.example.legacy:legacy-core': 'Copyright (c) 2009 Example Authors']
 *     licenses {
 *         register('Apache License, Version 2.0') { text = file('licenses/Apache-2.0.txt') }
 *         register('MIT License') { text = file('licenses/MIT.txt'); noticeRequired = true }
 *     }
 * }
 * </pre>
 *
 * Module keys are {@code group:artifact}, the form the license report names dependencies by.
 *
 * @see ThirdPartyLicensesPlugin
 * @see LicenseDefinition
 */
public abstract class ThirdPartyLicensesExtension {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public ThirdPartyLicensesExtension() {}

    /**
     * The JSON file listing the licenses each runtime dependency is allowed to carry, in the format of
     * the license report's own {@code checkLicense} task. Every non-empty {@code moduleLicense} in it
     * must be a license registered in {@link #getLicenses()}. Defaults to {@code allowed-licenses.json}
     * in the project directory.
     *
     * @return the allowlist file
     */
    public abstract RegularFileProperty getAllowedLicensesFile();

    /**
     * The copyright notice to print under a dependency's entry, keyed by {@code group:artifact}. A
     * dependency whose license sets {@link LicenseDefinition#getNoticeRequired()} fails the build
     * without one. Empty by default.
     *
     * @return the notice text for each module
     */
    public abstract MapProperty<String, String> getCopyrightNotices();

    /**
     * Whether to leave out a bill-of-materials platform, a module named {@code bom} or ending in
     * {@code -bom} that has no artifacts of its own and so carries no license. {@code false} by
     * default.
     *
     * @return whether to drop zero-artifact BOM platforms from the report
     */
    public abstract Property<Boolean> getExcludeBoms();

    /**
     * The group ids to leave out of the report, each matched against the whole group id as a regular
     * expression. The build's own group is always left out. Empty by default.
     *
     * @return the group ids of modules that need no attribution
     */
    public abstract ListProperty<String> getExcludeGroups();

    /**
     * The license to report for a dependency in place of whatever its POM declares, keyed by
     * {@code group:artifact}. This covers a dependency whose POM chain declares no license at all. The
     * value must be a license registered in {@link #getLicenses()}. Empty by default.
     *
     * @return the license name for each overridden module
     */
    public abstract MapProperty<String, String> getLicenseOverrides();

    /**
     * The licenses the build's dependencies may carry, by report name. A dependency under a license
     * that is not registered here fails {@code generateThirdPartyLicenses} instead of shipping an
     * incomplete attribution.
     *
     * @return the registered licenses
     */
    public abstract NamedDomainObjectContainer<LicenseDefinition> getLicenses();

    /**
     * The JSON file of license-name normalization rules, merged with the license report's built-in
     * bundle. Defaults to {@code license-normalizer-bundle.json} in the project directory. When no such
     * file exists, no normalizer runs and names are reported as each POM spells them.
     *
     * @return the normalizer bundle file
     */
    public abstract RegularFileProperty getNormalizerBundleFile();

    /**
     * Configures {@link #getLicenses()}, so a build can write {@code licenses { ... }} as a nested
     * block.
     *
     * @param action the configuration to apply to the license container
     */
    public void licenses(Action<? super NamedDomainObjectContainer<LicenseDefinition>> action) {
        action.execute(getLicenses());
    }
}

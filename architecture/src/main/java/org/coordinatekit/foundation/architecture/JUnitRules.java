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
package org.coordinatekit.foundation.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;

/**
 * The {@link ArchRule}s that apply to a module's test classes, one factory per {@link Rule}.
 *
 * <p>
 * The rules match JUnit by name rather than by type, so JUnit is not a dependency of this module.
 * Every rule is created with {@code allowEmptyShould(true)}.
 */
final class JUnitRules {
    private JUnitRules() {}

    /**
     * Creates the rule behind {@link Rule#TEST_CLASS_VISIBILITY}.
     *
     * @return a rule that fails for every non-interface class named {@code *Test} that is not
     *         package-private
     */
    static ArchRule testClassVisibility() {
        return classes().that()
                .areNotInterfaces()
                .and()
                .haveNameMatching(".*Test")
                .should()
                .bePackagePrivate()
                .as("Classes with names matching .*Test should be package-private")
                .allowEmptyShould(true);
    }
}

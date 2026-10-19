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

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;

/**
 * The {@link ArchRule}s that apply to a module's test classes, one factory per {@link Rule}. A
 * constant made of several rules returns them as a list.
 *
 * <p>
 * The rules match JUnit by name rather than by type, so JUnit is not a dependency of this module.
 * Every rule is created with {@code allowEmptyShould(true)}.
 */
final class JUnitRules {
    private static final String DISABLED = "org.junit.jupiter.api.Disabled";
    private static final String PARAMETERIZED_TEST = "org.junit.jupiter.params.ParameterizedTest";
    private static final String TEST = "org.junit.jupiter.api.Test";

    private JUnitRules() {}

    /**
     * Creates the rules behind {@link Rule#DISABLED_TESTS}.
     *
     * <p>
     * Both rules look for {@code @Disabled} as a meta-annotation, so an annotation composed from it
     * counts, and the annotation type itself fails the class rule. {@code @DisabledIfSystemProperty}
     * and the other conditional annotations are not {@code @Disabled} and pass.
     *
     * @return a rule for classes and a rule for non-synthetic methods, in that order
     */
    static List<ArchRule> disabledTests() {
        return List.of(
                noClasses().should()
                        .beMetaAnnotatedWith(DISABLED)
                        .as("Classes should not be annotated with " + DISABLED)
                        .allowEmptyShould(true),
                noMethods().that()
                        .doNotHaveModifier(JavaModifier.SYNTHETIC)
                        .should()
                        .beMetaAnnotatedWith(DISABLED)
                        .as("Methods should not be annotated with " + DISABLED)
                        .allowEmptyShould(true)
        );
    }

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

    /**
     * Creates the rule behind {@link Rule#TEST_METHOD_VISIBILITY}.
     *
     * <p>
     * A method is selected through a meta-annotation, so an annotation composed from {@code @Test}
     * counts. {@code @RepeatedTest}, {@code @TestFactory}, {@code @TestTemplate}, and lifecycle methods
     * are not selected.
     *
     * @return a rule that fails for every non-synthetic test method that is not package-private
     */
    static ArchRule testMethodVisibility() {
        return methods().that(are(metaAnnotatedWith(TEST).or(metaAnnotatedWith(PARAMETERIZED_TEST))))
                .and()
                .doNotHaveModifier(JavaModifier.SYNTHETIC)
                .should()
                .bePackagePrivate()
                .as("Methods annotated with " + TEST + " or " + PARAMETERIZED_TEST + " should be package-private")
                .allowEmptyShould(true);
    }
}

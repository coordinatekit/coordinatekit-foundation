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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.coordinatekit.foundation.architecture.fixtures.ComposedDisabledMethod;
import org.coordinatekit.foundation.architecture.fixtures.ConditionallyDisabledMethod;
import org.coordinatekit.foundation.architecture.fixtures.DisabledClass;
import org.coordinatekit.foundation.architecture.fixtures.DisabledMethod;
import org.coordinatekit.foundation.architecture.fixtures.NonPublicFixtures;
import org.coordinatekit.foundation.architecture.fixtures.PackagePrivateTestMethods;
import org.coordinatekit.foundation.architecture.fixtures.PublicAbstractClassTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicClassTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicClassWithOtherSuffix;
import org.coordinatekit.foundation.architecture.fixtures.PublicComposedTestMethod;
import org.coordinatekit.foundation.architecture.fixtures.PublicEnumTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicInterfaceTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicParameterizedTestMethod;
import org.coordinatekit.foundation.architecture.fixtures.PublicRecordTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicTestMethod;
import org.coordinatekit.foundation.architecture.fixtures.PublicUnselectedMethods;
import org.coordinatekit.foundation.architecture.fixtures.Skipped;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

/**
 * Tests for the factories in {@link JUnitRules}. Each case imports a few fixtures, evaluates one
 * rule, and asserts which fixtures its violations name.
 */
class JUnitRulesTest {
    record RuleParameters(String name, List<Class<?>> fixtures, List<Class<?>> violators) {}

    static Stream<RuleParameters> disabledTests() {
        return Stream.of(
                new RuleParameters(
                        "disabled class violates",
                        List.of(DisabledClass.class),
                        List.of(DisabledClass.class)
                ),
                new RuleParameters(
                        "disabled method violates",
                        List.of(DisabledMethod.class),
                        List.of(DisabledMethod.class)
                ),
                new RuleParameters(
                        "method disabled through a composed annotation violates",
                        List.of(ComposedDisabledMethod.class),
                        List.of(ComposedDisabledMethod.class)
                ),
                new RuleParameters(
                        "the composed annotation itself violates the class rule",
                        List.of(Skipped.class),
                        List.of(Skipped.class)
                ),
                new RuleParameters(
                        "conditionally disabled method passes",
                        List.of(ConditionallyDisabledMethod.class),
                        List.of()
                ),
                new RuleParameters(
                        "only the disabled classes are named",
                        List.of(DisabledClass.class, DisabledMethod.class, ConditionallyDisabledMethod.class),
                        List.of(DisabledClass.class, DisabledMethod.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void disabledTests(RuleParameters parameters) {
        Fixtures.assertViolators(
                JUnitRules.disabledTests(),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void disabledTests__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = JUnitRules.disabledTests().stream().map(rule -> rule.evaluate(none)).toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void disabledTests__hasDescriptions() {
        // ARRANGE //
        List<ArchRule> rules = JUnitRules.disabledTests();

        // ACT //
        List<String> descriptions = rules.stream().map(ArchRule::getDescription).toList();

        // ASSERT //
        assertEquals(
                List.of(
                        "Classes should not be annotated with org.junit.jupiter.api.Disabled",
                        "Methods should not be annotated with org.junit.jupiter.api.Disabled"
                ),
                descriptions
        );
    }

    static Stream<RuleParameters> testClassVisibility() {
        return Stream.of(
                new RuleParameters(
                        "public class violates",
                        List.of(PublicClassTest.class),
                        List.of(PublicClassTest.class)
                ),
                new RuleParameters(
                        "public enum violates",
                        List.of(PublicEnumTest.class),
                        List.of(PublicEnumTest.class)
                ),
                new RuleParameters(
                        "public record violates",
                        List.of(PublicRecordTest.class),
                        List.of(PublicRecordTest.class)
                ),
                new RuleParameters(
                        "public abstract class violates",
                        List.of(PublicAbstractClassTest.class),
                        List.of(PublicAbstractClassTest.class)
                ),
                new RuleParameters(
                        "package-private class passes",
                        List.of(NonPublicFixtures.PACKAGE_PRIVATE_CLASS_TEST),
                        List.of()
                ),
                new RuleParameters("public interface is not selected", List.of(PublicInterfaceTest.class), List.of()),
                new RuleParameters(
                        "another suffix is not selected",
                        List.of(PublicClassWithOtherSuffix.class),
                        List.of()
                ),
                new RuleParameters(
                        "only the violating classes are named",
                        List.of(
                                PublicClassTest.class,
                                NonPublicFixtures.PACKAGE_PRIVATE_CLASS_TEST,
                                PublicInterfaceTest.class,
                                PublicClassWithOtherSuffix.class,
                                PublicEnumTest.class
                        ),
                        List.of(PublicClassTest.class, PublicEnumTest.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void testClassVisibility(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JUnitRules.testClassVisibility()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void testClassVisibility__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        EvaluationResult result = JUnitRules.testClassVisibility().evaluate(none);

        // ASSERT //
        assertFalse(result.hasViolation());
    }

    @Test
    void testClassVisibility__hasDescription() {
        // ARRANGE //
        ArchRule rule = JUnitRules.testClassVisibility();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Classes with names matching .*Test should be package-private", description);
    }

    static Stream<RuleParameters> testMethodVisibility() {
        return Stream.of(
                new RuleParameters(
                        "public test method violates",
                        List.of(PublicTestMethod.class),
                        List.of(PublicTestMethod.class)
                ),
                new RuleParameters(
                        "public parameterized test method violates",
                        List.of(PublicParameterizedTestMethod.class),
                        List.of(PublicParameterizedTestMethod.class)
                ),
                new RuleParameters(
                        "public method with a composed test annotation violates",
                        List.of(PublicComposedTestMethod.class),
                        List.of(PublicComposedTestMethod.class)
                ),
                new RuleParameters(
                        "package-private test methods pass",
                        List.of(PackagePrivateTestMethods.class),
                        List.of()
                ),
                new RuleParameters(
                        "public repeated, factory, template, and lifecycle methods are not selected",
                        List.of(PublicUnselectedMethods.class),
                        List.of()
                ),
                new RuleParameters(
                        "only the public test methods are named",
                        List.of(PublicTestMethod.class, PackagePrivateTestMethods.class, PublicUnselectedMethods.class),
                        List.of(PublicTestMethod.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void testMethodVisibility(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JUnitRules.testMethodVisibility()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void testMethodVisibility__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JUnitRules.testMethodVisibility())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void testMethodVisibility__hasDescription() {
        // ARRANGE //
        ArchRule rule = JUnitRules.testMethodVisibility();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals(
                "Methods annotated with org.junit.jupiter.api.Test or org.junit.jupiter.params.ParameterizedTest should be package-private",
                description
        );
    }
}

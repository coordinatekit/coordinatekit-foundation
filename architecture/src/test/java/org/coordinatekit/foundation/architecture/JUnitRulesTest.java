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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
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
    record RuleParameters(String name, List<String> fixtures, List<String> violators) {}

    static Stream<RuleParameters> testClassVisibility() {
        return Stream.of(
                new RuleParameters("public class violates", List.of("PublicClassTest"), List.of("PublicClassTest")),
                new RuleParameters("public enum violates", List.of("PublicEnumTest"), List.of("PublicEnumTest")),
                new RuleParameters("public record violates", List.of("PublicRecordTest"), List.of("PublicRecordTest")),
                new RuleParameters(
                        "public abstract class violates",
                        List.of("PublicAbstractClassTest"),
                        List.of("PublicAbstractClassTest")
                ),
                new RuleParameters("package-private class passes", List.of("PackagePrivateClassTest"), List.of()),
                new RuleParameters("public interface is not selected", List.of("PublicInterfaceTest"), List.of()),
                new RuleParameters("another suffix is not selected", List.of("PublicClassWithOtherSuffix"), List.of()),
                new RuleParameters(
                        "only the violating classes are named",
                        List.of(
                                "PublicClassTest",
                                "PackagePrivateClassTest",
                                "PublicInterfaceTest",
                                "PublicClassWithOtherSuffix",
                                "PublicEnumTest"
                        ),
                        List.of("PublicClassTest", "PublicEnumTest")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void testClassVisibility(RuleParameters parameters) {
        // ARRANGE //
        JavaClasses classes = Fixtures.of(parameters.fixtures().toArray(String[]::new));

        // ACT //
        EvaluationResult result = JUnitRules.testClassVisibility().evaluate(classes);

        // ASSERT //
        assertEquals(!parameters.violators().isEmpty(), result.hasViolation(), parameters.name());
        String details = String.join("\n", result.getFailureReport().getDetails());
        for (String fixture : parameters.fixtures()) {
            boolean named = details.contains(Fixtures.fullName(fixture) + ">");
            assertEquals(parameters.violators().contains(fixture), named, parameters.name() + ": " + fixture);
        }
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
}

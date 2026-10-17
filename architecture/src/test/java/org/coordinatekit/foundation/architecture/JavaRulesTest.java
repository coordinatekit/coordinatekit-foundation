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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

/**
 * Tests for the factories in {@link JavaRules}. Each case imports a few fixtures, evaluates one
 * rule, and asserts which fixtures its violations name, so a change to the wording of a detail does
 * not break a test here.
 */
class JavaRulesTest {
    record RuleParameters(String name, List<String> fixtures, List<String> violators) {}

    private static void assertRule(ArchRule rule, RuleParameters parameters) {
        // ARRANGE //
        JavaClasses classes = Fixtures.of(parameters.fixtures().toArray(String[]::new));

        // ACT //
        EvaluationResult result = rule.evaluate(classes);

        // ASSERT //
        assertEquals(!parameters.violators().isEmpty(), result.hasViolation(), parameters.name());
        String details = String.join("\n", result.getFailureReport().getDetails());
        for (String fixture : parameters.fixtures()) {
            boolean named = details.contains(Fixtures.fullName(fixture) + ".")
                    || details.contains(Fixtures.fullName(fixture) + ">");
            assertEquals(parameters.violators().contains(fixture), named, parameters.name() + ": " + fixture);
        }
    }

    static Stream<RuleParameters> publicRecords() {
        return Stream.of(
                new RuleParameters("public record violates", List.of("PublicRecord"), List.of("PublicRecord")),
                new RuleParameters("package-private record passes", List.of("PackagePrivateRecord"), List.of()),
                new RuleParameters("public class is not a record", List.of("SystemOutPrinter"), List.of()),
                new RuleParameters(
                        "only the public record is named",
                        List.of("PublicRecord", "PackagePrivateRecord", "SystemOutPrinter"),
                        List.of("PublicRecord")
                ),
                new RuleParameters(
                        "a record that prints still violates",
                        List.of("PrintingRecord"),
                        List.of("PrintingRecord")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void publicRecords(RuleParameters parameters) {
        assertRule(JavaRules.publicRecords(), parameters);
    }

    @Test
    void publicRecords__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        EvaluationResult result = JavaRules.publicRecords().evaluate(none);

        // ASSERT //
        assertFalse(result.hasViolation());
    }

    @Test
    void publicRecords__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.publicRecords();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Records should not be public", description);
    }

    static Stream<RuleParameters> systemStreams() {
        return Stream.of(
                new RuleParameters("System.out in a method", List.of("SystemOutPrinter"), List.of("SystemOutPrinter")),
                new RuleParameters(
                        "System.err through a method reference",
                        List.of("SystemErrMethodReference"),
                        List.of("SystemErrMethodReference")
                ),
                new RuleParameters(
                        "System.out in a static initializer",
                        List.of("StaticInitializerPrinter"),
                        List.of("StaticInitializerPrinter")
                ),
                new RuleParameters("System.out in a lambda", List.of("LambdaPrinter"), List.of("LambdaPrinter")),
                new RuleParameters("printStackTrace passes", List.of("StackTracePrinter"), List.of()),
                new RuleParameters("a class with no output passes", List.of("PackagePrivateRecord"), List.of()),
                new RuleParameters(
                        "only the printing classes are named",
                        List.of("SystemOutPrinter", "StackTracePrinter", "LambdaPrinter", "PublicRecord"),
                        List.of("SystemOutPrinter", "LambdaPrinter")
                ),
                new RuleParameters(
                        "a public record that prints violates",
                        List.of("PrintingRecord"),
                        List.of("PrintingRecord")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void systemStreams(RuleParameters parameters) {
        assertRule(JavaRules.systemStreams(), parameters);
    }

    @Test
    void systemStreams__detailNamesMethodAndStream() {
        // ARRANGE //
        JavaClasses classes = Fixtures.of("SystemOutPrinter");

        // ACT //
        EvaluationResult result = JavaRules.systemStreams().evaluate(classes);

        // ASSERT //
        String details = String.join("\n", result.getFailureReport().getDetails());
        assertTrue(details.contains("Method "), details);
        assertTrue(details.contains(Fixtures.fullName("SystemOutPrinter") + ".print()"), details);
        assertTrue(details.contains("java.lang.System.out"), details);
    }

    @Test
    void systemStreams__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.systemStreams();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Classes should not use System.out or System.err", description);
    }
}

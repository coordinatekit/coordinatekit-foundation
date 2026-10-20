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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.coordinatekit.foundation.architecture.fixtures.AbstractEnumLogger;
import org.coordinatekit.foundation.architecture.fixtures.AbstractLogger;
import org.coordinatekit.foundation.architecture.fixtures.ConcreteLogger;
import org.coordinatekit.foundation.architecture.fixtures.ExtendedLogger;
import org.coordinatekit.foundation.architecture.fixtures.ExtendedLoggerHolder;
import org.coordinatekit.foundation.architecture.fixtures.InstanceLogger;
import org.coordinatekit.foundation.architecture.fixtures.InterfaceLogger;
import org.coordinatekit.foundation.architecture.fixtures.MisnamedLogger;
import org.coordinatekit.foundation.architecture.fixtures.NonFinalLogger;
import org.coordinatekit.foundation.architecture.fixtures.PackagePrivateLogger;
import org.coordinatekit.foundation.architecture.fixtures.StaticAbstractLogger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Tests for the factories in {@link LoggerRules}. Each case imports a few fixtures, evaluates the
 * rules, and asserts which fixtures its violations name.
 */
class LoggerRulesTest {
    record FactoryParameters(String name, Supplier<List<ArchRule>> rules, List<String> descriptions) {}

    record RuleParameters(String name, List<Class<?>> fixtures, List<Class<?>> violators) {}

    @MethodSource("factories")
    @ParameterizedTest
    void descriptions(FactoryParameters parameters) {
        // ARRANGE //
        List<ArchRule> rules = parameters.rules().get();

        // ACT //
        List<String> descriptions = rules.stream().map(ArchRule::getDescription).toList();

        // ASSERT //
        assertEquals(parameters.descriptions(), descriptions, parameters.name());
    }

    @MethodSource("factories")
    @ParameterizedTest
    void emptyClassesPass(FactoryParameters parameters) {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = parameters.rules().get().stream().map(rule -> rule.evaluate(none)).toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation), parameters.name());
    }

    static Stream<FactoryParameters> factories() {
        return Stream.of(
                new FactoryParameters(
                        "loggerNaming",
                        LoggerRules::loggerNaming,
                        List.of(
                                "Abstract classes should declare logger as 'private final Logger log'",
                                "Concrete classes should declare logger as 'private static final Logger LOG'"
                        )
                )
        );
    }

    static Stream<RuleParameters> loggerNaming() {
        return Stream.of(
                new RuleParameters("concrete class with LOG passes", List.of(ConcreteLogger.class), List.of()),
                new RuleParameters("abstract class with log passes", List.of(AbstractLogger.class), List.of()),
                new RuleParameters(
                        "concrete class with a misnamed logger violates",
                        List.of(MisnamedLogger.class),
                        List.of(MisnamedLogger.class)
                ),
                new RuleParameters(
                        "concrete class with a non-static logger violates",
                        List.of(InstanceLogger.class),
                        List.of(InstanceLogger.class)
                ),
                new RuleParameters(
                        "abstract class with a static logger violates",
                        List.of(StaticAbstractLogger.class),
                        List.of(StaticAbstractLogger.class)
                ),
                new RuleParameters(
                        "concrete class with a package-private logger violates",
                        List.of(PackagePrivateLogger.class),
                        List.of(PackagePrivateLogger.class)
                ),
                new RuleParameters(
                        "concrete class with a non-final logger violates",
                        List.of(NonFinalLogger.class),
                        List.of(NonFinalLogger.class)
                ),
                new RuleParameters(
                        "a logger held by an interface is not selected",
                        List.of(InterfaceLogger.class),
                        List.of()
                ),
                new RuleParameters(
                        "an enum with an abstract method follows the concrete-class rule",
                        List.of(AbstractEnumLogger.class),
                        List.of()
                ),
                new RuleParameters(
                        "a field of a logger subtype is not selected",
                        List.of(ExtendedLogger.class, ExtendedLoggerHolder.class),
                        List.of()
                ),
                new RuleParameters(
                        "only the violating classes are named",
                        List.of(
                                ConcreteLogger.class,
                                AbstractLogger.class,
                                MisnamedLogger.class,
                                StaticAbstractLogger.class
                        ),
                        List.of(MisnamedLogger.class, StaticAbstractLogger.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void loggerNaming(RuleParameters parameters) {
        Fixtures.assertViolators(
                LoggerRules.loggerNaming(),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }
}

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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.coordinatekit.foundation.architecture.fixtures.NonPublicFixtures;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Imports the classes of the {@code fixtures} subpackage and asserts which of them a rule names.
 *
 * <p>
 * A test names each fixture by its class literal, or through {@link NonPublicFixtures} for the few
 * whose visibility keeps a literal from reaching them here. A class literal does not initialize its
 * class, so a fixture's static initializer never runs.
 */
final class Fixtures {
    /** The package that holds every fixture. */
    static final String PACKAGE = NonPublicFixtures.class.getPackageName();

    private Fixtures() {}

    /**
     * Evaluates rules over fixtures and asserts which fixtures the violations name.
     *
     * <p>
     * A fixture counts as named when the joined details contain its full name followed by {@code .},
     * {@code >}, or a space, the last being how a custom condition names a class by its full name. A
     * case lists only the classes whose own violations it asserts, never a target that those classes
     * merely reference.
     *
     * @param rules the rules to evaluate, whose details are joined
     * @param name what the case shows, for the failure message
     * @param fixtures the fixtures to import
     * @param violators the fixtures the details must name, and no others
     */
    static void assertViolators(List<ArchRule> rules, String name, List<Class<?>> fixtures, List<Class<?>> violators) {
        // ARRANGE //
        JavaClasses classes = of(fixtures.toArray(Class<?>[]::new));

        // ACT //
        List<EvaluationResult> results = rules.stream().map(rule -> rule.evaluate(classes)).toList();

        // ASSERT //
        assertEquals(!violators.isEmpty(), results.stream().anyMatch(EvaluationResult::hasViolation), name);
        String details = results.stream()
                .flatMap(result -> result.getFailureReport().getDetails().stream())
                .collect(Collectors.joining("\n"));
        for (Class<?> fixture : fixtures) {
            boolean named = details.contains(fixture.getName() + ".") || details.contains(fixture.getName() + ">")
                    || details.contains(fixture.getName() + " ");
            assertEquals(violators.contains(fixture), named, name + ": " + fixture.getSimpleName());
        }
    }

    /**
     * Imports fixtures, along with any other class a case needs beside them, such as a JDK class that
     * supplies a second classpath root.
     *
     * @param classes the classes to import
     * @return the imported classes
     */
    static JavaClasses of(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }
}

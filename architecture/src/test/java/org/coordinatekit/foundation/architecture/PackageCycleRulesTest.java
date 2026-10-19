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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.coordinatekit.foundation.architecture.fixtures.cycles.acyclic.AcyclicRoot;
import org.coordinatekit.foundation.architecture.fixtures.cycles.acyclic.child.AcyclicChild;
import org.coordinatekit.foundation.architecture.fixtures.cycles.cyclic.CyclicRoot;
import org.coordinatekit.foundation.architecture.fixtures.cycles.cyclic.child.CyclicChild;
import org.junit.jupiter.api.Test;

/**
 * Tests for the factory in {@link PackageCycleRules}. Each case imports a pair of fixtures and
 * names the package of the pair's root class as the namespace, so the root class forms the
 * {@code (root)} slice and the other class forms the {@code child} slice.
 */
class PackageCycleRulesTest {
    private static final String ACYCLIC_NAMESPACE = Fixtures.PACKAGE + ".cycles.acyclic";
    private static final String CYCLIC_NAMESPACE = Fixtures.PACKAGE + ".cycles.cyclic";

    @Test
    void packageCycles__acyclicPairPasses() {
        // ARRANGE //
        ArchRule rule = PackageCycleRules.packageCycles(ACYCLIC_NAMESPACE);
        JavaClasses classes = Fixtures.of(AcyclicRoot.class, AcyclicChild.class);

        // ACT //
        EvaluationResult result = rule.evaluate(classes);

        // ASSERT //
        assertFalse(result.hasViolation(), String.valueOf(result.getFailureReport()));
    }

    @Test
    void packageCycles__cyclicPairNamesBothClasses() {
        // ARRANGE //
        ArchRule rule = PackageCycleRules.packageCycles(CYCLIC_NAMESPACE);
        JavaClasses classes = Fixtures.of(CyclicRoot.class, CyclicChild.class);

        // ACT //
        EvaluationResult result = rule.evaluate(classes);

        // ASSERT //
        assertTrue(result.hasViolation());
        String details = String.join("\n", result.getFailureReport().getDetails());
        assertTrue(details.contains(CyclicRoot.class.getName()), details);
        assertTrue(details.contains(CyclicChild.class.getName()), details);
        assertTrue(details.contains("(root)"), details);
        assertTrue(details.contains("child"), details);
    }

    @Test
    void packageCycles__deeperPackagesShareTheirFirstSegmentSlice() {
        // ARRANGE //
        ArchRule rule = PackageCycleRules.packageCycles(Fixtures.PACKAGE + ".cycles");
        JavaClasses classes = Fixtures.of(CyclicRoot.class, CyclicChild.class);

        // ACT //
        EvaluationResult result = rule.evaluate(classes);

        // ASSERT //
        assertFalse(result.hasViolation(), String.valueOf(result.getFailureReport()));
    }

    @Test
    void packageCycles__descriptionNamesTheSubpackages() {
        // ARRANGE //
        ArchRule rule = PackageCycleRules.packageCycles(CYCLIC_NAMESPACE);

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertTrue(description.contains("subpackages of " + CYCLIC_NAMESPACE), description);
    }

    @Test
    void packageCycles__emptyClassesPass() {
        // ARRANGE //
        ArchRule rule = PackageCycleRules.packageCycles(CYCLIC_NAMESPACE);
        JavaClasses none = Fixtures.of();

        // ACT //
        EvaluationResult result = rule.evaluate(none);

        // ASSERT //
        assertFalse(result.hasViolation(), String.valueOf(result.getFailureReport()));
    }

    @Test
    void packageCycles__ignoresClassesOutsideTheNamespace() {
        // ARRANGE //
        ArchRule rule = PackageCycleRules.packageCycles(ACYCLIC_NAMESPACE);
        JavaClasses classes = Fixtures.of(CyclicRoot.class, CyclicChild.class);

        // ACT //
        EvaluationResult result = rule.evaluate(classes);

        // ASSERT //
        assertFalse(result.hasViolation(), String.valueOf(result.getFailureReport()));
    }
}

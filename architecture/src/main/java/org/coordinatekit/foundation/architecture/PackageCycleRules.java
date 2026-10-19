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

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

/**
 * The {@link ArchRule} that applies to the dependencies between a module's packages, one factory
 * per {@link Rule}.
 *
 * <p>
 * The rule is created with {@code allowEmptyShould(true)}, so a module with no classes under its
 * namespace passes. It keeps ArchUnit's own description, which names the slices.
 */
final class PackageCycleRules {
    private PackageCycleRules() {}

    /**
     * Creates the rule behind {@link Rule#PACKAGE_CYCLES}.
     *
     * <p>
     * Slices come from {@link SubpackageSliceAssignment}, so the classes that sit directly in the
     * namespace form a slice of their own and take part in the check.
     *
     * @param namespace the module's root package
     * @return a rule that fails for every dependency cycle among the slices
     */
    static ArchRule packageCycles(String namespace) {
        return slices().assignedFrom(new SubpackageSliceAssignment(namespace))
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true);
    }

    /**
     * Assigns every class under a namespace to a slice named after its first subpackage segment.
     *
     * <p>
     * ArchUnit's string form of a slice, {@code namespace + ".(*).."}, silently drops the classes that
     * sit directly in the namespace from every slice. This assignment gives them a slice of their own,
     * so they still take part in the cycle check.
     */
    private static final class SubpackageSliceAssignment implements SliceAssignment {
        /** The name of the slice that holds the classes sitting directly in the namespace. */
        private static final String ROOT_SLICE_NAME = "(root)";

        /** The package whose subpackages become slices. */
        private final String namespace;

        /**
         * Creates an assignment for the subpackages of a namespace.
         *
         * @param namespace the module's root package
         */
        SubpackageSliceAssignment(String namespace) {
            this.namespace = namespace;
        }

        /**
         * Describes the slices, for the rule's description.
         *
         * @return the text {@code subpackages of} followed by the namespace
         */
        @Override
        public String getDescription() {
            return "subpackages of " + namespace;
        }

        /**
         * Returns the slice a class belongs to.
         *
         * <p>
         * A class in the namespace itself goes to the slice named {@code (root)}. A class in a subpackage
         * goes to the slice named after the first segment below the namespace, however deeply the class is
         * nested. A class outside the namespace is ignored.
         *
         * @param javaClass the class to assign
         * @return the slice identifier, or the ignore marker for a class outside the namespace
         */
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            if (packageName.equals(namespace)) {
                return SliceIdentifier.of(ROOT_SLICE_NAME);
            }
            if (!packageName.startsWith(namespace + ".")) {
                return SliceIdentifier.ignore();
            }
            String remainder = packageName.substring(namespace.length() + 1);
            int dot = remainder.indexOf('.');
            return SliceIdentifier.of(dot < 0 ? remainder : remainder.substring(0, dot));
        }
    }
}

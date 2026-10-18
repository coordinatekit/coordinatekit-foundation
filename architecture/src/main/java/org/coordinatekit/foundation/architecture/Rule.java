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

/**
 * A convention that {@link FoundationArchitecture} checks, and the key every override method of its
 * {@link FoundationArchitecture.Builder} takes.
 *
 * <p>
 * Each constant carries only metadata: the class set it examines and whether it is on by default.
 * The rules it runs live in package-private factories, so a constant can be added together with its
 * implementation and its tests.
 */
public enum Rule {
    /**
     * Records should not be public, so a record stays an implementation detail unless a module decides
     * otherwise. Examines production classes and is on by default. A module whose public records are
     * its wire format disables the rule with a reason.
     */
    PUBLIC_RECORDS(Scope.PRODUCTION, true),

    /**
     * Classes should not read {@code System.out} or {@code System.err}. Examines production classes and
     * is on by default. Every field access counts, so a static initializer, a lambda, and a method
     * reference such as {@code System.out::println} all violate the rule. A call to
     * {@code printStackTrace()} does not, and neither does JDK 25's {@code IO}.
     */
    SYSTEM_STREAMS(Scope.PRODUCTION, true),

    /**
     * Test classes whose names match {@code .*Test} should be package-private. Examines test classes
     * and is on by default. Enums, records, and abstract classes are included, and interfaces are not.
     */
    TEST_CLASS_VISIBILITY(Scope.TESTS, true);

    private final boolean onByDefault;
    private final Scope scope;

    Rule(Scope scope, boolean onByDefault) {
        this.scope = scope;
        this.onByDefault = onByDefault;
    }

    /**
     * Returns whether the rule runs without a call to
     * {@link FoundationArchitecture.Builder#enable(Rule)}.
     *
     * @return {@code true} when the rule is on by default
     */
    boolean onByDefault() {
        return onByDefault;
    }

    /**
     * Returns the class set the rule examines.
     *
     * @return the rule's scope
     */
    Scope scope() {
        return scope;
    }
}

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

import static com.tngtech.archunit.lang.conditions.ArchConditions.notBePublic;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * The {@link ArchRule}s that apply to Java classes in general, one factory per {@link Rule}.
 *
 * <p>
 * Every rule is created with {@code allowEmptyShould(true)}, so a module with no records or no
 * offending classes passes instead of failing for matching nothing.
 */
final class JavaRules {
    private JavaRules() {}

    /**
     * Creates the rule behind {@link Rule#PUBLIC_RECORDS}.
     *
     * @return a rule that fails for every public record
     */
    static ArchRule publicRecords() {
        return classes().that(are(JavaClass.Predicates.RECORDS))
                .should(notBePublic())
                .as("Records should not be public")
                .allowEmptyShould(true);
    }

    /**
     * Creates the rule behind {@link Rule#SYSTEM_STREAMS}.
     *
     * <p>
     * The rule reads field accesses, so it sees {@code System.out} wherever it is read, including a
     * static initializer, a lambda, and a method reference. It differs from ArchUnit's own
     * {@code NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS}, which also flags {@code printStackTrace()}.
     *
     * @return a rule that fails for every read of {@code System.out} or {@code System.err}
     */
    static ArchRule systemStreams() {
        return classes().should(new ArchCondition<JavaClass>("not use System.out or System.err") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaFieldAccess access : item.getFieldAccessesFromSelf()) {
                    String owner = access.getTarget().getOwner().getName();
                    String field = access.getTarget().getName();
                    if ("java.lang.System".equals(owner) && ("out".equals(field) || "err".equals(field))) {
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                    }
                }
            }
        }).as("Classes should not use System.out or System.err").allowEmptyShould(true);
    }
}

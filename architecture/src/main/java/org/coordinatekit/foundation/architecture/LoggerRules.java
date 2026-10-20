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

import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.properties.HasModifiers.Predicates.modifier;
import static com.tngtech.archunit.core.domain.properties.HasType.Predicates.rawType;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beFinal;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beStatic;
import static com.tngtech.archunit.lang.conditions.ArchConditions.haveName;
import static com.tngtech.archunit.lang.conditions.ArchConditions.notBeStatic;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.conditions.ArchConditions;

import java.util.List;

/**
 * The {@link ArchRule}s that apply to SLF4J loggers, one factory per {@link Rule}.
 *
 * <p>
 * The rules match {@code org.slf4j.Logger} by name rather than by type, so SLF4J is not a
 * dependency of this module. Every rule is created with {@code allowEmptyShould(true)}, so a module
 * with no loggers passes.
 */
final class LoggerRules {
    private static final String LOGGER = "org.slf4j.Logger";

    private LoggerRules() {}

    /**
     * Creates the predicate that matches an abstract class, as opposed to an interface or an enum that
     * the class file also marks abstract.
     *
     * @return a predicate that is true for an abstract class that is neither an interface nor an enum
     */
    private static DescribedPredicate<JavaClass> abstractClass() {
        return modifier(JavaModifier.ABSTRACT).<JavaClass>forSubtype()
                .and(DescribedPredicate.not(JavaClass.Predicates.INTERFACES))
                .and(DescribedPredicate.not(JavaClass.Predicates.ENUMS));
    }

    /**
     * Creates the predicate that selects logger fields declared in an abstract class.
     *
     * <p>
     * The class file marks every interface abstract, and marks an enum abstract when it declares an
     * abstract method for its constants to implement. Neither is an abstract class here, so both are
     * left out.
     *
     * @return a predicate that is true for a field of type {@code org.slf4j.Logger} whose owner is an
     *         abstract class that is neither an interface nor an enum
     */
    private static DescribedPredicate<JavaField> abstractLoggerField() {
        return rawType(LOGGER).<JavaField>forSubtype().and(declaredIn(abstractClass()));
    }

    /**
     * Creates the predicate that selects logger fields declared in a class that is not an abstract
     * class. An enum with an abstract method counts as concrete.
     *
     * @return a predicate that is true for a field of type {@code org.slf4j.Logger} whose owner is
     *         neither an abstract class nor an interface
     */
    private static DescribedPredicate<JavaField> concreteLoggerField() {
        return rawType(LOGGER).<JavaField>forSubtype()
                .and(declaredIn(DescribedPredicate.not(abstractClass())))
                .and(declaredIn(DescribedPredicate.not(JavaClass.Predicates.INTERFACES)));
    }

    /**
     * Creates the rules behind {@link Rule#LOGGER_NAMING}, one for abstract classes and one for the
     * rest.
     *
     * <p>
     * A field is selected when its raw type is exactly {@code org.slf4j.Logger}, so a field of a
     * subtype such as an interface that extends it is not. A field declared in an interface is not
     * selected either, because an interface field cannot be private.
     *
     * @return the abstract-class rule, then the concrete-class rule
     */
    static List<ArchRule> loggerNaming() {
        return List.of(
                fields().that(abstractLoggerField())
                        .should(
                                ArchConditions.<JavaField>bePrivate()
                                        .and(notBeStatic())
                                        .and(beFinal())
                                        .and(haveName("log"))
                        )
                        .as("Abstract classes should declare logger as 'private final Logger log'")
                        .allowEmptyShould(true),
                fields().that(concreteLoggerField())
                        .should(
                                ArchConditions.<JavaField>bePrivate()
                                        .and(beStatic())
                                        .and(beFinal())
                                        .and(haveName("LOG"))
                        )
                        .as("Concrete classes should declare logger as 'private static final Logger LOG'")
                        .allowEmptyShould(true)
        );
    }
}

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

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * The {@link ArchRule}s that apply to picocli commands, options, and mixins, one factory per
 * {@link Rule}.
 *
 * <p>
 * The rules match picocli's annotations by name, such as {@code picocli.CommandLine$Command}, so
 * picocli is not a dependency of this module. Every rule is created with
 * {@code allowEmptyShould(true)}, so a module with no picocli types passes.
 */
final class PicocliRules {
    private static final String COMMAND_ANNOTATION = "picocli.CommandLine$Command";
    private static final String DEFAULT_VALUE_PROPERTY = "defaultValue";
    private static final String ENVIRONMENT_VARIABLE_DEFAULT_MARKER = "${env:";
    private static final String MIXIN_ANNOTATION = "picocli.CommandLine$Mixin";
    private static final String MIXIN_STANDARD_HELP_OPTIONS_PROPERTY = "mixinStandardHelpOptions";
    private static final String NAMES_PROPERTY = "names";
    private static final String NO_VERSION_PROVIDER = "picocli.CommandLine$NoVersionProvider";
    private static final String OPTION_ANNOTATION = "picocli.CommandLine$Option";
    private static final String VALIDATE_METHOD_NAME = "validate";
    private static final String VERSION_PROVIDER_PROPERTY = "versionProvider";

    private PicocliRules() {}

    /**
     * Creates the condition that a mixin field is validated by its declaring class.
     *
     * <p>
     * A field passes when some method of the declaring class both reads the field and calls
     * {@code validate()} on the field's type. The two need not be the same expression, which is the
     * known limitation of {@link Rule#PICOCLI_MIXIN_VALIDATION}.
     *
     * @return a condition that reports a violation for every field no method validates
     */
    private static ArchCondition<JavaField> beValidatedByDeclaringClass() {
        return new ArchCondition<>("be validated by their declaring class") {
            @Override
            public void check(JavaField item, ConditionEvents events) {
                boolean validated = item.getOwner()
                        .getMethodCallsFromSelf()
                        .stream()
                        .filter(
                                call -> VALIDATE_METHOD_NAME.equals(call.getTarget().getName())
                                        && item.getRawType().getName().equals(call.getTarget().getOwner().getName())
                        )
                        .anyMatch(call -> readsField(call.getOrigin(), item));

                if (validated) {
                    events.add(
                            SimpleConditionEvent
                                    .satisfied(item, item.getFullName() + " is validated by its declaring class")
                    );
                } else {
                    events.add(
                            SimpleConditionEvent.violated(
                                    item,
                                    item.getFullName() + " is never validated via " + VALIDATE_METHOD_NAME + "()"
                            )
                    );
                }
            }
        };
    }

    /**
     * Creates the condition that an option declares no short name.
     *
     * @return a condition that reports a violation for every option with a name that does not start
     *         with {@code --}
     */
    private static ArchCondition<JavaField> declareOnlyLongOptionNames() {
        return new ArchCondition<>("declare only long option names") {
            @Override
            public void check(JavaField item, ConditionEvents events) {
                JavaAnnotation<JavaField> annotation = item.getAnnotationOfType(OPTION_ANNOTATION);
                Object[] names = (Object[]) annotation.get(NAMES_PROPERTY).orElse(new Object[0]);
                boolean onlyLongNames = Arrays.stream(names)
                        .map(String.class::cast)
                        .allMatch(name -> name.startsWith("--"));

                if (onlyLongNames) {
                    events.add(
                            SimpleConditionEvent
                                    .satisfied(item, item.getFullName() + " declares only long option names")
                    );
                } else {
                    events.add(
                            SimpleConditionEvent.violated(
                                    item,
                                    item.getFullName()
                                            + " declares a short option name despite an environment-variable default"
                            )
                    );
                }
            }
        };
    }

    /**
     * Creates the condition behind {@link Rule#PICOCLI_COMMANDS}.
     *
     * <p>
     * Each missing half is reported separately, so a command that lacks both gets two violations.
     *
     * @param versionProvider the binary name of the one class the command must name, or {@code null} to
     *        accept any class other than picocli's default
     * @param description what the condition requires, shown in the rule's description
     * @return a condition that reports a violation for every command missing either half
     */
    private static ArchCondition<JavaClass> declareVersionProviderAndStandardHelp(
            @Nullable String versionProvider,
            String description
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                JavaAnnotation<JavaClass> annotation = item.getAnnotationOfType(COMMAND_ANNOTATION);
                boolean mixinStandardHelpOptions = Boolean.TRUE
                        .equals(annotation.get(MIXIN_STANDARD_HELP_OPTIONS_PROPERTY).orElse(null));
                Object provider = annotation.get(VERSION_PROVIDER_PROPERTY).orElse(null);
                boolean providerAccepted = provider instanceof JavaClass javaClass
                        && (versionProvider == null ? !NO_VERSION_PROVIDER.equals(javaClass.getName())
                                : versionProvider.equals(javaClass.getName()));

                if (!mixinStandardHelpOptions) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    item,
                                    item.getFullName() + " does not declare mixinStandardHelpOptions = true"
                            )
                    );
                }
                if (!providerAccepted) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    item,
                                    item.getFullName()
                                            + (versionProvider == null ? " does not declare a versionProvider"
                                                    : " does not declare versionProvider = " + versionProvider
                                                            + ".class")
                            )
                    );
                }
                if (mixinStandardHelpOptions && providerAccepted) {
                    events.add(
                            SimpleConditionEvent
                                    .satisfied(item, item.getFullName() + " declares the required command conventions")
                    );
                }
            }
        };
    }

    /**
     * Creates the predicate that selects options whose default reads an environment variable.
     *
     * @return a predicate that is true for a field annotated with {@code @Option} whose
     *         {@code defaultValue} contains <code>${env:</code>
     */
    private static DescribedPredicate<JavaField> environmentBackedOptionField() {
        return annotatedWith(OPTION_ANNOTATION).<JavaField>forSubtype()
                .and(new DescribedPredicate<>("has a defaultValue referencing an environment variable") {
                    @Override
                    public boolean test(JavaField field) {
                        JavaAnnotation<JavaField> annotation = field.getAnnotationOfType(OPTION_ANNOTATION);
                        Object defaultValue = annotation.get(DEFAULT_VALUE_PROPERTY).orElse(null);
                        return defaultValue instanceof String stringValue
                                && stringValue.contains(ENVIRONMENT_VARIABLE_DEFAULT_MARKER);
                    }
                });
    }

    /**
     * Creates the rule behind {@link Rule#PICOCLI_COMMANDS}.
     *
     * <p>
     * An absent {@code versionProvider} value and picocli's default,
     * {@code picocli.CommandLine$NoVersionProvider}, both mean no provider. Whether ArchUnit reports
     * the default as a value depends on whether it imported the annotation type, so the condition
     * treats the two alike.
     *
     * @param versionProvider the binary name of the one class every command must name, or {@code null}
     *        to accept any class other than picocli's default
     * @return a rule that fails for every command that lacks {@code mixinStandardHelpOptions = true} or
     *         an accepted version provider
     */
    static ArchRule picocliCommands(@Nullable String versionProvider) {
        String description = "Commands should declare mixinStandardHelpOptions = true and a versionProvider"
                + (versionProvider == null ? "" : " = " + versionProvider + ".class");
        return classes().that(are(annotatedWith(COMMAND_ANNOTATION)))
                .should(declareVersionProviderAndStandardHelp(versionProvider, description))
                .as(description)
                .allowEmptyShould(true);
    }

    /**
     * Creates the rule behind {@link Rule#PICOCLI_ENVIRONMENT_OPTIONS}.
     *
     * @return a rule that fails for every option with an environment-variable default that declares a
     *         short name
     */
    static ArchRule picocliEnvironmentOptions() {
        return fields().that(environmentBackedOptionField())
                .should(declareOnlyLongOptionNames())
                .as("Options with an environment-variable-backed defaultValue should declare only a long name")
                .allowEmptyShould(true);
    }

    /**
     * Creates the rule behind {@link Rule#PICOCLI_MIXIN_VALIDATION}.
     *
     * <p>
     * The condition looks for a {@code validate()} call on the field's type, made from a method that
     * also reads the field. It cannot tell which field the call was made on. A method that reads field
     * A for some other purpose and calls {@code validate()} on field B therefore satisfies both, so a
     * class with two mixins of the same type can pass while one of them is never validated.
     *
     * @return a rule that fails for every mixin field whose type declares or inherits
     *         {@code validate()} and which no method of the declaring class validates
     */
    static ArchRule picocliMixinValidation() {
        return fields().that(validatableMixinField())
                .should(beValidatedByDeclaringClass())
                .as(
                        "Fields with a @Mixin type that declares validate() must have that method called by "
                                + "the declaring class"
                )
                .allowEmptyShould(true);
    }

    /**
     * Returns whether a method or constructor reads a field.
     *
     * @param codeUnit the method or constructor to look in
     * @param field the field to look for
     * @return {@code true} when the code unit has a read access to the field
     */
    private static boolean readsField(JavaCodeUnit codeUnit, JavaField field) {
        return codeUnit.getFieldAccesses()
                .stream()
                .anyMatch(
                        access -> access.getAccessType() == AccessType.GET
                                && field.getFullName().equals(access.getTarget().getFullName())
                );
    }

    /**
     * Creates the predicate that selects mixin fields whose type can be validated.
     *
     * @return a predicate that is true for a field annotated with {@code @Mixin} whose type declares or
     *         inherits a no-argument {@code validate()}
     */
    private static DescribedPredicate<JavaField> validatableMixinField() {
        return annotatedWith(MIXIN_ANNOTATION).<JavaField>forSubtype()
                .and(new DescribedPredicate<>("declares a " + VALIDATE_METHOD_NAME + "() method") {
                    @Override
                    public boolean test(JavaField field) {
                        return field.getRawType()
                                .getAllMethods()
                                .stream()
                                .anyMatch(
                                        method -> VALIDATE_METHOD_NAME.equals(method.getName())
                                                && method.getRawParameterTypes().isEmpty()
                                );
                    }
                });
    }
}

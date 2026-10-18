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

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.conditions.ArchConditions.beFinal;
import static com.tngtech.archunit.lang.conditions.ArchConditions.notBePublic;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.ConstructorCallTarget;
import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@link ArchRule}s that apply to Java classes in general, one factory per {@link Rule}. A
 * constant made of several rules returns them as a list.
 *
 * <p>
 * Every rule is created with {@code allowEmptyShould(true)}, so a module with no records or no
 * offending classes passes instead of failing for matching nothing.
 *
 * <p>
 * A violation from a custom condition reads like ArchUnit's own: the element's description, what is
 * wrong with it, and {@code in} followed by its source location.
 */
final class JavaRules {
    private static final Pattern CONSTANT_NAME = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "java.util.logging..",
            "sun..",
            "com.sun..",
            "java.awt..",
            "javax.swing..",
            "org.apache.commons.logging..",
            "org.apache.log4j..",
            "org.apache.logging.log4j.."
    );

    private JavaRules() {}

    /**
     * Returns whether any of the types is annotated directly with {@code @Deprecated}.
     *
     * @param types the erased types a call target takes
     * @return {@code true} when at least one type carries the annotation itself
     */
    private static boolean anyDeprecated(List<JavaClass> types) {
        return types.stream().anyMatch(type -> type.isAnnotatedWith(Deprecated.class));
    }

    /**
     * Creates the rule behind {@link Rule#CONSTANT_NAMING}.
     *
     * <p>
     * The rule selects every {@code static final} field, so enum constants and interface constants are
     * checked with the rest.
     *
     * @return a rule that fails for every constant not named in upper snake case
     */
    static ArchRule constantNaming() {
        return fields().that()
                .areFinal()
                .and()
                .areStatic()
                .should(new ArchCondition<JavaField>("have a name matching " + CONSTANT_NAME.pattern()) {
                    @Override
                    public void check(JavaField item, ConditionEvents events) {
                        if (item.getModifiers().contains(JavaModifier.SYNTHETIC)
                                || "serialVersionUID".equals(item.getName())
                                || CONSTANT_NAME.matcher(item.getName()).matches()) {
                            return;
                        }
                        events.add(
                                SimpleConditionEvent.violated(
                                        item,
                                        item.getDescription() + " does not match " + CONSTANT_NAME.pattern() + " in "
                                                + item.getSourceCodeLocation()
                                )
                        );
                    }
                })
                .as("Constants should follow constant naming conventions")
                .allowEmptyShould(true);
    }

    /**
     * Creates the rule behind {@link Rule#DEPRECATED_API}.
     *
     * <p>
     * Only a direct {@code @Deprecated} counts. A type annotated with an annotation that is itself
     * deprecated does not. A call to a member of the calling class is checked like any other.
     *
     * @return a rule that fails for every use of a deprecated field, method, constructor, or class
     */
    static ArchRule deprecatedApi() {
        return classes().should(new ArchCondition<JavaClass>("not use deprecated APIs") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaFieldAccess access : item.getFieldAccessesFromSelf()) {
                    if (access.getTarget().isAnnotatedWith(Deprecated.class)) {
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                    }
                }
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    MethodCallTarget target = call.getTarget();
                    if (target.isAnnotatedWith(Deprecated.class)
                            || target.getRawReturnType().isAnnotatedWith(Deprecated.class)
                            || anyDeprecated(target.getRawParameterTypes())) {
                        events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                    }
                }
                for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
                    ConstructorCallTarget target = call.getTarget();
                    if (target.isAnnotatedWith(Deprecated.class) || anyDeprecated(target.getRawParameterTypes())) {
                        events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                    }
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    if (dependency.getTargetClass().isAnnotatedWith(Deprecated.class)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        }).as("Classes should not use deprecated APIs").allowEmptyShould(true);
    }

    /**
     * Creates the rule behind {@link Rule#FINAL_CLASS_PROTECTED_MEMBERS}.
     *
     * <p>
     * The rule reads the class file, so a record and a plain enum are final, and an enum with constant
     * bodies is not.
     *
     * @return a rule that fails for every protected field or method of a final class
     */
    static ArchRule finalClassProtectedMembers() {
        return classes().that()
                .haveModifier(JavaModifier.FINAL)
                .should(new ArchCondition<JavaClass>("not have protected members") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaField field : item.getFields()) {
                            checkMember(field, events);
                        }
                        for (JavaMethod method : item.getMethods()) {
                            checkMember(method, events);
                        }
                    }

                    private void checkMember(JavaMember member, ConditionEvents events) {
                        if (member.getModifiers().contains(JavaModifier.PROTECTED)
                                && !member.getModifiers().contains(JavaModifier.SYNTHETIC)) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            member,
                                            member.getDescription() + " is protected in "
                                                    + member.getSourceCodeLocation()
                                    )
                            );
                        }
                    }
                })
                .as("Final classes should not have protected members")
                .allowEmptyShould(true);
    }

    /**
     * Creates the rules behind {@link Rule#FORBIDDEN_IMPORTS}, one per forbidden package.
     *
     * <p>
     * The rules read dependencies and not {@code import} lines, so a fully qualified reference counts.
     * A class inside a forbidden package is not checked against that package.
     *
     * @return one rule for each of the eight packages, each failing for every class that depends on it
     */
    static List<ArchRule> forbiddenImports() {
        return FORBIDDEN_PACKAGES.stream()
                .map(
                        forbidden -> noClasses().that(not(resideInAPackage(forbidden)))
                                .should()
                                .dependOnClassesThat(resideInAPackage(forbidden))
                                .as("No classes should import classes in package " + forbidden)
                                .allowEmptyShould(true)
                )
                .toList();
    }

    /**
     * Creates the rule behind {@link Rule#IMPL_SUFFIX}.
     *
     * <p>
     * The pattern is matched against the binary name, so the nested class {@code a.B$FooImpl} matches.
     *
     * @return a rule that fails for every class whose name ends in {@code Impl}
     */
    static ArchRule implSuffix() {
        return noClasses().should()
                .haveNameMatching(".*Impl")
                .as("Classes should not have names matching .*Impl")
                .allowEmptyShould(true);
    }

    /**
     * Creates the rule behind {@link Rule#INTERFACE_PREFIX}.
     *
     * <p>
     * The length is checked before the second character is read, so an interface named {@code I}
     * passes.
     *
     * @return a rule that fails for every interface whose name starts with {@code I} and an uppercase
     *         letter
     */
    static ArchRule interfacePrefix() {
        return classes().that().areInterfaces().should(new ArchCondition<JavaClass>("not be prefixed with I") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String name = item.getSimpleName();
                if (name.length() > 1 && name.charAt(0) == 'I' && Character.isUpperCase(name.charAt(1))) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    item,
                                    item.getDescription() + " is prefixed with I in " + item.getSourceCodeLocation()
                            )
                    );
                }
            }
        }).as("Interfaces should not be prefixed with I").allowEmptyShould(true);
    }

    /**
     * Creates the rule behind {@link Rule#PUBLIC_FIELDS}.
     *
     * <p>
     * Enum constants, interface constants, and record component fields are not public instance fields,
     * so they pass.
     *
     * @return a rule that fails for every public field that is not static
     */
    static ArchRule publicFields() {
        return fields().that()
                .doNotHaveModifier(JavaModifier.SYNTHETIC)
                .should(new ArchCondition<JavaField>("not be public unless static") {
                    @Override
                    public void check(JavaField item, ConditionEvents events) {
                        if (item.getModifiers().contains(JavaModifier.PUBLIC)
                                && !item.getModifiers().contains(JavaModifier.STATIC)) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            item,
                                            item.getDescription() + " is public in " + item.getSourceCodeLocation()
                                    )
                            );
                        }
                    }
                })
                .as("Fields should not be public unless they are static")
                .allowEmptyShould(true);
    }

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

    /**
     * Creates the rule behind {@link Rule#UTILITY_CLASSES}.
     *
     * <p>
     * A class is selected when it declares methods and all of them are static, none is named
     * {@code main}, and it extends nothing but {@code Object}. ArchUnit does not report the method a
     * lambda compiles to, so a lambda that captures {@code this} does not remove the class from
     * selection. An interface or abstract class with only static methods is selected and fails.
     *
     * @return a rule that fails for every utility class that is not final or has no private constructor
     */
    static ArchRule utilityClasses() {
        DescribedPredicate<JavaClass> onlyStaticMethods = DescribedPredicate.describe(
                "have only static methods, at least one, none named main",
                javaClass -> !javaClass.getMethods().isEmpty() && javaClass.getMethods()
                        .stream()
                        .allMatch(
                                method -> method.getModifiers().contains(JavaModifier.STATIC)
                                        && !"main".equals(method.getName())
                        )
        );
        DescribedPredicate<JavaClass> extendsAnotherClass = DescribedPredicate.describe(
                "extend a class other than Object",
                javaClass -> javaClass.getRawSuperclass()
                        .map(superclass -> !"java.lang.Object".equals(superclass.getName()))
                        .orElse(false)
        );
        return classes().that(onlyStaticMethods)
                .and(not(extendsAnotherClass))
                .should(beFinal())
                .andShould(new ArchCondition<JavaClass>("have a private constructor") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean hasPrivateConstructor = item.getConstructors()
                                .stream()
                                .anyMatch(constructor -> constructor.getModifiers().contains(JavaModifier.PRIVATE));
                        if (!hasPrivateConstructor) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            item,
                                            item.getDescription() + " does not have a private constructor in "
                                                    + item.getSourceCodeLocation()
                                    )
                            );
                        }
                    }
                })
                .as("Utility classes should be final and have a private constructor")
                .allowEmptyShould(true);
    }
}

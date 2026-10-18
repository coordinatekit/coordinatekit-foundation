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
import org.coordinatekit.foundation.architecture.fixtures.CapturingLambdaUtility;
import org.coordinatekit.foundation.architecture.fixtures.ComSunUser;
import org.coordinatekit.foundation.architecture.fixtures.ConstantNames;
import org.coordinatekit.foundation.architecture.fixtures.CurrentApiCaller;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedConstructorCaller;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedConstructorParameterCaller;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedFieldReader;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedMethodCaller;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedParameterCaller;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedReturnTypeCaller;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedSelfCaller;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedType;
import org.coordinatekit.foundation.architecture.fixtures.DeprecatedTypeUser;
import org.coordinatekit.foundation.architecture.fixtures.DesktopUser;
import org.coordinatekit.foundation.architecture.fixtures.FinalProtectedConstructor;
import org.coordinatekit.foundation.architecture.fixtures.FinalProtectedField;
import org.coordinatekit.foundation.architecture.fixtures.FinalProtectedMethod;
import org.coordinatekit.foundation.architecture.fixtures.FinalProtectedStaticMethod;
import org.coordinatekit.foundation.architecture.fixtures.FinalUtility;
import org.coordinatekit.foundation.architecture.fixtures.I;
import org.coordinatekit.foundation.architecture.fixtures.IMarker;
import org.coordinatekit.foundation.architecture.fixtures.IOChannel;
import org.coordinatekit.foundation.architecture.fixtures.IService;
import org.coordinatekit.foundation.architecture.fixtures.IWidget;
import org.coordinatekit.foundation.architecture.fixtures.ImplHolder;
import org.coordinatekit.foundation.architecture.fixtures.Item;
import org.coordinatekit.foundation.architecture.fixtures.JavaUtilLoggingUser;
import org.coordinatekit.foundation.architecture.fixtures.LambdaPrinter;
import org.coordinatekit.foundation.architecture.fixtures.LeadingUnderscoreConstant;
import org.coordinatekit.foundation.architecture.fixtures.LowerCaseConstant;
import org.coordinatekit.foundation.architecture.fixtures.LowerCaseEnumConstant;
import org.coordinatekit.foundation.architecture.fixtures.LowerCaseInterfaceConstant;
import org.coordinatekit.foundation.architecture.fixtures.MainHolder;
import org.coordinatekit.foundation.architecture.fixtures.MetaDeprecatedTypeUser;
import org.coordinatekit.foundation.architecture.fixtures.NonPublicFixtures;
import org.coordinatekit.foundation.architecture.fixtures.OpenProtectedMembers;
import org.coordinatekit.foundation.architecture.fixtures.OpenUtility;
import org.coordinatekit.foundation.architecture.fixtures.PrintingRecord;
import org.coordinatekit.foundation.architecture.fixtures.ProtectedMethodConstantBodyEnum;
import org.coordinatekit.foundation.architecture.fixtures.ProtectedMethodEnum;
import org.coordinatekit.foundation.architecture.fixtures.ProtectedMethodRecord;
import org.coordinatekit.foundation.architecture.fixtures.PublicConstructorUtility;
import org.coordinatekit.foundation.architecture.fixtures.PublicEnumTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicInstanceField;
import org.coordinatekit.foundation.architecture.fixtures.PublicRecord;
import org.coordinatekit.foundation.architecture.fixtures.PublicStaticField;
import org.coordinatekit.foundation.architecture.fixtures.ServiceImpl;
import org.coordinatekit.foundation.architecture.fixtures.ServiceImpls;
import org.coordinatekit.foundation.architecture.fixtures.StackTracePrinter;
import org.coordinatekit.foundation.architecture.fixtures.StaticInitializerPrinter;
import org.coordinatekit.foundation.architecture.fixtures.StaticOnlyAbstractClass;
import org.coordinatekit.foundation.architecture.fixtures.StaticOnlyInterface;
import org.coordinatekit.foundation.architecture.fixtures.StaticOnlySubclass;
import org.coordinatekit.foundation.architecture.fixtures.SystemErrMethodReference;
import org.coordinatekit.foundation.architecture.fixtures.SystemOutPrinter;
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
    record RuleParameters(String name, List<Class<?>> fixtures, List<Class<?>> violators) {}

    static Stream<RuleParameters> constantNaming() {
        return Stream.of(
                new RuleParameters(
                        "upper snake case, digits, and serialVersionUID pass",
                        List.of(ConstantNames.class),
                        List.of()
                ),
                new RuleParameters(
                        "lower case constant violates",
                        List.of(LowerCaseConstant.class),
                        List.of(LowerCaseConstant.class)
                ),
                new RuleParameters(
                        "leading underscore violates",
                        List.of(LeadingUnderscoreConstant.class),
                        List.of(LeadingUnderscoreConstant.class)
                ),
                new RuleParameters(
                        "lower case enum constant violates",
                        List.of(LowerCaseEnumConstant.class),
                        List.of(LowerCaseEnumConstant.class)
                ),
                new RuleParameters(
                        "lower case interface constant violates",
                        List.of(LowerCaseInterfaceConstant.class),
                        List.of(LowerCaseInterfaceConstant.class)
                ),
                new RuleParameters(
                        "the synthetic VALUES field of an enum is exempt",
                        List.of(PublicEnumTest.class),
                        List.of()
                ),
                new RuleParameters(
                        "only the badly named constants are named",
                        List.of(ConstantNames.class, LowerCaseConstant.class, LeadingUnderscoreConstant.class),
                        List.of(LowerCaseConstant.class, LeadingUnderscoreConstant.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void constantNaming(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.constantNaming()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void constantNaming__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JavaRules.constantNaming())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void constantNaming__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.constantNaming();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Constants should follow constant naming conventions", description);
    }

    @SuppressWarnings("deprecation")
    static Stream<RuleParameters> deprecatedApi() {
        return Stream.of(
                new RuleParameters(
                        "reading a deprecated field violates",
                        List.of(DeprecatedFieldReader.class),
                        List.of(DeprecatedFieldReader.class)
                ),
                new RuleParameters(
                        "calling a deprecated method violates",
                        List.of(DeprecatedMethodCaller.class),
                        List.of(DeprecatedMethodCaller.class)
                ),
                new RuleParameters(
                        "calling a method that returns a deprecated type violates",
                        List.of(DeprecatedReturnTypeCaller.class, DeprecatedType.class),
                        List.of(DeprecatedReturnTypeCaller.class)
                ),
                new RuleParameters(
                        "calling a method that takes a deprecated type violates",
                        List.of(DeprecatedParameterCaller.class, DeprecatedType.class),
                        List.of(DeprecatedParameterCaller.class)
                ),
                new RuleParameters(
                        "calling a deprecated constructor violates",
                        List.of(DeprecatedConstructorCaller.class),
                        List.of(DeprecatedConstructorCaller.class)
                ),
                new RuleParameters(
                        "calling a constructor that takes a deprecated type violates",
                        List.of(DeprecatedConstructorParameterCaller.class, DeprecatedType.class),
                        List.of(DeprecatedConstructorParameterCaller.class)
                ),
                new RuleParameters(
                        "depending on a deprecated class violates",
                        List.of(DeprecatedTypeUser.class),
                        List.of(DeprecatedTypeUser.class)
                ),
                new RuleParameters(
                        "calling its own deprecated member violates",
                        List.of(DeprecatedSelfCaller.class),
                        List.of(DeprecatedSelfCaller.class)
                ),
                new RuleParameters("calling a current member passes", List.of(CurrentApiCaller.class), List.of()),
                new RuleParameters("a meta-deprecated type passes", List.of(MetaDeprecatedTypeUser.class), List.of()),
                new RuleParameters(
                        "a class with no calls passes",
                        List.of(NonPublicFixtures.PACKAGE_PRIVATE_RECORD),
                        List.of()
                ),
                new RuleParameters(
                        "only the deprecated callers are named",
                        List.of(CurrentApiCaller.class, MetaDeprecatedTypeUser.class, DeprecatedMethodCaller.class),
                        List.of(DeprecatedMethodCaller.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void deprecatedApi(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.deprecatedApi()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void deprecatedApi__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JavaRules.deprecatedApi())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void deprecatedApi__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.deprecatedApi();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Classes should not use deprecated APIs", description);
    }

    static Stream<RuleParameters> finalClassProtectedMembers() {
        return Stream.of(
                new RuleParameters(
                        "protected field in a final class violates",
                        List.of(FinalProtectedField.class),
                        List.of(FinalProtectedField.class)
                ),
                new RuleParameters(
                        "protected method in a final class violates",
                        List.of(FinalProtectedMethod.class),
                        List.of(FinalProtectedMethod.class)
                ),
                new RuleParameters(
                        "protected static method violates",
                        List.of(FinalProtectedStaticMethod.class),
                        List.of(FinalProtectedStaticMethod.class)
                ),
                new RuleParameters(
                        "protected method in a record violates",
                        List.of(ProtectedMethodRecord.class),
                        List.of(ProtectedMethodRecord.class)
                ),
                new RuleParameters(
                        "protected method in a plain enum violates",
                        List.of(ProtectedMethodEnum.class),
                        List.of(ProtectedMethodEnum.class)
                ),
                new RuleParameters(
                        "protected constructor is not checked",
                        List.of(FinalProtectedConstructor.class),
                        List.of()
                ),
                new RuleParameters(
                        "an enum with constant bodies is not final",
                        List.of(ProtectedMethodConstantBodyEnum.class),
                        List.of()
                ),
                new RuleParameters("a class that is not final passes", List.of(OpenProtectedMembers.class), List.of()),
                new RuleParameters(
                        "only the final classes are named",
                        List.of(FinalProtectedField.class, FinalProtectedConstructor.class, OpenProtectedMembers.class),
                        List.of(FinalProtectedField.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void finalClassProtectedMembers(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.finalClassProtectedMembers()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void finalClassProtectedMembers__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JavaRules.finalClassProtectedMembers())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void finalClassProtectedMembers__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.finalClassProtectedMembers();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Final classes should not have protected members", description);
    }

    static Stream<RuleParameters> forbiddenImports() {
        return Stream.of(
                new RuleParameters(
                        "java.util.logging by full name violates",
                        List.of(JavaUtilLoggingUser.class),
                        List.of(JavaUtilLoggingUser.class)
                ),
                new RuleParameters(
                        "java.awt and javax.swing violate",
                        List.of(DesktopUser.class),
                        List.of(DesktopUser.class)
                ),
                new RuleParameters("com.sun violates", List.of(ComSunUser.class), List.of(ComSunUser.class)),
                new RuleParameters(
                        "a class with no forbidden dependency passes",
                        List.of(NonPublicFixtures.PACKAGE_PRIVATE_RECORD),
                        List.of()
                ),
                new RuleParameters(
                        "only the forbidden users are named",
                        List.of(JavaUtilLoggingUser.class, NonPublicFixtures.PACKAGE_PRIVATE_RECORD, ComSunUser.class),
                        List.of(JavaUtilLoggingUser.class, ComSunUser.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void forbiddenImports(RuleParameters parameters) {
        Fixtures.assertViolators(
                JavaRules.forbiddenImports(),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void forbiddenImports__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = JavaRules.forbiddenImports()
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void forbiddenImports__hasDescriptions() {
        // ARRANGE //
        List<ArchRule> rules = JavaRules.forbiddenImports();

        // ACT //
        List<String> descriptions = rules.stream().map(ArchRule::getDescription).toList();

        // ASSERT //
        assertEquals(
                List.of(
                        "No classes should import classes in package java.util.logging..",
                        "No classes should import classes in package sun..",
                        "No classes should import classes in package com.sun..",
                        "No classes should import classes in package java.awt..",
                        "No classes should import classes in package javax.swing..",
                        "No classes should import classes in package org.apache.commons.logging..",
                        "No classes should import classes in package org.apache.log4j..",
                        "No classes should import classes in package org.apache.logging.log4j.."
                ),
                descriptions
        );
    }

    static Stream<RuleParameters> implSuffix() {
        return Stream.of(
                new RuleParameters(
                        "class with the suffix violates",
                        List.of(ServiceImpl.class),
                        List.of(ServiceImpl.class)
                ),
                new RuleParameters(
                        "nested class matches on its binary name",
                        List.of(NonPublicFixtures.IMPL_HOLDER_NESTED_IMPL),
                        List.of(NonPublicFixtures.IMPL_HOLDER_NESTED_IMPL)
                ),
                new RuleParameters("the enclosing class passes", List.of(ImplHolder.class), List.of()),
                new RuleParameters("a name that only contains Impl passes", List.of(ServiceImpls.class), List.of()),
                new RuleParameters(
                        "only the suffixed classes are named",
                        List.of(
                                ServiceImpl.class,
                                ImplHolder.class,
                                ServiceImpls.class,
                                NonPublicFixtures.IMPL_HOLDER_NESTED_IMPL
                        ),
                        List.of(ServiceImpl.class, NonPublicFixtures.IMPL_HOLDER_NESTED_IMPL)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void implSuffix(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.implSuffix()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void implSuffix__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JavaRules.implSuffix())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void implSuffix__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.implSuffix();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Classes should not have names matching .*Impl", description);
    }

    static Stream<RuleParameters> interfacePrefix() {
        return Stream.of(
                new RuleParameters("prefixed interface violates", List.of(IService.class), List.of(IService.class)),
                new RuleParameters(
                        "an acronym counts as the prefix",
                        List.of(IOChannel.class),
                        List.of(IOChannel.class)
                ),
                new RuleParameters("lowercase second letter passes", List.of(Item.class), List.of()),
                new RuleParameters("an interface named I passes", List.of(I.class), List.of()),
                new RuleParameters("a class prefixed with I is not an interface", List.of(IWidget.class), List.of()),
                new RuleParameters(
                        "an annotation type prefixed with I violates",
                        List.of(IMarker.class),
                        List.of(IMarker.class)
                ),
                new RuleParameters(
                        "only the prefixed interfaces are named",
                        List.of(IService.class, Item.class, I.class, IWidget.class),
                        List.of(IService.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void interfacePrefix(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.interfacePrefix()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void interfacePrefix__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JavaRules.interfacePrefix())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void interfacePrefix__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.interfacePrefix();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Interfaces should not be prefixed with I", description);
    }

    static Stream<RuleParameters> publicFields() {
        return Stream.of(
                new RuleParameters(
                        "public instance field violates",
                        List.of(PublicInstanceField.class),
                        List.of(PublicInstanceField.class)
                ),
                new RuleParameters("public static field passes", List.of(PublicStaticField.class), List.of()),
                new RuleParameters("enum constants pass", List.of(PublicEnumTest.class), List.of()),
                new RuleParameters("interface constants pass", List.of(LowerCaseInterfaceConstant.class), List.of()),
                new RuleParameters("record component fields pass", List.of(PublicRecord.class), List.of()),
                new RuleParameters(
                        "only the public instance field is named",
                        List.of(PublicInstanceField.class, PublicStaticField.class, PublicRecord.class),
                        List.of(PublicInstanceField.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void publicFields(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.publicFields()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void publicFields__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JavaRules.publicFields())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void publicFields__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.publicFields();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Fields should not be public unless they are static", description);
    }

    static Stream<RuleParameters> publicRecords() {
        return Stream.of(
                new RuleParameters("public record violates", List.of(PublicRecord.class), List.of(PublicRecord.class)),
                new RuleParameters(
                        "package-private record passes",
                        List.of(NonPublicFixtures.PACKAGE_PRIVATE_RECORD),
                        List.of()
                ),
                new RuleParameters("public class is not a record", List.of(SystemOutPrinter.class), List.of()),
                new RuleParameters(
                        "only the public record is named",
                        List.of(PublicRecord.class, NonPublicFixtures.PACKAGE_PRIVATE_RECORD, SystemOutPrinter.class),
                        List.of(PublicRecord.class)
                ),
                new RuleParameters(
                        "a record that prints still violates",
                        List.of(PrintingRecord.class),
                        List.of(PrintingRecord.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void publicRecords(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.publicRecords()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
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
                new RuleParameters(
                        "System.out in a method",
                        List.of(SystemOutPrinter.class),
                        List.of(SystemOutPrinter.class)
                ),
                new RuleParameters(
                        "System.err through a method reference",
                        List.of(SystemErrMethodReference.class),
                        List.of(SystemErrMethodReference.class)
                ),
                new RuleParameters(
                        "System.out in a static initializer",
                        List.of(StaticInitializerPrinter.class),
                        List.of(StaticInitializerPrinter.class)
                ),
                new RuleParameters(
                        "System.out in a lambda",
                        List.of(LambdaPrinter.class),
                        List.of(LambdaPrinter.class)
                ),
                new RuleParameters("printStackTrace passes", List.of(StackTracePrinter.class), List.of()),
                new RuleParameters(
                        "a class with no output passes",
                        List.of(NonPublicFixtures.PACKAGE_PRIVATE_RECORD),
                        List.of()
                ),
                new RuleParameters(
                        "only the printing classes are named",
                        List.of(
                                SystemOutPrinter.class,
                                StackTracePrinter.class,
                                LambdaPrinter.class,
                                PublicRecord.class
                        ),
                        List.of(SystemOutPrinter.class, LambdaPrinter.class)
                ),
                new RuleParameters(
                        "a public record that prints violates",
                        List.of(PrintingRecord.class),
                        List.of(PrintingRecord.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void systemStreams(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.systemStreams()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void systemStreams__detailNamesMethodStreamAndLocation() {
        // ARRANGE //
        JavaClasses classes = Fixtures.of(SystemOutPrinter.class);

        // ACT //
        EvaluationResult result = JavaRules.systemStreams().evaluate(classes);

        // ASSERT //
        String details = String.join("\n", result.getFailureReport().getDetails());
        assertTrue(details.contains("Method "), details);
        assertTrue(details.contains(SystemOutPrinter.class.getName() + ".print()"), details);
        assertTrue(details.contains("java.lang.System.out"), details);
        assertTrue(details.contains("SystemOutPrinter.java:"), details);
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

    static Stream<RuleParameters> utilityClasses() {
        return Stream.of(
                new RuleParameters(
                        "final class with a private constructor passes",
                        List.of(FinalUtility.class),
                        List.of()
                ),
                new RuleParameters(
                        "class that is not final violates",
                        List.of(OpenUtility.class),
                        List.of(OpenUtility.class)
                ),
                new RuleParameters(
                        "public constructor violates",
                        List.of(PublicConstructorUtility.class),
                        List.of(PublicConstructorUtility.class)
                ),
                new RuleParameters(
                        "interface with only static methods violates",
                        List.of(StaticOnlyInterface.class),
                        List.of(StaticOnlyInterface.class)
                ),
                new RuleParameters(
                        "abstract class with only static methods violates",
                        List.of(StaticOnlyAbstractClass.class),
                        List.of(StaticOnlyAbstractClass.class)
                ),
                new RuleParameters(
                        "a class whose only method is main is not selected",
                        List.of(MainHolder.class),
                        List.of()
                ),
                new RuleParameters(
                        "a lambda capturing this does not remove the class from selection",
                        List.of(CapturingLambdaUtility.class),
                        List.of(CapturingLambdaUtility.class)
                ),
                new RuleParameters(
                        "a class with a superclass is not selected",
                        List.of(StaticOnlySubclass.class),
                        List.of()
                ),
                new RuleParameters(
                        "a class with no methods is not selected",
                        List.of(StaticInitializerPrinter.class),
                        List.of()
                ),
                new RuleParameters("an enum is not selected", List.of(PublicEnumTest.class), List.of()),
                new RuleParameters(
                        "only the utility classes that fail are named",
                        List.of(FinalUtility.class, OpenUtility.class, MainHolder.class),
                        List.of(OpenUtility.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void utilityClasses(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(JavaRules.utilityClasses()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void utilityClasses__emptyClassesPass() {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = List.of(JavaRules.utilityClasses())
                .stream()
                .map(rule -> rule.evaluate(none))
                .toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation));
    }

    @Test
    void utilityClasses__hasDescription() {
        // ARRANGE //
        ArchRule rule = JavaRules.utilityClasses();

        // ACT //
        String description = rule.getDescription();

        // ASSERT //
        assertEquals("Utility classes should be final and have a private constructor", description);
    }
}

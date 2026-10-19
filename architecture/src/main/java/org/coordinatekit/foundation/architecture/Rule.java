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
     * Classes whose simple names end in {@code Configuration} should not be records. Examines
     * production classes and is on by default. The simple name is matched, so a nested record such as
     * {@code Outer.ServerConfiguration} violates the rule, and a record with any other name passes.
     */
    CONFIGURATION_RECORDS(Scope.PRODUCTION, true),

    /**
     * Fields that are both {@code static} and {@code final} should be named in upper snake case,
     * matching {@code ^[A-Z][A-Z0-9_]*$}. Examines production classes and is on by default. Enum
     * constants and interface constants are included. Synthetic fields and fields named
     * {@code serialVersionUID} are exempt.
     */
    CONSTANT_NAMING(Scope.PRODUCTION, true),

    /**
     * Classes should not use an API annotated directly with {@code @Deprecated}. Examines production
     * classes and is on by default. A class violates the rule when it reads a deprecated field, calls a
     * deprecated method or constructor, calls one whose raw return type or any erased parameter type is
     * deprecated, or depends directly on a deprecated class. A meta-annotation does not count, so a
     * type annotated with an annotation that is itself {@code @Deprecated} passes. A call to a
     * deprecated member of the calling class itself violates the rule too. ArchUnit reads a return or
     * parameter type's annotations only when the type is among the imported classes, so a deprecated
     * type from a library is caught by a direct dependency and not through the signature of a method
     * that returns or takes it.
     */
    DEPRECATED_API(Scope.PRODUCTION, true),

    /**
     * Test classes and test methods should not be disabled. Examines test classes and is on by default.
     * Two rules sit under this constant, one for classes and one for non-synthetic methods, and each
     * fails for anything meta-annotated with {@code org.junit.jupiter.api.Disabled}, so an annotation
     * composed from {@code @Disabled} counts. Conditional annotations such as
     * {@code @DisabledIfSystemProperty} are not {@code @Disabled} and pass.
     */
    DISABLED_TESTS(Scope.TESTS, true),

    /**
     * Final classes should not declare protected fields or methods. Examines production classes and is
     * on by default. A class counts as final when its class file says so, which includes records and
     * plain enums but not an enum whose constants have bodies. Static members are checked and
     * constructors are not. A method that overrides or hides a protected method of a superclass is
     * exempt, because Java allows it no narrower access, and so is a generic override that javac links
     * to such a method through a bridge. An override of a package-private method violates, since it can
     * stay package-private. Synthetic members are exempt.
     */
    FINAL_CLASS_PROTECTED_MEMBERS(Scope.PRODUCTION, true),

    /**
     * Classes should not depend on {@code java.util.logging..}, {@code sun..}, {@code com.sun..},
     * {@code java.awt..}, {@code javax.swing..}, {@code org.apache.commons.logging..},
     * {@code org.apache.log4j..}, or {@code org.apache.logging.log4j..}. Examines production classes
     * and is on by default. The rule reads dependencies and not {@code import} lines, so a fully
     * qualified reference counts. A class inside one of the packages is not checked against it. One
     * exclusion covers all eight packages. A class that has to depend on {@code com.sun.source}, such
     * as an Error Prone check, is the expected exclusion.
     */
    FORBIDDEN_IMPORTS(Scope.PRODUCTION, true),

    /**
     * Methods should not declare {@code Exception} or {@code RuntimeException} in a {@code throws}
     * clause. Examines production classes and is on by default. The rule reads declarations and not
     * {@code throw} statements, so a method that throws a {@code RuntimeException} without declaring it
     * passes. Only the two exact types count, so a method that declares {@code IOException} passes.
     * Constructors are not checked. A class that has to declare a generic exception, such as an adapter
     * for an interface it does not control, is the expected exclusion.
     */
    GENERIC_EXCEPTIONS(Scope.PRODUCTION, true),

    /**
     * Classes should not be named with the suffix {@code Impl}. Examines production classes and is on
     * by default. The pattern {@code .*Impl} is matched against the binary name, so the nested class
     * {@code a.B$FooImpl} matches and {@code a.B$FooImpl$Inner} does not.
     */
    IMPL_SUFFIX(Scope.PRODUCTION, true),

    /**
     * Interfaces should not be named with a prefix {@code I}. Examines production classes and is on by
     * default. A name violates the rule when it starts with {@code I} followed by an uppercase letter,
     * so an acronym such as {@code IOChannel} counts as the prefix. ArchUnit counts an annotation type
     * as an interface, so {@code @IMarker} violates the rule too. An interface named exactly {@code I}
     * passes.
     */
    INTERFACE_PREFIX(Scope.PRODUCTION, true),

    /**
     * Loggers should follow one naming scheme. Examines production classes and is on by default. Two
     * rules sit under this constant. In an abstract class a field of type {@code org.slf4j.Logger} must
     * be {@code private final}, not static, and named {@code log}. In any other class, including an
     * enum that declares an abstract method for its constants, it must be {@code private static final}
     * and named {@code LOG}. A field declared in an interface is not selected, because an interface
     * field cannot be private. The type is matched by name and exactly, so a field of a subtype such as
     * Gradle's {@code org.gradle.api.logging.Logger}, or of an interface that extends the SLF4J one, is
     * not selected.
     */
    LOGGER_NAMING(Scope.PRODUCTION, true),

    /**
     * Packages under the module's namespace should be free of dependency cycles. Examines production
     * classes and is off by default, because most modules were not written with the constraint in mind.
     * {@link FoundationArchitecture.Builder#enable(Rule)} turns it on. Each class goes to a slice named
     * after its first subpackage segment below the namespace, however deeply it is nested. The classes
     * that sit directly in the namespace form a slice named {@code (root)}, which ArchUnit's own slice
     * syntax would drop. A cycle between {@code (root)} and a subpackage counts. An exclusion removes a
     * class from every slice, so the dependencies it carries are no longer considered.
     */
    PACKAGE_CYCLES(Scope.PRODUCTION, false),

    /**
     * Picocli commands should declare {@code mixinStandardHelpOptions = true} and a version provider.
     * Examines production classes and is on by default. A class is a command when it is annotated with
     * {@code picocli.CommandLine.Command}, and the annotation is matched by name. The two halves are
     * checked in order and only the first failing one is reported. Without configuration the provider
     * can be any class other than picocli's default, {@code picocli.CommandLine.NoVersionProvider}, so
     * a command that leaves the property out fails. After
     * {@link FoundationArchitecture.Builder#commandVersionProvider(String)} the provider must be
     * exactly the class it names.
     */
    PICOCLI_COMMANDS(Scope.PRODUCTION, true),

    /**
     * Picocli options that read an environment variable should declare only long names. Examines
     * production classes and is on by default. A field annotated with
     * {@code picocli.CommandLine.Option} is selected when its {@code defaultValue} contains
     * <code>${env:</code>, and it violates the rule when any of its names does not start with
     * {@code --}. The short form invites retyping a flag when the environment variable is the intended
     * way to set it.
     */
    PICOCLI_ENVIRONMENT_OPTIONS(Scope.PRODUCTION, true),

    /**
     * Picocli mixins that can be validated should be validated by the class that declares them.
     * Examines production classes and is on by default. A field annotated with
     * {@code picocli.CommandLine.Mixin} whose type declares a no-argument {@code validate()} method
     * violates the rule unless a method of the declaring class both reads the field and calls
     * {@code validate()}. Validating a throwaway instance of the type does not count, and neither does
     * validating only one of several fields of the same type. A known limitation remains: a method that
     * reads field A for some other purpose and calls {@code validate()} on field B satisfies the rule
     * for both fields.
     */
    PICOCLI_MIXIN_VALIDATION(Scope.PRODUCTION, true),

    /**
     * Fields should not be public unless they are static. Examines production classes and is on by
     * default. Synthetic fields are not checked. Enum constants, interface constants, and the fields
     * behind record components pass.
     */
    PUBLIC_FIELDS(Scope.PRODUCTION, true),

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
    TEST_CLASS_VISIBILITY(Scope.TESTS, true),

    /**
     * Test methods should be package-private. Examines test classes and is on by default. A method is
     * selected when it is meta-annotated with {@code org.junit.jupiter.api.Test} or
     * {@code org.junit.jupiter.params.ParameterizedTest} and is not synthetic. Methods annotated with
     * {@code @RepeatedTest}, {@code @TestFactory}, or {@code @TestTemplate}, and lifecycle methods such
     * as {@code @BeforeEach}, are not selected.
     */
    TEST_METHOD_VISIBILITY(Scope.TESTS, true),

    /**
     * Utility classes should be final and declare only private constructors. Examines production
     * classes and is on by default. A class is a utility class when it declares at least one method,
     * every declared method is static, none is named {@code main}, and its superclass is absent or
     * {@code Object}. Fields are not considered. ArchUnit does not report the method a lambda compiles
     * to, so a lambda that captures {@code this} does not remove its class from the rule. An interface
     * or abstract class with only static methods is selected and fails.
     */
    UTILITY_CLASSES(Scope.PRODUCTION, true);

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

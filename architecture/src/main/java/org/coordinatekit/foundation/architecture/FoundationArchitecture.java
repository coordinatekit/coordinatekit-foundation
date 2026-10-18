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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Runs CoordinateKit's architecture rules over one module's classes and fails with a single
 * combined report.
 *
 * <p>
 * A module calls it from one {@code ArchitectureTest}:
 *
 * <pre>
 * class ArchitectureTest {
 *     &#64;Test
 *     void architecture__followsConventions() {
 *         FoundationArchitecture.builder(ArchitectureTest.class).build().check();
 *     }
 * }
 * </pre>
 *
 * <p>
 * The anchor passed to {@link #builder(Class)} fixes the namespace: its package is the module's
 * root package, and every class under it is examined. An explicit anchor keeps the scope the same
 * when a shared test helper is the one that calls {@link #check()}.
 *
 * <p>
 * {@link #check()} imports two sets of classes from the namespace, one of production classes and
 * one of test classes, and leaves out jars, so a module that arrives as a dependency jar is never
 * examined. Each {@link Rule} examines one of the two sets. A rule added through
 * {@link Builder#addRule(ArchRule)} and a check added through
 * {@link Builder#addCheck(String, Runnable)} run against the production set.
 *
 * <p>
 * Exclusion removes a class from what a rule examines, and nothing more.
 * {@link Builder#exclude(Rule, String...)} applies to one rule and
 * {@link Builder#excludeNamespace(String...)} to all of them. An excluded class is still imported,
 * so it can still be the target of another class's dependency. A pattern is a class name or a
 * package in one of three forms, matched against the binary name of a class:
 *
 * <ul>
 * <li>{@code a.b.C} matches that class and not its nested classes.</li>
 * <li>{@code a.b.*} matches the classes directly in {@code a.b}, nested classes included.</li>
 * <li>{@code a.b..} matches {@code a.b} and every subpackage.</li>
 * </ul>
 *
 * A string in none of these forms is rejected when it is passed, with an
 * {@link IllegalArgumentException}.
 *
 * <p>
 * Before any rule runs, the decider checks that each class set comes from a single classpath root,
 * the directory or jar its class files were read from, and fails at once if one set spans several.
 * The import is scoped to the classpath and not to the module, so it finds every class under the
 * namespace wherever it sits. Gradle puts a project dependency on the test runtime classpath as a
 * jar, which is left out, so a Gradle run sees only the module's own classes. An IntelliJ run puts
 * class directories there instead. A module whose package nests inside another's, such as
 * {@code geocoding} and {@code geocoding.benchmark}, then lands in the outer module's check, and a
 * rule's results over a set that holds another module's classes would only add noise. A package
 * prefix cannot tell those classes apart from the module's own, so the failure names every root
 * with the packages imported from it and asks for {@code excludeNamespace} on each package that
 * belongs to another module. The guard runs after namespace exclusions, so that call is also what
 * silences it. A module compiled from two languages has two production roots and trips the guard.
 *
 * <p>
 * Every rule this module defines is created with {@code allowEmptyShould(true)}, as is every rule
 * added through the builder, so a module with no records or no loggers passes instead of failing
 * for matching nothing. ArchUnit's {@code archRule.failOnEmptyShould} setting is global to the JVM,
 * and this module never changes it through {@code ArchConfiguration}, so it cannot alter the
 * behavior of any other ArchUnit test that runs beside it.
 *
 * <p>
 * The decider ignores {@code org.coordinatekit.foundation.architecture..}, this module's own
 * package, unless the anchor is inside it, so the module's self-check does not exclude everything
 * it would check.
 *
 * <p>
 * A failing run throws one {@link AssertionError} that lists every failing rule and check with its
 * violations, in the order of the {@link Rule} constants, then added rules, then added checks, each
 * in the order it was added. An added check that throws is listed like any other failure, and what
 * it threw is attached to the error as a suppressed exception, so its stack trace is kept.
 */
public final class FoundationArchitecture {
    private static final String OWN_PACKAGE = FoundationArchitecture.class.getPackageName();

    private final List<NamedCheck> addedChecks;
    private final List<ArchRule> addedRules;
    private final Map<Rule, String> disabled;
    private final Set<Rule> enabled;
    private final Map<Rule, List<ClassNamePattern>> exclusions;
    private final String namespace;
    private final List<ClassNamePattern> namespaceExclusions;

    private FoundationArchitecture(Builder builder) {
        addedChecks = List.copyOf(builder.addedChecks);
        addedRules = List.copyOf(builder.addedRules);
        disabled = new EnumMap<>(builder.disabled);
        enabled = EnumSet.copyOf(builder.enabled);
        exclusions = new EnumMap<>(Rule.class);
        builder.exclusions.forEach((rule, patterns) -> exclusions.put(rule, List.copyOf(patterns)));
        namespace = builder.namespace;
        namespaceExclusions = List.copyOf(builder.namespaceExclusions);
    }

    /**
     * Returns the {@link ArchRule}s that implement a {@link Rule}.
     *
     * <p>
     * The switch has no default, so a constant added without a case fails to compile.
     *
     * @param rule the rule to implement
     * @return the rules to evaluate against the rule's class set
     */
    private List<ArchRule> archRules(Rule rule) {
        return switch (rule) {
            case CONSTANT_NAMING -> List.of(JavaRules.constantNaming());
            case DEPRECATED_API -> List.of(JavaRules.deprecatedApi());
            case DISABLED_TESTS -> JUnitRules.disabledTests();
            case FINAL_CLASS_PROTECTED_MEMBERS -> List.of(JavaRules.finalClassProtectedMembers());
            case FORBIDDEN_IMPORTS -> JavaRules.forbiddenImports();
            case IMPL_SUFFIX -> List.of(JavaRules.implSuffix());
            case INTERFACE_PREFIX -> List.of(JavaRules.interfacePrefix());
            case PUBLIC_FIELDS -> List.of(JavaRules.publicFields());
            case PUBLIC_RECORDS -> List.of(JavaRules.publicRecords());
            case SYSTEM_STREAMS -> List.of(JavaRules.systemStreams());
            case TEST_CLASS_VISIBILITY -> List.of(JUnitRules.testClassVisibility());
            case TEST_METHOD_VISIBILITY -> List.of(JUnitRules.testMethodVisibility());
            case UTILITY_CLASSES -> List.of(JavaRules.utilityClasses());
        };
    }

    /**
     * Starts a configuration whose namespace is the package of a class.
     *
     * @param anchor the class whose package is the module's root package, usually the
     *        {@code ArchitectureTest} that calls {@link #check()}
     * @return a builder with every rule at its default
     */
    public static Builder builder(Class<?> anchor) {
        return new Builder(anchor.getPackageName());
    }

    /**
     * Imports the namespace's production and test classes and checks them.
     *
     * @throws AssertionError if the classes span several classpath roots, or if any rule or check fails
     */
    public void check() {
        JavaClasses production = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages(namespace);
        JavaClasses tests = new ClassFileImporter().withImportOption(new ImportOption.OnlyIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages(namespace);
        check(production, tests);
    }

    /**
     * Decides whether two class sets pass, with nothing read from the classpath.
     *
     * @param production the production classes to check
     * @param tests the test classes to check
     * @throws AssertionError if the sets span several classpath roots, or if any rule or check fails
     */
    void check(JavaClasses production, JavaClasses tests) {
        List<ClassNamePattern> ignored = new ArrayList<>(namespaceExclusions);
        if (!namespace.equals(OWN_PACKAGE) && !namespace.startsWith(OWN_PACKAGE + ".")) {
            ignored.add(ClassNamePattern.of(OWN_PACKAGE + ".."));
        }
        JavaClasses examinedProduction = production.that(notMatching(ignored));
        JavaClasses examinedTests = tests.that(notMatching(ignored));

        requireSingleRoot("production classes", examinedProduction);
        requireSingleRoot("test classes", examinedTests);

        List<Failure> failures = new ArrayList<>();
        for (Rule rule : Rule.values()) {
            if (!isEnabled(rule)) {
                continue;
            }
            JavaClasses examined = rule.scope() == Scope.PRODUCTION ? examinedProduction : examinedTests;
            JavaClasses remaining = examined.that(notMatching(exclusions.getOrDefault(rule, List.of())));
            for (ArchRule archRule : archRules(rule)) {
                evaluate(archRule, remaining).ifPresent(failures::add);
            }
        }
        for (ArchRule addedRule : addedRules) {
            evaluate(addedRule.allowEmptyShould(true), examinedProduction).ifPresent(failures::add);
        }
        List<Throwable> thrown = new ArrayList<>();
        for (NamedCheck addedCheck : addedChecks) {
            try {
                addedCheck.check().run();
            } catch (AssertionError | RuntimeException error) {
                thrown.add(error);
                failures.add(new Failure(addedCheck.description(), List.of(detail(error))));
            }
        }

        if (!failures.isEmpty()) {
            AssertionError error = new AssertionError(report(failures));
            thrown.forEach(error::addSuppressed);
            throw error;
        }
    }

    /**
     * Describes what an added check threw, for the report.
     *
     * <p>
     * An {@link AssertionError} is a violation, so its message stands alone. Any other exception is
     * prefixed with its class name, so a check that crashed reads differently from one that found
     * something.
     *
     * @param thrown what the check threw
     * @return the message, or the class name when there is no message
     */
    private static String detail(Throwable thrown) {
        String className = thrown.getClass().getName();
        if (thrown instanceof AssertionError) {
            return Objects.toString(thrown.getMessage(), className);
        }
        return thrown.getMessage() == null ? className : className + ": " + thrown.getMessage();
    }

    /**
     * Evaluates a rule over a class set.
     *
     * @param rule the rule to evaluate
     * @param classes the classes it examines
     * @return the failure, or empty when the rule passes
     */
    private static Optional<Failure> evaluate(ArchRule rule, JavaClasses classes) {
        EvaluationResult result = rule.evaluate(classes);
        if (!result.hasViolation()) {
            return Optional.empty();
        }
        return Optional.of(new Failure(rule.getDescription(), result.getFailureReport().getDetails()));
    }

    /**
     * Returns whether a rule runs, once disabling and enabling are taken into account.
     *
     * <p>
     * A disabled rule stays off whatever else is called, and enabling a rule that is on by default
     * changes nothing.
     *
     * @param rule the rule to look up
     * @return {@code true} when the rule is evaluated
     */
    private boolean isEnabled(Rule rule) {
        return !disabled.containsKey(rule) && (rule.onByDefault() || enabled.contains(rule));
    }

    /**
     * Describes the classes that match none of the patterns.
     *
     * @param patterns the patterns that remove a class
     * @return a predicate that is true for a class no pattern matches
     */
    private static DescribedPredicate<JavaClass> notMatching(List<ClassNamePattern> patterns) {
        return DescribedPredicate.describe(
                "not excluded",
                javaClass -> patterns.stream().noneMatch(pattern -> pattern.matches(javaClass.getName()))
        );
    }

    /**
     * Renders the combined report for one or more failures.
     *
     * @param failures the failing rules and checks, in report order
     * @return the report, headed by the violation and rule counts
     */
    private static String report(List<Failure> failures) {
        int violations = failures.stream().mapToInt(failure -> failure.details().size()).sum();
        StringBuilder report = new StringBuilder(
                String.format("Found %d architecture violations for %d rules", violations, failures.size())
        );
        for (Failure failure : failures) {
            report.append("\n\nRule: ").append(failure.description());
            for (String detail : failure.details()) {
                report.append("\n\t").append(detail);
            }
        }
        return report.toString();
    }

    /**
     * Fails when a class set comes from more than one classpath root.
     *
     * @param label what the set holds, for the message
     * @param classes the set to check
     * @throws AssertionError if the set spans several roots
     */
    private void requireSingleRoot(String label, JavaClasses classes) {
        Map<String, SortedSet<String>> packagesByRoot = new TreeMap<>();
        for (JavaClass javaClass : classes) {
            javaClass.getSource()
                    .map(source -> root(javaClass, source.getUri()))
                    .ifPresent(
                            root -> packagesByRoot.computeIfAbsent(root, key -> new TreeSet<>())
                                    .add(javaClass.getPackageName())
                    );
        }
        if (packagesByRoot.size() < 2) {
            return;
        }
        StringBuilder message = new StringBuilder("The ").append(label)
                .append(" under ")
                .append(namespace)
                .append(" span ")
                .append(packagesByRoot.size())
                .append(" classpath roots, so this check would cover another module's classes:");
        packagesByRoot.forEach((root, packages) -> {
            message.append("\n  ").append(root);
            packages.forEach(name -> message.append("\n    ").append(name));
        });
        message.append("\nPass each package that belongs to another module to excludeNamespace(\"<package>..\").");
        throw new AssertionError(message.toString());
    }

    /**
     * Returns the classpath root a class was read from.
     *
     * <p>
     * The root is the class file's URI with the package path and file name removed, so
     * {@code jrt:/java.base/java/lang/String.class} has the root {@code jrt:/java.base/}.
     *
     * @param javaClass the class
     * @param source the URI of the class's file
     * @return the root, or the whole URI when it does not end in the class's own path
     */
    private static String root(JavaClass javaClass, URI source) {
        String uri = source.toString();
        String suffix = javaClass.getName().replace('.', '/') + ".class";
        return uri.endsWith(suffix) ? uri.substring(0, uri.length() - suffix.length()) : uri;
    }

    /**
     * Collects the settings of a {@link FoundationArchitecture}.
     *
     * <p>
     * Every method returns the builder, and {@link #build()} takes a snapshot, so a builder can be kept
     * and extended after a configuration is built from it.
     */
    public static final class Builder {
        private final List<NamedCheck> addedChecks = new ArrayList<>();
        private final List<ArchRule> addedRules = new ArrayList<>();
        private final Map<Rule, String> disabled = new EnumMap<>(Rule.class);
        private final Set<Rule> enabled = EnumSet.noneOf(Rule.class);
        private final Map<Rule, List<ClassNamePattern>> exclusions = new EnumMap<>(Rule.class);
        private final String namespace;
        private final List<ClassNamePattern> namespaceExclusions = new ArrayList<>();

        private Builder(String namespace) {
            this.namespace = namespace;
        }

        /**
         * Adds a check that ArchUnit cannot express, such as a scan of files on disk.
         *
         * <p>
         * The check signals failure by throwing {@link AssertionError}, and its message joins the combined
         * report under the description. A {@link RuntimeException} is reported under the description too,
         * as its class name and message, and the rules and checks after it still run. Each exception a
         * check throws is attached to the final error as a suppressed exception. An {@link Error} other
         * than {@link AssertionError} is not caught.
         *
         * @param description what the check verifies, shown as the failure's rule
         * @param check the check to run
         * @return this builder
         */
        public Builder addCheck(String description, Runnable check) {
            addedChecks.add(new NamedCheck(description, check));
            return this;
        }

        /**
         * Adds a rule of the module's own.
         *
         * <p>
         * The rule runs against the production classes left after namespace exclusions, with
         * {@code allowEmptyShould(true)} applied. No {@link Rule} constant addresses it, so it cannot be
         * disabled or excluded through this builder.
         *
         * @param rule the rule to run
         * @return this builder
         */
        public Builder addRule(ArchRule rule) {
            addedRules.add(rule);
            return this;
        }

        /**
         * Builds a configuration from the current settings.
         *
         * @return a configuration that is unaffected by later calls on this builder
         */
        public FoundationArchitecture build() {
            return new FoundationArchitecture(this);
        }

        /**
         * Turns a rule off, and records why.
         *
         * <p>
         * A disabled rule stays off, so a later {@link #enable(Rule)} does not bring it back, and an
         * exclusion for it has no effect.
         *
         * @param rule the rule to turn off
         * @param reason why the rule does not apply, which must not be null, empty, or blank
         * @return this builder
         * @throws IllegalArgumentException if the reason is null, empty, or blank
         */
        public Builder disable(Rule rule, @Nullable String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("A reason is required to disable " + rule);
            }
            disabled.put(rule, reason);
            return this;
        }

        /**
         * Turns on a rule that is off by default.
         *
         * <p>
         * A rule that is on by default is unaffected, so this call has no effect on it.
         *
         * @param rule the rule to turn on
         * @return this builder
         */
        public Builder enable(Rule rule) {
            enabled.add(rule);
            return this;
        }

        /**
         * Removes classes from what one rule examines.
         *
         * @param rule the rule to exempt the classes from
         * @param classes the classes to exempt, matched by binary name, so a nested class is not covered by
         *        its enclosing class
         * @return this builder
         */
        public Builder exclude(Rule rule, Class<?>... classes) {
            List<ClassNamePattern> patterns = new ArrayList<>();
            for (Class<?> excluded : classes) {
                patterns.add(ClassNamePattern.of(excluded.getName()));
            }
            exclusions.computeIfAbsent(rule, key -> new ArrayList<>()).addAll(patterns);
            return this;
        }

        /**
         * Removes classes from what one rule examines, by name.
         *
         * <p>
         * This form reaches a package-private class, which a test in another package cannot name with
         * {@code .class}.
         *
         * @param rule the rule to exempt the classes from
         * @param classNames the patterns to exempt, each {@code a.b.C}, {@code a.b.*}, or {@code a.b..}
         * @return this builder
         * @throws IllegalArgumentException if a pattern is in none of those forms
         */
        public Builder exclude(Rule rule, String... classNames) {
            List<ClassNamePattern> patterns = new ArrayList<>();
            for (String className : classNames) {
                patterns.add(ClassNamePattern.of(className));
            }
            exclusions.computeIfAbsent(rule, key -> new ArrayList<>()).addAll(patterns);
            return this;
        }

        /**
         * Removes packages from every rule.
         *
         * <p>
         * It serves a subpackage the module wants left out, such as its test fixtures, and a nested module
         * whose classes reach the classpath beside this one's.
         *
         * @param namespaces the patterns to remove, each {@code a.b.C}, {@code a.b.*}, or {@code a.b..}
         * @return this builder
         * @throws IllegalArgumentException if a pattern is in none of those forms
         */
        public Builder excludeNamespace(String... namespaces) {
            List<ClassNamePattern> patterns = new ArrayList<>();
            for (String excluded : namespaces) {
                patterns.add(ClassNamePattern.of(excluded));
            }
            namespaceExclusions.addAll(patterns);
            return this;
        }
    }

    /**
     * One failing rule or check, as the combined report lists it.
     *
     * @param description the rule's description, or the check's
     * @param details one entry per violation
     */
    private record Failure(String description, List<String> details) {}

    /**
     * A check added through {@link Builder#addCheck(String, Runnable)}.
     *
     * @param description what the check verifies
     * @param check the check, which throws {@link AssertionError} or a {@link RuntimeException} to fail
     */
    private record NamedCheck(String description, Runnable check) {}
}

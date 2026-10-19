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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.coordinatekit.foundation.architecture.fixtures.CompliantCommand;
import org.coordinatekit.foundation.architecture.fixtures.DesktopUser;
import org.coordinatekit.foundation.architecture.fixtures.FixtureVersionProvider;
import org.coordinatekit.foundation.architecture.fixtures.NonPublicFixtures;
import org.coordinatekit.foundation.architecture.fixtures.OtherVersionProviderCommand;
import org.coordinatekit.foundation.architecture.fixtures.PrintingRecord;
import org.coordinatekit.foundation.architecture.fixtures.PublicClassTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicEnumTest;
import org.coordinatekit.foundation.architecture.fixtures.PublicRecord;
import org.coordinatekit.foundation.architecture.fixtures.StackTracePrinter;
import org.coordinatekit.foundation.architecture.fixtures.SystemOutPrinter;
import org.coordinatekit.foundation.architecture.fixtures.cycles.cyclic.CyclicRoot;
import org.coordinatekit.foundation.architecture.fixtures.cycles.cyclic.child.CyclicChild;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

/**
 * Tests for the decider, {@link FoundationArchitecture#check(JavaClasses, JavaClasses)}, driven
 * with hand-picked production and test sets so that no classpath scan is involved. The anchor is
 * this class, which sits in the module's own package, so the decider's self-exclusion does not
 * remove the fixtures.
 *
 * <p>
 * Assertions look for rule descriptions and fixture names in the report rather than its full text.
 * The root guard's second root comes from {@code String.class}, whose source is a {@code jrt:} URI,
 * and {@code excludeNamespace("java.lang..")} silences it.
 */
class FoundationArchitectureTest {
    record BlankClassNameParameters(String name, @Nullable String className) {}

    record BlankReasonParameters(String name, @Nullable String reason) {}

    record RootParameters(String name, boolean inProduction) {}

    record ScopeParameters(
            String name,
            List<Class<?>> production,
            List<Class<?>> tests,
            List<String> reported,
            List<String> notReported
    ) {}

    private static final String ADDED_RULE_DESCRIPTION = "Production classes should be named Zzz";
    private static final String PACKAGE_CYCLES = PackageCycleRules.packageCycles(CyclicRoot.class.getPackageName())
            .getDescription();
    private static final String PICOCLI_COMMANDS_CONFIGURED = PicocliRules
            .picocliCommands(FixtureVersionProvider.class.getName())
            .getDescription();
    private static final String PUBLIC_RECORDS = JavaRules.publicRecords().getDescription();
    private static final String SYSTEM_STREAMS = JavaRules.systemStreams().getDescription();
    private static final String TEST_CLASS_VISIBILITY = JUnitRules.testClassVisibility().getDescription();

    @Test
    void addCheck__errorOtherThanAssertionErrorPropagates() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().addCheck("recursive scan", () -> {
            throw new StackOverflowError();
        }).build();

        // ACT //
        // ASSERT //
        assertThrows(StackOverflowError.class, () -> architecture.check(Fixtures.of(), Fixtures.of()));
    }

    @Test
    void addCheck__failureJoinsReport() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().addCheck("no stray files", () -> {
            throw new AssertionError("found stray.txt");
        }).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(), Fixtures.of());

        // ASSERT //
        assertTrue(message.startsWith("Found 1 architecture violations for 1 rules"), message);
        assertTrue(message.contains("Rule: no stray files\n\tfound stray.txt"), message);
    }

    @Test
    void addCheck__messagelessErrorNamesItsClass() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().addCheck("no stray files", () -> {
            throw new AssertionError();
        }).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(), Fixtures.of());

        // ASSERT //
        assertTrue(message.contains("Rule: no stray files\n\t" + AssertionError.class.getName()), message);
    }

    @Test
    void addCheck__passingCheckAddsNothing() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().addCheck("always fine", () -> {}).build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(() -> architecture.check(Fixtures.of(), Fixtures.of()));
    }

    @Test
    void addCheck__runtimeExceptionKeepsOtherFailuresAndLaterChecks() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().addCheck("file scan", () -> {
            throw new IllegalStateException("scan failed");
        }).addCheck("later check", () -> {
            throw new AssertionError("found stray.txt");
        }).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PublicRecord.class), Fixtures.of());

        // ASSERT //
        assertTrue(message.startsWith("Found 3 architecture violations for 3 rules"), message);
        assertTrue(message.contains("Rule: " + PUBLIC_RECORDS), message);
        assertTrue(message.contains("Rule: file scan\n\tjava.lang.IllegalStateException: scan failed"), message);
        assertTrue(message.contains("Rule: later check\n\tfound stray.txt"), message);
    }

    @Test
    void addCheck__runtimeExceptionWithoutMessageNamesItsClass() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().addCheck("file scan", () -> {
            throw new IllegalStateException();
        }).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(), Fixtures.of());

        // ASSERT //
        assertTrue(message.endsWith("Rule: file scan\n\t" + IllegalStateException.class.getName()), message);
    }

    @Test
    void addCheck__throwablesAreSuppressedOnTheError() {
        // ARRANGE //
        AssertionError violation = new AssertionError("found stray.txt");
        IllegalStateException crash = new IllegalStateException("scan failed");
        FoundationArchitecture architecture = newBuilder().addCheck("no stray files", () -> {
            throw violation;
        }).addCheck("file scan", () -> {
            throw crash;
        }).build();

        // ACT //
        AssertionError error = assertThrows(
                AssertionError.class,
                () -> architecture.check(Fixtures.of(), Fixtures.of())
        );

        // ASSERT //
        assertEquals(List.of(violation, crash), List.of(error.getSuppressed()));
    }

    @Test
    void addRule__acceptsEmptyProduction() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder()
                .addRule(classes().should().haveSimpleName("Zzz").as(ADDED_RULE_DESCRIPTION))
                .build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(() -> architecture.check(Fixtures.of(), Fixtures.of()));
    }

    @Test
    void addRule__failureJoinsReport() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder()
                .addRule(classes().should().haveSimpleName("Zzz").as(ADDED_RULE_DESCRIPTION))
                .build();

        // ACT //
        String message = failure(architecture, Fixtures.of(NonPublicFixtures.PACKAGE_PRIVATE_RECORD), Fixtures.of());

        // ASSERT //
        assertTrue(message.contains("Rule: " + ADDED_RULE_DESCRIPTION), message);
        assertTrue(message.contains(NonPublicFixtures.PACKAGE_PRIVATE_RECORD.getName()), message);
    }

    @Test
    void addRule__skipsExcludedNamespace() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().excludeNamespace(Fixtures.PACKAGE + "..")
                .addRule(classes().should().haveSimpleName("Zzz").as(ADDED_RULE_DESCRIPTION))
                .build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(
                () -> architecture.check(Fixtures.of(NonPublicFixtures.PACKAGE_PRIVATE_RECORD), Fixtures.of())
        );
    }

    @Test
    void build__ignoresLaterCalls() {
        // ARRANGE //
        FoundationArchitecture.Builder builder = newBuilder();
        FoundationArchitecture architecture = builder.build();
        builder.addCheck("added after build", () -> {
            throw new AssertionError("late");
        });

        // ACT //
        // ASSERT //
        assertDoesNotThrow(() -> architecture.check(Fixtures.of(), Fixtures.of()));
    }

    @Test
    void check__emptySetsPass() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(() -> architecture.check(Fixtures.of(), Fixtures.of()));
    }

    @Test
    void check__ordersRulesThenAddedRulesThenAddedChecks() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().addCheck("an added check", () -> {
            throw new AssertionError("check failed");
        }).addRule(classes().should().haveSimpleName("Zzz").as(ADDED_RULE_DESCRIPTION)).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PrintingRecord.class), Fixtures.of(PublicClassTest.class));

        // ASSERT //
        int publicRecords = message.indexOf("Rule: " + PUBLIC_RECORDS);
        int systemStreams = message.indexOf("Rule: " + SYSTEM_STREAMS);
        int testClassVisibility = message.indexOf("Rule: " + TEST_CLASS_VISIBILITY);
        int addedRule = message.indexOf("Rule: " + ADDED_RULE_DESCRIPTION);
        int addedCheck = message.indexOf("Rule: an added check");
        assertTrue(0 < publicRecords, message);
        assertTrue(publicRecords < systemStreams, message);
        assertTrue(systemStreams < testClassVisibility, message);
        assertTrue(testClassVisibility < addedRule, message);
        assertTrue(addedRule < addedCheck, message);
    }

    @Test
    void check__rootGuardPassesForOneRoot() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(
                () -> architecture.check(
                        Fixtures.of(NonPublicFixtures.PACKAGE_PRIVATE_RECORD, StackTracePrinter.class),
                        Fixtures.of(NonPublicFixtures.PACKAGE_PRIVATE_CLASS_TEST)
                )
        );
    }

    static Stream<RootParameters> check__rootGuardPassesOnceNamespaceIsExcluded() {
        return Stream.of(new RootParameters("production", true), new RootParameters("tests", false));
    }

    @MethodSource
    @ParameterizedTest
    void check__rootGuardPassesOnceNamespaceIsExcluded(RootParameters parameters) {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().excludeNamespace("java.lang..").build();
        JavaClasses mixed = Fixtures.of(String.class, NonPublicFixtures.PACKAGE_PRIVATE_RECORD);

        // ACT //
        // ASSERT //
        assertDoesNotThrow(
                () -> architecture.check(
                        parameters.inProduction() ? mixed : Fixtures.of(),
                        parameters.inProduction() ? Fixtures.of() : mixed
                ),
                parameters.name()
        );
    }

    static Stream<RootParameters> check__rootGuardRejectsTwoRoots() {
        return Stream.of(new RootParameters("production", true), new RootParameters("tests", false));
    }

    @MethodSource
    @ParameterizedTest
    void check__rootGuardRejectsTwoRoots(RootParameters parameters) {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().build();
        JavaClasses mixed = Fixtures.of(String.class, PublicRecord.class, PublicClassTest.class);
        JavaClasses empty = Fixtures.of();

        // ACT //
        String message = failure(
                architecture,
                parameters.inProduction() ? mixed : empty,
                parameters.inProduction() ? empty : mixed
        );

        // ASSERT //
        assertTrue(
                message.contains(
                        parameters.inProduction() ? "The production classes under " : "The test classes under "
                ),
                message
        );
        assertTrue(message.contains("span 2 classpath roots"), message);
        assertTrue(message.contains("jrt:/java.base/\n    java.lang"), message);
        assertTrue(message.contains("\n    " + Fixtures.PACKAGE), message);
        assertTrue(message.contains("excludeNamespace(\"<package>..\")"), message);
        assertFalse(message.contains("Rule:"), "no rule runs before the guard: " + message);
    }

    static Stream<ScopeParameters> check__scopes() {
        return Stream.of(
                new ScopeParameters(
                        "each rule reads its own set",
                        List.of(PublicRecord.class),
                        List.of(PublicClassTest.class),
                        List.of(PUBLIC_RECORDS, TEST_CLASS_VISIBILITY),
                        List.of(SYSTEM_STREAMS)
                ),
                new ScopeParameters(
                        "a production violator in the test set is not seen",
                        List.of(),
                        List.of(PublicRecord.class, SystemOutPrinter.class),
                        List.of(),
                        List.of(PUBLIC_RECORDS, SYSTEM_STREAMS, TEST_CLASS_VISIBILITY)
                ),
                new ScopeParameters(
                        "a test violator in the production set is not seen",
                        List.of(PublicClassTest.class),
                        List.of(),
                        List.of(),
                        List.of(PUBLIC_RECORDS, SYSTEM_STREAMS, TEST_CLASS_VISIBILITY)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void check__scopes(ScopeParameters parameters) {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().build();
        JavaClasses production = Fixtures.of(parameters.production().toArray(Class<?>[]::new));
        JavaClasses tests = Fixtures.of(parameters.tests().toArray(Class<?>[]::new));

        // ACT //
        AssertionError error = null;
        try {
            architecture.check(production, tests);
        } catch (AssertionError e) {
            error = e;
        }

        // ASSERT //
        if (parameters.reported().isEmpty()) {
            assertNull(error, parameters.name());
            return;
        }
        String message = error == null ? "" : String.valueOf(error.getMessage());
        for (String description : parameters.reported()) {
            assertTrue(message.contains("Rule: " + description), parameters.name() + ": " + message);
        }
        for (String description : parameters.notReported()) {
            assertFalse(message.contains("Rule: " + description), parameters.name() + ": " + message);
        }
    }

    @Test
    void check__severalFailuresShareOneError() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PrintingRecord.class), Fixtures.of(PublicClassTest.class));

        // ASSERT //
        assertTrue(message.startsWith("Found 3 architecture violations for 3 rules"), message);
        assertTrue(message.contains("\n\nRule: " + PUBLIC_RECORDS + "\n\t"), message);
        assertTrue(message.contains("\n\nRule: " + SYSTEM_STREAMS + "\n\t"), message);
        assertTrue(message.contains("\n\nRule: " + TEST_CLASS_VISIBILITY + "\n\t"), message);
        assertTrue(message.contains(PrintingRecord.class.getName()), message);
        assertTrue(message.contains(PublicClassTest.class.getName()), message);
    }

    @Test
    void check__skipsThisModulesOwnPackageUnlessAnchoredInIt() {
        // ARRANGE //
        FoundationArchitecture outsider = FoundationArchitecture.builder(Object.class).build();
        FoundationArchitecture insider = newBuilder().build();

        // ACT //
        String insiderMessage = failure(insider, Fixtures.of(PublicRecord.class), Fixtures.of());

        // ASSERT //
        assertDoesNotThrow(() -> outsider.check(Fixtures.of(PublicRecord.class), Fixtures.of(PublicClassTest.class)));
        assertTrue(insiderMessage.contains(PUBLIC_RECORDS), insiderMessage);
    }

    @Test
    void check__subpackageAnchorKeepsTheFixtures() {
        // ARRANGE //
        FoundationArchitecture architecture = FoundationArchitecture.builder(PublicRecord.class).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PublicRecord.class), Fixtures.of());

        // ASSERT //
        assertTrue(message.contains("Rule: " + PUBLIC_RECORDS), message);
        assertTrue(message.contains(PublicRecord.class.getName()), message);
    }

    static Stream<BlankClassNameParameters> commandVersionProvider__blankName() {
        return Stream.of(
                new BlankClassNameParameters("null", null),
                new BlankClassNameParameters("empty", ""),
                new BlankClassNameParameters("blank", "  \t")
        );
    }

    @MethodSource
    @ParameterizedTest
    void commandVersionProvider__blankName(BlankClassNameParameters parameters) {
        // ARRANGE //
        FoundationArchitecture.Builder builder = newBuilder();

        // ACT //
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> builder.commandVersionProvider(parameters.className()),
                parameters.name()
        );

        // ASSERT //
        assertEquals("A class name is required for commandVersionProvider", thrown.getMessage(), parameters.name());
    }

    @Test
    void commandVersionProvider__lastCallWins() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().commandVersionProvider("com.example.Unused")
                .commandVersionProvider(FixtureVersionProvider.class.getName())
                .build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(() -> architecture.check(Fixtures.of(CompliantCommand.class), Fixtures.of()));
    }

    @Test
    void commandVersionProvider__narrowsPicocliCommands() {
        // ARRANGE //
        FoundationArchitecture unconfigured = newBuilder().build();
        FoundationArchitecture configured = newBuilder().commandVersionProvider(FixtureVersionProvider.class.getName())
                .build();

        // ACT //
        String message = failure(configured, Fixtures.of(OtherVersionProviderCommand.class), Fixtures.of());

        // ASSERT //
        assertDoesNotThrow(() -> unconfigured.check(Fixtures.of(OtherVersionProviderCommand.class), Fixtures.of()));
        assertTrue(message.contains("Rule: " + PICOCLI_COMMANDS_CONFIGURED), message);
        assertTrue(message.contains(OtherVersionProviderCommand.class.getName()), message);
        assertDoesNotThrow(() -> configured.check(Fixtures.of(CompliantCommand.class), Fixtures.of()));
    }

    static Stream<BlankReasonParameters> disable__blankReason() {
        return Stream.of(
                new BlankReasonParameters("null", null),
                new BlankReasonParameters("empty", ""),
                new BlankReasonParameters("blank", "  \t")
        );
    }

    @MethodSource
    @ParameterizedTest
    void disable__blankReason(BlankReasonParameters parameters) {
        // ARRANGE //
        FoundationArchitecture.Builder builder = newBuilder();

        // ACT //
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> builder.disable(Rule.PUBLIC_RECORDS, parameters.reason()),
                parameters.name()
        );

        // ASSERT //
        assertEquals("A reason is required to disable PUBLIC_RECORDS", thrown.getMessage(), parameters.name());
    }

    @Test
    void disable__silencesOnlyThatRule() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder()
                .disable(Rule.PUBLIC_RECORDS, "the records are the wire format")
                .build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PrintingRecord.class), Fixtures.of());

        // ASSERT //
        assertFalse(message.contains(PUBLIC_RECORDS), message);
        assertTrue(message.contains(SYSTEM_STREAMS), message);
    }

    @Test
    void disable__thenEnableStaysDisabled() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().disable(Rule.PUBLIC_RECORDS, "wire format")
                .enable(Rule.PUBLIC_RECORDS)
                .build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(() -> architecture.check(Fixtures.of(PublicRecord.class), Fixtures.of()));
    }

    @Test
    void enable__packageCyclesStaysOffUntilEnabled() {
        // ARRANGE //
        FoundationArchitecture off = FoundationArchitecture.builder(CyclicRoot.class).build();
        FoundationArchitecture on = FoundationArchitecture.builder(CyclicRoot.class)
                .enable(Rule.PACKAGE_CYCLES)
                .build();
        JavaClasses cycle = Fixtures.of(CyclicRoot.class, CyclicChild.class);

        // ACT //
        String message = failure(on, cycle, Fixtures.of());

        // ASSERT //
        assertDoesNotThrow(() -> off.check(cycle, Fixtures.of()));
        assertTrue(message.contains("Rule: " + PACKAGE_CYCLES), message);
        assertTrue(message.contains(CyclicRoot.class.getName()), message);
        assertTrue(message.contains(CyclicChild.class.getName()), message);
    }

    @Test
    void enable__ruleOnByDefaultIsUnchanged() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().enable(Rule.PUBLIC_RECORDS).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PublicRecord.class), Fixtures.of());

        // ASSERT //
        assertTrue(message.contains(PUBLIC_RECORDS), message);
    }

    @Test
    void exclude__badPatternIsRejectedAtTheCall() {
        // ARRANGE //
        FoundationArchitecture.Builder builder = newBuilder();

        // ACT //
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> builder.exclude(Rule.PUBLIC_RECORDS, "a.*.B")
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains("a.*.B"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("a.b.C, a.b.*, or a.b.."), thrown.getMessage());
    }

    @Test
    void exclude__byClassRemovesViolationForThatRuleOnly() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().exclude(Rule.PUBLIC_RECORDS, PrintingRecord.class).build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PrintingRecord.class), Fixtures.of());

        // ASSERT //
        assertFalse(message.contains("Rule: " + PUBLIC_RECORDS), message);
        assertTrue(message.contains("Rule: " + SYSTEM_STREAMS), message);
        assertTrue(message.contains(PrintingRecord.class.getName()), message);
    }

    @Test
    void exclude__byNameRemovesViolationForThatRuleOnly() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().exclude(Rule.PUBLIC_RECORDS, PrintingRecord.class.getName())
                .build();

        // ACT //
        String message = failure(architecture, Fixtures.of(PrintingRecord.class), Fixtures.of());

        // ASSERT //
        assertFalse(message.contains("Rule: " + PUBLIC_RECORDS), message);
        assertTrue(message.contains("Rule: " + SYSTEM_STREAMS), message);
    }

    @Test
    void exclude__disabledRuleIsIgnored() {
        // ARRANGE //
        FoundationArchitecture.Builder builder = newBuilder().disable(Rule.PUBLIC_RECORDS, "wire format");

        // ACT //
        FoundationArchitecture architecture = assertDoesNotThrow(
                () -> builder.exclude(Rule.PUBLIC_RECORDS, PublicRecord.class.getName()).build()
        );

        // ASSERT //
        assertDoesNotThrow(() -> architecture.check(Fixtures.of(PublicRecord.class), Fixtures.of()));
    }

    @Test
    void exclude__forbiddenImportsCoversEveryPackage() {
        // ARRANGE //
        FoundationArchitecture unexcluded = newBuilder().build();
        FoundationArchitecture excluded = newBuilder().exclude(Rule.FORBIDDEN_IMPORTS, DesktopUser.class.getName())
                .build();

        // ACT //
        String message = failure(unexcluded, Fixtures.of(DesktopUser.class), Fixtures.of());

        // ASSERT //
        assertTrue(message.contains("No classes should import classes in package java.awt.."), message);
        assertTrue(message.contains("No classes should import classes in package javax.swing.."), message);
        assertDoesNotThrow(() -> excluded.check(Fixtures.of(DesktopUser.class), Fixtures.of()));
    }

    @Test
    void exclude__starPatternCoversThePackage() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().exclude(Rule.TEST_CLASS_VISIBILITY, Fixtures.PACKAGE + ".*")
                .build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(
                () -> architecture.check(Fixtures.of(), Fixtures.of(PublicClassTest.class, PublicEnumTest.class))
        );
    }

    @Test
    void excludeNamespace__badPatternIsRejectedAtTheCall() {
        // ARRANGE //
        FoundationArchitecture.Builder builder = newBuilder();

        // ACT //
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> builder.excludeNamespace("org.example.*.internal")
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains("org.example.*.internal"), thrown.getMessage());
    }

    @Test
    void excludeNamespace__removesViolationsFromEveryRule() {
        // ARRANGE //
        FoundationArchitecture architecture = newBuilder().excludeNamespace(Fixtures.PACKAGE + "..").build();

        // ACT //
        // ASSERT //
        assertDoesNotThrow(
                () -> architecture.check(Fixtures.of(PrintingRecord.class), Fixtures.of(PublicClassTest.class))
        );
    }

    private static String failure(FoundationArchitecture architecture, JavaClasses production, JavaClasses tests) {
        return assertThrows(AssertionError.class, () -> architecture.check(production, tests)).getMessage();
    }

    private static FoundationArchitecture.Builder newBuilder() {
        return FoundationArchitecture.builder(FoundationArchitectureTest.class);
    }
}

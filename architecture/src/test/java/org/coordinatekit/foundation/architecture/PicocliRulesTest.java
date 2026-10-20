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
import org.coordinatekit.foundation.architecture.fixtures.CompliantCommand;
import org.coordinatekit.foundation.architecture.fixtures.CompliantEnvironmentOption;
import org.coordinatekit.foundation.architecture.fixtures.FixtureVersionProvider;
import org.coordinatekit.foundation.architecture.fixtures.IndirectlyValidatingMixinHolder;
import org.coordinatekit.foundation.architecture.fixtures.InheritedValidateUnvalidatedHolder;
import org.coordinatekit.foundation.architecture.fixtures.InheritedValidateValidatingHolder;
import org.coordinatekit.foundation.architecture.fixtures.MissingEverythingCommand;
import org.coordinatekit.foundation.architecture.fixtures.MissingStandardHelpCommand;
import org.coordinatekit.foundation.architecture.fixtures.MissingVersionProviderCommand;
import org.coordinatekit.foundation.architecture.fixtures.MultipleLongNamesEnvironmentOption;
import org.coordinatekit.foundation.architecture.fixtures.NonValidatableMixinHolder;
import org.coordinatekit.foundation.architecture.fixtures.OtherVersionProviderCommand;
import org.coordinatekit.foundation.architecture.fixtures.PartiallyValidatingMixinHolder;
import org.coordinatekit.foundation.architecture.fixtures.ShortNameWithoutEnvironmentDefault;
import org.coordinatekit.foundation.architecture.fixtures.ThrowawayValidatingMixinHolder;
import org.coordinatekit.foundation.architecture.fixtures.UnvalidatedMixinHolder;
import org.coordinatekit.foundation.architecture.fixtures.ValidatingMixinHolder;
import org.coordinatekit.foundation.architecture.fixtures.ViolatingEnvironmentOption;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Tests for the factories in {@link PicocliRules}. Each case imports a few fixtures, evaluates one
 * rule, and asserts which fixtures its violations name. A detail test pins the fragments of a
 * message that tell a reader which half of a convention failed.
 */
class PicocliRulesTest {
    private static final String CONFIGURED_PROVIDER = FixtureVersionProvider.class.getName();

    record CommandParameters(
            String name,
            @Nullable String versionProvider,
            List<Class<?>> fixtures,
            List<Class<?>> violators
    ) {}

    record FactoryParameters(String name, Supplier<List<ArchRule>> rules, List<String> descriptions) {}

    record RuleParameters(String name, List<Class<?>> fixtures, List<Class<?>> violators) {}

    @MethodSource("factories")
    @ParameterizedTest
    void descriptions(FactoryParameters parameters) {
        // ARRANGE //
        List<ArchRule> rules = parameters.rules().get();

        // ACT //
        List<String> descriptions = rules.stream().map(ArchRule::getDescription).toList();

        // ASSERT //
        assertEquals(parameters.descriptions(), descriptions, parameters.name());
    }

    private static String details(ArchRule rule, Class<?>... fixtures) {
        return String.join("\n", rule.evaluate(Fixtures.of(fixtures)).getFailureReport().getDetails());
    }

    @MethodSource("factories")
    @ParameterizedTest
    void emptyClassesPass(FactoryParameters parameters) {
        // ARRANGE //
        JavaClasses none = Fixtures.of();

        // ACT //
        List<EvaluationResult> results = parameters.rules().get().stream().map(rule -> rule.evaluate(none)).toList();

        // ASSERT //
        assertTrue(results.stream().noneMatch(EvaluationResult::hasViolation), parameters.name());
    }

    static Stream<FactoryParameters> factories() {
        return Stream.of(
                new FactoryParameters(
                        "picocliCommands unconfigured",
                        () -> List.of(PicocliRules.picocliCommands(null)),
                        List.of("Commands should declare mixinStandardHelpOptions = true and a versionProvider")
                ),
                new FactoryParameters(
                        "picocliCommands configured",
                        () -> List.of(PicocliRules.picocliCommands(CONFIGURED_PROVIDER)),
                        List.of(
                                "Commands should declare mixinStandardHelpOptions = true and a versionProvider = "
                                        + CONFIGURED_PROVIDER + ".class"
                        )
                ),
                new FactoryParameters(
                        "picocliEnvironmentOptions",
                        () -> List.of(PicocliRules.picocliEnvironmentOptions()),
                        List.of(
                                "Options with an environment-variable-backed defaultValue should declare only a long name"
                        )
                ),
                new FactoryParameters(
                        "picocliMixinValidation",
                        () -> List.of(PicocliRules.picocliMixinValidation()),
                        List.of(
                                "Fields with a @Mixin type that declares validate() must have that method called by the declaring class"
                        )
                )
        );
    }

    static Stream<CommandParameters> picocliCommands() {
        return Stream.of(
                new CommandParameters(
                        "unconfigured: standard help and any provider passes",
                        null,
                        List.of(CompliantCommand.class),
                        List.of()
                ),
                new CommandParameters(
                        "unconfigured: a different provider passes",
                        null,
                        List.of(OtherVersionProviderCommand.class),
                        List.of()
                ),
                new CommandParameters(
                        "unconfigured: missing standard help violates",
                        null,
                        List.of(MissingStandardHelpCommand.class),
                        List.of(MissingStandardHelpCommand.class)
                ),
                new CommandParameters(
                        "unconfigured: absent provider violates",
                        null,
                        List.of(MissingVersionProviderCommand.class),
                        List.of(MissingVersionProviderCommand.class)
                ),
                new CommandParameters(
                        "unconfigured: missing both halves violates",
                        null,
                        List.of(MissingEverythingCommand.class),
                        List.of(MissingEverythingCommand.class)
                ),
                new CommandParameters(
                        "unconfigured: only the violating commands are named",
                        null,
                        List.of(
                                CompliantCommand.class,
                                OtherVersionProviderCommand.class,
                                MissingStandardHelpCommand.class,
                                MissingVersionProviderCommand.class
                        ),
                        List.of(MissingStandardHelpCommand.class, MissingVersionProviderCommand.class)
                ),
                new CommandParameters(
                        "configured: the named provider passes",
                        CONFIGURED_PROVIDER,
                        List.of(CompliantCommand.class),
                        List.of()
                ),
                new CommandParameters(
                        "configured: a different provider violates",
                        CONFIGURED_PROVIDER,
                        List.of(OtherVersionProviderCommand.class),
                        List.of(OtherVersionProviderCommand.class)
                ),
                new CommandParameters(
                        "configured: absent provider violates",
                        CONFIGURED_PROVIDER,
                        List.of(MissingVersionProviderCommand.class),
                        List.of(MissingVersionProviderCommand.class)
                ),
                new CommandParameters(
                        "configured: missing standard help violates",
                        CONFIGURED_PROVIDER,
                        List.of(MissingStandardHelpCommand.class),
                        List.of(MissingStandardHelpCommand.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void picocliCommands(CommandParameters parameters) {
        Fixtures.assertViolators(
                List.of(PicocliRules.picocliCommands(parameters.versionProvider())),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void picocliCommands__detailNamesTheFailingHalf() {
        // ARRANGE //
        ArchRule unconfigured = PicocliRules.picocliCommands(null);
        ArchRule configured = PicocliRules.picocliCommands(CONFIGURED_PROVIDER);

        // ACT //
        String help = details(unconfigured, MissingStandardHelpCommand.class);
        String absent = details(unconfigured, MissingVersionProviderCommand.class);
        String other = details(configured, OtherVersionProviderCommand.class);

        // ASSERT //
        assertTrue(help.contains("does not declare mixinStandardHelpOptions = true"), help);
        assertTrue(absent.contains("does not declare a versionProvider"), absent);
        assertTrue(other.contains("does not declare versionProvider = " + CONFIGURED_PROVIDER + ".class"), other);
    }

    @Test
    void picocliCommands__detailReportsBothMissingHalves() {
        // ARRANGE //
        ArchRule rule = PicocliRules.picocliCommands(null);

        // ACT //
        String details = details(rule, MissingEverythingCommand.class);

        // ASSERT //
        assertTrue(details.contains("does not declare mixinStandardHelpOptions = true"), details);
        assertTrue(details.contains("does not declare a versionProvider"), details);
    }

    static Stream<RuleParameters> picocliEnvironmentOptions() {
        return Stream.of(
                new RuleParameters(
                        "long name with an environment default passes",
                        List.of(CompliantEnvironmentOption.class),
                        List.of()
                ),
                new RuleParameters(
                        "several long names with an environment default pass",
                        List.of(MultipleLongNamesEnvironmentOption.class),
                        List.of()
                ),
                new RuleParameters(
                        "short name without an environment default is not selected",
                        List.of(ShortNameWithoutEnvironmentDefault.class),
                        List.of()
                ),
                new RuleParameters(
                        "short name with an environment default violates",
                        List.of(ViolatingEnvironmentOption.class),
                        List.of(ViolatingEnvironmentOption.class)
                ),
                new RuleParameters(
                        "only the violating option is named",
                        List.of(
                                CompliantEnvironmentOption.class,
                                MultipleLongNamesEnvironmentOption.class,
                                ShortNameWithoutEnvironmentDefault.class,
                                ViolatingEnvironmentOption.class
                        ),
                        List.of(ViolatingEnvironmentOption.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void picocliEnvironmentOptions(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(PicocliRules.picocliEnvironmentOptions()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    static Stream<RuleParameters> picocliMixinValidation() {
        return Stream.of(
                new RuleParameters(
                        "validating the mixin directly passes",
                        List.of(ValidatingMixinHolder.class),
                        List.of()
                ),
                new RuleParameters(
                        "validating the mixin through a helper passes",
                        List.of(IndirectlyValidatingMixinHolder.class),
                        List.of()
                ),
                new RuleParameters(
                        "validating a mixin whose validate() is inherited passes",
                        List.of(InheritedValidateValidatingHolder.class),
                        List.of()
                ),
                new RuleParameters(
                        "never validating a mixin whose validate() is inherited violates",
                        List.of(InheritedValidateUnvalidatedHolder.class),
                        List.of(InheritedValidateUnvalidatedHolder.class)
                ),
                new RuleParameters(
                        "a mixin type without validate() is not selected",
                        List.of(NonValidatableMixinHolder.class),
                        List.of()
                ),
                new RuleParameters(
                        "never validating the mixin violates",
                        List.of(UnvalidatedMixinHolder.class),
                        List.of(UnvalidatedMixinHolder.class)
                ),
                new RuleParameters(
                        "validating only one of two mixins violates",
                        List.of(PartiallyValidatingMixinHolder.class),
                        List.of(PartiallyValidatingMixinHolder.class)
                ),
                new RuleParameters(
                        "validating a throwaway instance violates",
                        List.of(ThrowawayValidatingMixinHolder.class),
                        List.of(ThrowawayValidatingMixinHolder.class)
                ),
                new RuleParameters(
                        "only the unvalidated holders are named",
                        List.of(
                                ValidatingMixinHolder.class,
                                UnvalidatedMixinHolder.class,
                                NonValidatableMixinHolder.class,
                                ThrowawayValidatingMixinHolder.class
                        ),
                        List.of(UnvalidatedMixinHolder.class, ThrowawayValidatingMixinHolder.class)
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void picocliMixinValidation(RuleParameters parameters) {
        Fixtures.assertViolators(
                List.of(PicocliRules.picocliMixinValidation()),
                parameters.name(),
                parameters.fixtures(),
                parameters.violators()
        );
    }

    @Test
    void picocliMixinValidation__detailNamesTheUnvalidatedField() {
        // ARRANGE //
        ArchRule rule = PicocliRules.picocliMixinValidation();

        // ACT //
        String details = details(rule, PartiallyValidatingMixinHolder.class);

        // ASSERT //
        assertTrue(details.contains(".secondaryOutput is never validated via validate()"), details);
        assertFalse(details.contains("primaryOutput"), details);
    }
}

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
package org.coordinatekit.foundation.third.party.licenses.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Unit tests for {@link Allowlist}. The matching and the election are pure functions of strings and
 * rules, so none of these cases needs a Gradle project.
 */
class AllowlistTest {

    /**
     * One rule's {@code moduleLicense} and whether it approves a license.
     *
     * @param name what the case shows
     * @param moduleLicense the rule's license field
     * @param license the license name tested
     * @param expected whether the rule approves it
     */
    private record AllowsParameters(String name, @Nullable String moduleLicense, String license, boolean expected) {}

    /**
     * One rule and whether it covers a dependency.
     *
     * @param name what the case shows
     * @param rule the rule tested
     * @param moduleName the dependency's {@code group:artifact}
     * @param moduleVersion the dependency's version
     * @param expected whether the rule covers it
     */
    private record AppliesToParameters(
            String name,
            Allowlist.Rule rule,
            String moduleName,
            String moduleVersion,
            boolean expected
    ) {}

    /**
     * One dependency's declared licenses, the allowlist and registered licenses it is elected against,
     * and the license expected.
     *
     * @param name what the case shows
     * @param declared the licenses the dependency declares, in POM order
     * @param rules the allowlist's rules
     * @param registered the names of the registered licenses
     * @param expected the license elected, or {@code null} for none
     */
    private record ElectParameters(
            String name,
            List<String> declared,
            List<Allowlist.Rule> rules,
            Set<String> registered,
            @Nullable String expected
    ) {}

    /**
     * One allowlist that cannot be read as one, and the part of the message that says why.
     *
     * @param name what the case shows
     * @param allowlist the allowlist file contents
     * @param expectedMessage text the failure message contains
     */
    private record MalformedAllowlistParameters(String name, String allowlist, String expectedMessage) {}

    /** The Apache 2.0 license name the cases use. */
    private static final String APACHE = "Apache License, Version 2.0";

    /** The LGPL license name the cases use. */
    private static final String LGPL = "GNU Lesser General Public License, Version 2.1";

    /** The coordinate of the dependency the election cases are about. */
    private static final String MODULE = "example:lib";

    /** The version of the dependency the election cases are about. */
    private static final String VERSION = "1.0";

    /** A license name nothing registers. */
    private static final String WEIRD = "Weird License";

    static Stream<AllowsParameters> allows__cases() {
        return Stream.of(
                new AllowsParameters("absent field approves anything", null, APACHE, true),
                new AllowsParameters("match-anything pattern", ".*", WEIRD, true),
                new AllowsParameters("exact name", APACHE, APACHE, true),
                new AllowsParameters("pattern that matches in full", "Apache.*", APACHE, true),
                new AllowsParameters("pattern that matches only part", "Apache", APACHE, false),
                new AllowsParameters("different name", LGPL, APACHE, false),
                new AllowsParameters("invalid expression matched literally", "Apache (", "Apache (", true),
                new AllowsParameters("invalid expression matches nothing else", "Apache (", APACHE, false)
        );
    }

    @MethodSource("allows__cases")
    @ParameterizedTest
    void allows__cases(AllowsParameters parameters) {
        // ARRANGE //
        Allowlist.Rule rule = rule(MODULE, parameters.moduleLicense());

        // ACT //
        boolean allowed = rule.allows(parameters.license());

        // ASSERT //
        assertEquals(parameters.expected(), allowed, parameters.name());
    }

    static Stream<AppliesToParameters> appliesTo__cases() {
        return Stream.of(
                new AppliesToParameters(
                        "absent name and version",
                        new Allowlist.Rule(null, null, null),
                        "a:a",
                        "1.0",
                        true
                ),
                new AppliesToParameters(
                        "exact name and version",
                        new Allowlist.Rule("a:a", "1.0", null),
                        "a:a",
                        "1.0",
                        true
                ),
                new AppliesToParameters(
                        "patterns that match in full",
                        new Allowlist.Rule("a:.*", "1\\..*", null),
                        "a:a",
                        "1.0",
                        true
                ),
                new AppliesToParameters(
                        "name pattern that matches only part",
                        new Allowlist.Rule("a", null, null),
                        "a:a",
                        "1.0",
                        false
                ),
                new AppliesToParameters(
                        "version pattern that misses",
                        new Allowlist.Rule("a:a", "2\\..*", null),
                        "a:a",
                        "1.0",
                        false
                ),
                new AppliesToParameters(
                        "invalid expression matched literally",
                        new Allowlist.Rule("a:(", null, null),
                        "a:(",
                        "1.0",
                        true
                ),
                new AppliesToParameters(
                        "invalid expression matches nothing else",
                        new Allowlist.Rule("a:(", null, null),
                        "a:a",
                        "1.0",
                        false
                )
        );
    }

    @MethodSource("appliesTo__cases")
    @ParameterizedTest
    void appliesTo__cases(AppliesToParameters parameters) {
        // ACT //
        boolean applies = parameters.rule().appliesTo(parameters.moduleName(), parameters.moduleVersion());

        // ASSERT //
        assertEquals(parameters.expected(), applies, parameters.name());
    }

    static Stream<ElectParameters> elect__cases() {
        return Stream.of(
                new ElectParameters("no declared licenses", List.of(), List.of(), Set.of(APACHE), null),
                new ElectParameters(
                        "no rules, first unregistered, gives the first registered",
                        List.of(WEIRD, APACHE),
                        List.of(),
                        Set.of(APACHE),
                        APACHE
                ),
                new ElectParameters(
                        "no rules, none registered, gives the first declared",
                        List.of(WEIRD, LGPL),
                        List.of(),
                        Set.of(),
                        WEIRD
                ),
                new ElectParameters(
                        "rule approves the second while the first is registered but unapproved",
                        List.of(APACHE, LGPL),
                        List.of(rule(MODULE, LGPL)),
                        Set.of(APACHE, LGPL),
                        LGPL
                ),
                new ElectParameters(
                        "first unregistered, second approved and registered",
                        List.of(WEIRD, APACHE),
                        List.of(rule(MODULE, APACHE)),
                        Set.of(APACHE),
                        APACHE
                ),
                new ElectParameters(
                        "match-anything rule with the first unregistered",
                        List.of(WEIRD, APACHE),
                        List.of(rule(MODULE, ".*")),
                        Set.of(APACHE),
                        APACHE
                ),
                new ElectParameters(
                        "rule for another module ignored",
                        List.of(APACHE, LGPL),
                        List.of(rule("other:lib", LGPL)),
                        Set.of(APACHE, LGPL),
                        APACHE
                ),
                new ElectParameters(
                        "version-scoped rule that misses the version ignored",
                        List.of(APACHE, LGPL),
                        List.of(new Allowlist.Rule(MODULE, "2.0", LGPL)),
                        Set.of(APACHE, LGPL),
                        APACHE
                ),
                new ElectParameters(
                        "approved but unregistered gives the first approved",
                        List.of(APACHE, WEIRD, "Other License"),
                        List.of(rule(MODULE, WEIRD), rule(MODULE, "Other License")),
                        Set.of(APACHE),
                        WEIRD
                ),
                new ElectParameters(
                        "both approved and registered gives the first in POM order",
                        List.of(LGPL, APACHE),
                        List.of(rule(MODULE, APACHE), rule(MODULE, LGPL)),
                        Set.of(APACHE, LGPL),
                        LGPL
                )
        );
    }

    @MethodSource("elect__cases")
    @ParameterizedTest
    void elect__cases(ElectParameters parameters) {
        // ACT //
        String elected = Allowlist
                .elect(MODULE, VERSION, parameters.declared(), parameters.rules(), parameters.registered());

        // ASSERT //
        assertEquals(parameters.expected(), elected, parameters.name());
    }

    static Stream<MalformedAllowlistParameters> parse__malformed() {
        return Stream.of(
                new MalformedAllowlistParameters("not JSON", "{\"allowedLicenses\": [", "not valid JSON"),
                new MalformedAllowlistParameters("top level is a list", "[]", "top level is not an object"),
                new MalformedAllowlistParameters(
                        "allowedLicenses is an object",
                        "{\"allowedLicenses\": {}}",
                        "allowedLicenses is not a list"
                ),
                new MalformedAllowlistParameters(
                        "entry is a string",
                        "{\"allowedLicenses\": [\"MIT License\"]}",
                        "entry that is not an object"
                )
        );
    }

    @MethodSource("parse__malformed")
    @ParameterizedTest
    void parse__malformed(MalformedAllowlistParameters parameters) {
        // ACT //
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> Allowlist.parse(parameters.allowlist()),
                parameters.name()
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains(parameters.expectedMessage()), thrown.getMessage());
    }

    /**
     * Builds a rule that covers every version of a module.
     *
     * @param moduleName the rule's module field
     * @param moduleLicense the rule's license field
     * @return the rule
     */
    private static Allowlist.Rule rule(String moduleName, @Nullable String moduleLicense) {
        return new Allowlist.Rule(moduleName, null, moduleLicense);
    }
}

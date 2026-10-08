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

import groovy.json.JsonException;
import groovy.json.JsonParserType;
import groovy.json.JsonSlurper;
import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The license report's allowlist, read and matched the way its {@code checkLicense} task does. The
 * plugin checks the allowlist against the registered licenses when the project is evaluated, and
 * the attribution task uses it to choose which of a dual-licensed dependency's licenses to name, so
 * both go through the one definition of what a rule matches.
 *
 * <p>
 * A rule applies to a dependency when its {@code moduleName} and {@code moduleVersion} each match,
 * and it approves a license when its {@code moduleLicense} matches. A field matches when it is
 * absent, equals the value, or matches it in full as a regular expression. A field that is not a
 * valid expression can only match by equality, as it could not match in the report either.
 */
final class Allowlist {
    /** Not instantiable, because the class holds only static members. */
    private Allowlist() {}

    /**
     * One entry of the allowlist's {@code allowedLicenses} list.
     *
     * @param moduleName the {@code group:artifact} the rule covers, or {@code null} for any module
     * @param moduleVersion the version the rule covers, or {@code null} for any version
     * @param moduleLicense the license the rule approves, or {@code null} for any license
     */
    record Rule(@Nullable String moduleName, @Nullable String moduleVersion, @Nullable String moduleLicense) {
        /**
         * Returns whether the rule approves a license.
         *
         * @param license a license name as the report spells it
         * @return whether the rule's {@code moduleLicense} matches the name
         */
        boolean allows(String license) {
            return moduleLicense == null || moduleLicense.equals(".*") || matches(moduleLicense, license);
        }

        /**
         * Returns whether the rule approves at least one of the given license names. A rule that approves
         * anything does so even when no names are given.
         *
         * @param names the license names to test, such as the registered ones
         * @return whether some name is approved
         */
        boolean allowsAnyOf(Set<String> names) {
            return moduleLicense == null || moduleLicense.equals(".*") || names.stream().anyMatch(this::allows);
        }

        /**
         * Returns whether the rule covers a dependency.
         *
         * @param name the dependency's {@code group:artifact}
         * @param version the dependency's version
         * @return whether the rule's {@code moduleName} and {@code moduleVersion} both match
         */
        boolean appliesTo(String name, String version) {
            return (moduleName == null || matches(moduleName, name))
                    && (moduleVersion == null || matches(moduleVersion, version));
        }
    }

    /**
     * Chooses the license to attribute to a dependency from those its POM declares. A declared license
     * counts as approved when some rule that covers the dependency approves it, and every declared
     * license counts as approved when there are no rules. Of the approved licenses, the first one that
     * is registered wins, so a dependency does not fail for naming an unregistered license while a
     * registered one is also approved. When none of them is registered, the first approved license is
     * returned so the failure names the license the allowlist let through, and when none is approved,
     * the first declared one is.
     *
     * @param moduleName the dependency's {@code group:artifact}
     * @param moduleVersion the dependency's version
     * @param declared the licenses the report lists for the dependency, in POM order
     * @param rules the allowlist's rules
     * @param registered the names of the registered licenses
     * @return the license to attribute, or {@code null} when the dependency declares none
     */
    static @Nullable String elect(
            String moduleName,
            String moduleVersion,
            List<String> declared,
            List<Rule> rules,
            Set<String> registered
    ) {
        if (declared.isEmpty()) {
            return null;
        }
        List<String> approved = rules.isEmpty() ? declared
                : declared.stream()
                        .filter(
                                license -> rules.stream()
                                        .anyMatch(
                                                rule -> rule.appliesTo(moduleName, moduleVersion)
                                                        && rule.allows(license)
                                        )
                        )
                        .toList();
        for (String license : approved) {
            if (registered.contains(license)) {
                return license;
            }
        }
        return approved.isEmpty() ? declared.get(0) : approved.get(0);
    }

    /**
     * Returns whether a value satisfies a rule field: it equals the field, or the field matches it in
     * full as a regular expression.
     *
     * @param field the rule's text for the field
     * @param value the value to test
     * @return whether the value satisfies the field
     */
    private static boolean matches(String field, String value) {
        if (field.equals(value)) {
            return true;
        }
        try {
            return Pattern.compile(field).matcher(value).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * Parses the contents of an allowlist file, as leniently as the license report does, so comments,
     * unquoted keys, and single-quoted strings are accepted. A missing {@code allowedLicenses} gives no
     * rules, and a rule field that is not a string is treated as absent.
     *
     * @param json the contents of the allowlist file
     * @return the rules, in file order
     * @throws IllegalArgumentException if the contents do not parse, or are not an object whose
     *         {@code allowedLicenses} is a list of objects
     */
    static List<Rule> parse(String json) {
        Object allowlist;
        try {
            allowlist = new JsonSlurper().setType(JsonParserType.LAX).parseText(json);
        } catch (JsonException e) {
            throw new IllegalArgumentException("it is not valid JSON: " + e.getMessage(), e);
        }
        if (!(allowlist instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("its top level is not an object");
        }
        Object entries = root.get("allowedLicenses");
        List<Rule> rules = new ArrayList<>();
        if (entries == null) {
            return rules;
        }
        if (!(entries instanceof List<?> list)) {
            throw new IllegalArgumentException("allowedLicenses is not a list");
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> allowed)) {
                throw new IllegalArgumentException("allowedLicenses holds an entry that is not an object: " + entry);
            }
            rules.add(
                    new Rule(
                            text(allowed.get("moduleName")),
                            text(allowed.get("moduleVersion")),
                            text(allowed.get("moduleLicense"))
                    )
            );
        }
        return rules;
    }

    /**
     * Reads and parses an allowlist file.
     *
     * @param allowlist the allowlist file
     * @return the rules, in file order
     * @throws GradleException if the contents are not an allowlist
     * @throws UncheckedIOException if the file cannot be read
     */
    static List<Rule> read(File allowlist) {
        String json;
        try {
            json = Files.readString(allowlist.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + allowlist, e);
        }
        try {
            return parse(json);
        } catch (IllegalArgumentException e) {
            throw new GradleException("Cannot read " + allowlist.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Narrows a parsed JSON value to the string a rule field holds.
     *
     * @param value a value the JSON parser returned
     * @return the value when it is a string, otherwise {@code null}
     */
    private static @Nullable String text(@Nullable Object value) {
        return value instanceof String string ? string : null;
    }
}

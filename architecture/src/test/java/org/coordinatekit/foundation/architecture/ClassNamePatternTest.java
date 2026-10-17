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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Unit tests for {@link ClassNamePattern}, driven by the table of forms in the module's design:
 * each form against the names it must and must not match, and the strings that are in none of the
 * forms.
 */
class ClassNamePatternTest {
    record InvalidParameters(String name, String pattern) {}

    record MatchParameters(String name, String pattern, String className, boolean matches) {}

    static Stream<MatchParameters> matches__forms() {
        return Stream.of(
                new MatchParameters("exact matches itself", "a.b.C", "a.b.C", true),
                new MatchParameters("exact does not match a nested class", "a.b.C", "a.b.C$D", false),
                new MatchParameters("exact does not match a longer name", "a.b.C", "a.b.CD", false),
                new MatchParameters("exact does not match another package", "a.b.C", "a.x.C", false),
                new MatchParameters("exact treats the dot literally", "a.b.C", "aXbXC", false),
                new MatchParameters("exact matches a nested class by its binary name", "a.b.C$D", "a.b.C$D", true),
                new MatchParameters("star matches a direct class", "a.b.*", "a.b.C", true),
                new MatchParameters("star matches a nested class", "a.b.*", "a.b.C$D", true),
                new MatchParameters("star does not match a subpackage class", "a.b.*", "a.b.c.D", false),
                new MatchParameters("star does not match the package name itself", "a.b.*", "a.b", false),
                new MatchParameters("star does not match a sibling prefix", "a.b.*", "a.bc.D", false),
                new MatchParameters("package marker matches a direct class", "a.b..", "a.b.C", true),
                new MatchParameters("package marker matches a subpackage class", "a.b..", "a.b.c.D", true),
                new MatchParameters("package marker matches a deep subpackage class", "a.b..", "a.b.c.d.E$F", true),
                new MatchParameters("package marker does not match a sibling prefix", "a.b..", "a.bc.D", false),
                new MatchParameters("package marker does not match another package", "a.b..", "a.x.C", false),
                new MatchParameters("package marker does not match a parent package", "a.b..", "a.C", false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void matches__forms(MatchParameters parameters) {
        // ARRANGE //
        ClassNamePattern pattern = ClassNamePattern.of(parameters.pattern());

        // ACT //
        boolean matches = pattern.matches(parameters.className());

        // ASSERT //
        assertEquals(parameters.matches(), matches, parameters.name());
    }

    static Stream<InvalidParameters> of__invalid() {
        return Stream.of(
                new InvalidParameters("blank", "  "),
                new InvalidParameters("empty", ""),
                new InvalidParameters("bare wildcard", "*"),
                new InvalidParameters("bare package marker", ".."),
                new InvalidParameters("bare trailing star", ".*"),
                new InvalidParameters("wildcard inside a name", "a.*.B"),
                new InvalidParameters("wildcard in a class name", "a.b.Foo*"),
                new InvalidParameters("double wildcard", "a.b.**"),
                new InvalidParameters("package marker inside a name", "a..b.C"),
                new InvalidParameters("package marker beside a wildcard", "a.b...*"),
                new InvalidParameters("trailing dot", "a.b."),
                new InvalidParameters("leading dot", ".a.b"),
                new InvalidParameters("raw regular expression", "^a\\.b\\.C$")
        );
    }

    @MethodSource
    @ParameterizedTest
    void of__invalid(InvalidParameters parameters) {
        // ARRANGE //
        String pattern = parameters.pattern();

        // ACT //
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> ClassNamePattern.of(pattern)
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains("\"" + pattern + "\""), parameters.name());
        assertTrue(thrown.getMessage().contains("a.b.C, a.b.*, or a.b.."), parameters.name());
    }
}

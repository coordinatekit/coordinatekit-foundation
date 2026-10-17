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

import java.util.regex.Pattern;

/**
 * Matches a class's binary name against one of the three exclusion forms.
 *
 * <p>
 * {@code a.b.C} matches exactly that class and not its nested classes. {@code a.b.*} matches the
 * classes directly in {@code a.b}, nested classes included, because {@code $} is not a dot.
 * {@code a.b..} matches {@code a.b} and every subpackage, and not a sibling that merely shares the
 * prefix, such as {@code a.bc}. The forms and their regular expressions follow Taikai 2.0.0, which
 * this module replaces, so an exclusion written for Taikai keeps its meaning. Taikai's fallback of
 * treating any other string as a raw regular expression is not carried over: a string in none of
 * the forms is rejected.
 */
final class ClassNamePattern {
    private static final String ACCEPTED_FORMS = "a.b.C, a.b.*, or a.b..";

    private final Pattern regex;

    private ClassNamePattern(Pattern regex) {
        this.regex = regex;
    }

    private static ClassNamePattern compile(String regex) {
        return new ClassNamePattern(Pattern.compile(regex));
    }

    /**
     * A dotted name whose every segment is a non-empty run of Java identifier characters, which rules
     * out a wildcard, an empty segment, and a regular expression.
     */
    private static boolean isPlainName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (String segment : name.split("\\.", -1)) {
            if (segment.isEmpty() || !segment.chars().allMatch(Character::isJavaIdentifierPart)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether a class name matches the pattern.
     *
     * @param binaryName the class's binary name, such as {@code a.B$C} for a nested class
     * @return {@code true} when the name matches
     */
    boolean matches(String binaryName) {
        return regex.matcher(binaryName).matches();
    }

    /**
     * Parses an exclusion string.
     *
     * @param pattern the string in one of the three forms
     * @return the parsed pattern
     * @throws IllegalArgumentException if the string is blank or in none of the forms
     */
    static ClassNamePattern of(String pattern) {
        String stem = pattern.length() >= 2 ? pattern.substring(0, pattern.length() - 2) : "";
        if (pattern.endsWith("..") && isPlainName(stem)) {
            return compile("^" + Pattern.quote(stem) + "(\\..+)?$");
        }
        if (pattern.endsWith(".*") && isPlainName(stem)) {
            return compile("^" + Pattern.quote(stem) + "\\.[^.]+$");
        }
        if (isPlainName(pattern)) {
            return compile("^" + Pattern.quote(pattern) + "$");
        }
        throw new IllegalArgumentException(
                "Invalid exclusion pattern \"" + pattern + "\": the accepted forms are " + ACCEPTED_FORMS
        );
    }
}

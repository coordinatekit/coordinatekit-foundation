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
package org.coordinatekit.foundation.bump.version.gradle;

import org.gradle.api.GradleException;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The names a bump anchors its rewrites to, all read from the Gradle model. A coordinate is only
 * this project's when it starts with {@link #group}, a jar filename only when it starts with one of
 * the {@link #modules}, and an archive or version line only when it starts with the
 * {@link #rootName}, so a dependency a document happens to name is left alone.
 *
 * <p>
 * Files are read as ISO-8859-1 so that every byte survives the round trip, which means a name with
 * a non-ASCII character has to be re-encoded the same way to match the bytes the file holds.
 * {@link #of} does that, and the strings held here are the re-encoded forms.
 *
 * @param group the root project's group id
 * @param rootName the root project's name
 * @param modules the module names, longest first so that a name that is a prefix of another cannot
 *        shadow it
 */
record Anchors(String group, String rootName, List<String> modules) {

    /** A dotted group id, such as one a Maven coordinate carries. */
    private static final Pattern GROUP_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_-]+)+");

    /**
     * Re-encodes a name the way a file's bytes are read.
     *
     * @param name the name as Gradle holds it
     * @return the name's UTF-8 bytes read as ISO-8859-1
     */
    private static String encode(String name) {
        return new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    /**
     * Validates the names and puts them in the form the rewrite rules match against.
     *
     * @param group the root project's group
     * @param rootName the root project's name
     * @param moduleNames the subproject names, which may be empty
     * @return the anchors, with the root project's name as the only module when there are no
     *         subprojects
     * @throws GradleException if {@code group} is not a dotted group id
     */
    static Anchors of(String group, String rootName, Collection<String> moduleNames) {
        if (!GROUP_ID.matcher(group).matches()) {
            throw new GradleException(
                    "bumpVersion anchors its rewrites to the root project's group, which has to be a dotted group id"
                            + " such as com.example.widgets, but it is '" + group
                            + "'. Set group in the root build before running the task."
            );
        }
        List<String> modules = moduleNames.stream()
                .filter(name -> !name.isBlank())
                .distinct()
                .map(Anchors::encode)
                .sorted(
                        Comparator.<String>comparingInt(String::length)
                                .reversed()
                                .thenComparing(Comparator.naturalOrder())
                )
                .toList();
        return new Anchors(encode(group), encode(rootName), modules.isEmpty() ? List.of(encode(rootName)) : modules);
    }
}

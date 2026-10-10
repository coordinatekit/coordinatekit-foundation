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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

/** Tests {@link Anchors}, which validates the names every rewrite rule is anchored to. */
class AnchorsTest {
    /**
     * One group id the anchors have to reject.
     *
     * @param name what the case shows
     * @param group the group id
     */
    private record RejectedGroupParameters(String name, String group) {}

    @Test
    void of__acceptsHyphensAndUnderscoresInLaterSegments() {
        // ACT //
        Anchors anchors = Anchors.of("com.example-corp.widget_kit", "widgets", List.of("beta"));

        // ASSERT //
        assertEquals("com.example-corp.widget_kit", anchors.group());
    }

    @Test
    void of__dropsBlankAndRepeatedModules() {
        // ACT //
        Anchors anchors = Anchors.of("com.example.widgets", "widgets", List.of("beta", " ", "beta", "alpha"));

        // ASSERT //
        assertEquals(List.of("alpha", "beta"), anchors.modules());
    }

    @Test
    void of__ordersModulesLongestFirst() {
        // ACT //
        Anchors anchors = Anchors
                .of("com.example.widgets", "widgets", List.of("core", "core-extras", "api", "core-extras-more"));

        // ASSERT //
        assertEquals(List.of("core-extras-more", "core-extras", "core", "api"), anchors.modules());
    }

    @Test
    void of__reEncodesNamesToMatchFileBytes() {
        // ARRANGE //
        String name = "wïdgets";

        // ACT //
        Anchors anchors = Anchors.of("com.example.widgets", name, List.of(name));

        // ASSERT //
        String expected = new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        assertNotEquals(name, anchors.rootName());
        assertEquals(expected, anchors.rootName());
        assertEquals(List.of(expected), anchors.modules());
    }

    static Stream<RejectedGroupParameters> of__rejectedGroup() {
        return Stream.of(
                new RejectedGroupParameters("empty", ""),
                new RejectedGroupParameters("one segment", "verification"),
                new RejectedGroupParameters("leading dot", ".example.widgets"),
                new RejectedGroupParameters("trailing dot", "com.example."),
                new RejectedGroupParameters("space", "com.example widgets"),
                new RejectedGroupParameters("colon", "com.example:widgets")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void of__rejectedGroup(RejectedGroupParameters parameters) {
        // ACT //
        GradleException thrown = assertThrows(
                GradleException.class,
                () -> Anchors.of(parameters.group(), "widgets", List.of("beta"))
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains("dotted group id"), thrown.getMessage());
    }

    @Test
    void of__usesTheRootNameWhenThereAreNoModules() {
        // ACT //
        Anchors anchors = Anchors.of("com.example.widgets", "widgets", List.of());

        // ASSERT //
        assertEquals(List.of("widgets"), anchors.modules());
        assertEquals("widgets", anchors.rootName());
    }
}

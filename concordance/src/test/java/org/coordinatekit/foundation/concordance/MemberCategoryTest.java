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
package org.coordinatekit.foundation.concordance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit test for {@link MemberCategory}. The enum has no behavior to exercise; what it has is an
 * order, and that order is the rule Concordance enforces, so the one thing worth asserting is that
 * the constants still run in the sequence members must be declared in.
 */
class MemberCategoryTest {
    @Test
    void values__pinCategorySequence() {
        // ARRANGE //
        List<String> expected = List.of("ENUM_CONSTANT", "CONSTANT", "FIELD", "CONSTRUCTOR", "METHOD");

        // ACT //
        List<String> declared = Arrays.stream(MemberCategory.values()).map(Enum::name).toList();

        // ASSERT //
        assertEquals(expected, declared, "MemberCategory's declaration order is the category sequence itself");
    }
}

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
package org.coordinatekit.foundation.architecture.fixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestTemplate;

import java.util.Collection;
import java.util.List;

/** Public methods the rule does not select: passes {@code TEST_METHOD_VISIBILITY}. */
public abstract class PublicUnselectedMethods {
    @TestFactory
    @SuppressWarnings("unused")
    public Collection<DynamicTest> factory() {
        return List.of();
    }

    @RepeatedTest(1)
    @SuppressWarnings("unused")
    public void repeated() {}

    @BeforeEach
    public void setUp() {}

    @TestTemplate
    @SuppressWarnings("unused")
    public void template() {}
}

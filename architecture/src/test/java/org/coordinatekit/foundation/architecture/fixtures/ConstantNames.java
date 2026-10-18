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

/**
 * Constants that follow the convention, and fields the rule does not select: passes
 * {@code CONSTANT_NAMING}.
 */
@SuppressWarnings("NonSerializableClassWithSerialVersionUID")
public class ConstantNames {
    @SuppressWarnings("unused")
    static final int A1 = 1;
    @SuppressWarnings("unused")
    static final int MAX_SIZE = 10;
    @SuppressWarnings("unused")
    static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    static int counter;
    @SuppressWarnings("unused")
    final int instanceFinal = 1;
}

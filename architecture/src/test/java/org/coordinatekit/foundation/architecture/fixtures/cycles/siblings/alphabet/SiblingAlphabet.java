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
package org.coordinatekit.foundation.architecture.fixtures.cycles.siblings.alphabet;

import org.coordinatekit.foundation.architecture.fixtures.cycles.siblings.alpha.SiblingAlpha;

/**
 * A class in a sibling subpackage whose name begins with the other's name, depending back on it to
 * close a cycle.
 */
public class SiblingAlphabet {
    /** Returns a new sibling. */
    @SuppressWarnings("unused")
    public SiblingAlpha sibling() {
        return new SiblingAlpha();
    }
}

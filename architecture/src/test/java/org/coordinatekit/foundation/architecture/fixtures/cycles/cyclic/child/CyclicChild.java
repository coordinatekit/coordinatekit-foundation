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
package org.coordinatekit.foundation.architecture.fixtures.cycles.cyclic.child;

import org.coordinatekit.foundation.architecture.fixtures.cycles.cyclic.CyclicRoot;

/** A class in a subpackage that depends on the namespace root, closing a cycle. */
public class CyclicChild {
    /** Returns a new root. */
    public CyclicRoot root() {
        return new CyclicRoot();
    }
}

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
 * A public, non-final class that holds a protected class: its nested record violates
 * {@code PUBLIC_API_RECORDS}.
 */
public class ProtectedClassRecordHolder {
    /**
     * A protected class that a subclass can reach, so it hides nothing from {@code PUBLIC_API_RECORDS}.
     */
    protected static class Middle {
        /** A public record inside a protected class: violates {@code PUBLIC_API_RECORDS}. */
        public record Nested() {}
    }
}

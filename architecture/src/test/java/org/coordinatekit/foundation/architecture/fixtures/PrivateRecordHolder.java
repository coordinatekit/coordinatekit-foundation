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
 * A public class that holds private types, so {@code PUBLIC_API_RECORDS} passes both of its
 * records.
 */
public class PrivateRecordHolder {
    /** The private {@link Middle.Nested}, which a class literal outside this class cannot name. */
    static final Class<?> MIDDLE_NESTED = Middle.Nested.class;

    /** The private {@link Nested}, which a class literal outside this class cannot name. */
    static final Class<?> NESTED = Nested.class;

    /** A private class that hides the public record inside it: passes {@code PUBLIC_API_RECORDS}. */
    private static class Middle {
        /** A public record inside a private class: passes {@code PUBLIC_API_RECORDS}. */
        public record Nested() {}
    }

    /**
     * A private record that no code outside this class can reach: passes {@code PUBLIC_API_RECORDS}.
     */
    private record Nested() {}
}

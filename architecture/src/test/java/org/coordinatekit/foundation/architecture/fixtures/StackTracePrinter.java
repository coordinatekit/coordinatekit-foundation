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
 * A class that calls {@code printStackTrace()}, which reads no field of {@code System} and passes.
 *
 * <p>
 * JDK 25 adds {@code IO.println}, which passes for the same reason. It is out of reach on JDK 21,
 * so it has no fixture.
 */
public class StackTracePrinter {
    /**
     * Prints a stack trace.
     *
     * @param failure the failure to print
     */
    @SuppressWarnings({"CallToPrintStackTrace", "unused"})
    public void print(Exception failure) {
        failure.printStackTrace();
    }
}

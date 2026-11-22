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

/** A public class that declares a local record: passes {@code PUBLIC_API_RECORDS}. */
public class LocalRecordHolder {
    /** The local record from {@link #declare()}, which a class literal cannot name. */
    static final Class<?> LOCAL = declare();

    /**
     * Declares a local record, which no code outside the method can reach.
     *
     * @return the class of the local record
     */
    private static Class<?> declare() {
        record Local() {}
        return Local.class;
    }
}

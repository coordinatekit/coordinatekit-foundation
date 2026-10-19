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
 * A constructor that declares {@code throws Exception}: passes {@code GENERIC_EXCEPTIONS}, which
 * does not check constructors.
 */
public class GenericExceptionConstructor {
    /** Declares the generic exception. */
    @SuppressWarnings("RedundantThrows")
    public GenericExceptionConstructor() throws Exception {}
}

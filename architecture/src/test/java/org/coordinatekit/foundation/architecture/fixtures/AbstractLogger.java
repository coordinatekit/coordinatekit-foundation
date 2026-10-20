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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An abstract class with the required logger: passes {@code LOGGER_NAMING}. */
public abstract class AbstractLogger {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Logs a message. */
    @SuppressWarnings("unused")
    public void report() {
        log.info("message");
    }
}

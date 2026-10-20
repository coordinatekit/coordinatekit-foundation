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

import picocli.CommandLine.Mixin;

/**
 * A holder that validates only one of two mixins of the same type: violates
 * {@code PICOCLI_MIXIN_VALIDATION} for {@code secondaryOutput}.
 */
public class PartiallyValidatingMixinHolder {
    @Mixin
    ValidatableOptions primaryOutput = new ValidatableOptions();

    @SuppressWarnings("unused")
    @Mixin
    ValidatableOptions secondaryOutput = new ValidatableOptions();

    @SuppressWarnings("unused")
    void execute() {
        primaryOutput.validate();
    }
}

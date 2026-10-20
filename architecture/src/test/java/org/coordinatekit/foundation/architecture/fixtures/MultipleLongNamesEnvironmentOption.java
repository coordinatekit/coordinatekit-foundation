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

import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/**
 * An option with an environment default and several long names: passes
 * {@code PICOCLI_ENVIRONMENT_OPTIONS}.
 */
public class MultipleLongNamesEnvironmentOption {
    @SuppressWarnings("unused")
    @Option(names = {"--database", "--db"}, defaultValue = "${env:SOME_ENVIRONMENT_VARIABLE:-}")
    @Nullable
    String database;
}

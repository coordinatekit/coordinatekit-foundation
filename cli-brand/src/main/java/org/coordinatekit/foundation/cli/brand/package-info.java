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

/**
 * The CoordinateKit brand art shared across command-line tools.
 *
 * <p>
 * {@link org.coordinatekit.foundation.cli.brand.Banner} renders the globe mark and the
 * "CoordinateKit" wordmark as a printable string, adapting to terminal width and color capability.
 * A tool that shares its name with the brand uses the no-argument constructor; a tool with its own
 * product name supplies a {@link org.coordinatekit.foundation.cli.brand.Banner.Product} built from
 * its own wordmark art and accent color.
 *
 * <p>
 * This package is published as a library dependency,
 * {@code org.coordinatekit.foundation:cli-brand}.
 */
@NullMarked
package org.coordinatekit.foundation.cli.brand;

import org.jspecify.annotations.NullMarked;

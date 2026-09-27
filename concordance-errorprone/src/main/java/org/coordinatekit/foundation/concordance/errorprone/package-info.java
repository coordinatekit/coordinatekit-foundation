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
 * The Error Prone check that enforces Concordance, the member-order rule CoordinateKit's Java
 * sources follow.
 *
 * <p>
 * {@link org.coordinatekit.foundation.concordance.errorprone.Concordance} is the whole package. It
 * registers itself through {@code META-INF/services}, so a build that puts this jar on Error
 * Prone's own classpath gets the check with no further wiring, and the Gradle plugin published as
 * {@code org.coordinatekit.foundation:concordance-gradle} does exactly that.
 *
 * <p>
 * Nothing here depends on {@code org.coordinatekit.foundation:concordance}. The check recognises
 * that module's two annotations by fully qualified name through javac's annotation mirrors, which
 * keeps the annotations on the consumer's compile classpath and off Error Prone's.
 */
@NullMarked
package org.coordinatekit.foundation.concordance.errorprone;

import org.jspecify.annotations.NullMarked;

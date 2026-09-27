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
 * Concordance, the member-order rule CoordinateKit's Java sources follow: class members are
 * declared in category order, and alphabetically within each category.
 *
 * <p>
 * {@link org.coordinatekit.foundation.concordance.MemberCategory} names the five categories and, by
 * its declaration order, fixes the sequence they appear in. The two annotations grant exemptions
 * and both require a reason: {@link org.coordinatekit.foundation.concordance.IntentionalOrder}
 * frees whole categories of one type's members, and
 * {@link org.coordinatekit.foundation.concordance.IgnoreOrder} takes a single member out of the
 * check.
 *
 * <p>
 * Nothing here enforces anything. The rule is enforced by the Error Prone check published as
 * {@code org.coordinatekit.foundation:concordance-errorprone} and configured by the Gradle plugin
 * published as {@code org.coordinatekit.foundation:concordance-gradle}, neither of which a consumer
 * compiles against. This package is the compile dependency,
 * {@code org.coordinatekit.foundation:concordance}, and holds only what annotated code needs to
 * name.
 */
@NullMarked
package org.coordinatekit.foundation.concordance;

import org.jspecify.annotations.NullMarked;

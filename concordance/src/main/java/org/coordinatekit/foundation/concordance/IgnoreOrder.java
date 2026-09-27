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
package org.coordinatekit.foundation.concordance;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Takes one member out of the member-order check entirely. The annotated member is neither reported
 * nor compared against, so the members on either side of it compare with each other as though it
 * were not there. A field that has to sit beside the method using it, or a method deliberately
 * parked next to its overload, is the case this covers.
 *
 * <p>
 * Use {@link IntentionalOrder} instead when a whole category of a type's members is unsorted on
 * purpose, or {@code @SuppressWarnings("Concordance")} to suppress the check for a whole type. This
 * annotation is source-retained: javac attaches it to the member's symbol for the duration of the
 * compilation, which is long enough for the check to read it, and nothing survives into the class
 * file.
 *
 * @see IntentionalOrder
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD})
public @interface IgnoreOrder {
    /**
     * Why this member sits where it does. Required, so that an exemption records its own justification
     * in the source rather than in a review comment.
     *
     * @return the justification for taking this member out of the check
     */
    String reason();
}

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
 * Exempts whole categories of a type's members from the member-order check, for a type whose
 * declaration order carries meaning the alphabet would destroy. A width ladder whose rungs run
 * richest to leanest, or a state enum whose constants run in the order the states are entered, is
 * the case this covers.
 *
 * <p>
 * The named categories are exempt from both their position in the category sequence and their order
 * within the category; every category left unnamed stays checked. Listing one member as an
 * exception rather than a whole category is {@link IgnoreOrder}'s job.
 *
 * <p>
 * An enum whose constants run in the order its states are entered carries
 * {@code @IntentionalOrder(members = MemberCategory.ENUM_CONSTANT, reason = "states run in entry
 * order")} on the enum itself, and its nested types, fields, and methods stay checked as usual.
 *
 * @see IgnoreOrder
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface IntentionalOrder {
    /**
     * The categories this type declares in an order of its own.
     *
     * @return the exempt categories; an empty array exempts nothing
     */
    MemberCategory[] members();

    /**
     * What the declaration order carries that alphabetical order would not. Required, so that an
     * exemption records its own justification in the source rather than in a review comment.
     *
     * @return the justification for the type's own ordering
     */
    String reason();
}

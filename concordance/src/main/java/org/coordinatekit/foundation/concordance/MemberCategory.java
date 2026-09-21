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

/**
 * The kinds a class member is sorted into, listed in the order the kinds must be declared. A type
 * declares its enum constants, then its nested types, then its constants, then its fields, then its
 * constructors, then its methods. Within a kind the order is alphabetical and case-insensitive,
 * except for {@link #CONSTRUCTOR}, which runs by ascending parameter count.
 *
 * <p>
 * The constants are deliberately not alphabetical, because declaration order is the rule itself:
 * comparing two members' categories by {@link Enum#compareTo} is what decides whether one may
 * precede the other. {@code MemberCategoryTest} pins the sequence so a reordering cannot land
 * unnoticed.
 *
 * <p>
 * Naming a category in {@link IntentionalOrder#members()} exempts a type's members of that kind
 * from both their position in this sequence and their order within it, which is what this enum does
 * for its own constants.
 */
@IntentionalOrder(members = MemberCategory.ENUM_CONSTANT, reason = "declaration order is the rule")
public enum MemberCategory {
    /**
     * Enum constants. Java already requires them ahead of every other member, so this category records
     * a fact rather than imposing one; it exists so a type whose constants are deliberately unsorted
     * can name it in {@link IntentionalOrder#members()}.
     */
    ENUM_CONSTANT,

    /** Nested classes, interfaces, enums, records, and annotation types. */
    TYPE,

    /** Fields declared both {@code static} and {@code final}. */
    CONSTANT,

    /** Instance fields, and {@code static} fields that are not {@code final}. */
    FIELD,

    /** Constructors, ordered by ascending parameter count rather than alphabetically. */
    CONSTRUCTOR,

    /** Methods, static and instance alike, in one sequence. */
    METHOD
}

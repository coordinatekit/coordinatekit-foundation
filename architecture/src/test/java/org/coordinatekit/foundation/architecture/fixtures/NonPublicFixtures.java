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
 * Hands the tests the fixtures that a class literal cannot name from outside this package.
 *
 * <p>
 * Each of these fixtures is non-public because its visibility is what a rule checks, so it cannot
 * be made public to reach it. Every other fixture is public and is named by its class literal.
 */
public final class NonPublicFixtures {
    /** The package-private {@link PackagePrivateDeepRecordHolder.Middle.Nested}. */
    public static final Class<?> DEEP_RECORD_HOLDER_NESTED_RECORD = PackagePrivateDeepRecordHolder.Middle.Nested.class;

    /** The protected {@link FinalProtectedRecordHolder.Nested}. */
    public static final Class<?> FINAL_PROTECTED_RECORD_HOLDER_NESTED_RECORD = FinalProtectedRecordHolder.Nested.class;

    /** The package-private {@link ImplHolder.NestedImpl}. */
    public static final Class<?> IMPL_HOLDER_NESTED_IMPL = ImplHolder.NestedImpl.class;

    /** The package-private {@link PackagePrivateClassTest}. */
    public static final Class<?> PACKAGE_PRIVATE_CLASS_TEST = PackagePrivateClassTest.class;

    /** The package-private {@link PackagePrivateRecord}. */
    public static final Class<?> PACKAGE_PRIVATE_RECORD = PackagePrivateRecord.class;

    /** The protected {@link ProtectedClassRecordHolder.Middle.Nested}. */
    public static final Class<?> PROTECTED_CLASS_RECORD_HOLDER_NESTED_RECORD = ProtectedClassRecordHolder.Middle.Nested.class;

    /** The protected {@link ProtectedRecordHolder.Nested}. */
    public static final Class<?> PROTECTED_RECORD_HOLDER_NESTED_RECORD = ProtectedRecordHolder.Nested.class;

    /** The package-private {@link PackagePrivateRecordHolder.Nested}. */
    public static final Class<?> RECORD_HOLDER_NESTED_RECORD = PackagePrivateRecordHolder.Nested.class;

    /** The package-private {@link PackagePrivateSealedInterface.Nested}. */
    public static final Class<?> SEALED_INTERFACE_NESTED_RECORD = PackagePrivateSealedInterface.Nested.class;

    private NonPublicFixtures() {}
}

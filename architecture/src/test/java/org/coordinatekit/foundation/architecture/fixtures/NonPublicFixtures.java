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
 * Each of these fixtures cannot be named from outside this package, because its own visibility, or
 * that of a class around it, is what a rule checks. Every other fixture is public and is named by
 * its class literal.
 */
public final class NonPublicFixtures {
    /** The protected {@link FinalProtectedRecordHolder.Nested}, which no subclass can reach. */
    public static final Class<?> FINAL_PROTECTED_RECORD_HOLDER_NESTED = FinalProtectedRecordHolder.Nested.class;

    /** The package-private {@link ImplHolder.NestedImpl}. */
    public static final Class<?> IMPL_HOLDER_NESTED_IMPL = ImplHolder.NestedImpl.class;

    /** The local record declared inside {@link LocalRecordHolder}. */
    public static final Class<?> LOCAL_RECORD_HOLDER_LOCAL = LocalRecordHolder.LOCAL;

    /** The package-private {@link PackagePrivateClassTest}. */
    public static final Class<?> PACKAGE_PRIVATE_CLASS_TEST = PackagePrivateClassTest.class;

    /**
     * The public {@link PackagePrivateDeepRecordHolder.Middle.Nested}, hidden by the package-private
     * class two levels around it.
     */
    public static final Class<?> PACKAGE_PRIVATE_DEEP_RECORD_HOLDER_MIDDLE_NESTED = PackagePrivateDeepRecordHolder.Middle.Nested.class;

    /** The package-private {@link PackagePrivateRecord}. */
    public static final Class<?> PACKAGE_PRIVATE_RECORD = PackagePrivateRecord.class;

    /**
     * The public {@link PackagePrivateRecordHolder.Nested}, hidden by the package-private class around
     * it.
     */
    public static final Class<?> PACKAGE_PRIVATE_RECORD_HOLDER_NESTED = PackagePrivateRecordHolder.Nested.class;

    /**
     * The implicitly public {@link PackagePrivateSealedInterface.Nested}, hidden by the package-private
     * interface around it.
     */
    public static final Class<?> PACKAGE_PRIVATE_SEALED_INTERFACE_NESTED = PackagePrivateSealedInterface.Nested.class;

    /**
     * The public {@code PrivateRecordHolder.Middle.Nested}, hidden by the private class around it.
     */
    public static final Class<?> PRIVATE_RECORD_HOLDER_MIDDLE_NESTED = PrivateRecordHolder.MIDDLE_NESTED;

    /** The private {@code PrivateRecordHolder.Nested}. */
    public static final Class<?> PRIVATE_RECORD_HOLDER_NESTED = PrivateRecordHolder.NESTED;

    /**
     * The public {@link ProtectedClassRecordHolder.Middle.Nested}, inside the protected class around
     * it, which a subclass can reach.
     */
    public static final Class<?> PROTECTED_CLASS_RECORD_HOLDER_MIDDLE_NESTED = ProtectedClassRecordHolder.Middle.Nested.class;

    /** The protected {@link ProtectedRecordHolder.Nested}, which a subclass can reach. */
    public static final Class<?> PROTECTED_RECORD_HOLDER_NESTED = ProtectedRecordHolder.Nested.class;

    private NonPublicFixtures() {}
}

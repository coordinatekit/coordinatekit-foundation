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
package org.coordinatekit.foundation.concordance.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.CompilationTestHelper;
import org.coordinatekit.foundation.concordance.MemberCategory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Unit tests for {@link Concordance}. Each case compiles a small fixture through
 * {@link CompilationTestHelper} and marks the line a finding is expected on with a
 * {@code // BUG: Diagnostic contains:} comment; a fixture with no such comment must compile without
 * a finding at all. Assertions name the message rather than a line number, so the finding's wording
 * is covered along with its placement.
 *
 * <p>
 * One pair of adjacent categories has no test, because Java itself rules it out: an enum constant
 * cannot follow any other member, so {@code ENUM_CONSTANT} can never be the category declared too
 * late. Every other adjacent pair appears in {@link #matchClass__categorySequence}.
 */
class ConcordanceTest {
    /**
     * One category-sequence case: a member of a category that must come later, declared ahead of one
     * that must come earlier.
     *
     * @param name the case name
     * @param first the member declared first, of the category that should come second
     * @param second the member declared second, of the category that should come first
     * @param diagnostic the message the finding on {@code second} must contain
     */
    record SequenceParameters(String name, String first, String second, String diagnostic) {}

    /**
     * One within-category case: two members of the same category, in the order a fixture declares them.
     *
     * @param name the case name
     * @param first the member declared first
     * @param second the member declared second
     * @param diagnostic the message the finding on {@code second} must contain, or {@code null} when
     *        the pair is in order and nothing should be reported
     */
    record WithinCategoryParameters(String name, String first, String second, @Nullable String diagnostic) {}

    @Test
    void category__mirrorsMemberCategory() {
        // ARRANGE //
        List<String> published = Arrays.stream(MemberCategory.values()).map(Enum::name).toList();

        // ACT //
        List<String> checker = Arrays.stream(Concordance.Category.values()).map(Enum::name).toList();

        // ASSERT //
        assertEquals(published, checker, "the checker's private category enum has drifted from the published one");
    }

    /**
     * Returns a test helper for the check under test.
     *
     * @return the helper, with no flags set
     */
    private static CompilationTestHelper helper() {
        return CompilationTestHelper.newInstance(Concordance.class, ConcordanceTest.class);
    }

    /**
     * Returns a test helper for the check under test, configured with the given Error Prone flags.
     *
     * @param args the {@code -XepOpt} flags to compile the fixture with
     * @return the configured helper
     */
    private static CompilationTestHelper helper(String... args) {
        return helper().setArgs(Arrays.asList(args));
    }

    /**
     * Wraps fixture members in a class body, marking the second member as the one a finding is expected
     * on.
     *
     * @param first the member declared first
     * @param second the member declared second
     * @param diagnostic the message the finding on {@code second} must contain, or {@code null} when
     *        nothing should be reported
     * @return the fixture source, one string per line
     */
    private static String[] fixture(String first, String second, @Nullable String diagnostic) {
        List<String> lines = new ArrayList<>(List.of("class Fixture {", "    " + first));
        if (diagnostic != null) {
            lines.add("    // BUG: Diagnostic contains: " + diagnostic);
        }
        lines.add("    " + second);
        lines.add("}");
        return lines.toArray(String[]::new);
    }

    static Stream<SequenceParameters> matchClass__categorySequence() {
        return Stream.of(
                new SequenceParameters(
                        "nested_type_after_constant",
                        "static final int A = 1;",
                        "class B {}",
                        "nested type B declared after constant A"
                ),
                new SequenceParameters(
                        "constant_after_field",
                        "int a;",
                        "static final int B = 1;",
                        "constant B declared after field a"
                ),
                new SequenceParameters(
                        "field_after_constructor",
                        "Fixture() {}",
                        "int a;",
                        "field a declared after constructor with 0 parameters"
                ),
                new SequenceParameters(
                        "constructor_after_method",
                        "void a() {}",
                        "Fixture() {}",
                        "constructor with 0 parameters declared after method a()"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void matchClass__categorySequence(SequenceParameters parameters) {
        // ARRANGE //
        String[] source = fixture(parameters.first(), parameters.second(), parameters.diagnostic());

        // ACT + ASSERT //
        helper().addSourceLines("Fixture.java", source).doTest();
    }

    @Test
    void matchClass__constructorsByArity() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "class Fixture {",
                "    Fixture(int a, int b) {}",
                "",
                "    // BUG: Diagnostic contains: constructor with 0 parameters out of order with constructor"
                        + " with 2 parameters",
                "    Fixture() {}",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__emptyClass() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines("Fixture.java", "class Fixture {}").expectNoDiagnostics().doTest();
    }

    @Test
    void matchClass__enumWithBody() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "enum Fixture {",
                "    ALPHA,",
                "    ZETA;",
                "",
                "    static final int MAX = 1;",
                "",
                "    void beta() {}",
                "",
                "    // BUG: Diagnostic contains: method alpha() out of order with method beta()",
                "    void alpha() {}",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__ignoreOrderHidesMember() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "import org.coordinatekit.foundation.concordance.IgnoreOrder;",
                "",
                "class Fixture {",
                "    void b() {}",
                "",
                "    @IgnoreOrder(reason = \"sits beside the method that reads it\")",
                "    void a() {}",
                "}"
        ).expectNoDiagnostics().doTest();
    }

    @Test
    void matchClass__ignoreOrderJoinsNeighbours() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "import org.coordinatekit.foundation.concordance.IgnoreOrder;",
                "",
                "class Fixture {",
                "    void c() {}",
                "",
                "    @IgnoreOrder(reason = \"parked next to its overload\")",
                "    void a() {}",
                "",
                "    // BUG: Diagnostic contains: method b() out of order with method c()",
                "    void b() {}",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__initializerBlocksInvisible() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "class Fixture {",
                "    static final int B = 1;",
                "",
                "    static {",
                "    }",
                "",
                "    // BUG: Diagnostic contains: constant A out of order with constant B",
                "    static final int A = 2;",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__intentionalOrderExemptsNamedCategories() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "import org.coordinatekit.foundation.concordance.IntentionalOrder;",
                "import org.coordinatekit.foundation.concordance.MemberCategory;",
                "",
                "@IntentionalOrder(members = MemberCategory.CONSTANT, reason = \"the ladder runs widest first\")",
                "class Fixture {",
                "    static final int WIDE = 2;",
                "    static final int NARROW = 1;",
                "",
                "    void b() {}",
                "",
                "    // BUG: Diagnostic contains: method a() out of order with method b()",
                "    void a() {}",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__interfaceMembers() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "interface Fixture {",
                "    int ZEBRA = 1;",
                "",
                "    // BUG: Diagnostic contains: constant APPLE out of order with constant ZEBRA",
                "    int APPLE = 2;",
                "",
                "    void b();",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__lifecycleMethodsCheckedWhenUnconfigured() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "BeforeEach.java",
                "package org.junit.jupiter.api;",
                "",
                "public @interface BeforeEach {}"
        )
                .addSourceLines(
                        "Fixture.java",
                        "import org.junit.jupiter.api.BeforeEach;",
                        "",
                        "class Fixture {",
                        "    void zebra() {}",
                        "",
                        "    @BeforeEach",
                        "    // BUG: Diagnostic contains: method setUp() out of order with method zebra()",
                        "    void setUp() {}",
                        "}"
                )
                .doTest();
    }

    @Test
    void matchClass__lifecycleMethodsExemptWhenConfigured() {
        // ARRANGE + ACT + ASSERT //
        helper("-XepOpt:Concordance:LifecycleAnnotations=org.junit.jupiter.api.BeforeEach")
                .addSourceLines(
                        "BeforeEach.java",
                        "package org.junit.jupiter.api;",
                        "",
                        "public @interface BeforeEach {}"
                )
                .addSourceLines(
                        "Fixture.java",
                        "import org.junit.jupiter.api.BeforeEach;",
                        "",
                        "class Fixture {",
                        "    void zebra() {}",
                        "",
                        "    @BeforeEach",
                        "    void setUp() {}",
                        "}"
                )
                .expectNoDiagnostics()
                .doTest();
    }

    @Test
    void matchClass__nestedTypeBodyChecked() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "class Fixture {",
                "    static class Inner {",
                "        void b() {}",
                "",
                "        // BUG: Diagnostic contains: method a() out of order with method b()",
                "        void a() {}",
                "    }",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__recordComponentsInvisible() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "record Fixture(int zeta, int alpha) {",
                "    static final int MAX = 1;",
                "",
                "    void beta() {}",
                "}"
        ).expectNoDiagnostics().doTest();
    }

    @Test
    void matchClass__scaffoldingFieldsCheckedWhenUnconfigured() {
        // ARRANGE + ACT + ASSERT //
        helper().addSourceLines(
                "Fixture.java",
                "import org.slf4j.Logger;",
                "",
                "class Fixture {",
                "    static final int ZEBRA = 1;",
                "",
                "    // BUG: Diagnostic contains: constant log out of order with constant ZEBRA",
                "    static final Logger log = null;",
                "}"
        ).doTest();
    }

    @Test
    void matchClass__scaffoldingFieldsExemptWhenConfigured() {
        // ARRANGE + ACT + ASSERT //
        helper("-XepOpt:Concordance:ScaffoldingFieldTypes=org.slf4j.Logger")
                .addSourceLines(
                        "Fixture.java",
                        "import org.slf4j.Logger;",
                        "",
                        "class Fixture {",
                        "    static final int ZEBRA = 1;",
                        "",
                        "    static final Logger log = null;",
                        "}"
                )
                .expectNoDiagnostics()
                .doTest();
    }

    static Stream<WithinCategoryParameters> matchClass__withinCategory() {
        return Stream.of(
                new WithinCategoryParameters("methods_in_order", "void a() {}", "void b() {}", null),
                new WithinCategoryParameters(
                        "methods_out_of_order",
                        "void b() {}",
                        "void a() {}",
                        "method a() out of order with method b()"
                ),
                new WithinCategoryParameters(
                        "methods_compared_case_insensitively",
                        "void Beta() {}",
                        "void alpha() {}",
                        "method alpha() out of order with method Beta()"
                ),
                new WithinCategoryParameters("overloads_compare_equal", "void a(int first) {}", "void a() {}", null),
                new WithinCategoryParameters(
                        "constants_out_of_order",
                        "static final int B = 1;",
                        "static final int A = 2;",
                        "constant A out of order with constant B"
                ),
                new WithinCategoryParameters(
                        "fields_out_of_order",
                        "int b;",
                        "int a;",
                        "field a out of order with field b"
                ),
                new WithinCategoryParameters(
                        "nested_types_out_of_order",
                        "class B {}",
                        "class A {}",
                        "nested type A out of order with nested type B"
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void matchClass__withinCategory(WithinCategoryParameters parameters) {
        // ARRANGE //
        String[] source = fixture(parameters.first(), parameters.second(), parameters.diagnostic());
        CompilationTestHelper helper = helper().addSourceLines("Fixture.java", source);

        // ACT + ASSERT //
        if (parameters.diagnostic() == null) {
            helper.expectNoDiagnostics();
        }
        helper.doTest();
    }
}

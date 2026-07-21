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
package org.coordinatekit.foundation.cli.brand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.foundation.cli.brand.Banner.ColorMode;
import org.coordinatekit.foundation.cli.brand.Banner.ColorRole;
import org.coordinatekit.foundation.cli.brand.Banner.Segment;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unit tests for the pure {@link Banner#compose(int, ColorMode, Banner.Product)} layout/color
 * ladders and the {@link Banner#markSegments(String)} glyph-coloring rule. Everything here drives
 * width and color mode directly, so no terminal is involved and the assertions describe structure
 * (mark presence, width bands, SGR form) rather than exact art.
 *
 * <p>
 * Most cases drive the brand-only path with a {@code null} product — the way the
 * {@code coordinatekit} tool renders — while
 * {@link #compose__productSeamShowsBrandAndProductColors}, {@link #compose__productWidthLadder},
 * {@link #compose__wordBlockRectangular}, and {@link #of__validation} exercise the product seam
 * through {@link Banner.Product#of} so both rendering paths stay covered.
 *
 * <p>
 * Width expectations are measured from the art resources at class-load time rather than hardcoded,
 * so a redrawn wordmark cannot break a test here; {@link #mark__widthPinned} is the single
 * deliberate exception, pinning the mark's width to catch an accidental change to {@code mark.txt}.
 */
class BannerTest {
    /** The escape that opens every ANSI control sequence. */
    private static final String ESCAPE = "\u001b";

    /** The globe glyph appears only in the mark, never in the figlet words — a clean mark marker. */
    private static final String MARK_GLYPH = "+";

    /**
     * {@code X} appears in neither the brand art nor the mark, whose only glyphs are
     * {@code _ , . - ' ( ) / \ ` < |} and {@code # + - . : =} — a clean product marker.
     */
    private static final String PRODUCT_GLYPH = "X";

    /** The widest line of coordinatekit-big.txt, measured at class-load time. */
    private static final int BRAND_BIG_WIDTH = artWidth("banner/coordinatekit-big.txt");

    /** The widest line of coordinatekit-small.txt, measured at class-load time. */
    private static final int BRAND_SMALL_WIDTH = artWidth("banner/coordinatekit-small.txt");

    /** The one-space gutter Banner places between wordmark columns. */
    private static final int GUTTER_WIDTH = 1;

    /** The mark's height, measured from mark.txt at class-load time. */
    private static final int MARK_HEIGHT = artHeight("banner/mark.txt");

    /** The mark's width, measured from mark.txt; {@link #mark__widthPinned} is the deliberate pin. */
    private static final int MARK_WIDTH = artWidth("banner/mark.txt");

    /**
     * Synthetic big product art width; mark-relative so the product rungs outrank the mark at any mark
     * width.
     */
    private static final int PRODUCT_BIG_WIDTH = MARK_WIDTH + 17;

    /**
     * Synthetic small product art width; the gap below PRODUCT_BIG_WIDTH keeps the boundary rows on
     * distinct rungs.
     */
    private static final int PRODUCT_SMALL_WIDTH = MARK_WIDTH + 7;

    /**
     * The truecolor SGR code for {@link ColorRole#BRAND} (Banner.java ColorRole constructor order),
     * derived from the role's own RGB so a palette retune cannot break this file's structural
     * assertions; {@link #mark__widthPinned} stays the file's single deliberate pin.
     */
    private static final String BRAND_SGR = truecolorSgr(ColorRole.BRAND);

    /** The truecolor SGR code for {@link ColorRole#PIN}, derived the same way as {@link #BRAND_SGR}. */
    private static final String PIN_SGR = truecolorSgr(ColorRole.PIN);

    /**
     * The truecolor SGR code for {@link ColorRole#GLOBE}, derived the same way as {@link #BRAND_SGR}.
     */
    private static final String GLOBE_SGR = truecolorSgr(ColorRole.GLOBE);

    /** The truecolor SGR code for the {@code #0C2238} accent used in product tests (12, 34, 56). */
    private static final String PRODUCT_ACCENT_SGR = "38;2;12;34;56";

    /** The SGR prefix that identifies the truecolor (24-bit RGB) form. */
    private static final String RGB_FORM = "38;2;";

    /** The SGR prefix that identifies the 256-indexed form. */
    private static final String INDEXED_FORM = "38;5;";

    static Stream<ColorModeParameters> colorMode__ladder() {
        return Stream.of(
                new ColorModeParameters(
                        "ansi_disabled_outranks_every_enabling_signal",
                        false,
                        "xterm",
                        256,
                        "truecolor",
                        ColorMode.MONOCHROME
                ),
                new ColorModeParameters(
                        "ansi_disabled_with_no_signals",
                        false,
                        "xterm",
                        null,
                        null,
                        ColorMode.MONOCHROME
                ),
                new ColorModeParameters(
                        "dumb_type_is_monochrome",
                        true,
                        Terminal.TYPE_DUMB,
                        null,
                        null,
                        ColorMode.MONOCHROME
                ),
                new ColorModeParameters(
                        "dumb_color_type_is_monochrome",
                        true,
                        Terminal.TYPE_DUMB_COLOR,
                        null,
                        null,
                        ColorMode.MONOCHROME
                ),
                new ColorModeParameters(
                        "dumb_type_outranks_colorterm_truecolor",
                        true,
                        Terminal.TYPE_DUMB,
                        null,
                        "truecolor",
                        ColorMode.MONOCHROME
                ),
                new ColorModeParameters(
                        "dumb_type_outranks_max_colors",
                        true,
                        Terminal.TYPE_DUMB,
                        256,
                        null,
                        ColorMode.MONOCHROME
                ),
                new ColorModeParameters(
                        "truecolor_colorterm_selects_truecolor",
                        true,
                        "xterm",
                        null,
                        "truecolor",
                        ColorMode.TRUECOLOR
                ),
                new ColorModeParameters(
                        "24bit_colorterm_selects_truecolor",
                        true,
                        "xterm",
                        null,
                        "24bit",
                        ColorMode.TRUECOLOR
                ),
                new ColorModeParameters(
                        "colorterm_case_folding_uppercase_truecolor",
                        true,
                        "xterm",
                        null,
                        "TrueColor",
                        ColorMode.TRUECOLOR
                ),
                new ColorModeParameters(
                        "colorterm_substring_matching",
                        true,
                        "xterm",
                        null,
                        "something-truecolor-ish",
                        ColorMode.TRUECOLOR
                ),
                new ColorModeParameters(
                        "colorterm_outranks_max_colors_even_when_low",
                        true,
                        "xterm",
                        0,
                        "truecolor",
                        ColorMode.TRUECOLOR
                ),
                new ColorModeParameters(
                        "colorterm_unrelated_value_falls_through",
                        true,
                        "xterm",
                        256,
                        "xterm-256color",
                        ColorMode.C256
                ),
                new ColorModeParameters("colorterm_empty_falls_through", true, "xterm", 256, "", ColorMode.C256),
                new ColorModeParameters("colorterm_null_falls_through", true, "xterm", 256, null, ColorMode.C256),
                new ColorModeParameters(
                        "max_colors_null_is_monochrome",
                        true,
                        "xterm",
                        null,
                        null,
                        ColorMode.MONOCHROME
                ),
                new ColorModeParameters("max_colors_zero_is_monochrome", true, "xterm", 0, null, ColorMode.MONOCHROME),
                new ColorModeParameters("max_colors_seven_is_monochrome", true, "xterm", 7, null, ColorMode.MONOCHROME),
                new ColorModeParameters("max_colors_eight_is_c16", true, "xterm", 8, null, ColorMode.C16),
                new ColorModeParameters("max_colors_255_is_c16", true, "xterm", 255, null, ColorMode.C16),
                new ColorModeParameters("max_colors_256_is_c256", true, "xterm", 256, null, ColorMode.C256),
                new ColorModeParameters(
                        "max_colors_truecolor_count_is_still_c256",
                        true,
                        "xterm",
                        16_777_216,
                        null,
                        ColorMode.C256
                ),
                new ColorModeParameters("null_type_with_max_colors_is_not_dumb", true, null, 256, null, ColorMode.C256)
        );
    }

    @MethodSource
    @ParameterizedTest
    void colorMode__ladder(ColorModeParameters parameters) {
        // ACT //
        ColorMode mode = Banner
                .colorMode(parameters.ansiEnabled(), parameters.type(), parameters.maxColors(), parameters.colorterm());

        // ASSERT //
        assertEquals(parameters.expected(), mode, parameters.name());
    }

    static Stream<ComposeWidthParameters> compose__widthLadder() {
        // The "T - 1" rows assume BRAND_BIG_WIDTH > BRAND_SMALL_WIDTH strictly, true today; a redraw
        // violating that ordering arguably should fail one of these rows.
        return Stream.of(
                new ComposeWidthParameters("big_at_exact_fit", BRAND_BIG_WIDTH, BRAND_BIG_WIDTH, true),
                new ComposeWidthParameters("small_one_below_big", BRAND_BIG_WIDTH - 1, BRAND_SMALL_WIDTH, true),
                new ComposeWidthParameters("small_at_exact_fit", BRAND_SMALL_WIDTH, BRAND_SMALL_WIDTH, true),
                new ComposeWidthParameters("mark_only_one_below_small", BRAND_SMALL_WIDTH - 1, MARK_WIDTH, true),
                new ComposeWidthParameters("mark_only_at_floor", MARK_WIDTH, MARK_WIDTH, true),
                new ComposeWidthParameters("below_floor", MARK_WIDTH - 1, 0, false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void compose__widthLadder(ComposeWidthParameters parameters) {
        // ACT //
        String art = Banner.compose(parameters.width(), ColorMode.MONOCHROME, null);

        // ASSERT //
        assertEquals(
                parameters.expectedWidth(),
                widestLine(art),
                "width " + parameters.width() + " should produce block width " + parameters.expectedWidth()
        );
        assertEquals(
                parameters.expectMark(),
                art.contains(MARK_GLYPH),
                "mark presence at width " + parameters.width() + " should be " + parameters.expectMark()
        );
    }

    static Stream<ProductWidthParameters> compose__productWidthLadder() {
        int bothBig = BRAND_BIG_WIDTH + GUTTER_WIDTH + PRODUCT_BIG_WIDTH;
        int bothSmall = BRAND_SMALL_WIDTH + GUTTER_WIDTH + PRODUCT_SMALL_WIDTH;
        return Stream.of(
                new ProductWidthParameters("both_big_at_exact_fit", bothBig, bothBig, true, true),
                new ProductWidthParameters("both_small_one_below_big", bothBig - 1, bothSmall, true, true),
                new ProductWidthParameters("both_small_at_exact_fit", bothSmall, bothSmall, true, true),
                new ProductWidthParameters(
                        "product_big_one_below_both_small",
                        bothSmall - 1,
                        PRODUCT_BIG_WIDTH,
                        true,
                        true
                ),
                new ProductWidthParameters(
                        "product_big_at_exact_fit",
                        PRODUCT_BIG_WIDTH,
                        PRODUCT_BIG_WIDTH,
                        true,
                        true
                ),
                new ProductWidthParameters(
                        "product_small_one_below_product_big",
                        PRODUCT_BIG_WIDTH - 1,
                        PRODUCT_SMALL_WIDTH,
                        true,
                        true
                ),
                new ProductWidthParameters(
                        "product_small_at_exact_fit",
                        PRODUCT_SMALL_WIDTH,
                        PRODUCT_SMALL_WIDTH,
                        true,
                        true
                ),
                new ProductWidthParameters("mark_only_drops_product", PRODUCT_SMALL_WIDTH - 1, MARK_WIDTH, true, false),
                new ProductWidthParameters("mark_only_at_floor", MARK_WIDTH, MARK_WIDTH, true, false),
                new ProductWidthParameters("below_floor", MARK_WIDTH - 1, 0, false, false)
        );
    }

    @MethodSource
    @ParameterizedTest
    void compose__productWidthLadder(ProductWidthParameters parameters) {
        // ARRANGE //
        Banner.Product product = Banner.Product.of(
                List.of(PRODUCT_GLYPH.repeat(PRODUCT_BIG_WIDTH)),
                List.of(PRODUCT_GLYPH.repeat(PRODUCT_SMALL_WIDTH)),
                "#0C2238"
        );

        // ACT //
        String art = Banner.compose(parameters.width(), ColorMode.MONOCHROME, product);

        // ASSERT //
        assertEquals(
                parameters.expectedWidth(),
                widestLine(art),
                "width " + parameters.width() + " should produce block width " + parameters.expectedWidth()
        );
        assertEquals(
                parameters.expectMark(),
                art.contains(MARK_GLYPH),
                "mark presence at width " + parameters.width() + " should be " + parameters.expectMark()
        );
        assertEquals(
                parameters.expectProduct(),
                art.contains(PRODUCT_GLYPH),
                "product presence at width " + parameters.width() + " should be " + parameters.expectProduct()
        );
    }

    static Stream<ComposeSweepParameters> compose__neverWiderThanTerminal() {
        Banner.Product standardProduct = Banner.Product.of(
                List.of(PRODUCT_GLYPH.repeat(PRODUCT_BIG_WIDTH)),
                List.of(PRODUCT_GLYPH.repeat(PRODUCT_SMALL_WIDTH)),
                "#0C2238"
        );
        // Wide enough that the mark-plus-product-big rung is unreachable: the LAYOUTS Javadoc
        // (Banner.java:240-246) documents this as a skipped rung, not a broken layout.
        int pathologicalSmallWidth = 3;
        int pathologicalBigWidth = BRAND_SMALL_WIDTH + GUTTER_WIDTH + pathologicalSmallWidth + 10;
        Banner.Product pathologicalProduct = Banner.Product.of(
                List.of(PRODUCT_GLYPH.repeat(pathologicalBigWidth)),
                List.of(PRODUCT_GLYPH.repeat(pathologicalSmallWidth)),
                "#0C2238"
        );
        return Stream.of(
                new ComposeSweepParameters("brand_only", null, BRAND_BIG_WIDTH + 5),
                new ComposeSweepParameters(
                        "standard_product",
                        standardProduct,
                        BRAND_BIG_WIDTH + GUTTER_WIDTH + PRODUCT_BIG_WIDTH + 5
                ),
                new ComposeSweepParameters(
                        "pathological_product_skips_a_rung",
                        pathologicalProduct,
                        BRAND_BIG_WIDTH + GUTTER_WIDTH + pathologicalBigWidth + 5
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void compose__neverWiderThanTerminal(ComposeSweepParameters parameters) {
        // ACT & ASSERT //
        for (int w = -2; w <= parameters.sweepCeiling(); w++) {
            String art = Banner.compose(w, ColorMode.MONOCHROME, parameters.product());
            assertTrue(
                    widestLine(art) <= Math.max(w, 0),
                    parameters.name() + " at width " + w + " should never exceed the terminal width"
            );
        }
    }

    static Stream<ColorFormParameters> compose__colorForm() {
        return Stream.of(
                new ColorFormParameters(
                        "monochrome_emits_no_ansi",
                        ColorMode.MONOCHROME,
                        List.of(),
                        List.of(ESCAPE),
                        List.of()
                ),
                new ColorFormParameters(
                        "c16_emits_sixteen_color_sgr_not_indexed_or_rgb",
                        ColorMode.C16,
                        List.of(),
                        List.of(INDEXED_FORM, RGB_FORM),
                        List.of(ESCAPE + "\\[(3[0-7]|9[0-7])m")
                ),
                new ColorFormParameters(
                        "c256_emits_indexed_not_rgb",
                        ColorMode.C256,
                        List.of(INDEXED_FORM),
                        List.of(RGB_FORM),
                        List.of()
                ),
                new ColorFormParameters(
                        "truecolor_emits_rgb_form",
                        ColorMode.TRUECOLOR,
                        List.of(RGB_FORM),
                        List.of(),
                        List.of()
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void compose__colorForm(ColorFormParameters parameters) {
        // ACT //
        String art = Banner.compose(BRAND_BIG_WIDTH, parameters.mode(), null);

        // ASSERT //
        for (String expected : parameters.mustContain()) {
            assertTrue(art.contains(expected), parameters.name() + " should contain \"" + expected + "\"");
        }
        for (String unexpected : parameters.mustNotContain()) {
            assertFalse(art.contains(unexpected), parameters.name() + " should not contain \"" + unexpected + "\"");
        }
        for (String pattern : parameters.mustMatch()) {
            assertTrue(
                    Pattern.compile(pattern).matcher(art).find(),
                    parameters.name() + " should match pattern \"" + pattern + "\""
            );
        }
    }

    static Stream<MarkRowParameters> markSegments() {
        return Stream.of(
                new MarkRowParameters(
                        "plain_glyphs",
                        "#+-.: ",
                        List.of(
                                new Segment(ColorRole.PIN, "#"),
                                new Segment(ColorRole.GLOBE, "+"),
                                new Segment(ColorRole.PIN, "-.:"),
                                new Segment(ColorRole.NONE, " ")
                        )
                ),
                new MarkRowParameters("equals_after_pin_is_pin", "#=", List.of(new Segment(ColorRole.PIN, "#="))),
                new MarkRowParameters("equals_after_globe_is_globe", "+=", List.of(new Segment(ColorRole.GLOBE, "+="))),
                new MarkRowParameters(
                        "equals_after_space_is_globe",
                        " =",
                        List.of(new Segment(ColorRole.NONE, " "), new Segment(ColorRole.GLOBE, "="))
                ),
                new MarkRowParameters("equals_chain_stays_pin", "#==", List.of(new Segment(ColorRole.PIN, "#=="))),
                new MarkRowParameters("equals_at_line_start_is_globe", "=", List.of(new Segment(ColorRole.GLOBE, "="))),
                new MarkRowParameters("plus_between_hashes_is_pin", "#+#", List.of(new Segment(ColorRole.PIN, "#+#"))),
                new MarkRowParameters(
                        "equals_between_hashes_is_pin",
                        "#=#",
                        List.of(new Segment(ColorRole.PIN, "#=#"))
                ),
                new MarkRowParameters(
                        "plus_left_of_left_hash_is_globe",
                        "+#",
                        List.of(new Segment(ColorRole.GLOBE, "+"), new Segment(ColorRole.PIN, "#"))
                ),
                new MarkRowParameters(
                        "plus_right_of_right_hash_is_globe",
                        "#+",
                        List.of(new Segment(ColorRole.PIN, "#"), new Segment(ColorRole.GLOBE, "+"))
                ),
                new MarkRowParameters("star_is_pin", "*", List.of(new Segment(ColorRole.PIN, "*"))),
                new MarkRowParameters("equals_after_star_is_pin", "*=", List.of(new Segment(ColorRole.PIN, "*="))),
                new MarkRowParameters("unknown_glyph_is_unstyled", "?", List.of(new Segment(ColorRole.NONE, "?")))
        );
    }

    @MethodSource
    @ParameterizedTest
    void markSegments(MarkRowParameters parameters) {
        // ACT & ASSERT //
        assertEquals(parameters.expectedSegments(), Banner.markSegments(parameters.row()), parameters.name());
    }

    static Stream<OfParameters> of__validation() {
        List<String> bigArt = List.of("Xx");
        List<String> smallArt = List.of("x");
        return Stream.of(
                new OfParameters("five_digit_accent_rejected", bigArt, smallArt, "#0C223", false),
                new OfParameters("seven_digit_accent_rejected", bigArt, smallArt, "#0C22388", false),
                new OfParameters("non_hex_accent_rejected", bigArt, smallArt, "#GGGGGG", false),
                new OfParameters("empty_accent_rejected", bigArt, smallArt, "", false),
                new OfParameters(
                        "big_art_narrower_than_small_art_rejected",
                        List.of("XXXXX"),
                        List.of("XXXXXXXXXX"),
                        "#0C2238",
                        false
                ),
                new OfParameters("hash_form_accepted", bigArt, smallArt, "#0C2238", true),
                new OfParameters("bare_form_accepted", bigArt, smallArt, "0C2238", true),
                new OfParameters("lowercase_form_accepted", bigArt, smallArt, "#0c2238", true)
        );
    }

    @MethodSource
    @ParameterizedTest
    void of__validation(OfParameters parameters) {
        if (!parameters.accepted()) {
            // ACT & ASSERT //
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Banner.Product.of(parameters.bigArt(), parameters.smallArt(), parameters.accentHex()),
                    parameters.name()
            );
            return;
        }

        // ACT //
        Banner.Product product = Banner.Product.of(parameters.bigArt(), parameters.smallArt(), parameters.accentHex());
        // The exact both-big threshold for this inline 2-wide fixture.
        String art = Banner.compose(BRAND_BIG_WIDTH + GUTTER_WIDTH + 2, ColorMode.TRUECOLOR, product);

        // ASSERT //
        assertTrue(
                art.contains(PRODUCT_ACCENT_SGR),
                parameters.name() + " should parse " + parameters.accentHex() + " to the product accent color"
        );
    }

    static Stream<TerminalWidthParameters> terminalWidth__fallbackLadder() {
        return Stream.of(
                new TerminalWidthParameters("reported_wins_without_columns", 132, null, 132),
                new TerminalWidthParameters("reported_wins_over_columns", 132, "200", 132),
                new TerminalWidthParameters("zero_reported_falls_through_to_columns", 0, "200", 200),
                new TerminalWidthParameters("negative_reported_falls_through_to_columns", -1, "200", 200),
                new TerminalWidthParameters("columns_trimmed", 0, "  200  ", 200),
                new TerminalWidthParameters("columns_zero_falls_through_to_default", 0, "0", 80),
                new TerminalWidthParameters("columns_negative_falls_through_to_default", 0, "-5", 80),
                new TerminalWidthParameters("columns_unparseable_falls_through_to_default", 0, "abc", 80),
                new TerminalWidthParameters("columns_empty_falls_through_to_default", 0, "", 80),
                new TerminalWidthParameters("columns_blank_falls_through_to_default", 0, "   ", 80),
                new TerminalWidthParameters("columns_int_overflow_falls_through_to_default", 0, "99999999999", 80),
                new TerminalWidthParameters("columns_null_falls_through_to_default", 0, null, 80)
        );
    }

    @MethodSource
    @ParameterizedTest
    void terminalWidth__fallbackLadder(TerminalWidthParameters parameters) {
        // ACT //
        int width = Banner.terminalWidth(parameters.reportedColumns(), parameters.columnsEnvironment());

        // ASSERT //
        assertEquals(parameters.expected(), width, parameters.name());
    }

    @Test
    void compose__markCentersOnWordmark() {
        // ACT //
        String art = Banner.compose(BRAND_BIG_WIDTH, ColorMode.MONOCHROME, null);

        // ASSERT //
        List<String> markRows = List.of(art.split("\n", -1)).subList(0, MARK_HEIGHT);
        int markLeft = markRows.stream().mapToInt(BannerTest::leadingSpaces).min().orElseThrow();
        int blockWidth = widestLine(art);

        assertEquals((blockWidth - MARK_WIDTH) / 2, markLeft, "the mark is centered on the wordmark block");
    }

    @Test
    void compose__productSeamShowsBrandAndProductColors() {
        // ARRANGE //
        Banner.Product product = Banner.Product.of(List.of("Xx"), List.of("x"), "#0C2238");

        // ACT //
        // The exact both-big threshold for this inline 2-wide fixture.
        String art = Banner.compose(BRAND_BIG_WIDTH + GUTTER_WIDTH + 2, ColorMode.TRUECOLOR, product);

        // ASSERT //
        assertTrue(art.contains(BRAND_SGR), "the CoordinateKit word should carry the brand color");
        assertTrue(art.contains(PRODUCT_ACCENT_SGR), "the product word should carry the product accent color");
    }

    @Test
    void compose__truecolorColorsMarkAndText() {
        // ACT //
        // BRAND_BIG_WIDTH keeps the brand-only banner on the big rung so BRAND_SGR is asserted at the
        // width it is actually emitted.
        String art = Banner.compose(BRAND_BIG_WIDTH, ColorMode.TRUECOLOR, null);

        // ASSERT //
        assertTrue(art.contains(PIN_SGR), "mark pin glyphs should carry the lightened pin color");
        assertTrue(art.contains(GLOBE_SGR), "mark globe glyphs should carry the globe color");
        assertTrue(art.contains(BRAND_SGR), "the CoordinateKit word should carry the brand color");

        Set<String> knownCodes = Set.of(PIN_SGR, GLOBE_SGR, BRAND_SGR);
        Set<String> found = Pattern.compile("38;2;\\d+;\\d+;\\d+")
                .matcher(art)
                .results()
                .map(MatchResult::group)
                .collect(Collectors.toSet());
        assertTrue(knownCodes.containsAll(found), "no product accent leaks into the brand-only banner: found " + found);
    }

    @Test
    void compose__wordBlockRectangular() {
        // ARRANGE //
        Banner.Product product = Banner.Product.of(List.of("XX", "X"), List.of("X"), "#0C2238");

        // ACT //
        // The exact both-big threshold for this inline 2-wide fixture; the brand column is taller than
        // the product's 2 rows (the blank-fill path) and row 1's "X" is narrower than the column (the
        // rightPad path).
        String art = Banner.compose(BRAND_BIG_WIDTH + GUTTER_WIDTH + 2, ColorMode.MONOCHROME, product);

        // ASSERT //
        List<String> lines = List.of(art.split("\n", -1));
        List<String> textRows = lines.subList(MARK_HEIGHT, lines.size() - 1);
        int expectedWidth = textRows.getFirst().length();
        for (String row : textRows) {
            assertEquals(expectedWidth, row.length(), "every text row should be the same width: \"" + row + "\"");
        }
    }

    /**
     * The single deliberate pin on the art: every other width expectation in this file is measured off
     * the resources, so a redrawn wordmark breaks nothing here, and an accidental change to
     * {@code mark.txt} fails exactly this test.
     */
    @Test
    void mark__widthPinned() {
        // ASSERT //
        assertEquals(33, MARK_WIDTH, "mark.txt should be 33 columns wide; a deliberate redraw updates this pin");
    }

    /**
     * The only test in the file that builds a <em>system</em> terminal. JLine tracks the system
     * terminal process-wide, so a second build-and-close cycle elsewhere in this JVM would be a
     * flakiness source with no added coverage; every other test drives {@code render}'s pure delegate,
     * {@link #renderArt__appendsBlankLineAfterArt renderArt}, or the fully pure decider functions
     * directly.
     */
    @Test
    void render__producesArtOrNothing() {
        // ARRANGE //
        Banner banner = new Banner();

        // ACT //
        String rendered = banner.render(false);

        // ASSERT //
        assertTrue(
                rendered.isEmpty() || rendered.endsWith("\n\n"),
                "render should be empty or end with a blank line, at any width or color mode"
        );
        assertFalse(rendered.endsWith("\n\n\n"), "render should never double the trailing blank line");
        assertFalse(rendered.contains(ESCAPE), "ansiEnabled=false should reach render and suppress every ANSI byte");
    }

    @Test
    void renderArt__appendsBlankLineAfterArt() {
        // ACT //
        String art = new Banner().renderArt(BRAND_BIG_WIDTH, ColorMode.MONOCHROME);

        // ASSERT //
        assertTrue(art.endsWith("\n\n"), "renderArt should append a blank line after the art");
        assertFalse(art.endsWith("\n\n\n"), "renderArt should append exactly one blank line");
    }

    @Test
    void renderArt__belowFloorStaysEmpty() {
        // ACT //
        String art = new Banner().renderArt(MARK_WIDTH - 1, ColorMode.MONOCHROME);

        // ASSERT //
        assertEquals("", art, "below the mark-only floor renderArt should render nothing, not just a blank line");
    }

    @Test
    void renderArt__usesProduct() {
        // ARRANGE //
        Banner.Product product = Banner.Product.of(List.of("Xx"), List.of("x"), "#0C2238");
        Banner banner = new Banner(product);

        // ACT //
        // The exact both-big threshold for this inline 2-wide fixture.
        String art = banner.renderArt(BRAND_BIG_WIDTH + GUTTER_WIDTH + 2, ColorMode.TRUECOLOR);

        // ASSERT //
        assertTrue(
                art.contains(PRODUCT_ACCENT_SGR),
                "renderArt should drive the product seam through the instance's own product"
        );
    }

    /**
     * The one real-terminal test in the file. Everything else drives
     * {@link Banner#colorMode(boolean, String, Integer, String)} and
     * {@link Banner#terminalWidth(int, String)} as pure functions, which cannot exercise the live
     * {@code getSize()} round trip; this is the only assertion that runs it against the JLine version
     * this module depends on. {@code system(false)} means it never touches the process tty.
     */
    @Test
    void terminalWidth__readsColumnsFromTerminalSize() throws Exception {
        // ARRANGE // ACT //
        // org.jline.terminal.Size (imported above) is unrelated to the nested Banner.Size enum; this
        // file does not import Banner.Size, so the two names do not collide today.
        try (Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .streams(InputStream.nullInputStream(), OutputStream.nullOutputStream())
                .type("xterm")
                .build()) {
            terminal.setSize(Size.of(132, 24));

            // ASSERT //
            assertEquals(
                    132,
                    Banner.terminalWidth(terminal),
                    "terminalWidth should read columns off Terminal#getSize()"
            );
        }
    }

    /**
     * Measures the height of a banner art resource, dropping trailing blank lines the same way
     * {@code Banner.load} does, so the two agree on height.
     *
     * @param resource the classpath resource path, resolved against {@link Banner}'s package
     * @return the number of lines in the resource, excluding trailing blank lines
     */
    private static int artHeight(String resource) {
        try (InputStream in = Banner.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, "missing banner resource: " + resource);
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
            while (!lines.isEmpty() && lines.getLast().isBlank()) {
                lines.removeLast();
            }
            return lines.size();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read banner resource: " + resource, exception);
        }
    }

    /**
     * Measures the widest line of a banner art resource, independently of {@link Banner}'s own loader:
     * a bug in {@code Banner.load} cannot silently feed the expectations meant to catch it. Reads with
     * a plain reader rather than {@code Banner.load}'s trimming of trailing blank lines, which cannot
     * affect the max line width anyway, so the two agree.
     *
     * @param resource the classpath resource path, resolved against {@link Banner}'s package
     * @return the width of the resource's widest line
     */
    private static int artWidth(String resource) {
        try (InputStream in = Banner.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, "missing banner resource: " + resource);
            return widestLine(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read banner resource: " + resource, exception);
        }
    }

    /** Returns the count of leading space characters in {@code line}. */
    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    /**
     * Returns the truecolor SGR parameter string for {@code role}'s own RGB, so a palette retune in
     * {@link ColorRole} cannot break the structural assertions built on {@link #BRAND_SGR},
     * {@link #PIN_SGR}, and {@link #GLOBE_SGR}.
     *
     * @param role the color role to render
     * @return the {@code "38;2;R;G;B"} SGR parameter string for {@code role}
     */
    private static String truecolorSgr(ColorRole role) {
        return "38;2;" + role.red + ";" + role.green + ";" + role.blue;
    }

    /** Returns the length of the longest line in a rendered (monochrome) art block. */
    private static int widestLine(String art) {
        int widest = 0;
        for (String line : art.split("\n", -1)) {
            widest = Math.max(widest, line.length());
        }
        return widest;
    }

    record ColorFormParameters(
            String name,
            ColorMode mode,
            List<String> mustContain,
            List<String> mustNotContain,
            List<String> mustMatch
    ) {}

    record ColorModeParameters(
            String name,
            boolean ansiEnabled,
            @Nullable String type,
            @Nullable Integer maxColors,
            @Nullable String colorterm,
            ColorMode expected
    ) {}

    record ComposeSweepParameters(String name, Banner.@Nullable Product product, int sweepCeiling) {}

    record ComposeWidthParameters(String name, int width, int expectedWidth, boolean expectMark) {}

    record MarkRowParameters(String name, String row, List<Segment> expectedSegments) {}

    record OfParameters(String name, List<String> bigArt, List<String> smallArt, String accentHex, boolean accepted) {}

    record ProductWidthParameters(
            String name,
            int width,
            int expectedWidth,
            boolean expectMark,
            boolean expectProduct
    ) {}

    record TerminalWidthParameters(
            String name,
            int reportedColumns,
            @Nullable String columnsEnvironment,
            int expected
    ) {}
}

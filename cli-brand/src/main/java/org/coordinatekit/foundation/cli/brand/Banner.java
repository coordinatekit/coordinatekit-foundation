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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedCharSequence;
import org.jline.utils.AttributedCharSequence.ForceMode;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Colors;
import org.jline.utils.InfoCmp;

/**
 * Renders the CoordinateKit brand art — the globe "mark" stacked above a figlet-style word banner —
 * as a string for the consumer to print wherever its tool announces itself. The consumer calls
 * {@link #render} with its own ANSI decision; nothing here assumes a CLI framework or a particular
 * place in the output.
 *
 * <p>
 * The class is written to be shared across CoordinateKit command-line tools: the globe mark, the
 * "CoordinateKit" wordmark, the layout/color engine, and the brand colors are fixed. A tool renders
 * in one of two ways. When product and brand differ, it supplies a per-tool <em>product</em>
 * wordmark and accent color through a {@link Product} passed to the constructor, and the banner
 * shows "CoordinateKit &lt;product&gt;". When product equals brand, it uses the no-argument
 * constructor and the banner shows the "CoordinateKit" wordmark alone. Nothing here names a
 * specific tool; the owning launcher builds its {@link Product} and only the product's wordmark art
 * differs between tools.
 *
 * <p>
 * The art adapts to the terminal in two independent ways. <strong>Width</strong> selects a layout:
 * the full mark plus the wordmark(s) when there is room, dropping the wordmark(s) as the window
 * narrows, down to the mark alone, then nothing below the smallest legible size (see
 * {@link #compose}). Each layout's threshold is derived from the art it renders, so any product
 * wordmark width adapts without hand-tuned numbers. <strong>Color capability</strong> selects a
 * {@link ColorMode}: 24-bit truecolor, 256-color, or 16-color when attached to a capable terminal,
 * and uncolored plain text otherwise — including whenever output is piped or redirected, so a
 * captured stream never carries stray ANSI bytes. The caller's ANSI decision is weighed first and
 * can only turn color off: a {@code NO_COLOR} environment variable, {@code CLICOLOR=0}, or a
 * consumer's own {@code --ansi} wiring wins over every terminal-capability signal below it.
 */
@NullMarked
public final class Banner {
    /**
     * Color capability tiers, used to pick how RGB is emitted. The constants sit in alphabetical order
     * and nothing reads it: {@link #colorMode} selects a tier outright, and {@link #renderLine} and
     * {@link #styleFor} match on the name. The truecolor → 256 → 16 → mono ladder lives in
     * {@link #colorMode}, not in this ordering.
     */
    enum ColorMode {
        C16, C256, MONOCHROME, TRUECOLOR
    }

    /**
     * The brand colors a cell can take. RGB values drive truecolor and are rounded down for the 256-
     * and 16-color modes. Constants name what the text <em>is</em> rather than its hue, so the same
     * role keeps its meaning even where a sibling tool paints it a different color.
     */
    enum ColorRole {
        /** The "CoordinateKit" wordmark color. */
        BRAND(252, 142, 45),
        /** The globe fill woven through the mark. */
        GLOBE(74, 163, 68),
        /** No styling; used for spaces and padding. */
        NONE(-1, -1, -1),
        /**
         * The map-pin color. The logo's pin {@code #2F3C49} is near-black and washes out on dark terminals,
         * so it is lightened to a blue-gray that reads on both dark and light backgrounds.
         */
        PIN(107, 126, 145),
        /**
         * The product wordmark color. Unlike the other roles its RGB is not fixed here: it is supplied by
         * the active {@link Product}, so each tool paints its own name in its own accent. The placeholder
         * components below are never read — {@link #styleFor} resolves this role from the product instead.
         */
        PRODUCT(-1, -1, -1);

        final int blue;
        final int green;
        final int red;

        ColorRole(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    /**
     * One wordmark standing in the composed word banner: its art and the role it is painted in. A
     * layout contributes the brand column, the product column, both, or neither, and {@link #wordRows}
     * joins whatever it is given side by side. Expressing the banner as a list of columns is what lets
     * the brand-only and product renderings share a single code path.
     */
    record Column(List<String> art, ColorRole role) {}

    /**
     * A choice of what to render at a given width: whether to show the mark, which size of the brand
     * wordmark to show (or {@code null} to drop it), and which size of the product wordmark to show (or
     * {@code null} to drop it). A rung's product size is also dropped when the banner has no product at
     * all, which is what collapses {@link #LAYOUTS} onto the brand-only ladder. See {@link #LAYOUTS}
     * and {@link #layout}.
     */
    record Layout(boolean mark, @Nullable Size brand, @Nullable Size product) {}

    /**
     * A tool's product identity: the wordmark art in two sizes plus the accent color its name is
     * painted in. Public so a launcher in any package can build one; the implementation is a private
     * record, and {@link #of} is the factory.
     */
    public interface Product {
        /**
         * Builds a product from in-memory wordmark art and an accent color.
         *
         * @param bigArt the large wordmark art, one string per row
         * @param smallArt the small wordmark art, one string per row
         * @param accentHex the accent color as {@code #RRGGBB} or {@code RRGGBB} hex; the leading {@code #}
         *        is optional
         * @return the product backed by the given art and accent
         * @throws IllegalArgumentException if {@code accentHex} is not a six-digit hex color, or if
         *         {@code bigArt} is narrower than {@code smallArt}
         */
        static Product of(List<String> bigArt, List<String> smallArt, String accentHex) {
            Objects.requireNonNull(bigArt, "bigArt must not be null");
            Objects.requireNonNull(smallArt, "smallArt must not be null");
            Objects.requireNonNull(accentHex, "accentHex must not be null");
            String digits = accentHex.startsWith("#") ? accentHex.substring(1) : accentHex;
            if (!digits.matches("[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("accent must be a #RRGGBB or RRGGBB hex color, got: " + accentHex);
            }
            if (Banner.textWidth(bigArt) < Banner.textWidth(smallArt)) {
                throw new IllegalArgumentException("bigArt must be at least as wide as smallArt");
            }
            int rgb = Integer.parseInt(digits, 16);
            return new ProductImplementation(
                    List.copyOf(bigArt),
                    List.copyOf(smallArt),
                    (rgb >> 16) & 0xFF,
                    (rgb >> 8) & 0xFF,
                    rgb & 0xFF
            );
        }

        /**
         * The blue component of the accent color, 0-255.
         *
         * @return the blue component of the accent color
         */
        int accentBlue();

        /**
         * The green component of the accent color, 0-255.
         *
         * @return the green component of the accent color
         */
        int accentGreen();

        /**
         * The red component of the accent color, 0-255.
         *
         * @return the red component of the accent color
         */
        int accentRed();

        /**
         * The large wordmark art, one string per row.
         *
         * @return the large wordmark art
         */
        List<String> bigArt();

        /**
         * The small wordmark art, one string per row.
         *
         * @return the small wordmark art
         */
        List<String> smallArt();
    }

    /** A run of contiguous characters sharing one {@link ColorRole}. */
    record Segment(ColorRole role, String text) {}

    /** The two sizes each wordmark is cut in; {@link Layout} selects between them. */
    enum Size {
        BIG, SMALL
    }

    /** The default brand colors live on {@link ColorRole}; only the product accent varies. */
    private record ProductImplementation(
            List<String> bigArt,
            List<String> smallArt,
            int accentRed,
            int accentGreen,
            int accentBlue
    ) implements Product {}

    private static final List<String> COORDINATEKIT_BIG = load("banner/coordinatekit-big.txt");
    private static final List<String> COORDINATEKIT_SMALL = load("banner/coordinatekit-small.txt");

    /**
     * The width ladder, ordered richest to leanest. {@link #compose} renders the first layout whose own
     * width fits the terminal, so order is significant: the entries descend in visual completeness,
     * which for any plausible wordmark also descends in width.
     *
     * <p>
     * Every rung shows the mark, so it is never dropped ahead of anything else: a product banner
     * descends from both wordmarks at full size, through both at small size, to the brand wordmark
     * dropped and the product shown big then small, down to the mark alone — the floor, chosen over
     * going blank the moment the product wordmark no longer fits. One ladder serves both rendering
     * paths: a brand-only banner drops every product column, which collapses the three product-only
     * rungs onto the same mark-alone floor, so it folds the five rungs down to three — mark plus the
     * big wordmark, mark plus the small wordmark, then the mark alone.
     *
     * <p>
     * The ordering assumes each rung is no wider than the one above it, which holds given the fixed
     * brand art and {@link Product#of} enforcing that a product's big art is at least as wide as its
     * small art. One case still escapes that check: a product whose big art is wider than the brand's
     * small wordmark plus its own small art (about 56 columns wider than its own small art) makes the
     * mark-plus-product-big rung unreachable, because the richer mark-plus-both-small rung above it
     * already fits at that width. Output is still never wider than the terminal — the effect is a
     * skipped rung, not a broken layout.
     */
    private static final List<Layout> LAYOUTS = List.of(
            new Layout(true, Size.BIG, Size.BIG),
            new Layout(true, Size.SMALL, Size.SMALL),
            new Layout(true, null, Size.BIG),
            new Layout(true, null, Size.SMALL),
            new Layout(true, null, null)
    );

    private static final List<String> MARK = load("banner/mark.txt");

    /** Derived from the art so the centering axis cannot drift when the mark is redrawn. */
    private static final int MARK_WIDTH = textWidth(MARK);

    private final @Nullable Product product;

    /**
     * Creates a brand-only banner: the mark above the "CoordinateKit" wordmark, with no product name.
     * Used where product equals brand, as it does for the {@code coordinatekit} tool.
     */
    public Banner() {
        this.product = null;
    }

    /**
     * Creates a banner for {@code product}. The mark and "CoordinateKit" wordmark are fixed; the
     * product supplies its own wordmark art and accent color.
     *
     * @param product the per-tool wordmark and accent color
     */
    public Banner(Product product) {
        this.product = Objects.requireNonNull(product, "product must not be null");
    }

    /**
     * Renders the art for a given terminal width, color mode, and product. A {@code null} product
     * selects the brand-only path (the "CoordinateKit" wordmark alone, dropping to the mark alone as
     * the window narrows); otherwise the product wordmark is shown beside the brand. The returned block
     * ends with a trailing newline after its last line, or is empty when {@code width} is below the
     * smallest layout.
     *
     * @param width the terminal width in columns
     * @param mode the color capability to emit for
     * @param product the product wordmark and accent color to render, or {@code null} for brand-only
     * @return the rendered art block, or the empty string when nothing fits
     */
    static String compose(int width, ColorMode mode, @Nullable Product product) {
        Layout layout = layout(width, product);
        if (layout == null) {
            return "";
        }

        List<List<Segment>> textRows = wordRows(columns(layout, product));

        int textWidth = blockWidth(textRows);
        int markWidth = layout.mark() ? MARK_WIDTH : 0;
        int blockWidth = Math.max(markWidth, textWidth);
        int markPadding = (blockWidth - markWidth) / 2;
        int textPadding = (blockWidth - textWidth) / 2;

        List<String> lines = new ArrayList<>();
        if (layout.mark()) {
            for (String markRow : MARK) {
                lines.add(renderLine(markPadding, markSegments(markRow), mode, product));
            }
        }
        for (List<Segment> textRow : textRows) {
            lines.add(renderLine(textPadding, textRow, mode, product));
        }
        return String.join("\n", lines) + "\n";
    }

    /**
     * Renders the brand art for the attached terminal. Probes the terminal lazily, at render time
     * rather than construction, so a run that never prints the banner pays no probe cost, then
     * delegates to {@link #renderArt}, which owns the blank line that separates the art from whatever
     * the consumer prints next.
     *
     * @param ansiEnabled the caller-owned color-off policy; the caller is expected to fold in
     *        {@code NO_COLOR}, {@code CLICOLOR}/{@code CLICOLOR_FORCE}, and its own {@code --ansi}
     *        wiring
     * @return the rendered banner, or the empty string when no art fits
     */
    public String render(boolean ansiEnabled) {
        int width;
        ColorMode mode;
        try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build()) {
            width = terminalWidth(terminal);
            mode = colorMode(terminal, ansiEnabled);
        } catch (IOException exception) {
            // A terminal we cannot probe degrades to a safe, uncolored default rather than failing.
            width = 80;
            mode = ColorMode.MONOCHROME;
        }
        return renderArt(width, mode);
    }

    /** Returns the width of the widest row in a block of colored rows. */
    private static int blockWidth(List<List<Segment>> rows) {
        int width = 0;
        for (List<Segment> row : rows) {
            int rowWidth = 0;
            for (Segment segment : row) {
                rowWidth += segment.text().length();
            }
            width = Math.max(width, rowWidth);
        }
        return width;
    }

    /**
     * Decides the color capability tier from already-gathered terminal facts, following the truecolor →
     * 256 → 16 → mono ladder.
     *
     * @param ansiEnabled the caller-supplied ANSI decision; already weighs {@code NO_COLOR},
     *        {@code CLICOLOR}/{@code CLICOLOR_FORCE}, and the caller's own {@code --ansi} wiring, so a
     *        {@code false} here outranks every other signal below
     * @param type the terminal type, or {@code null} when {@link Terminal#getType()} left it
     *        unspecified
     * @param maxColors the terminal's {@code max_colors} numeric capability, or {@code null} when the
     *        terminal does not report one
     * @param colorterm the {@code COLORTERM} environment variable, or {@code null} when unset
     * @return the color mode to render in
     */
    static ColorMode colorMode(
            boolean ansiEnabled,
            @Nullable String type,
            @Nullable Integer maxColors,
            @Nullable String colorterm
    ) {
        if (!ansiEnabled) {
            // The caller has already weighed NO_COLOR, CLICOLOR*, and its own --ansi wiring.
            return ColorMode.MONOCHROME;
        }
        if (Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type)) {
            // Not a real TTY (piped, redirected, dumb): never emit ANSI.
            return ColorMode.MONOCHROME;
        }
        // JLine's toAnsi(Terminal) caps at 256 and ignores COLORTERM, so truecolor is detected here.
        if (colorterm != null) {
            String normalized = colorterm.toLowerCase(Locale.ROOT);
            if (normalized.contains("truecolor") || normalized.contains("24bit")) {
                return ColorMode.TRUECOLOR;
            }
        }
        int colors = maxColors == null ? 0 : maxColors;
        if (colors >= 256) {
            return ColorMode.C256;
        }
        if (colors >= 8) {
            return ColorMode.C16;
        }
        return ColorMode.MONOCHROME;
    }

    /**
     * Gathers the terminal facts {@link #colorMode(boolean, String, Integer, String)} needs and defers
     * the decision to it, alongside {@code ansiEnabled} supplied by the caller. Stays private: unlike
     * {@link #terminalWidth(Terminal)}'s {@code getSize()} round trip, nothing here is stably testable
     * — a live terminal with {@code COLORTERM=truecolor} set reports truecolor {@code max_colors} for
     * every type, including {@code dumb}, so there is no environment-independent expectation to pin.
     */
    private static ColorMode colorMode(Terminal terminal, boolean ansiEnabled) {
        return colorMode(
                ansiEnabled,
                terminal.getType(),
                terminal.getNumericCapability(InfoCmp.Capability.max_colors),
                System.getenv("COLORTERM")
        );
    }

    /**
     * Resolves {@code layout} into the wordmark columns it renders, left to right: the brand wordmark
     * when the rung keeps one, then the product wordmark when the rung keeps one <em>and</em> the
     * banner has a product. A brand-only banner therefore yields at most the brand column, and the
     * rungs that carry only a product size yield none at all.
     */
    private static List<Column> columns(Layout layout, @Nullable Product product) {
        List<Column> columns = new ArrayList<>();
        if (layout.brand() != null) {
            List<String> brandArt = layout.brand() == Size.BIG ? COORDINATEKIT_BIG : COORDINATEKIT_SMALL;
            columns.add(new Column(brandArt, ColorRole.BRAND));
        }
        if (product != null && layout.product() != null) {
            List<String> productArt = layout.product() == Size.BIG ? product.bigArt() : product.smallArt();
            columns.add(new Column(productArt, ColorRole.PRODUCT));
        }
        return List.copyOf(columns);
    }

    /**
     * Returns the width the columns occupy side by side, counting the one-space gutter between them.
     */
    private static int columnsWidth(List<Column> columns) {
        int width = 0;
        for (Column column : columns) {
            if (width > 0) {
                width++;
            }
            width += textWidth(column.art());
        }
        return width;
    }

    /**
     * Selects the layout for {@code width} as the richest one whose rendered width fits. Returns
     * {@code null} when even the leanest rung — the mark alone — is wider than the terminal, in which
     * case no art is shown. Walks {@link #LAYOUTS} richest-first and takes the first that fits.
     */
    private static @Nullable Layout layout(int width, @Nullable Product product) {
        for (Layout candidate : LAYOUTS) {
            if (width >= layoutWidth(candidate, product)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the column width {@code layout} renders to for {@code product}: the mark width (when
     * shown) against the word block of whichever wordmark columns survive. This is exactly the width
     * {@link #compose} lays out, so a layout is selectable precisely when the terminal is at least this
     * wide.
     */
    private static int layoutWidth(Layout layout, @Nullable Product product) {
        int markWidth = layout.mark() ? MARK_WIDTH : 0;
        return Math.max(markWidth, columnsWidth(columns(layout, product)));
    }

    /**
     * Loads a brand-art resource into its lines, dropping trailing blank lines so the renderer alone
     * controls vertical spacing. Resolves {@code resource} against {@link Banner}'s package. Fails
     * loudly if the resource is missing from the jar.
     */
    private static List<String> load(String resource) {
        try (InputStream in = Banner.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, "missing banner resource: " + resource);
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
            while (!lines.isEmpty() && lines.getLast().isBlank()) {
                lines.removeLast();
            }
            return List.copyOf(lines);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read banner resource: " + resource, exception);
        }
    }

    /**
     * Colors one row of the mark, reproducing the glyph rule from the source {@code mark.sh}: the pin
     * glyphs {@code # * - . :} and a context-dependent {@code =} are pin-colored, {@code +} is globe,
     * and spaces are unstyled. The shared {@code =} glyph is pin only when its immediate left neighbor
     * is itself a pin glyph (the pin set includes {@code =}), otherwise globe.
     *
     * <p>
     * A position rule then overrides color for the globe interior: any {@code +} or {@code =} that sits
     * strictly between the row's left-most and right-most {@code #} is pin-colored rather than globe,
     * so the sprout glyphs woven through the globe read as part of the mark. Glyphs outside that span —
     * the sprout proper to the right, and any stem to the left — keep their globe color.
     *
     * <p>
     * Adjacent cells of the same role are merged into a single segment. Package-private so the glyph
     * rule can be unit-tested directly.
     */
    static List<Segment> markSegments(String row) {
        int leftmostHash = row.indexOf('#');
        int rightmostHash = row.lastIndexOf('#');
        List<Segment> segments = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        ColorRole runRole = null;
        for (int i = 0; i < row.length(); i++) {
            char cell = row.charAt(i);
            boolean betweenHashes = i > leftmostHash && i < rightmostHash;
            ColorRole role;
            if (cell == ' ') {
                role = ColorRole.NONE;
            } else if (cell == '+') {
                role = betweenHashes ? ColorRole.PIN : ColorRole.GLOBE;
            } else if ("#*-.:".indexOf(cell) >= 0) {
                role = ColorRole.PIN;
            } else if (cell == '=') {
                char left = i > 0 ? row.charAt(i - 1) : ' ';
                role = betweenHashes || "#*-.:=".indexOf(left) >= 0 ? ColorRole.PIN : ColorRole.GLOBE;
            } else {
                role = ColorRole.NONE;
            }
            if (role != runRole) {
                if (runRole != null) {
                    segments.add(new Segment(runRole, run.toString()));
                }
                run.setLength(0);
                runRole = role;
            }
            run.append(cell);
        }
        if (runRole != null) {
            segments.add(new Segment(runRole, run.toString()));
        }
        return segments;
    }

    /**
     * Composes the art for {@code width} and {@code mode} against this banner's product, and appends
     * the trailing blank line that separates it from whatever the consumer prints next. The only method
     * that reads {@link #product}, so it is the delegation target both {@link #render} and the deferred
     * terminal-injection seam call into.
     *
     * @param width the terminal width in columns
     * @param mode the color capability to emit for
     * @return the rendered art block followed by a blank line, or the empty string when nothing fits
     */
    String renderArt(int width, ColorMode mode) {
        String art = compose(width, mode, product);
        return art.isEmpty() ? "" : art + "\n";
    }

    /**
     * Renders one laid-out line: {@code padding} leading spaces followed by the colored segments,
     * emitted for {@code mode}. Monochrome returns the raw characters with no ANSI. Each colored mode
     * passes its own color count, which is what settles the SGR form; truecolor and 256 pass the
     * matching {@code ForceMode} on top so neither is emitted in a leaner encoding than its name
     * promises. Sixteen-color passes {@code ForceMode.None} because a count of 16 admits only the plain
     * SGR form. The {@code product} is {@code null} on the brand-only path, whose rows never carry a
     * {@code PRODUCT} segment, so {@link #styleFor} never dereferences it.
     */
    private static String renderLine(int padding, List<Segment> segments, ColorMode mode, @Nullable Product product) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        builder.append(" ".repeat(padding));
        for (Segment segment : segments) {
            builder.style(styleFor(mode, segment.role(), product));
            builder.append(segment.text());
        }
        AttributedString line = builder.toAttributedString();
        return switch (mode) {
            case TRUECOLOR -> line.toAnsi(AttributedCharSequence.TRUE_COLORS, ForceMode.ForceTrueColors);
            case C256 -> line.toAnsi(256, ForceMode.Force256Colors);
            case C16 -> line.toAnsi(16, ForceMode.None);
            case MONOCHROME -> line.toString();
        };
    }

    /** Right-pads {@code text} with spaces to {@code width}; returns it unchanged if already wider. */
    private static String rightPad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    /**
     * Maps a color role to a JLine style for the mode: RGB for truecolor/256, a rounded index for 16.
     * The {@link ColorRole#PRODUCT} role resolves to {@code product}'s accent; every other role carries
     * its own fixed RGB. Only a product banner ever emits a {@code PRODUCT} segment, so {@code product}
     * is required to be non-null exactly when that role is read.
     */
    private static AttributedStyle styleFor(ColorMode mode, ColorRole role, @Nullable Product product) {
        if (role == ColorRole.NONE) {
            return AttributedStyle.DEFAULT;
        }
        int blue;
        int green;
        int red;
        if (role == ColorRole.PRODUCT) {
            Product resolvedProduct = Objects.requireNonNull(product, "product must not be null for a PRODUCT segment");
            blue = resolvedProduct.accentBlue();
            green = resolvedProduct.accentGreen();
            red = resolvedProduct.accentRed();
        } else {
            blue = role.blue;
            green = role.green;
            red = role.red;
        }
        return switch (mode) {
            case TRUECOLOR, C256 -> AttributedStyle.DEFAULT.foreground(red, green, blue);
            case C16 -> AttributedStyle.DEFAULT.foreground(Colors.roundRgbColor(red, green, blue, 16));
            case MONOCHROME -> AttributedStyle.DEFAULT;
        };
    }

    /**
     * Decides the terminal width from an already-gathered reported column count, falling back to
     * {@code columnsEnvironment} then 80 when the report is unknown.
     *
     * @param reportedColumns the terminal's reported column count; non-positive means unknown
     * @param columnsEnvironment the {@code COLUMNS} environment variable, or {@code null} when unset
     * @return the terminal width to render at
     */
    static int terminalWidth(int reportedColumns, @Nullable String columnsEnvironment) {
        if (reportedColumns > 0) {
            return reportedColumns;
        }
        if (columnsEnvironment != null) {
            try {
                int parsed = Integer.parseInt(columnsEnvironment.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Unparseable COLUMNS falls through to the default.
            }
        }
        return 80;
    }

    /**
     * Gathers the terminal facts {@link #terminalWidth(int, String)} needs and defers the decision to
     * it. Reads the width off {@link Terminal#getSize()} rather than {@code Terminal.getWidth()}, which
     * JLine 4 deprecates in favor of {@code getColumns()}.
     *
     * <p>
     * Package-private, unlike {@link #colorMode(Terminal, boolean)}: the {@code getSize()} round trip
     * is environment-independent, so a test can pin it directly. That is what makes this overload
     * testable where the color gatherer is not.
     */
    static int terminalWidth(Terminal terminal) {
        return terminalWidth(terminal.getSize().getColumns(), System.getenv("COLUMNS"));
    }

    /** Returns the width of the widest line in a list of art lines. */
    private static int textWidth(List<String> lines) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }
        return width;
    }

    /**
     * Joins wordmark columns into colored rows, side by side with a one-space gutter, each column
     * right-padded to its own width and short columns blank-filled to the tallest. One column yields
     * that wordmark standing alone; two yield the composed "CoordinateKit &lt;product&gt;" banner; none
     * yields no rows at all, which is how the mark-only rung renders.
     */
    private static List<List<Segment>> wordRows(List<Column> columns) {
        int height = 0;
        for (Column column : columns) {
            height = Math.max(height, column.art().size());
        }

        List<List<Segment>> rows = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            List<Segment> segments = new ArrayList<>();
            for (Column column : columns) {
                if (!segments.isEmpty()) {
                    segments.add(new Segment(ColorRole.NONE, " "));
                }
                String line = row < column.art().size() ? column.art().get(row) : "";
                segments.add(new Segment(column.role(), rightPad(line, textWidth(column.art()))));
            }
            rows.add(List.copyOf(segments));
        }
        return rows;
    }
}

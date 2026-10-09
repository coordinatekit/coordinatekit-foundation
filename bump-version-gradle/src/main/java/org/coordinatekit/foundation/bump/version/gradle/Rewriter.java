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
package org.coordinatekit.foundation.bump.version.gradle;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rewrites the places one file names the project's version. It is a pure function from text to
 * text, so a test can drive every form with strings and no build.
 *
 * <p>
 * Each form is a pattern with a named group {@code ver}, and a rule replaces only that group. All
 * rules match against the original line, the {@code -SNAPSHOT} gate included, and the edits they
 * produce are spliced in together, with the first rule winning an overlap. No rule's output is
 * another rule's input, which is what keeps a repeated bump from changing anything. A POM's
 * {@code <version>} elements are read apart from the line rules, because whether one moves depends
 * on the dependency it sits in.
 *
 * <p>
 * The forms that track the version in development, the build's own declaration and the snapshot
 * coordinates, are rewritten on every bump. The forms that name the last release, published
 * coordinates, jar and archive names, plugin ids, and the version line a command prints, are
 * rewritten only when the new version is a release and the line does not show a snapshot, so a line
 * that documents a snapshot is never turned into a release. A form shown at exactly the current
 * version is also caught up when that version is a snapshot, which is how the release bump turns an
 * example written at the snapshot into the release.
 *
 * <p>
 * A line that names the project in an anchored form, carries a version that is not the new one, and
 * is targeted by the bump but matched by no rule is reported in {@link FileRewrite#unmatched}, so a
 * document format the rules do not know fails the bump and does not slip through unchanged.
 *
 * <p>
 * An instance is built for one bump and holds the patterns that depend on it, compiled once, so
 * rewriting every tracked file does not compile them again. It holds nothing else and is immutable.
 */
final class Rewriter {
    /** The terminator of an archive name: a path separator, or a dot that does not begin a version. */
    private static final String ARCHIVE_END = "(?=/|\\.(?![0-9]))";

    /** The artifact part of a coordinate. */
    private static final String ARTIFACT = "[A-Za-z0-9._-]+";

    /** An {@code <artifactId>} element, with its trimmed text in group 1. */
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>\\s*([^<]*?)\\s*</artifactId>");

    /** A {@code <dependency>} element of a POM, across lines. */
    private static final Pattern DEPENDENCY = Pattern.compile("<dependency>.*?</dependency>", Pattern.DOTALL);

    /** A {@code <groupId>} element, with its trimmed text in group 1. */
    private static final Pattern GROUP_ID = Pattern.compile("<groupId>\\s*([^<]*?)\\s*</groupId>");

    /** A classifier that follows the version in a jar filename, such as {@code sources}. */
    private static final String JAR_CLASSIFIER = "(?:-[a-z]+)*";

    /**
     * A version that stops at the first point a classifier and {@code .jar} can follow. It is
     * {@link VersionBump#VERSION_PATTERN} with the qualifiers made lazy, so {@code x-1.2-sources.jar}
     * reads {@code 1.2} and not {@code 1.2-sources}.
     */
    private static final String LAZY_VERSION_PATTERN = "[0-9]+(?:\\.[0-9]+)*(?:[-+][A-Za-z0-9]+(?:\\.[0-9]+)*)*?";

    /** The character before a name that rules out a longer name ending in it. */
    private static final String LEFT_GUARD = "(?<![A-Za-z0-9._-])";

    /** The start of a plugin id in a {@code plugins} block, up to the opening quote. */
    private static final String PLUGIN_ID_START = "(?<![A-Za-z0-9_.-])id\\s*\\(?\\s*";

    /** The segments a plugin id may carry after the project's group. */
    private static final String PLUGIN_ID_SUBPATH = "(?:\\.[A-Za-z0-9_-]+)*";

    /** The character after a version that rules out a longer version beginning with it. */
    private static final String RIGHT_GUARD = "(?![A-Za-z0-9_+-]|\\.[A-Za-z0-9])";

    /** The {@code <version>} element of a dependency, whose text may be anything but markup. */
    private static final Pattern VERSION_TAG = Pattern.compile("<version>\\s*(?<ver>[^<\\s]+)\\s*</version>");

    /**
     * A version-like token, used to decide whether a line shows a version at all. Unlike
     * {@link VersionBump#VERSION_PATTERN} it needs at least one dot and stands alone, so {@code line 3}
     * and {@code abc1.2} are not versions.
     */
    private static final Pattern VERSION_TOKEN = Pattern
            .compile("(?<![0-9A-Za-z.])[0-9]+(?:\\.[0-9]+)+(?:[-+][A-Za-z0-9]+(?:\\.[0-9]+)*)*");

    private final Pattern anchorPattern;

    private final Anchors anchors;

    private final VersionBump bump;

    private final Pattern currentVersionElement;

    private final List<LineRule> lineRules;

    /**
     * Compiles the patterns one bump needs.
     *
     * @param anchors the names the rules are anchored to
     * @param bump the bump
     */
    Rewriter(Anchors anchors, VersionBump bump) {
        this.anchors = anchors;
        this.bump = bump;
        this.anchorPattern = anchorPattern(anchors);
        this.currentVersionElement = Pattern
                .compile("<version>(?<ver>" + Pattern.quote(bump.current()) + ")</version>");
        this.lineRules = lineRules(anchors, bump);
    }

    /**
     * A named form of the project's version. The names are what a test, a report, and a counted edit
     * refer to.
     */
    enum Form {
        /** A filename such as {@code <root>-<version>.tar.gz}, or a directory inside one. */
        ARCHIVE,
        /** A published coordinate, {@code <group>:<artifact>:<version>}. */
        COORDINATE,
        /** A jar filename of one of the project's modules. */
        JAR,
        /** A {@code <dependency>} block on one of the project's modules. */
        MAVEN_DEPENDENCY,
        /** A Gradle plugin of the project's own, in the {@code plugins} block. */
        PLUGIN_ID,
        /** The {@code version = "..."} assignment of a build script. */
        VERSION_ASSIGNMENT,
        /** A {@code <version>} element outside any dependency. */
        VERSION_ELEMENT,
        /** The line a command prints, {@code <root> <version> (}. */
        VERSION_LINE,
        /** The {@code version=...} entry of a properties file. */
        VERSION_PROPERTY
    }

    /** When a rule may fire, beyond its pattern matching. */
    enum Gate {
        /** Always. */
        ALWAYS,
        /** Only when the line carries no {@code -SNAPSHOT}. */
        NO_SNAPSHOT_ON_LINE,
        /** Only when the matched version is itself a snapshot. */
        SNAPSHOT_VERSION;

        /**
         * Decides whether a rule may claim a match.
         *
         * @param snapshotLine whether the line carries {@code -SNAPSHOT}
         * @param version the version the rule matched
         * @return {@code true} if this gate lets the rule claim the match
         */
        boolean allows(boolean snapshotLine, String version) {
            return switch (this) {
                case ALWAYS -> true;
                case NO_SNAPSHOT_ON_LINE -> !snapshotLine;
                case SNAPSHOT_VERSION -> version.endsWith(VersionBump.SNAPSHOT);
            };
        }
    }

    /**
     * One replacement a rewrite made.
     *
     * @param line the 1-based line it was made on
     * @param form the form that matched
     * @param from the version it replaced
     */
    record Edit(int line, Form form, String from) {}

    /**
     * What a rewrite of one file produced.
     *
     * @param text the file's new text
     * @param edits the replacements made, in line order
     * @param unmatched the 1-based lines, ascending, that name the project but that no rule matched
     */
    record FileRewrite(String text, List<Edit> edits, List<Integer> unmatched) {
        /**
         * Whether the rewrite changed the file.
         *
         * @return {@code true} if there is at least one edit
         */
        boolean changed() {
            return !edits.isEmpty();
        }
    }

    /**
     * One pattern that rewrites one form.
     *
     * @param form the form it rewrites
     * @param pattern the pattern, with a named group {@code ver} around the version
     * @param gate the condition beyond the pattern
     */
    record LineRule(Form form, Pattern pattern, Gate gate) {}

    /**
     * A {@code <dependency>} element, read far enough to decide whether to touch it.
     *
     * @param firstLine the 0-based line the element opens on
     * @param lastLine the 0-based line the element closes on
     * @param groupId the text of its {@code groupId}, empty if it has none
     * @param artifactId the text of its {@code artifactId}, empty if it has none
     * @param version its {@code version} element, or {@code null} if it has none
     */
    private record Block(
            int firstLine,
            int lastLine,
            String groupId,
            String artifactId,
            @Nullable VersionElement version
    ) {
        /**
         * Whether the element covers a line, in whole or in part.
         *
         * @param line the 0-based line
         * @return {@code true} if the element opens on, closes on, or crosses it
         */
        boolean covers(int line) {
            return firstLine <= line && line <= lastLine;
        }
    }

    /**
     * One line of the file and the spans claimed on it so far.
     *
     * @param text the line, with its terminator
     * @param spans the spans to replace, in the order they were claimed
     */
    private record Line(String text, List<Span> spans) {
        /**
         * Claims a span unless one already claimed on this line overlaps it, so the first claim wins.
         *
         * @param span the span to claim
         */
        void claim(Span span) {
            if (spans.stream().noneMatch(taken -> overlaps(taken, span))) {
                spans.add(span);
            }
        }

        /**
         * Whether some rule matched this line, including a match already at the new version.
         *
         * @return {@code true} if any span was claimed on it
         */
        boolean matched() {
            return !spans.isEmpty();
        }
    }

    /**
     * One span of a line that a rule will replace.
     *
     * @param start the offset of the first character, within the line
     * @param end the offset after the last character, within the line
     * @param form the form that matched
     */
    private record Span(int start, int end, Form form) {}

    /**
     * The text of a {@code <version>} element and where it sits.
     *
     * @param line the 0-based line it is on
     * @param start the offset of its first character, within the line
     * @param end the offset after its last character, within the line
     * @param text the version as written
     */
    private record VersionElement(int line, int start, int end, String text) {}

    /**
     * Adds the forms that name a module, a plugin, or the root project at some version.
     *
     * @param rules the list to add to
     * @param group the quoted group
     * @param modules the module alternation
     * @param root the quoted root project name
     * @param exact the quoted version the forms are tied to, or {@code null} for any version on a line
     *        that shows no snapshot, which also lets a jar's version give way to a classifier
     */
    private static void addNamedForms(
            List<LineRule> rules,
            String group,
            String modules,
            String root,
            @Nullable String exact
    ) {
        String version = exact == null ? VersionBump.VERSION_PATTERN : exact;
        String jarVersion = exact == null ? LAZY_VERSION_PATTERN : exact;
        Gate gate = exact == null ? Gate.NO_SNAPSHOT_ON_LINE : Gate.ALWAYS;
        rules.add(
                rule(Form.JAR, LEFT_GUARD + modules + "-(?<ver>" + jarVersion + ")" + JAR_CLASSIFIER + "\\.jar", gate)
        );
        rules.add(
                rule(
                        Form.PLUGIN_ID,
                        PLUGIN_ID_START + "([\"'])" + group + PLUGIN_ID_SUBPATH + "\\1\\s*\\)?\\s+version\\s+"
                                + "([\"'])(?<ver>" + version + ")\\2",
                        gate
                )
        );
        rules.add(rule(Form.ARCHIVE, LEFT_GUARD + root + "-(?<ver>" + version + ")" + ARCHIVE_END, gate));
        rules.add(rule(Form.VERSION_LINE, LEFT_GUARD + root + " (?<ver>" + version + ") \\(", gate));
    }

    /**
     * Joins names into one alternation, longest first as {@link Anchors} orders them.
     *
     * @param names the names to match
     * @return a non-capturing group that matches any of them literally
     */
    private static String alternation(List<String> names) {
        return names.stream().map(Pattern::quote).collect(Collectors.joining("|", "(?:", ")"));
    }

    /**
     * Builds the pattern that spots a line naming the project.
     *
     * @param anchors the names the rules are anchored to
     * @return a pattern that finds a project coordinate, plugin id, module jar stem, or root archive
     *         stem
     */
    private static Pattern anchorPattern(Anchors anchors) {
        String group = Pattern.quote(anchors.group());
        return Pattern.compile(
                LEFT_GUARD + group + ":" + "|" + PLUGIN_ID_START + "[\"']" + group + PLUGIN_ID_SUBPATH + "[\"']" + "|"
                        + LEFT_GUARD + alternation(anchors.modules()) + "-[0-9]" + "|" + LEFT_GUARD
                        + Pattern.quote(anchors.rootName()) + "-[0-9]"
        );
    }

    /**
     * Finds every {@code <dependency>} element and reads its coordinates.
     *
     * @param text the whole file
     * @param lines the file split by {@link #lines}
     * @return the blocks in file order
     */
    private static List<Block> blocks(String text, List<Line> lines) {
        int[] starts = new int[lines.size()];
        for (int i = 1; i < starts.length; i++) {
            starts[i] = starts[i - 1] + lines.get(i - 1).text().length();
        }
        List<Block> blocks = new ArrayList<>();
        Matcher dependency = DEPENDENCY.matcher(text);
        while (dependency.find()) {
            String body = dependency.group();
            VersionElement version = null;
            Matcher element = VERSION_TAG.matcher(body);
            if (element.find()) {
                int offset = dependency.start() + element.start("ver");
                int line = lineOf(starts, offset);
                int start = offset - starts[line];
                version = new VersionElement(line, start, start + element.group("ver").length(), element.group("ver"));
            }
            blocks.add(
                    new Block(
                            lineOf(starts, dependency.start()),
                            lineOf(starts, dependency.end() - 1),
                            tagText(body, GROUP_ID),
                            tagText(body, ARTIFACT_ID),
                            version
                    )
            );
        }
        return blocks;
    }

    /**
     * Claims the version of every match whose gate allows it, rule by rule, so an earlier rule wins an
     * overlap.
     *
     * @param lines the file's lines
     */
    private void claimLineRules(List<Line> lines) {
        for (Line line : lines) {
            boolean snapshotLine = line.text().contains(VersionBump.SNAPSHOT);
            for (LineRule rule : lineRules) {
                Matcher matcher = rule.pattern().matcher(line.text());
                while (matcher.find()) {
                    if (rule.gate().allows(snapshotLine, matcher.group("ver"))) {
                        line.claim(new Span(matcher.start("ver"), matcher.end("ver"), rule.form()));
                    }
                }
            }
        }
    }

    /**
     * Claims every {@code <version>} element of a POM that the bump rewrites. A project dependency's
     * version is claimed when {@link #editable} allows it, and an element on a line outside every
     * dependency is claimed when it holds exactly the current version. An element inside a dependency
     * of another group is never claimed.
     *
     * @param lines the file's lines
     * @param blocks the file's dependency elements
     */
    private void claimPomVersions(List<Line> lines, List<Block> blocks) {
        for (Block block : blocks) {
            VersionElement version = block.version();
            if (version != null && editable(block, version)) {
                lines.get(version.line()).claim(new Span(version.start(), version.end(), Form.MAVEN_DEPENDENCY));
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            int index = i;
            if (blocks.stream().anyMatch(block -> block.covers(index))) {
                continue;
            }
            Matcher matcher = currentVersionElement.matcher(lines.get(i).text());
            while (matcher.find()) {
                lines.get(i).claim(new Span(matcher.start("ver"), matcher.end("ver"), Form.VERSION_ELEMENT));
            }
        }
    }

    /**
     * Decides whether a dependency block is one the bump rewrites: the project's group, one of its
     * modules, a version of the kind the bump moves, and the version written out and not a property.
     *
     * @param block the block
     * @param version the block's version element
     * @return {@code true} if the version should become the new one
     */
    private boolean editable(Block block, VersionElement version) {
        boolean snapshot = version.text().endsWith(VersionBump.SNAPSHOT);
        return block.groupId().equals(anchors.group()) && anchors.modules().contains(block.artifactId())
                && isLiteral(version.text()) && snapshot != bump.nextIsRelease();
    }

    /**
     * Whether a version element holds a version written out, and not a property reference.
     *
     * @param text the element's text
     * @return {@code true} if the whole text has the shape of a version
     */
    private static boolean isLiteral(String text) {
        return text.matches(VersionBump.VERSION_PATTERN);
    }

    /**
     * Finds the line that holds an offset.
     *
     * @param starts the offset each line starts at, ascending
     * @param offset the offset into the text
     * @return the 0-based index of the line
     */
    private static int lineOf(int[] starts, int offset) {
        int index = Arrays.binarySearch(starts, offset);
        return index >= 0 ? index : -index - 2;
    }

    /**
     * Builds the line rules a bump applies, in the order they win an overlap.
     *
     * @param anchors the names the rules are anchored to
     * @param bump the bump
     * @return the rules, which depend on the kind of bump and on whether the current version is a
     *         snapshot
     */
    static List<LineRule> lineRules(Anchors anchors, VersionBump bump) {
        String current = Pattern.quote(bump.current());
        String group = Pattern.quote(anchors.group());
        String root = Pattern.quote(anchors.rootName());
        String modules = alternation(anchors.modules());

        List<LineRule> rules = new ArrayList<>();
        rules.add(rule(Form.VERSION_PROPERTY, "version=(?<ver>" + current + ")" + RIGHT_GUARD, Gate.ALWAYS));
        rules.add(rule(Form.VERSION_ASSIGNMENT, "version\\s*=\\s*([\"'])(?<ver>" + current + ")\\1", Gate.ALWAYS));
        rules.add(
                rule(
                        Form.COORDINATE,
                        LEFT_GUARD + group + ":" + ARTIFACT + ":(?<ver>" + VersionBump.VERSION_PATTERN + ")"
                                + RIGHT_GUARD,
                        bump.nextIsRelease() ? Gate.NO_SNAPSHOT_ON_LINE : Gate.SNAPSHOT_VERSION
                )
        );
        if (bump.nextIsRelease()) {
            addNamedForms(rules, group, modules, root, null);
        }
        if (bump.currentIsSnapshot()) {
            addNamedForms(rules, group, modules, root, current);
        }
        return rules;
    }

    /**
     * Splits text into lines, each keeping its own terminator, so joining them gives the text back byte
     * for byte whatever the line endings are and whether or not the last line has one.
     *
     * @param text the text to split
     * @return the lines
     */
    private static List<Line> lines(String text) {
        List<Line> lines = new ArrayList<>();
        int from = 0;
        while (from < text.length()) {
            int newline = text.indexOf('\n', from);
            int end = newline < 0 ? text.length() : newline + 1;
            lines.add(new Line(text.substring(from, end), new ArrayList<>()));
            from = end;
        }
        return lines;
    }

    /**
     * Whether two spans share a character.
     *
     * @param first one span
     * @param second the other
     * @return {@code true} if they overlap
     */
    private static boolean overlaps(Span first, Span second) {
        return first.start() < second.end() && second.start() < first.end();
    }

    /**
     * Rewrites one file's text.
     *
     * @param text the text, decoded so that every byte maps to one character
     * @return the new text with the edits that made it and the lines no rule recognised
     */
    FileRewrite rewrite(String text) {
        List<Line> lines = lines(text);
        List<Block> blocks = blocks(text, lines);
        claimPomVersions(lines, blocks);
        claimLineRules(lines);
        return splice(text, lines, unmatched(lines, blocks));
    }

    /**
     * Compiles one line rule.
     *
     * @param form the form it rewrites
     * @param regex the pattern, with a named group {@code ver}
     * @param gate the condition beyond the pattern
     * @return the rule
     */
    private static LineRule rule(Form form, String regex, Gate gate) {
        return new LineRule(form, Pattern.compile(regex), gate);
    }

    /**
     * Whether a line shows a version other than the new one.
     *
     * @param line the line
     * @return {@code true} if some version-like token differs from the new version
     */
    private boolean showsOtherVersion(String line) {
        Matcher token = VERSION_TOKEN.matcher(line);
        while (token.find()) {
            if (!token.group().equals(bump.next())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces every claimed span with the new version and records each replacement that changed the
     * text. When no replacement changed it, the original text is returned as is.
     *
     * @param text the original text the lines were split from
     * @param lines the file's lines, with their spans claimed
     * @param unmatched the lines {@link #unmatched} reported, carried into the result
     * @return the new text, its edits, and the unmatched lines
     */
    private FileRewrite splice(String text, List<Line> lines, List<Integer> unmatched) {
        StringBuilder result = new StringBuilder();
        List<Edit> edits = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).text();
            int position = 0;
            List<Span> ordered = lines.get(i).spans().stream().sorted(Comparator.comparingInt(Span::start)).toList();
            for (Span span : ordered) {
                String from = line.substring(span.start(), span.end());
                result.append(line, position, span.start());
                if (from.equals(bump.next())) {
                    result.append(from);
                } else {
                    result.append(bump.next());
                    edits.add(new Edit(i + 1, span.form(), from));
                }
                position = span.end();
            }
            result.append(line, position, line.length());
        }
        return new FileRewrite(edits.isEmpty() ? text : result.toString(), edits, unmatched);
    }

    /**
     * Reads the trimmed text of the first match of an element pattern.
     *
     * @param body the text to search
     * @param tag the pattern of the element, with its text in group 1
     * @return the element's text, or an empty string if there is none
     */
    private static String tagText(String body, Pattern tag) {
        Matcher matcher = tag.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Whether a bump looks for this kind of line: one with no snapshot on a release bump, and one with
     * a snapshot on a snapshot bump.
     *
     * @param line the line
     * @return {@code true} if the bump targets it
     */
    private boolean targetsLine(String line) {
        return line.contains(VersionBump.SNAPSHOT) != bump.nextIsRelease();
    }

    /**
     * Whether a bump looks for this kind of version: a release on a release bump, and a snapshot on a
     * snapshot bump.
     *
     * @param version the version as written
     * @return {@code true} if the bump targets it
     */
    private boolean targetsVersion(String version) {
        return version.contains(VersionBump.SNAPSHOT) != bump.nextIsRelease();
    }

    /**
     * Finds the lines that name the project but that no rule matched.
     *
     * <p>
     * A line is reported when it contains an anchor (a coordinate of the project's group, a plugin id
     * of its group, a module jar name, or a root archive name), carries a version-like token other than
     * the new version, and is of the kind this bump targets: it shows no snapshot on a release bump and
     * one on a snapshot bump. A project dependency block whose version no rule edited is reported at
     * its {@code <version>} line.
     *
     * @param lines the file's lines, with their spans claimed
     * @param blocks the file's dependency elements
     * @return the 1-based line numbers, ascending
     */
    private List<Integer> unmatched(List<Line> lines, List<Block> blocks) {
        Set<Integer> flagged = new TreeSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).text();
            if (!lines.get(i).matched() && targetsLine(line) && anchorPattern.matcher(line).find()
                    && showsOtherVersion(line)) {
                flagged.add(i + 1);
            }
        }
        for (Block block : blocks) {
            VersionElement version = block.version();
            if (version != null && block.groupId().equals(anchors.group()) && !lines.get(version.line()).matched()
                    && isLiteral(version.text()) && !version.text().equals(bump.next())
                    && targetsVersion(version.text())) {
                flagged.add(version.line() + 1);
            }
        }
        return List.copyOf(flagged);
    }
}

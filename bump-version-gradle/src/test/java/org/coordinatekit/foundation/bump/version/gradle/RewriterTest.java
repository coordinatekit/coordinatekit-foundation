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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.coordinatekit.foundation.bump.version.gradle.Rewriter.Form;
import org.coordinatekit.foundation.bump.version.gradle.Rewriter.FileRewrite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tests {@link Rewriter} with strings alone. Every case names the bump it runs under, so a row
 * reads as: under this move, this text becomes that one. The anchors are synthetic, a group, a root
 * name, and three modules that exist nowhere else, and the versions are ordinary ones, because
 * foundation's own release bump leaves this directory alone.
 */
class RewriterTest {
    /**
     * One bump over one piece of text.
     *
     * @param name what the case shows
     * @param from the version the text names now
     * @param to the version to write
     * @param before the text to rewrite
     * @param after the text the rewrite should produce
     */
    private record RewriteParameters(String name, String from, String to, String before, String after) {}

    /**
     * One bump of this repository's own documents.
     *
     * @param name what the case shows
     * @param to the version to write
     */
    private record RepositoryParameters(String name, String to) {}

    /**
     * What a bump's line rules should be.
     *
     * @param name what the case shows
     * @param from the current version
     * @param to the new version
     * @param forms the forms the rules rewrite, in order
     */
    private record RuleParameters(String name, String from, String to, List<Form> forms) {}

    /**
     * Text that no rule matches, or that one does, and the lines the check should report.
     *
     * @param name what the case shows
     * @param from the current version
     * @param to the new version
     * @param text the text to check
     * @param lines the 1-based lines the check should report
     */
    private record UnmatchedParameters(String name, String from, String to, String text, List<Integer> lines) {}

    /** The names every rule is anchored to. */
    private static final Anchors ANCHORS = Anchors
            .of("com.example.widgets", "widgets", List.of("alpha-core", "beta", "delta"));

    /** The version in development. */
    private static final String DEV = "2.0.0-SNAPSHOT";

    /** The last release. */
    private static final String LAST = "1.2.3";

    /** The development version after the next release. */
    private static final String NEXT_DEV = "2.1.0-SNAPSHOT";

    /** The release the development version becomes. */
    private static final String RELEASE = "2.0.0";

    /**
     * Rewrites a text under the bump a case names and compares it with the text the case expects.
     *
     * @param parameters the case
     */
    private static void assertRewrites(RewriteParameters parameters) {
        // ARRANGE //
        VersionBump bump = VersionBump.of(parameters.to(), parameters.from(), "unused");

        // ACT //
        String after = Rewriter.rewrite(parameters.before(), ANCHORS, bump).text();

        // ASSERT //
        assertEquals(parameters.after(), after);
    }

    static Stream<RuleParameters> lineRules__activeForms() {
        List<Form> always = List
                .of(Form.VERSION_PROPERTY, Form.VERSION_ASSIGNMENT, Form.VERSION_ELEMENT, Form.COORDINATE);
        List<Form> named = List.of(Form.JAR, Form.PLUGIN_ID, Form.ARCHIVE, Form.VERSION_LINE);
        return Stream.of(
                new RuleParameters(
                        "release bump from a snapshot",
                        DEV,
                        RELEASE,
                        Stream.of(always, named, named).flatMap(List::stream).toList()
                ),
                new RuleParameters(
                        "release bump from a release",
                        LAST,
                        "1.3.0",
                        Stream.of(always, named).flatMap(List::stream).toList()
                ),
                new RuleParameters(
                        "snapshot bump from a snapshot",
                        DEV,
                        NEXT_DEV,
                        Stream.of(always, named).flatMap(List::stream).toList()
                ),
                new RuleParameters("snapshot bump from a release", LAST, NEXT_DEV, always)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void lineRules__activeForms(RuleParameters parameters) {
        // ACT //
        List<Form> forms = Rewriter.lineRules(ANCHORS, VersionBump.of(parameters.to(), parameters.from(), "unused"))
                .stream()
                .map(Rewriter.LineRule::form)
                .toList();

        // ASSERT //
        assertEquals(parameters.forms(), forms);
    }

    /**
     * Reads a file the way the task does, one character per byte.
     *
     * @param file the file
     * @return its text
     * @throws IOException if the file cannot be read
     */
    private static String readFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    }

    static Stream<RewriteParameters> rewrite__archivesAndVersionLines() {
        return Stream.of(
                new RewriteParameters("archive name", DEV, RELEASE, "widgets-1.2.3.tar.gz\n", "widgets-2.0.0.tar.gz\n"),
                new RewriteParameters(
                        "directory inside an archive",
                        DEV,
                        RELEASE,
                        "tar xf widgets-1.2.3.tar.gz && widgets-1.2.3/bin/widgets\n",
                        "tar xf widgets-2.0.0.tar.gz && widgets-2.0.0/bin/widgets\n"
                ),
                new RewriteParameters(
                        "archive at the snapshot",
                        DEV,
                        RELEASE,
                        "unzip widgets-2.0.0-SNAPSHOT.zip\n",
                        "unzip widgets-2.0.0.zip\n"
                ),
                new RewriteParameters(
                        "archive of another name",
                        DEV,
                        RELEASE,
                        "mywidgets-1.2.3.zip and widgets-core-1.2.3.zip\n",
                        "mywidgets-1.2.3.zip and widgets-core-1.2.3.zip\n"
                ),
                new RewriteParameters(
                        "archive with a longer version",
                        DEV,
                        RELEASE,
                        "widgets-1.2.3x.zip\n",
                        "widgets-1.2.3x.zip\n"
                ),
                new RewriteParameters(
                        "snapshot archive of another version",
                        DEV,
                        RELEASE,
                        "widgets-1.9.0-SNAPSHOT.zip\n",
                        "widgets-1.9.0-SNAPSHOT.zip\n"
                ),
                new RewriteParameters(
                        "version line",
                        DEV,
                        RELEASE,
                        "widgets 1.2.3 (build 5)\n",
                        "widgets 2.0.0 (build 5)\n"
                ),
                new RewriteParameters(
                        "version line without its parenthesis",
                        DEV,
                        RELEASE,
                        "widgets 1.2.3\n",
                        "widgets 1.2.3\n"
                ),
                new RewriteParameters(
                        "version line of another name",
                        DEV,
                        RELEASE,
                        "mywidgets 1.2.3 (build 5)\n",
                        "mywidgets 1.2.3 (build 5)\n"
                ),
                new RewriteParameters(
                        "archive on a snapshot bump",
                        LAST,
                        NEXT_DEV,
                        "widgets-1.2.3.tar.gz\n",
                        "widgets-1.2.3.tar.gz\n"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__archivesAndVersionLines(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    static Stream<RewriteParameters> rewrite__byteRoundTrip() {
        return Stream.of(
                new RewriteParameters(
                        "carriage returns",
                        DEV,
                        RELEASE,
                        "version=2.0.0-SNAPSHOT\r\ncom.example.widgets:beta:1.2.3\r\n",
                        "version=2.0.0\r\ncom.example.widgets:beta:2.0.0\r\n"
                ),
                new RewriteParameters(
                        "no final newline",
                        DEV,
                        RELEASE,
                        "line\nversion=2.0.0-SNAPSHOT",
                        "line\nversion=2.0.0"
                ),
                new RewriteParameters(
                        "bytes that are not UTF-8",
                        DEV,
                        RELEASE,
                        "café ÿ\u0080 version=2.0.0-SNAPSHOT Ã©\n",
                        "café ÿ\u0080 version=2.0.0 Ã©\n"
                ),
                new RewriteParameters("empty file", DEV, RELEASE, "", ""),
                new RewriteParameters("blank lines", DEV, RELEASE, "\n\n", "\n\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__byteRoundTrip(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    static Stream<RewriteParameters> rewrite__coordinates() {
        return Stream.of(
                new RewriteParameters(
                        "release coordinate",
                        DEV,
                        RELEASE,
                        "    com.example.widgets:alpha-core:1.2.3\n",
                        "    com.example.widgets:alpha-core:2.0.0\n"
                ),
                new RewriteParameters(
                        "coordinate at the start of a line",
                        DEV,
                        RELEASE,
                        "com.example.widgets:beta:1.2.3 is the artifact\n",
                        "com.example.widgets:beta:2.0.0 is the artifact\n"
                ),
                new RewriteParameters(
                        "coordinate with a classifier",
                        DEV,
                        RELEASE,
                        "com.example.widgets:beta:1.2.3:sources\n",
                        "com.example.widgets:beta:2.0.0:sources\n"
                ),
                new RewriteParameters(
                        "snapshot coordinate on a release bump",
                        DEV,
                        RELEASE,
                        "com.example.widgets:beta:2.0.0-SNAPSHOT\n",
                        "com.example.widgets:beta:2.0.0-SNAPSHOT\n"
                ),
                new RewriteParameters(
                        "release and snapshot on one line",
                        DEV,
                        RELEASE,
                        "com.example.widgets:beta:1.2.3 or com.example.widgets:beta:2.0.0-SNAPSHOT\n",
                        "com.example.widgets:beta:1.2.3 or com.example.widgets:beta:2.0.0-SNAPSHOT\n"
                ),
                new RewriteParameters(
                        "foreign group",
                        DEV,
                        RELEASE,
                        "org.other:beta:1.2.3\n",
                        "org.other:beta:1.2.3\n"
                ),
                new RewriteParameters(
                        "group that is the end of another name",
                        DEV,
                        RELEASE,
                        "notcom.example.widgets:beta:1.2.3\n",
                        "notcom.example.widgets:beta:1.2.3\n"
                ),
                new RewriteParameters(
                        "coordinate without a version",
                        DEV,
                        RELEASE,
                        "See {@code com.example.widgets:beta} for details.\n",
                        "See {@code com.example.widgets:beta} for details.\n"
                ),
                new RewriteParameters(
                        "version with a qualifier no rule knows",
                        DEV,
                        RELEASE,
                        "com.example.widgets:beta:1.0.0.Final\n",
                        "com.example.widgets:beta:1.0.0.Final\n"
                ),
                new RewriteParameters(
                        "snapshot coordinate on a snapshot bump",
                        LAST,
                        NEXT_DEV,
                        "com.example.widgets:beta:2.0.0-SNAPSHOT\n",
                        "com.example.widgets:beta:2.1.0-SNAPSHOT\n"
                ),
                new RewriteParameters(
                        "release coordinate on a snapshot bump",
                        LAST,
                        NEXT_DEV,
                        "com.example.widgets:beta:1.2.3\n",
                        "com.example.widgets:beta:1.2.3\n"
                ),
                new RewriteParameters(
                        "coordinate already at the new version",
                        DEV,
                        RELEASE,
                        "com.example.widgets:beta:2.0.0\n",
                        "com.example.widgets:beta:2.0.0\n"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__coordinates(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    @Test
    void rewrite__editsRecordWhatChanged() {
        // ARRANGE //
        VersionBump bump = VersionBump.of(RELEASE, DEV, "unused");

        // ACT //
        FileRewrite rewrite = Rewriter
                .rewrite("version=2.0.0-SNAPSHOT\ncom.example.widgets:beta:1.2.3\nplain\n", ANCHORS, bump);

        // ASSERT //
        assertEquals(
                List.of(new Rewriter.Edit(1, Form.VERSION_PROPERTY, DEV), new Rewriter.Edit(2, Form.COORDINATE, LAST)),
                rewrite.edits()
        );
        assertEquals(List.of(1, 2), List.copyOf(rewrite.matchedLines()));
        assertTrue(rewrite.changed());
    }

    static Stream<RewriteParameters> rewrite__exactForms() {
        return Stream.of(
                new RewriteParameters("property", DEV, RELEASE, "version=2.0.0-SNAPSHOT\n", "version=2.0.0\n"),
                new RewriteParameters(
                        "assignment with double quotes",
                        DEV,
                        RELEASE,
                        "version = \"2.0.0-SNAPSHOT\"\n",
                        "version = \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "assignment with single quotes and loose spacing",
                        DEV,
                        RELEASE,
                        "version   =   '2.0.0-SNAPSHOT'\n",
                        "version   =   '2.0.0'\n"
                ),
                new RewriteParameters(
                        "assignment without a space",
                        DEV,
                        RELEASE,
                        "version=\"2.0.0-SNAPSHOT\"\n",
                        "version=\"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "element outside a dependency",
                        DEV,
                        RELEASE,
                        "<project>\n    <version>2.0.0-SNAPSHOT</version>\n</project>\n",
                        "<project>\n    <version>2.0.0</version>\n</project>\n"
                ),
                new RewriteParameters(
                        "property on a snapshot bump",
                        DEV,
                        NEXT_DEV,
                        "version=2.0.0-SNAPSHOT\n",
                        "version=2.1.0-SNAPSHOT\n"
                ),
                new RewriteParameters(
                        "property that is longer than the current version",
                        LAST,
                        "1.3.0",
                        "version=1.2.3-SNAPSHOT\nversion=1.2.30\nversion=1.2.3.4\n",
                        "version=1.2.3-SNAPSHOT\nversion=1.2.30\nversion=1.2.3.4\n"
                ),
                new RewriteParameters(
                        "property of another version",
                        DEV,
                        RELEASE,
                        "version=1.2.3\n",
                        "version=1.2.3\n"
                ),
                new RewriteParameters(
                        "assignment inside a longer version",
                        LAST,
                        "1.3.0",
                        "version = \"1.2.3-rc1\"\n",
                        "version = \"1.2.3-rc1\"\n"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__exactForms(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    static Stream<RewriteParameters> rewrite__jarNames() {
        return Stream.of(
                new RewriteParameters("own jar", DEV, RELEASE, "beta-1.2.3.jar\n", "beta-2.0.0.jar\n"),
                new RewriteParameters(
                        "own jar with a hyphenated module",
                        DEV,
                        RELEASE,
                        "    alpha-core-1.2.3.jar\n",
                        "    alpha-core-2.0.0.jar\n"
                ),
                new RewriteParameters(
                        "sources jar",
                        DEV,
                        RELEASE,
                        "alpha-core-1.2.3-sources.jar\n",
                        "alpha-core-2.0.0-sources.jar\n"
                ),
                new RewriteParameters(
                        "jar with a release candidate",
                        DEV,
                        RELEASE,
                        "beta-1.2.3-rc1.jar\n",
                        "beta-2.0.0.jar\n"
                ),
                new RewriteParameters("foreign jar", DEV, RELEASE, "jline-3.30.5.jar\n", "jline-3.30.5.jar\n"),
                new RewriteParameters(
                        "jar that ends in a module name",
                        DEV,
                        RELEASE,
                        "my-beta-1.2.3.jar\n",
                        "my-beta-1.2.3.jar\n"
                ),
                new RewriteParameters(
                        "jar at the snapshot",
                        DEV,
                        RELEASE,
                        "beta-2.0.0-SNAPSHOT.jar\n",
                        "beta-2.0.0.jar\n"
                ),
                new RewriteParameters(
                        "snapshot jar of another version",
                        DEV,
                        RELEASE,
                        "beta-1.9.0-SNAPSHOT.jar\n",
                        "beta-1.9.0-SNAPSHOT.jar\n"
                ),
                new RewriteParameters(
                        "jar on a snapshot bump from a release",
                        LAST,
                        NEXT_DEV,
                        "beta-1.2.3.jar\n",
                        "beta-1.2.3.jar\n"
                ),
                new RewriteParameters(
                        "jar at the snapshot on a snapshot bump",
                        DEV,
                        NEXT_DEV,
                        "beta-2.0.0-SNAPSHOT.jar\n",
                        "beta-2.1.0-SNAPSHOT.jar\n"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__jarNames(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    static Stream<RewriteParameters> rewrite__mavenDependencies() {
        String own = """
                <dependency>
                    <groupId>com.example.widgets</groupId>
                    <artifactId>alpha-core</artifactId>
                    <version>%s</version>
                </dependency>
                """;
        String foreign = """
                <dependency>
                    <groupId>org.other</groupId>
                    <artifactId>alpha-core</artifactId>
                    <version>%s</version>
                </dependency>
                """;
        String unknownArtifact = """
                <dependency>
                    <groupId>com.example.widgets</groupId>
                    <artifactId>gizmo</artifactId>
                    <version>%s</version>
                </dependency>
                """;
        String compact = "<dependency><groupId>com.example.widgets</groupId><artifactId>beta</artifactId>"
                + "<version>%s</version></dependency>\n";
        return Stream.of(
                new RewriteParameters("release block", DEV, RELEASE, own.formatted(LAST), own.formatted(RELEASE)),
                new RewriteParameters(
                        "snapshot block on a release bump",
                        DEV,
                        RELEASE,
                        own.formatted(DEV),
                        own.formatted(DEV)
                ),
                new RewriteParameters(
                        "snapshot block on a snapshot bump",
                        LAST,
                        NEXT_DEV,
                        own.formatted("1.9.0-SNAPSHOT"),
                        own.formatted(NEXT_DEV)
                ),
                new RewriteParameters(
                        "release block on a snapshot bump",
                        LAST,
                        NEXT_DEV,
                        own.formatted(LAST),
                        own.formatted(LAST)
                ),
                new RewriteParameters("foreign group", DEV, RELEASE, foreign.formatted(LAST), foreign.formatted(LAST)),
                new RewriteParameters(
                        "artifact that is no module",
                        DEV,
                        RELEASE,
                        unknownArtifact.formatted(LAST),
                        unknownArtifact.formatted(LAST)
                ),
                new RewriteParameters(
                        "one-line block",
                        DEV,
                        RELEASE,
                        compact.formatted(LAST),
                        compact.formatted(RELEASE)
                ),
                new RewriteParameters(
                        "version held in a property",
                        DEV,
                        RELEASE,
                        own.formatted("${widgets.version}"),
                        own.formatted("${widgets.version}")
                ),
                new RewriteParameters(
                        "foreign block at the current release on a snapshot bump",
                        RELEASE,
                        NEXT_DEV,
                        "<version>2.0.0</version>\n" + foreign.formatted(RELEASE),
                        "<version>2.1.0-SNAPSHOT</version>\n" + foreign.formatted(RELEASE)
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__mavenDependencies(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    static Stream<RewriteParameters> rewrite__pluginIds() {
        return Stream.of(
                new RewriteParameters(
                        "own id",
                        DEV,
                        RELEASE,
                        "    id \"com.example.widgets.gizmo\" version \"1.2.3\"\n",
                        "    id \"com.example.widgets.gizmo\" version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "own id with single quotes",
                        DEV,
                        RELEASE,
                        "id 'com.example.widgets.gizmo' version '1.2.3'\n",
                        "id 'com.example.widgets.gizmo' version '2.0.0'\n"
                ),
                new RewriteParameters(
                        "own id in the Kotlin form",
                        DEV,
                        RELEASE,
                        "id(\"com.example.widgets.gizmo\") version \"1.2.3\"\n",
                        "id(\"com.example.widgets.gizmo\") version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "own id with several segments",
                        DEV,
                        RELEASE,
                        "id \"com.example.widgets.gizmo.deep\" version \"1.2.3\"\n",
                        "id \"com.example.widgets.gizmo.deep\" version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "foreign id",
                        DEV,
                        RELEASE,
                        "id \"org.other.gizmo\" version \"1.2.3\"\n",
                        "id \"org.other.gizmo\" version \"1.2.3\"\n"
                ),
                new RewriteParameters(
                        "id of a group that begins with the same letters",
                        DEV,
                        RELEASE,
                        "id \"com.example.widgetsmith.gizmo\" version \"1.2.3\"\n",
                        "id \"com.example.widgetsmith.gizmo\" version \"1.2.3\"\n"
                ),
                new RewriteParameters(
                        "own id at the snapshot",
                        DEV,
                        RELEASE,
                        "id \"com.example.widgets.gizmo\" version \"2.0.0-SNAPSHOT\"\n",
                        "id \"com.example.widgets.gizmo\" version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "foreign id at the snapshot",
                        DEV,
                        RELEASE,
                        "id \"org.other.gizmo\" version \"2.0.0-SNAPSHOT\"\n",
                        "id \"org.other.gizmo\" version \"2.0.0-SNAPSHOT\"\n"
                ),
                new RewriteParameters(
                        "own id at another snapshot",
                        DEV,
                        RELEASE,
                        "id \"com.example.widgets.gizmo\" version \"1.9.0-SNAPSHOT\"\n",
                        "id \"com.example.widgets.gizmo\" version \"1.9.0-SNAPSHOT\"\n"
                ),
                new RewriteParameters(
                        "own id on a snapshot bump from a release",
                        LAST,
                        NEXT_DEV,
                        "id \"com.example.widgets.gizmo\" version \"1.2.3\"\n",
                        "id \"com.example.widgets.gizmo\" version \"1.2.3\"\n"
                ),
                new RewriteParameters(
                        "own id at the snapshot on a snapshot bump",
                        DEV,
                        NEXT_DEV,
                        "id \"com.example.widgets.gizmo\" version \"2.0.0-SNAPSHOT\"\n",
                        "id \"com.example.widgets.gizmo\" version \"2.1.0-SNAPSHOT\"\n"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__pluginIds(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    @Test
    void rewrite__repeatedBumpChangesNothing() {
        // ARRANGE //
        VersionBump bump = VersionBump.of(RELEASE, DEV, "unused");
        String once = Rewriter.rewrite("version=2.0.0-SNAPSHOT\ncom.example.widgets:beta:1.2.3\n", ANCHORS, bump)
                .text();

        // ACT //
        FileRewrite again = Rewriter.rewrite(once, ANCHORS, bump);

        // ASSERT //
        assertFalse(again.changed());
        assertEquals(once, again.text());
        assertTrue(Rewriter.unmatchedLines(once, ANCHORS, bump, again).isEmpty());
    }

    static Stream<RepositoryParameters> rewrite__repositoryDocs() {
        String current = System.getProperty("bumpVersion.currentVersion", "");
        String release = current.equals("9.9.9") ? "9.9.8" : "9.9.9";
        return Stream.of(
                new RepositoryParameters("release bump", release),
                new RepositoryParameters("snapshot bump", release + "-SNAPSHOT")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__repositoryDocs(RepositoryParameters parameters) throws IOException {
        // ARRANGE //
        assumeTrue(System.getProperty("bumpVersion.repositoryRoot") != null, "set by the Gradle test task");
        Path root = Path.of(System.getProperty("bumpVersion.repositoryRoot"));
        Anchors anchors = Anchors.of(
                System.getProperty("bumpVersion.group"),
                System.getProperty("bumpVersion.rootName"),
                Arrays.asList(System.getProperty("bumpVersion.modules").split(","))
        );
        VersionBump bump = VersionBump.of(parameters.to(), System.getProperty("bumpVersion.currentVersion"), "unused");
        boolean release = bump.releaseBump();

        // ACT //
        FileRewrite readme = rewriteFile(root.resolve("README.md"), anchors, bump);
        FileRewrite releaseNotes = rewriteFile(root.resolve("RELEASE.md"), anchors, bump);

        // ASSERT //
        assertTrue(Rewriter.unmatchedLines(readFile(root.resolve("README.md")), anchors, bump, readme).isEmpty());
        assertTrue(
                Rewriter.unmatchedLines(readFile(root.resolve("RELEASE.md")), anchors, bump, releaseNotes).isEmpty()
        );
        assertEquals(
                release,
                readme.edits().stream().anyMatch(edit -> edit.form() == Form.COORDINATE),
                "README names the last release in its coordinates, so only a release moves them"
        );
        assertEquals(
                release,
                readme.edits().stream().anyMatch(edit -> edit.form() == Form.JAR),
                "README names the last release in its jar filenames, so only a release moves them"
        );
        assertEquals(
                !release,
                releaseNotes.changed(),
                "RELEASE.md shows the snapshot, so only a snapshot bump moves it"
        );
    }

    /**
     * Rewrites a file under the given anchors without writing it back.
     *
     * @param file the file
     * @param anchors the names the rules are anchored to
     * @param bump the bump
     * @return the rewrite
     * @throws IOException if the file cannot be read
     */
    private static FileRewrite rewriteFile(Path file, Anchors anchors, VersionBump bump) throws IOException {
        return Rewriter.rewrite(readFile(file), anchors, bump);
    }

    static Stream<UnmatchedParameters> unmatchedLines__reported() {
        return Stream.of(
                new UnmatchedParameters(
                        "version with a qualifier no rule knows",
                        DEV,
                        RELEASE,
                        "ok\ncom.example.widgets:alpha-core:1.0.0.Final\n",
                        List.of(2)
                ),
                new UnmatchedParameters(
                        "version catalog entry",
                        DEV,
                        RELEASE,
                        "alpha = { module = \"com.example.widgets:alpha-core\", version = \"1.2.3\" }\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "stale snapshot plugin id on a snapshot bump",
                        DEV,
                        NEXT_DEV,
                        "id \"com.example.widgets.gizmo\" version \"1.0.0-SNAPSHOT\"\n",
                        List.of(1)
                ),
                new UnmatchedParameters("dependency block with an artifact that is no module", DEV, RELEASE, """
                        <dependency>
                        <groupId>com.example.widgets</groupId>
                        <artifactId>gizmo</artifactId>
                        <version>1.2.3</version>
                        </dependency>
                        """, List.of(4)),
                new UnmatchedParameters(
                        "module jar stem with a version no rule reads",
                        DEV,
                        RELEASE,
                        "copy beta-1.2.3 into place\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "class name beside a ceiling",
                        DEV,
                        RELEASE,
                        "@Gate(type = com.example.widgets.Thing.class, ceiling = \"0.7\")\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "bare group beside a licence version",
                        DEV,
                        RELEASE,
                        "excludeGroups = ['com.example.widgets'] // Apache 2.0\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "line already at the new version",
                        DEV,
                        RELEASE,
                        "com.example.widgets:alpha-core:2.0.0\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "snapshot line on a release bump",
                        DEV,
                        RELEASE,
                        "com.example.widgets:alpha-core:3.0.0-SNAPSHOT.Final\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "line a rule rewrites",
                        DEV,
                        RELEASE,
                        "com.example.widgets:alpha-core:1.2.3\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "text that names nothing",
                        DEV,
                        RELEASE,
                        "Version 1.2.3 of nothing.\n",
                        List.of()
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void unmatchedLines__reported(UnmatchedParameters parameters) {
        // ARRANGE //
        VersionBump bump = VersionBump.of(parameters.to(), parameters.from(), "unused");
        FileRewrite rewrite = Rewriter.rewrite(parameters.text(), ANCHORS, bump);

        // ACT //
        List<Integer> lines = Rewriter.unmatchedLines(parameters.text(), ANCHORS, bump, rewrite);

        // ASSERT //
        assertEquals(parameters.lines(), lines);
    }
}

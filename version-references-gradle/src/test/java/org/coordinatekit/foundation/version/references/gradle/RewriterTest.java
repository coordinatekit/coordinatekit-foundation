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
package org.coordinatekit.foundation.version.references.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.coordinatekit.foundation.version.references.gradle.Rewriter.FileRewrite;
import org.coordinatekit.foundation.version.references.gradle.Rewriter.Form;
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
 * Tests {@link Rewriter} with strings alone. Every case runs under the same pair of versions, so a
 * row reads as: under this release and this next version, this text becomes that one. The anchors
 * are synthetic, a group, a root name, and three modules that exist nowhere else, and the versions
 * are ordinary ones, because foundation's own release leaves this directory alone.
 */
class RewriterTest {
    /**
     * One piece of text and what the rewrite should make of it.
     *
     * @param name what the case shows
     * @param before the text to rewrite
     * @param after the text the rewrite should produce
     */
    private record RewriteParameters(String name, String before, String after) {}

    /**
     * Text that no rule matches, or that one does, and the lines the check should report.
     *
     * @param name what the case shows
     * @param text the text to check
     * @param lines the 1-based lines the check should report
     */
    private record UnmatchedParameters(String name, String text, List<Integer> lines) {}

    /** The names every rule is anchored to. */
    private static final Anchors ANCHORS = Anchors
            .of("com.example.widgets", "widgets", List.of("alpha-core", "beta", "delta"));

    /** The version in development before the release. */
    private static final String DEV = "2.0.0-SNAPSHOT";

    /** The last release. */
    private static final String LAST = "1.2.3";

    /** The version in development after the release. */
    private static final String NEXT = "2.1.0-SNAPSHOT";

    /** The version being released. */
    private static final String RELEASE = "2.0.0";

    /** The pair every case rewrites under. */
    private static final ReleaseVersions VERSIONS = ReleaseVersions.of(RELEASE, NEXT);

    /**
     * Rewrites a text and compares it with the text the case expects.
     *
     * @param parameters the case
     */
    private static void assertRewrites(RewriteParameters parameters) {
        // ACT //
        String after = new Rewriter(ANCHORS, VERSIONS).rewrite(parameters.before()).text();

        // ASSERT //
        assertEquals(parameters.after(), after);
    }

    @Test
    void lineRules__activeForms() {
        // ACT //
        List<Form> forms = Rewriter.lineRules(ANCHORS).stream().map(Rewriter.LineRule::form).toList();

        // ASSERT //
        assertEquals(List.of(Form.COORDINATE, Form.JAR, Form.PLUGIN_ID, Form.ARCHIVE, Form.VERSION_LINE), forms);
    }

    /**
     * Reads a file the way the task does, one character per byte.
     *
     * @param file the file
     * @return its text
     * @throws IOException if the file cannot be read
     */
    private static String readFile(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.ISO_8859_1);
    }

    static Stream<RewriteParameters> rewrite__archivesAndVersionLines() {
        return Stream.of(
                new RewriteParameters("archive name", "widgets-1.2.3.tar.gz\n", "widgets-2.0.0.tar.gz\n"),
                new RewriteParameters(
                        "directory inside an archive",
                        "tar xf widgets-1.2.3.tar.gz && widgets-1.2.3/bin/widgets\n",
                        "tar xf widgets-2.0.0.tar.gz && widgets-2.0.0/bin/widgets\n"
                ),
                new RewriteParameters(
                        "archive at the snapshot",
                        "unzip widgets-2.0.0-SNAPSHOT.zip\n",
                        "unzip widgets-2.0.0.zip\n"
                ),
                new RewriteParameters(
                        "archive at another snapshot",
                        "widgets-1.9.0-SNAPSHOT.zip\n",
                        "widgets-2.0.0.zip\n"
                ),
                new RewriteParameters("archive already at the release", "widgets-2.0.0.zip\n", "widgets-2.0.0.zip\n"),
                new RewriteParameters(
                        "archive of another name",
                        "mywidgets-1.2.3.zip and widgets-core-1.2.3.zip\n",
                        "mywidgets-1.2.3.zip and widgets-core-1.2.3.zip\n"
                ),
                new RewriteParameters("archive with a longer version", "widgets-1.2.3x.zip\n", "widgets-1.2.3x.zip\n"),
                new RewriteParameters("version line", "widgets 1.2.3 (build 5)\n", "widgets 2.0.0 (build 5)\n"),
                new RewriteParameters(
                        "version line at a snapshot",
                        "widgets 2.0.0-SNAPSHOT (build 5)\n",
                        "widgets 2.0.0 (build 5)\n"
                ),
                new RewriteParameters("version line without its parenthesis", "widgets 1.2.3\n", "widgets 1.2.3\n"),
                new RewriteParameters(
                        "version line of another name",
                        "mywidgets 1.2.3 (build 5)\n",
                        "mywidgets 1.2.3 (build 5)\n"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__archivesAndVersionLines(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    static Stream<RewriteParameters> rewrite__buildVersionDeclarations() {
        return Stream.of(
                new RewriteParameters("property", "version=2.0.0-SNAPSHOT\n", "version=2.0.0-SNAPSHOT\n"),
                new RewriteParameters(
                        "assignment with double quotes",
                        "version = \"2.0.0-SNAPSHOT\"\n",
                        "version = \"2.0.0-SNAPSHOT\"\n"
                ),
                new RewriteParameters(
                        "assignment with single quotes and loose spacing",
                        "version   =   '2.0.0-SNAPSHOT'\n",
                        "version   =   '2.0.0-SNAPSHOT'\n"
                ),
                new RewriteParameters(
                        "assignment without a space",
                        "version=\"2.0.0-SNAPSHOT\"\n",
                        "version=\"2.0.0-SNAPSHOT\"\n"
                ),
                new RewriteParameters(
                        "element outside a dependency",
                        "<project>\n    <version>2.0.0-SNAPSHOT</version>\n</project>\n",
                        "<project>\n    <version>2.0.0-SNAPSHOT</version>\n</project>\n"
                ),
                new RewriteParameters("property of another version", "version=1.2.3\n", "version=1.2.3\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__buildVersionDeclarations(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    static Stream<RewriteParameters> rewrite__byteRoundTrip() {
        return Stream.of(
                new RewriteParameters(
                        "carriage returns",
                        "com.example.widgets:beta:1.2.3\r\ncom.example.widgets:beta:2.0.0-SNAPSHOT\r\n",
                        "com.example.widgets:beta:2.0.0\r\ncom.example.widgets:beta:2.1.0-SNAPSHOT\r\n"
                ),
                new RewriteParameters(
                        "no final newline",
                        "line\ncom.example.widgets:beta:1.2.3",
                        "line\ncom.example.widgets:beta:2.0.0"
                ),
                new RewriteParameters(
                        "bytes that are not UTF-8",
                        "café ÿ\u0080 com.example.widgets:beta:1.2.3 Ã©\n",
                        "café ÿ\u0080 com.example.widgets:beta:2.0.0 Ã©\n"
                ),
                new RewriteParameters("empty file", "", ""),
                new RewriteParameters("blank lines", "\n\n", "\n\n")
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
                        "    com.example.widgets:alpha-core:1.2.3\n",
                        "    com.example.widgets:alpha-core:2.0.0\n"
                ),
                new RewriteParameters(
                        "coordinate at the start of a line",
                        "com.example.widgets:beta:1.2.3 is the artifact\n",
                        "com.example.widgets:beta:2.0.0 is the artifact\n"
                ),
                new RewriteParameters(
                        "coordinate of an artifact that is no module",
                        "com.example.widgets:gizmo:1.2.3\n",
                        "com.example.widgets:gizmo:2.0.0\n"
                ),
                new RewriteParameters(
                        "coordinate with a classifier",
                        "com.example.widgets:beta:1.2.3:sources\n",
                        "com.example.widgets:beta:2.0.0:sources\n"
                ),
                new RewriteParameters(
                        "snapshot coordinate",
                        "com.example.widgets:beta:2.0.0-SNAPSHOT\n",
                        "com.example.widgets:beta:2.1.0-SNAPSHOT\n"
                ),
                new RewriteParameters(
                        "snapshot coordinate of an older version",
                        "com.example.widgets:beta:1.9.0-SNAPSHOT\n",
                        "com.example.widgets:beta:2.1.0-SNAPSHOT\n"
                ),
                new RewriteParameters(
                        "release and snapshot on one line",
                        "com.example.widgets:beta:1.2.3 or com.example.widgets:beta:2.0.0-SNAPSHOT\n",
                        "com.example.widgets:beta:2.0.0 or com.example.widgets:beta:2.1.0-SNAPSHOT\n"
                ),
                new RewriteParameters("foreign group", "org.other:beta:1.2.3\n", "org.other:beta:1.2.3\n"),
                new RewriteParameters(
                        "group that is the end of another name",
                        "notcom.example.widgets:beta:1.2.3\n",
                        "notcom.example.widgets:beta:1.2.3\n"
                ),
                new RewriteParameters(
                        "coordinate without a version",
                        "See {@code com.example.widgets:beta} for details.\n",
                        "See {@code com.example.widgets:beta} for details.\n"
                ),
                new RewriteParameters(
                        "version with a qualifier no rule knows",
                        "com.example.widgets:beta:1.0.0.Final\n",
                        "com.example.widgets:beta:1.0.0.Final\n"
                ),
                new RewriteParameters(
                        "coordinate already at the release",
                        "com.example.widgets:beta:2.0.0\n",
                        "com.example.widgets:beta:2.0.0\n"
                ),
                new RewriteParameters(
                        "coordinate already at the next version",
                        "com.example.widgets:beta:2.1.0-SNAPSHOT\n",
                        "com.example.widgets:beta:2.1.0-SNAPSHOT\n"
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
        // ACT //
        FileRewrite rewrite = new Rewriter(ANCHORS, VERSIONS)
                .rewrite("com.example.widgets:beta:1.2.3\ncom.example.widgets:beta:2.0.0-SNAPSHOT\nplain\n");

        // ASSERT //
        assertEquals(
                List.of(
                        new Rewriter.Edit(1, Form.COORDINATE, LAST, RELEASE),
                        new Rewriter.Edit(2, Form.COORDINATE, DEV, NEXT)
                ),
                rewrite.edits()
        );
        assertTrue(rewrite.changed());
    }

    static Stream<RewriteParameters> rewrite__jarNames() {
        return Stream.of(
                new RewriteParameters("own jar", "beta-1.2.3.jar\n", "beta-2.0.0.jar\n"),
                new RewriteParameters(
                        "own jar with a hyphenated module",
                        "    alpha-core-1.2.3.jar\n",
                        "    alpha-core-2.0.0.jar\n"
                ),
                new RewriteParameters(
                        "sources jar",
                        "alpha-core-1.2.3-sources.jar\n",
                        "alpha-core-2.0.0-sources.jar\n"
                ),
                new RewriteParameters("jar with a release candidate", "beta-1.2.3-rc1.jar\n", "beta-2.0.0.jar\n"),
                new RewriteParameters("foreign jar", "jline-3.30.5.jar\n", "jline-3.30.5.jar\n"),
                new RewriteParameters("jar that ends in a module name", "my-beta-1.2.3.jar\n", "my-beta-1.2.3.jar\n"),
                new RewriteParameters("jar at the snapshot", "beta-2.0.0-SNAPSHOT.jar\n", "beta-2.0.0.jar\n"),
                new RewriteParameters("jar at another snapshot", "beta-1.9.0-SNAPSHOT.jar\n", "beta-2.0.0.jar\n"),
                new RewriteParameters("jar already at the release", "beta-2.0.0.jar\n", "beta-2.0.0.jar\n")
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
                new RewriteParameters("release block", own.formatted(LAST), own.formatted(RELEASE)),
                new RewriteParameters("snapshot block", own.formatted(DEV), own.formatted(NEXT)),
                new RewriteParameters(
                        "snapshot block of an older version",
                        own.formatted("1.9.0-SNAPSHOT"),
                        own.formatted(NEXT)
                ),
                new RewriteParameters("block already at the release", own.formatted(RELEASE), own.formatted(RELEASE)),
                new RewriteParameters("foreign group", foreign.formatted(LAST), foreign.formatted(LAST)),
                new RewriteParameters(
                        "artifact that is no module",
                        unknownArtifact.formatted(LAST),
                        unknownArtifact.formatted(LAST)
                ),
                new RewriteParameters("one-line block", compact.formatted(LAST), compact.formatted(RELEASE)),
                new RewriteParameters(
                        "version held in a property",
                        own.formatted("${widgets.version}"),
                        own.formatted("${widgets.version}")
                ),
                new RewriteParameters(
                        "bare version element beside a foreign block",
                        "<version>2.0.0-SNAPSHOT</version>\n" + foreign.formatted(RELEASE),
                        "<version>2.0.0-SNAPSHOT</version>\n" + foreign.formatted(RELEASE)
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
                        "    id \"com.example.widgets.gizmo\" version \"1.2.3\"\n",
                        "    id \"com.example.widgets.gizmo\" version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "own id with single quotes",
                        "id 'com.example.widgets.gizmo' version '1.2.3'\n",
                        "id 'com.example.widgets.gizmo' version '2.0.0'\n"
                ),
                new RewriteParameters(
                        "own id in the Kotlin form",
                        "id(\"com.example.widgets.gizmo\") version \"1.2.3\"\n",
                        "id(\"com.example.widgets.gizmo\") version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "own id with several segments",
                        "id \"com.example.widgets.gizmo.deep\" version \"1.2.3\"\n",
                        "id \"com.example.widgets.gizmo.deep\" version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "foreign id",
                        "id \"org.other.gizmo\" version \"1.2.3\"\n",
                        "id \"org.other.gizmo\" version \"1.2.3\"\n"
                ),
                new RewriteParameters(
                        "id of a group that begins with the same letters",
                        "id \"com.example.widgetsmith.gizmo\" version \"1.2.3\"\n",
                        "id \"com.example.widgetsmith.gizmo\" version \"1.2.3\"\n"
                ),
                new RewriteParameters(
                        "own id at the snapshot",
                        "id \"com.example.widgets.gizmo\" version \"2.0.0-SNAPSHOT\"\n",
                        "id \"com.example.widgets.gizmo\" version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "own id at another snapshot",
                        "id \"com.example.widgets.gizmo\" version \"1.9.0-SNAPSHOT\"\n",
                        "id \"com.example.widgets.gizmo\" version \"2.0.0\"\n"
                ),
                new RewriteParameters(
                        "foreign id at the snapshot",
                        "id \"org.other.gizmo\" version \"2.0.0-SNAPSHOT\"\n",
                        "id \"org.other.gizmo\" version \"2.0.0-SNAPSHOT\"\n"
                ),
                new RewriteParameters(
                        "own id already at the release",
                        "id \"com.example.widgets.gizmo\" version \"2.0.0\"\n",
                        "id \"com.example.widgets.gizmo\" version \"2.0.0\"\n"
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__pluginIds(RewriteParameters parameters) {
        assertRewrites(parameters);
    }

    @Test
    void rewrite__repeatedRewriteChangesNothing() {
        // ARRANGE //
        Rewriter rewriter = new Rewriter(ANCHORS, VERSIONS);
        String once = rewriter
                .rewrite("com.example.widgets:beta:1.2.3\ncom.example.widgets:beta:2.0.0-SNAPSHOT\nbeta-1.2.3.jar\n")
                .text();

        // ACT //
        FileRewrite again = rewriter.rewrite(once);

        // ASSERT //
        assertFalse(again.changed());
        assertEquals(once, again.text());
        assertTrue(again.unmatched().isEmpty());
    }

    @Test
    void rewrite__repositoryDocs() throws IOException {
        // ARRANGE //
        assumeTrue(System.getProperty("versionReferences.repositoryRoot") != null, "set by the Gradle test task");
        Path root = Path.of(System.getProperty("versionReferences.repositoryRoot"));
        Anchors anchors = Anchors.of(
                System.getProperty("versionReferences.group"),
                System.getProperty("versionReferences.rootName"),
                Arrays.asList(System.getProperty("versionReferences.modules").split(","))
        );
        String release = System.getProperty("versionReferences.releaseVersion");
        String next = (release.equals("9.9.9") ? "9.9.8" : "9.9.9") + "-SNAPSHOT";
        ReleaseVersions versions = ReleaseVersions.of(release, next);

        // ACT //
        FileRewrite readme = rewriteFile(root.resolve("README.md"), anchors, versions);
        FileRewrite releaseNotes = rewriteFile(root.resolve("RELEASE.md"), anchors, versions);

        // ASSERT //
        assertTrue(readme.unmatched().isEmpty());
        assertTrue(releaseNotes.unmatched().isEmpty());
        assertTrue(
                readme.edits().stream().anyMatch(edit -> edit.form() == Form.COORDINATE),
                "README names the last release in its coordinates, so the release moves them"
        );
        assertTrue(
                readme.edits().stream().anyMatch(edit -> edit.form() == Form.JAR),
                "README names the last release in its jar filenames, so the release moves them"
        );
        assertTrue(
                readme.edits()
                        .stream()
                        .filter(edit -> edit.form() == Form.COORDINATE || edit.form() == Form.JAR)
                        .allMatch(edit -> edit.to().equals(release)),
                "README advertises releases only, so every coordinate and jar edit goes to the release"
        );
        assertTrue(
                releaseNotes.edits()
                        .stream()
                        .anyMatch(edit -> edit.form() == Form.COORDINATE && edit.to().equals(next)),
                "RELEASE.md shows the snapshot coordinate, so the next version moves it"
        );
    }

    @Test
    void rewrite__unchangedText() {
        // ARRANGE //
        String text = "plain\nnothing to rewrite here\n";

        // ACT //
        FileRewrite rewrite = new Rewriter(ANCHORS, VERSIONS).rewrite(text);

        // ASSERT //
        assertFalse(rewrite.changed());
        assertSame(text, rewrite.text());
    }

    static Stream<UnmatchedParameters> rewrite__unmatchedLines() {
        return Stream.of(
                new UnmatchedParameters(
                        "version with a qualifier no rule knows",
                        "ok\ncom.example.widgets:alpha-core:1.0.0.Final\n",
                        List.of(2)
                ),
                new UnmatchedParameters(
                        "version catalog entry",
                        "alpha = { module = \"com.example.widgets:alpha-core\", version = \"1.2.3\" }\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "second mention on a line a rule rewrites",
                        "com.example.widgets:beta:1.2.3 and com.example.widgets:alpha-core:1.0.0.Final\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "plugin id in a version catalog table",
                        "gizmo = { id = \"com.example.widgets.gizmo\", version = \"1.2.3\" }\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "plugin id in a version catalog string",
                        "gizmo = \"com.example.widgets.gizmo:1.2.3\"\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "unrelated version beside a rewritten mention",
                        "com.example.widgets:beta:1.2.3 needs Gradle 9.7.1\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "release and snapshot mentions that a rule rewrites",
                        "com.example.widgets:beta:2.0.0-SNAPSHOT or com.example.widgets:beta:1.2.3\n",
                        List.of()
                ),
                new UnmatchedParameters("sources jar a rule rewrites", "alpha-core-1.2.3-sources.jar\n", List.of()),
                new UnmatchedParameters(
                        "snapshot plugin id a rule rewrites",
                        "id \"com.example.widgets.gizmo\" version \"1.0.0-SNAPSHOT\"\n",
                        List.of()
                ),
                new UnmatchedParameters("dependency block with an artifact that is no module", """
                        <dependency>
                        <groupId>com.example.widgets</groupId>
                        <artifactId>gizmo</artifactId>
                        <version>1.2.3</version>
                        </dependency>
                        """, List.of(4)),
                new UnmatchedParameters(
                        "module jar stem with a version no rule reads",
                        "copy beta-1.2.3 into place\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "snapshot with a qualifier no rule knows",
                        "com.example.widgets:alpha-core:3.0.0-SNAPSHOT.Final\n",
                        List.of(1)
                ),
                new UnmatchedParameters(
                        "class name beside a ceiling",
                        "@Gate(type = com.example.widgets.Thing.class, ceiling = \"0.7\")\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "bare group beside a licence version",
                        "excludeGroups = ['com.example.widgets'] // Apache 2.0\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "line already at the release",
                        "com.example.widgets:alpha-core:2.0.0\n",
                        List.of()
                ),
                new UnmatchedParameters(
                        "line already at the next version",
                        "com.example.widgets:alpha-core:2.1.0-SNAPSHOT\n",
                        List.of()
                ),
                new UnmatchedParameters("line a rule rewrites", "com.example.widgets:alpha-core:1.2.3\n", List.of()),
                new UnmatchedParameters("text that names nothing", "Version 1.2.3 of nothing.\n", List.of())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void rewrite__unmatchedLines(UnmatchedParameters parameters) {
        // ACT //
        List<Integer> lines = new Rewriter(ANCHORS, VERSIONS).rewrite(parameters.text()).unmatched();

        // ASSERT //
        assertEquals(parameters.lines(), lines);
    }

    /**
     * Rewrites a file under the given anchors without writing it back.
     *
     * @param file the file
     * @param anchors the names the rules are anchored to
     * @param versions the release version and the next version
     * @return the rewrite
     * @throws IOException if the file cannot be read
     */
    private static FileRewrite rewriteFile(Path file, Anchors anchors, ReleaseVersions versions) throws IOException {
        return new Rewriter(anchors, versions).rewrite(readFile(file));
    }
}

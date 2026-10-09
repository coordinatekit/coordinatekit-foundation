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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Unit and functional tests for {@link BumpVersionPlugin}. What the plugin wires onto the task is
 * read straight off it through {@link ProjectBuilder}. Everything that only a real build can show,
 * the version and group read late, the Git index read from a real repository, files rewritten on
 * disk, and the configuration cache reusing the task, runs through TestKit against a throwaway
 * repository in one of three shapes the consuming repositories have: the version in
 * {@code gradle.properties} beside several modules, the version in {@code build.gradle}, and a
 * single project that is documented by its archive and its printed version line.
 *
 * <p>
 * The fixture builds run in daemons that the test JVM's coverage agent cannot reach. When the test
 * task sets {@code testKit.fixtureJvmArgs}, {@code writeFixture} passes it on through each
 * fixture's {@code gradle.properties}, which is how the build records their coverage. Without it,
 * as under an IDE's own test runner, the fixtures run the same and record nothing.
 */
class BumpVersionPluginTest {
    /** The group every fixture build declares. */
    private static final String GROUP = "com.example.widgets";

    /** The id the plugin is applied under. */
    private static final String PLUGIN_ID = "org.coordinatekit.foundation.bump-version";

    @Test
    void apply__failsOnASubproject() {
        // ARRANGE //
        Project root = ProjectBuilder.builder().build();
        Project child = ProjectBuilder.builder().withParent(root).withName("child").build();

        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> child.getPluginManager().apply(PLUGIN_ID));

        // ASSERT //
        // Gradle wraps whatever a plugin throws from apply.
        String cause = thrown.getCause().getMessage();
        assertTrue(cause.contains("root project"), cause);
        assertTrue(cause.contains(":child"), cause);
    }

    @Test
    void apply__readsTheGroupAndVersionWhenTheyAreAsked() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(PLUGIN_ID);
        BumpVersion task = bumpVersion(project);

        // ACT //
        project.setGroup("com.example.late");
        project.setVersion("9.9.9");

        // ASSERT //
        assertEquals("com.example.late", task.getProjectGroup().get());
        assertEquals("9.9.9", task.getBuildVersion().get());
    }

    @Test
    void apply__registersTheTaskAndTheExtension() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().withName("widgets").build();

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);

        // ASSERT //
        BumpVersion task = bumpVersion(project);
        assertEquals("release", task.getGroup());
        assertEquals("widgets", task.getRootProjectName().get());
        assertEquals(project.getProjectDir(), task.getRootDirectory().get().getAsFile());
        assertTrue(task.getExcludes().get().isEmpty());
        assertTrue(project.getExtensions().getByType(BumpVersionExtension.class).getExcludes().get().isEmpty());
    }

    @Test
    void apply__wiresTheExcludesFromTheExtension() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(PLUGIN_ID);

        // ACT //
        project.getExtensions().getByType(BumpVersionExtension.class).exclude("docs/**");

        // ASSERT //
        assertEquals(List.of("docs/**"), bumpVersion(project).getExcludes().get());
    }

    @Test
    void apply__wiresTheSubprojectNames() {
        // ARRANGE //
        Project root = ProjectBuilder.builder().build();
        ProjectBuilder.builder().withParent(root).withName("beta").build();
        ProjectBuilder.builder().withParent(root).withName("alpha").build();

        // ACT //
        root.getPluginManager().apply(PLUGIN_ID);

        // ASSERT //
        assertEquals(List.of("alpha", "beta"), bumpVersion(root).getModules().get());
    }

    /**
     * Returns the task the plugin registered.
     *
     * @param project the project the plugin was applied to
     * @return the {@code bumpVersion} task
     */
    private static BumpVersion bumpVersion(Project project) {
        return (BumpVersion) project.getTasks().getByName("bumpVersion");
    }

    @Test
    void bumpVersion__ignoresTheGitEnvironment(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        Path project = Files.createDirectory(directory.resolve("project"));
        Path decoy = Files.createDirectory(directory.resolve("decoy"));
        writeFoundationFixture(project, "1.2.0-SNAPSHOT", "");
        Files.writeString(decoy.resolve("decoy.txt"), "version=1.2.0-SNAPSHOT\n");
        try (Git git = Git.init().setDirectory(decoy.toFile()).call()) {
            git.add().addFilepattern("decoy.txt").call();
        }
        track(project);

        // ACT //
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("GIT_DIR", decoy.resolve(".git").toString());
        environment.put("GIT_INDEX_FILE", decoy.resolve(".git/index").toString());
        environment.put("GIT_WORK_TREE", decoy.toString());
        runner(project).withEnvironment(environment).withArguments("bumpVersion", "--to=1.2.0").build();

        // ASSERT //
        assertTrue(Files.readString(project.resolve("gradle.properties")).contains("version=1.2.0\n"));
        assertEquals("version=1.2.0-SNAPSHOT\n", Files.readString(decoy.resolve("decoy.txt")));
    }

    @Test
    void bumpVersion__leavesAnExcludedFileAlone(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFoundationFixture(directory, "1.2.0-SNAPSHOT", """

                foundationVersion {
                    exclude "docs/**"
                }
                """);
        Files.createDirectories(directory.resolve("docs"));
        Files.writeString(directory.resolve("docs/guide.md"), "com.example.widgets:beta:1.1.0\n");
        track(directory);

        // ACT //
        runner(directory).withArguments("bumpVersion", "--to=1.2.0").build();

        // ASSERT //
        assertEquals("com.example.widgets:beta:1.1.0\n", Files.readString(directory.resolve("docs/guide.md")));
        assertTrue(Files.readString(directory.resolve("README.md")).contains("com.example.widgets:beta:1.2.0"));
    }

    @Test
    void bumpVersion__movesASingleProject(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, "1.2.0-SNAPSHOT", "");
        Files.writeString(directory.resolve("README.md"), """
                widgets 1.1.0 (build 7)
                Run widgets-1.1.0/bin/widgets from widgets-1.1.0.tar.gz or widgets-1.1.0.jar.
                <dependency>
                    <groupId>com.example.widgets</groupId>
                    <artifactId>widgets</artifactId>
                    <version>1.1.0</version>
                </dependency>
                """);
        track(directory);

        // ACT //
        runner(directory).withArguments("bumpVersion", "--to=1.2.0").build();

        // ASSERT //
        assertEquals("""
                widgets 1.2.0 (build 7)
                Run widgets-1.2.0/bin/widgets from widgets-1.2.0.tar.gz or widgets-1.2.0.jar.
                <dependency>
                    <groupId>com.example.widgets</groupId>
                    <artifactId>widgets</artifactId>
                    <version>1.2.0</version>
                </dependency>
                """, Files.readString(directory.resolve("README.md")));
    }

    @Test
    void bumpVersion__movesAVersionDeclaredInTheBuildScript(@TempDir Path directory)
            throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, null, "version = \"1.2.0-SNAPSHOT\"\n", "alpha-core", "beta");
        Files.writeString(directory.resolve("README.md"), "Download widgets-1.1.0.tar.gz or alpha-core-1.1.0.jar.\n");
        track(directory);

        // ACT //
        BuildResult result = runner(directory).withArguments("bumpVersion", "--to=1.2.0").build();

        // ASSERT //
        assertTrue(Files.readString(directory.resolve("build.gradle")).contains("version = \"1.2.0\""));
        assertEquals(
                "Download widgets-1.2.0.tar.gz or alpha-core-1.2.0.jar.\n",
                Files.readString(directory.resolve("README.md"))
        );
        assertTrue(result.getOutput().contains("Updated version: 1.2.0-SNAPSHOT -> 1.2.0"), result.getOutput());
    }

    @Test
    void bumpVersion__movesFromAVersionTheBuildDoesNotKnow(@TempDir Path directory)
            throws GitAPIException, IOException {
        // ARRANGE //
        writeFixture(directory, null, "", "beta");
        Files.writeString(directory.resolve("README.md"), "com.example.widgets:beta:1.1.0\n");
        track(directory);

        // ACT //
        BuildResult refused = runner(directory).withArguments("bumpVersion", "--to=1.2.0").buildAndFail();
        runner(directory).withArguments("bumpVersion", "--from=1.1.0", "--to=1.2.0").build();

        // ASSERT //
        assertTrue(refused.getOutput().contains("declares no version"), refused.getOutput());
        assertEquals("com.example.widgets:beta:1.2.0\n", Files.readString(directory.resolve("README.md")));
    }

    @Test
    void bumpVersion__movesTheFilesAfterARelease(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFoundationFixture(directory, "1.2.0", "");
        track(directory);
        Map<String, String> before = read(directory);

        // ACT //
        runner(directory).withArguments("bumpVersion", "--to=1.3.0-SNAPSHOT").build();

        // ASSERT //
        assertTrue(Files.readString(directory.resolve("gradle.properties")).contains("version=1.3.0-SNAPSHOT\n"));
        assertEquals(before.get("README.md"), Files.readString(directory.resolve("README.md")));
        String releaseNotes = Files.readString(directory.resolve("RELEASE.md"));
        assertTrue(releaseNotes.contains("com.example.widgets:beta:1.3.0-SNAPSHOT"), releaseNotes);
    }

    @Test
    void bumpVersion__movesTheFilesForARelease(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFoundationFixture(directory, "1.2.0-SNAPSHOT", "");
        Files.writeString(directory.resolve("NOTES.md"), "version=1.2.0-SNAPSHOT\n");
        Files.write(directory.resolve("blob.bin"), "\0version=1.2.0-SNAPSHOT\n".getBytes(StandardCharsets.ISO_8859_1));
        track(directory, "NOTES.md");
        Map<String, String> before = read(directory);

        // ACT //
        BuildResult result = runner(directory).withArguments("bumpVersion", "--to=1.2.0").build();

        // ASSERT //
        assertTrue(Files.readString(directory.resolve("gradle.properties")).contains("version=1.2.0\n"));
        String readme = Files.readString(directory.resolve("README.md"));
        assertTrue(readme.contains("com.example.widgets:alpha-core:1.2.0"), readme);
        assertTrue(readme.contains("beta-1.2.0.jar"), readme);
        assertEquals(before.get("RELEASE.md"), Files.readString(directory.resolve("RELEASE.md")));
        assertEquals("version=1.2.0-SNAPSHOT\n", Files.readString(directory.resolve("NOTES.md")));
        assertTrue(result.getOutput().contains("Updated version: 1.2.0-SNAPSHOT -> 1.2.0"), result.getOutput());
        // gradle.properties and RELEASE.md name the current version once each. The untracked NOTES.md and
        // the
        // binary blob.bin name it too and are not counted.
        assertTrue(result.getOutput().contains("Found 2 occurrence(s) of '1.2.0-SNAPSHOT'"), result.getOutput());
        assertTrue(result.getOutput().contains("README.md ("), result.getOutput());
        assertEquals(before.get("blob.bin"), read(directory).get("blob.bin"));
    }

    @Test
    void bumpVersion__refusesWithoutATarget(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFoundationFixture(directory, "1.2.0-SNAPSHOT", "");
        track(directory);

        // ACT //
        BuildResult result = runner(directory).withArguments("bumpVersion").buildAndFail();

        // ASSERT //
        assertTrue(result.getOutput().contains("Pass the new version with --to"), result.getOutput());
    }

    @Test
    void bumpVersion__reusesTheConfigurationCache(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        writeFoundationFixture(directory, "1.2.0-SNAPSHOT", "");
        track(directory);
        Map<String, String> before = read(directory);
        GradleRunner runner = runner(directory).withArguments("bumpVersion", "--to=1.2.0", "--configuration-cache");
        runner.build();
        // The bump changed gradle.properties, which is an input of the cache entry, so the bytes go back.
        restore(directory, before);

        // ACT //
        BuildResult result = runner.build();

        // ASSERT //
        assertTrue(result.getOutput().contains("Reusing configuration cache"), result.getOutput());
        assertTrue(Files.readString(directory.resolve("gradle.properties")).contains("version=1.2.0\n"));
    }

    @Test
    void bumpVersion__warnsAboutAnExcludeThatMatchesNothing(@TempDir Path directory)
            throws GitAPIException, IOException {
        // ARRANGE //
        writeFoundationFixture(directory, "1.2.0-SNAPSHOT", """

                foundationVersion {
                    exclude "./docs/**", "README.md"
                }
                """);
        Files.createDirectories(directory.resolve("docs"));
        Files.writeString(directory.resolve("docs/guide.md"), "com.example.widgets:beta:1.1.0\n");
        track(directory);

        // ACT //
        BuildResult result = runner(directory).withArguments("bumpVersion", "--to=1.2.0").build();

        // ASSERT //
        String output = result.getOutput();
        assertTrue(output.contains("The exclude './docs/**' matches no tracked file"), output);
        assertFalse(output.contains("The exclude 'README.md'"), output);
        // The misspelt glob protected nothing, which is the harm the warning is there to point at.
        assertEquals("com.example.widgets:beta:1.2.0\n", Files.readString(directory.resolve("docs/guide.md")));
    }

    @Test
    void bumpVersion__writesNothingWhenALineIsNotRecognised(@TempDir Path directory)
            throws GitAPIException, IOException {
        // ARRANGE //
        writeFoundationFixture(directory, "1.2.0-SNAPSHOT", "");
        Files.writeString(
                directory.resolve("README.md"),
                "com.example.widgets:beta:1.1.0\ncom.example.widgets:beta:1.0.0.Final\n"
        );
        track(directory);
        Map<String, String> before = read(directory);

        // ACT //
        BuildResult result = runner(directory).withArguments("bumpVersion", "--to=1.2.0").buildAndFail();

        // ASSERT //
        assertTrue(result.getOutput().contains("README.md:2"), result.getOutput());
        assertTrue(result.getOutput().contains("Nothing was written"), result.getOutput());
        assertEquals(before, read(directory));
    }

    @Test
    void exclude__appends() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(PLUGIN_ID);
        BumpVersionExtension extension = project.getExtensions().getByType(BumpVersionExtension.class);

        // ACT //
        extension.exclude("a/**", "b.txt");
        extension.exclude("c");

        // ASSERT //
        assertEquals(List.of("a/**", "b.txt", "c"), extension.getExcludes().get());
    }

    /**
     * Reads every file the fixture's Git index tracks, so a comparison across a bump covers the whole
     * tracked tree and not a chosen few. The bytes are decoded as ISO-8859-1, which keeps any byte
     * intact, a binary file's included.
     *
     * @param directory the fixture's project directory, already a Git repository
     * @return the files' content, by path
     * @throws IOException if the index or a file cannot be read
     */
    private static Map<String, String> read(Path directory) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (Git git = Git.open(directory.toFile())) {
            DirCache index = git.getRepository().readDirCache();
            for (int i = 0; i < index.getEntryCount(); i++) {
                String path = index.getEntry(i).getPathString();
                files.put(path, new String(Files.readAllBytes(directory.resolve(path)), StandardCharsets.ISO_8859_1));
            }
        }
        return files;
    }

    /**
     * Writes files back as they were.
     *
     * @param directory the fixture's project directory
     * @param files the content to restore, by path, as {@link #read} returns it
     * @throws IOException if a file cannot be written
     */
    private static void restore(Path directory, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.write(directory.resolve(file.getKey()), file.getValue().getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    /**
     * Returns a runner for a fixture build, with the plugin under test on its classpath.
     *
     * @param directory the fixture's project directory
     * @return a runner rooted there
     */
    private static GradleRunner runner(Path directory) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath();
    }

    /**
     * Turns the directory into a Git repository whose index holds every file but the ones left out.
     *
     * @param directory the project directory, which becomes the repository
     * @param untracked paths to leave out of the index
     * @throws GitAPIException if a Git operation fails
     * @throws IOException if the repository cannot be created
     */
    private static void track(Path directory, String... untracked) throws GitAPIException, IOException {
        try (Git git = Git.init().setDirectory(directory.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            for (String path : untracked) {
                git.rm().setCached(true).addFilepattern(path).call();
            }
        }
    }

    /**
     * Writes a consumer build that applies the plugin, followed by the given text. The build gets a
     * {@code gradle.properties} carrying the version when there is one, and the daemon arguments from
     * {@code testKit.fixtureJvmArgs} when the test task sets it. Each module gets a directory.
     *
     * @param directory the project directory to write into
     * @param version the version for {@code gradle.properties}, or {@code null} to leave it out
     * @param block Gradle script appended after the group
     * @param modules the subprojects to include
     * @throws IOException if the fixture cannot be written
     */
    private static void writeFixture(Path directory, @Nullable String version, String block, String... modules)
            throws IOException {
        StringBuilder settings = new StringBuilder("rootProject.name = \"widgets\"\n");
        for (String module : modules) {
            Files.createDirectories(directory.resolve(module));
            Files.writeString(directory.resolve(module).resolve("placeholder.txt"), module);
            settings.append("include \"").append(module).append("\"\n");
        }
        Files.writeString(directory.resolve("settings.gradle"), settings.toString());
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id "%s"
                }

                allprojects {
                    group = "%s"
                }
                """.formatted(PLUGIN_ID, GROUP) + block);

        Properties properties = new Properties();
        if (version != null) {
            properties.setProperty("version", version);
        }
        String jvmArgs = System.getProperty("testKit.fixtureJvmArgs");
        if (jvmArgs != null) {
            properties.setProperty("org.gradle.jvmargs", jvmArgs);
        }
        try (OutputStream file = Files.newOutputStream(directory.resolve("gradle.properties"))) {
            properties.store(file, null);
        }
    }

    /**
     * Writes a build the way foundation's is shaped: the version in {@code gradle.properties}, several
     * modules, last-release coordinates and jar names in {@code README.md}, and snapshot coordinates in
     * {@code RELEASE.md}.
     *
     * @param directory the project directory
     * @param version the version the build declares
     * @param block Gradle script appended to the build
     * @throws IOException if the fixture cannot be written
     */
    private static void writeFoundationFixture(Path directory, String version, String block) throws IOException {
        writeFixture(directory, version, block, "alpha-core", "beta");
        Files.writeString(directory.resolve("README.md"), """
                Release coordinates:

                    com.example.widgets:alpha-core:1.1.0
                    com.example.widgets:beta:1.1.0

                Jars: beta-1.1.0.jar and jline-3.30.5.jar.

                Plugin: id "com.example.widgets.gizmo" version "1.1.0"
                """);
        Files.writeString(directory.resolve("RELEASE.md"), """
                Consuming snapshots:

                    com.example.widgets:beta:%s
                """.formatted(version.endsWith("-SNAPSHOT") ? version : version + "-SNAPSHOT"));
    }
}

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
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Tests {@link TrackedFiles} against real repositories that JGit writes into a temporary directory.
 * The index is what the bump reads, so most cases build one directly and leave the working tree to
 * disagree with it in the one way the case is about.
 */
class TrackedFilesTest {
    /**
     * One exclusion glob against one path.
     *
     * @param name what the case shows
     * @param glob the glob
     * @param path the path, relative to the project directory
     * @param excluded whether the glob should exclude the path
     */
    private record ExcludedParameters(String name, String glob, String path, boolean excluded) {}

    /**
     * Content with at most one NUL byte, to be sniffed for binary.
     *
     * @param name what the case shows
     * @param length the content's length
     * @param nulAt the offset of the NUL byte, or {@code -1} for none
     * @param binary whether the content should be judged binary
     */
    private record BinaryParameters(String name, int length, int nulAt, boolean binary) {}

    /**
     * One exclusion glob against a fixed set of tracked paths.
     *
     * @param name what the case shows
     * @param glob the glob
     * @param used whether the glob matches some tracked path
     */
    private record UnusedParameters(String name, String glob, boolean used) {}

    /**
     * Builds an index entry for a path, with an object id that is never read.
     *
     * @param path the path
     * @param stage the merge stage
     * @param mode the file mode
     * @return the entry
     */
    private static DirCacheEntry entry(String path, int stage, FileMode mode) {
        DirCacheEntry entry = new DirCacheEntry(path, stage);
        entry.setFileMode(mode);
        return entry;
    }

    static Stream<BinaryParameters> isBinary__window() {
        return Stream.of(
                new BinaryParameters("empty", 0, -1, false),
                new BinaryParameters("text", 100, -1, false),
                new BinaryParameters("NUL first", 100, 0, true),
                new BinaryParameters("NUL last inside the window", 9000, 7999, true),
                new BinaryParameters("NUL first outside the window", 9000, 8000, false),
                new BinaryParameters("NUL at the end of a short file", 10, 9, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isBinary__window(BinaryParameters parameters) {
        // ARRANGE //
        byte[] content = new byte[parameters.length()];
        Arrays.fill(content, (byte) 'a');
        if (parameters.nulAt() >= 0) {
            content[parameters.nulAt()] = 0;
        }

        // ACT / ASSERT //
        assertEquals(parameters.binary(), TrackedFiles.isBinary(content));
    }

    @Test
    void isExcluded__anyGlobCounts() {
        // ACT / ASSERT //
        assertTrue(TrackedFiles.isExcluded("docs/x.md", TrackedFiles.excludePatterns(List.of("src/**", "docs/*.md"))));
    }

    static Stream<ExcludedParameters> isExcluded__globs() {
        return Stream.of(
                new ExcludedParameters("exact path", "docs/guide.md", "docs/guide.md", true),
                new ExcludedParameters("other path", "docs/guide.md", "docs/other.md", false),
                new ExcludedParameters("star within a segment", "docs/*.md", "docs/guide.md", true),
                new ExcludedParameters("star stops at a slash", "docs/*.md", "docs/deep/guide.md", false),
                new ExcludedParameters("question mark", "docs/guid?.md", "docs/guide.md", true),
                new ExcludedParameters("question mark is one character", "docs/guid?.md", "docs/guid.md", false),
                new ExcludedParameters("double star crosses segments", "docs/**", "docs/a/b/c.md", true),
                new ExcludedParameters("double star needs the directory", "docs/**", "docs", false),
                new ExcludedParameters("leading double star at the root", "**/guide.md", "guide.md", true),
                new ExcludedParameters("leading double star deeper", "**/guide.md", "a/b/guide.md", true),
                new ExcludedParameters("leading double star with a different name", "**/guide.md", "a/other.md", false),
                new ExcludedParameters("middle double star with no directory", "a/**/c.md", "a/c.md", true),
                new ExcludedParameters("middle double star with directories", "a/**/c.md", "a/b/b/c.md", true),
                new ExcludedParameters(
                        "trailing slash excludes a directory",
                        "docs/archive/",
                        "docs/archive/x.md",
                        true
                ),
                new ExcludedParameters("trailing slash leaves a sibling", "docs/archive/", "docs/archive2/x.md", false),
                new ExcludedParameters("dot is literal", "a.b", "axb", false),
                new ExcludedParameters("regex characters are literal", "a+(b)", "a+(b)", true),
                new ExcludedParameters("a root only star", "*.md", "docs/x.md", false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void isExcluded__globs(ExcludedParameters parameters) {
        // ACT / ASSERT //
        assertEquals(
                parameters.excluded(),
                TrackedFiles.isExcluded(parameters.path(), TrackedFiles.excludePatterns(List.of(parameters.glob())))
        );
    }

    @Test
    void isExcluded__noGlobsExcludesNothing() {
        // ACT / ASSERT //
        assertFalse(TrackedFiles.isExcluded("README.md", TrackedFiles.excludePatterns(List.of())));
    }

    @Test
    void list__bareRepository(@TempDir Path directory) throws GitAPIException {
        // ARRANGE //
        Git.init().setDirectory(directory.toFile()).setBare(true).call().close();

        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> TrackedFiles.list(directory));

        // ASSERT //
        assertTrue(thrown.getMessage().contains("bare"), thrown.getMessage());
    }

    @Test
    void list__dedupesConflictStages(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("a.txt"), "a");
            Files.writeString(directory.resolve("c.txt"), "c");
            writeIndex(
                    git.getRepository(),
                    entry("a.txt", DirCacheEntry.STAGE_0, FileMode.REGULAR_FILE),
                    entry("c.txt", DirCacheEntry.STAGE_1, FileMode.REGULAR_FILE),
                    entry("c.txt", DirCacheEntry.STAGE_2, FileMode.REGULAR_FILE),
                    entry("c.txt", DirCacheEntry.STAGE_3, FileMode.REGULAR_FILE)
            );

            // ACT //
            List<String> paths = List.copyOf(TrackedFiles.list(directory));

            // ASSERT //
            assertEquals(List.of("a.txt", "c.txt"), paths);
        }
    }

    @Test
    void list__indexOnly(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("tracked.txt"), "t");
            Files.writeString(directory.resolve("untracked.txt"), "u");
            git.add().addFilepattern("tracked.txt").call();

            // ACT //
            List<String> paths = List.copyOf(TrackedFiles.list(directory));

            // ASSERT //
            assertEquals(List.of("tracked.txt"), paths);
        }
    }

    @Test
    void list__keepsExecutableFiles(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("run.sh"), "#!/bin/sh");
            writeIndex(git.getRepository(), entry("run.sh", DirCacheEntry.STAGE_0, FileMode.EXECUTABLE_FILE));

            // ACT //
            List<String> paths = List.copyOf(TrackedFiles.list(directory));

            // ASSERT //
            assertEquals(List.of("run.sh"), paths);
        }
    }

    @Test
    void list__linkedWorktree(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        Path main = Files.createDirectory(directory.resolve("main"));
        Path worktree = Files.createDirectory(directory.resolve("wt"));
        Git.init().setDirectory(main.toFile()).setInitialBranch("main").call().close();
        Path admin = Files.createDirectories(main.resolve(".git/worktrees/wt"));
        Files.writeString(worktree.resolve(".git"), "gitdir: " + admin.toAbsolutePath() + "\n");
        Files.writeString(admin.resolve("commondir"), "../..\n");
        Files.writeString(admin.resolve("HEAD"), "ref: refs/heads/main\n");
        Files.writeString(admin.resolve("gitdir"), worktree.resolve(".git").toAbsolutePath() + "\n");
        Files.writeString(worktree.resolve("linked.txt"), "l");
        Files.writeString(main.resolve("main-only.txt"), "m");
        writeIndex(admin.resolve("index"), entry("linked.txt", DirCacheEntry.STAGE_0, FileMode.REGULAR_FILE));

        // ACT //
        List<String> paths = List.copyOf(TrackedFiles.list(worktree));

        // ASSERT //
        assertEquals(List.of("linked.txt"), paths);
    }

    @Test
    void list__missingFromDisk(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("kept.txt"), "k");
            Files.writeString(directory.resolve("gone.txt"), "g");
            git.add().addFilepattern(".").call();
            Files.delete(directory.resolve("gone.txt"));

            // ACT //
            List<String> paths = List.copyOf(TrackedFiles.list(directory));

            // ASSERT //
            assertEquals(List.of("kept.txt"), paths);
        }
    }

    @Test
    void list__notARepository(@TempDir Path directory) {
        // ACT //
        GradleException thrown = assertThrows(GradleException.class, () -> TrackedFiles.list(directory));

        // ASSERT //
        assertTrue(thrown.getMessage().contains("not inside a Git repository"), thrown.getMessage());
    }

    @Test
    void list__rootInASubdirectory(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.createDirectories(directory.resolve("sub/deep"));
            Files.createDirectories(directory.resolve("other"));
            Files.writeString(directory.resolve("top.txt"), "t");
            Files.writeString(directory.resolve("sub/inside.txt"), "i");
            Files.writeString(directory.resolve("sub/deep/deeper.txt"), "d");
            Files.writeString(directory.resolve("other/outside.txt"), "o");
            git.add().addFilepattern(".").call();

            // ACT //
            List<String> paths = List.copyOf(TrackedFiles.list(directory.resolve("sub")));

            // ASSERT //
            assertEquals(List.of("deep/deeper.txt", "inside.txt"), paths);
        }
    }

    @Test
    void list__skipsSubmodules(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.createDirectories(directory.resolve("vendor"));
            Files.writeString(directory.resolve("vendor/inside.txt"), "v");
            Files.writeString(directory.resolve("kept.txt"), "k");
            writeIndex(
                    git.getRepository(),
                    entry("kept.txt", DirCacheEntry.STAGE_0, FileMode.REGULAR_FILE),
                    entry("vendor", DirCacheEntry.STAGE_0, FileMode.GITLINK)
            );

            // ACT //
            List<String> paths = List.copyOf(TrackedFiles.list(directory));

            // ASSERT //
            assertEquals(List.of("kept.txt"), paths);
        }
    }

    @Test
    void list__skipsSymbolicLinks(@TempDir Path directory) throws GitAPIException, IOException {
        // ARRANGE //
        assumeFalse(
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
                "symbolic links need privileges"
        );
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.writeString(directory.resolve("real.txt"), "r");
            Files.writeString(directory.resolve("swapped.txt"), "s");
            Files.createSymbolicLink(directory.resolve("link.txt"), Path.of("real.txt"));
            git.add().addFilepattern(".").call();
            Files.delete(directory.resolve("swapped.txt"));
            Files.createSymbolicLink(directory.resolve("swapped.txt"), Path.of("real.txt"));

            // ACT //
            List<String> paths = List.copyOf(TrackedFiles.list(directory));

            // ASSERT //
            assertEquals(List.of("real.txt"), paths);
        }
    }

    static Stream<UnusedParameters> unusedGlobs__globs() {
        return Stream.of(
                new UnusedParameters("matching glob", "docs/**", true),
                new UnusedParameters("typo in a directory", "doc/**", false),
                new UnusedParameters("leading dot slash", "./docs/**", false),
                new UnusedParameters("leading slash", "/docs/guide.md", false),
                new UnusedParameters("exact path", "docs/guide.md", true),
                new UnusedParameters("directory with a trailing slash", "docs/", true),
                new UnusedParameters("glob that matches a directory name only", "docs", false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void unusedGlobs__globs(UnusedParameters parameters) {
        // ARRANGE //
        List<String> paths = List.of("README.md", "docs/guide.md");

        // ACT //
        List<String> unused = TrackedFiles.unusedGlobs(paths, List.of(parameters.glob()));

        // ASSERT //
        assertEquals(parameters.used() ? List.of() : List.of(parameters.glob()), unused);
    }

    @Test
    void unusedGlobs__keepsTheOrderGiven() {
        // ACT / ASSERT //
        assertEquals(
                List.of("z/**", "a/**"),
                TrackedFiles.unusedGlobs(List.of("docs/guide.md"), List.of("z/**", "docs/**", "a/**"))
        );
    }

    @Test
    void unusedGlobs__noGlobsReportsNothing() {
        // ACT / ASSERT //
        assertEquals(List.of(), TrackedFiles.unusedGlobs(List.of("README.md"), List.of()));
    }

    /**
     * Replaces the repository's index with exactly these entries.
     *
     * @param repository the repository
     * @param entries the entries
     * @throws IOException if the index cannot be written
     */
    private static void writeIndex(Repository repository, DirCacheEntry... entries) throws IOException {
        writeIndex(repository.getIndexFile().toPath(), entries);
    }

    /**
     * Writes an index file with exactly these entries.
     *
     * @param indexFile where the index goes
     * @param entries the entries
     * @throws IOException if the index cannot be written
     */
    private static void writeIndex(Path indexFile, DirCacheEntry... entries) throws IOException {
        DirCache cache = DirCache.lock(indexFile.toFile(), FS.DETECTED);
        DirCacheBuilder builder = cache.builder();
        for (DirCacheEntry entry : entries) {
            builder.add(entry);
        }
        builder.commit();
    }
}

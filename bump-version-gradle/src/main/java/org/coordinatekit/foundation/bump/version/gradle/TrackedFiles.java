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

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Decides which files a bump reads: the ones Git tracks under the project directory, minus the
 * binary ones and the ones a build excludes.
 *
 * <p>
 * The tracked files come from Git's index, read through JGit, and not from walking the directory,
 * so an untracked scratch file and a build output are never touched. The repository is found by
 * searching up from the project directory and never from the environment, so a {@code GIT_DIR} or
 * {@code GIT_INDEX_FILE} that a Git hook leaves set cannot point the bump at another repository.
 */
final class TrackedFiles {
    /** How many leading bytes {@link #isBinary} inspects, the same window Git itself uses. */
    private static final int BINARY_WINDOW = 8000;

    private TrackedFiles() {}

    /**
     * Whether content looks like a binary file, which is the case when a NUL byte appears in its first
     * {@value #BINARY_WINDOW} bytes.
     *
     * @param content the file's bytes
     * @return {@code true} if the file should be left alone
     */
    static boolean isBinary(byte[] content) {
        int window = Math.min(content.length, BINARY_WINDOW);
        for (int i = 0; i < window; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a path matches any of a build's exclusion globs.
     *
     * <p>
     * Paths and globs use {@code /} and are relative to the project directory. {@code *} matches any
     * run of characters within one path segment, {@code ?} matches one such character, and a double
     * {@code *} matches across segments. A double {@code *} segment at the start also matches at the
     * root, so a glob made of it, a slash, and {@code *.md} covers {@code README.md} as well as
     * {@code docs/guide.md}. A glob ending in {@code /} excludes everything under that directory.
     *
     * @param path the path, relative to the project directory
     * @param globs the exclusion globs
     * @return {@code true} if some glob matches the whole path
     */
    static boolean isExcluded(String path, List<String> globs) {
        return globs.stream().anyMatch(glob -> toPattern(glob).matcher(path).matches());
    }

    /**
     * Lists the regular files Git tracks under a directory.
     *
     * <p>
     * An entry is left out when it is a symbolic link or a submodule, which a rewrite must not follow,
     * or when it is no longer on disk. A path that is in conflict appears once whatever its stages.
     *
     * @param root the project directory, which may sit below the top of its repository
     * @return the paths relative to {@code root}, with {@code /} as the separator, in order
     * @throws GradleException if {@code root} is not in a Git repository, the repository is bare, or
     *         the index cannot be read
     */
    static SortedSet<String> list(Path root) {
        try {
            Path real = root.toRealPath();
            FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(real.toFile());
            if (builder.getGitDir() == null) {
                throw new GradleException(
                        "bumpVersion lists the files Git tracks, and " + root + " is not inside a Git repository."
                );
            }
            try (Repository repository = builder.build()) {
                if (repository.isBare()) {
                    throw new GradleException(
                            "bumpVersion needs a work tree, and " + root + " belongs to a bare repository."
                    );
                }
                return tracked(
                        repository.readDirCache(),
                        real,
                        prefix(repository.getWorkTree().toPath().toRealPath(), real)
                );
            }
        } catch (IOException e) {
            throw new GradleException("Could not read the files Git tracks under " + root + ".", e);
        }
    }

    /**
     * Returns the path of a directory below the top of its work tree.
     *
     * @param workTree the top of the work tree, resolved of links
     * @param directory the directory, resolved of links the same way
     * @return the path with {@code /} as the separator and a trailing {@code /}, or an empty string for
     *         the top itself
     */
    private static String prefix(Path workTree, Path directory) {
        String relative = workTree.relativize(directory).toString().replace(File.separatorChar, '/');
        return relative.isEmpty() ? "" : relative + "/";
    }

    /**
     * Turns an exclusion glob into a pattern.
     *
     * @param glob the glob
     * @return the pattern that matches a whole path
     */
    private static Pattern toPattern(String glob) {
        String text = glob.endsWith("/") ? glob + "**" : glob;
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '*' && text.startsWith("**", i)) {
                boolean segmentStart = i == 0 || text.charAt(i - 1) == '/';
                if (segmentStart && text.startsWith("/", i + 2)) {
                    regex.append("(?:.*/)?");
                    i += 3;
                } else {
                    regex.append(".*");
                    i += 2;
                }
            } else if (c == '*') {
                regex.append("[^/]*");
                i++;
            } else if (c == '?') {
                regex.append("[^/]");
                i++;
            } else {
                if (!Character.isLetterOrDigit(c) && c != '/') {
                    regex.append('\\');
                }
                regex.append(c);
                i++;
            }
        }
        return Pattern.compile(regex.toString());
    }

    /**
     * Collects the index entries that are plain files under a directory.
     *
     * @param index the repository's index
     * @param root the project directory, resolved of links
     * @param prefix the directory's path from the top of the work tree, as {@link #prefix} returns it
     * @return the entries' paths relative to {@code root}
     */
    private static SortedSet<String> tracked(DirCache index, Path root, String prefix) {
        SortedSet<String> paths = new TreeSet<>();
        for (int i = 0; i < index.getEntryCount(); i++) {
            DirCacheEntry entry = index.getEntry(i);
            int mode = entry.getRawMode();
            String path = entry.getPathString();
            if (!path.startsWith(prefix)
                    || (!FileMode.REGULAR_FILE.equals(mode) && !FileMode.EXECUTABLE_FILE.equals(mode))) {
                continue;
            }
            String relative = path.substring(prefix.length());
            if (Files.isRegularFile(root.resolve(relative), LinkOption.NOFOLLOW_LINKS)) {
                paths.add(relative);
            }
        }
        return paths;
    }
}

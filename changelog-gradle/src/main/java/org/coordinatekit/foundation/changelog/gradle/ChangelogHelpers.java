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
package org.coordinatekit.foundation.changelog.gradle;

import com.github.jknack.handlebars.Helper;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import se.bjurr.gitchangelog.api.model.Commit;
import se.bjurr.gitchangelog.internal.semantic.ConventionalCommitParser;
import se.bjurr.gitchangelog.plugin.gradle.HelperParam;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Handlebars helpers the changelog template calls, beyond the ones git-changelog ships. Each
 * decision sits in a package-private static over a plain string or path, and {@link #helpers} wraps
 * them as the adapters git-changelog registers, so the decisions can be tested without a template
 * engine.
 */
final class ChangelogHelpers {
    /**
     * Matches the trailing {@code (#NN)} that a squash merge appends to a commit's subject and captures
     * its number. Both {@link #description} and {@link #pullRequest} read the reference through this
     * pattern, so they cannot disagree about which one is the pull request.
     */
    private static final Pattern PULL_REQUEST_SUFFIX = Pattern.compile("\\s*\\(#(\\d+)\\)$");

    private ChangelogHelpers() {}

    /**
     * Tells whether a commit message carries a {@code DEPRECATED:} footer. A deprecation warns about a
     * removal one or more releases before the break, so it rides along with the commit that supersedes
     * the API and earns no commit type of its own. git-changelog has no predicate for a given footer,
     * so this supplies the one the template needs, mirroring its built-in breaking check.
     *
     * @param message the full commit message
     * @return whether a footer token equals {@code DEPRECATED}, ignoring case
     */
    static boolean deprecates(String message) {
        return ConventionalCommitParser.getMessageParts(message)
                .getFooters()
                .stream()
                .anyMatch(footer -> footer.getToken().equalsIgnoreCase("DEPRECATED"));
    }

    /**
     * Returns a commit's conventional-commit description with the squash-merge pull request reference
     * removed, so the template can render that reference as a link of its own. A subject that is not a
     * conventional commit, such as the {@code Revert "..."} line {@code git revert} writes, is shown as
     * written.
     *
     * @param message the full commit message
     * @return the description after the type and scope prefix, or the whole subject when it has no such
     *         prefix
     */
    static String description(String message) {
        String description = ConventionalCommitParser.commitDescription(message);
        if (description.isBlank()) {
            description = subject(message);
        }
        return PULL_REQUEST_SUFFIX.matcher(description).replaceAll("").trim();
    }

    /**
     * Builds the helpers the changelog template calls: {@code description}, {@code pullRequest},
     * {@code ifContainsDeprecated}, {@code ifCommitDeprecated}, {@code repoUrl}, and
     * {@code initialRelease}. Both providers are read when the template renders, not when the helpers
     * are built.
     *
     * @param repoUrl the repository URL every link in the changelog is built from
     * @param initialRelease the file appended after the generated releases, absent when there is none
     * @return the helpers to register on the {@code gitChangelog} task
     */
    static List<HelperParam> helpers(Provider<String> repoUrl, Provider<RegularFile> initialRelease) {
        Helper<Commit> description = (commit, options) -> description(commit.getMessage());
        Helper<Commit> pullRequest = (commit, options) -> pullRequest(commit.getMessage());
        Helper<List<Commit>> ifContainsDeprecated = (commits, options) -> commits.stream()
                .anyMatch(commit -> deprecates(commit.getMessage())) ? options.fn() : options.inverse();
        Helper<Commit> ifCommitDeprecated = (commit, options) -> deprecates(commit.getMessage()) ? options.fn()
                : options.inverse();
        Helper<Object> repoUrlHelper = (context, options) -> repoUrl(repoUrl.get());
        Helper<Object> initialReleaseHelper = (context, options) -> initialRelease.isPresent()
                ? initialRelease(initialRelease.get().getAsFile().toPath())
                : "";

        return List.of(
                new HelperParam("description", description),
                new HelperParam("pullRequest", pullRequest),
                new HelperParam("ifContainsDeprecated", ifContainsDeprecated),
                new HelperParam("ifCommitDeprecated", ifCommitDeprecated),
                new HelperParam("repoUrl", repoUrlHelper),
                new HelperParam("initialRelease", initialReleaseHelper)
        );
    }

    /**
     * Reads the initial-release file with its trailing whitespace dropped, so the template controls the
     * spacing after it.
     *
     * @param file the Markdown file holding the earlier releases
     * @return the file's text without trailing whitespace
     * @throws UncheckedIOException if the file cannot be read
     */
    static String initialRelease(Path file) {
        try {
            return Files.readString(file).stripTrailing();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the initial release file " + file, e);
        }
    }

    /**
     * Extracts the pull request number a squash merge appended to a commit's subject, so the template
     * can link to the pull request and fall back to the commit hash when a commit landed without one.
     *
     * @param message the full commit message
     * @return the number in the subject's trailing {@code (#NN)}, or an empty string when the subject
     *         has none; a reference in the body or inside the subject does not count
     */
    static String pullRequest(String message) {
        Matcher matcher = PULL_REQUEST_SUFFIX.matcher(subject(message));
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Drops trailing slashes from the repository URL, so the template can append {@code /pull/12}
     * without doubling the separator.
     *
     * @param url the repository URL as configured
     * @return the URL without trailing slashes
     */
    static String repoUrl(String url) {
        return url.replaceAll("/+$", "");
    }

    /**
     * Returns the first line of a commit message.
     *
     * @param message the full commit message
     * @return the subject line without surrounding whitespace
     */
    private static String subject(String message) {
        return message.lines().findFirst().orElse("").strip();
    }
}

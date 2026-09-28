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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.jknack.handlebars.Helper;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import se.bjurr.gitchangelog.plugin.gradle.HelperParam;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tests for {@link ChangelogHelpers}. The decisions are exercised as plain statics over strings and
 * paths, so no template engine is involved; the adapters that expose them to Handlebars get a check
 * of their own.
 */
class ChangelogHelpersTest {
    /**
     * One commit message and whether it carries a {@code DEPRECATED:} footer.
     *
     * @param name what the case shows
     * @param message the full commit message
     * @param expected whether the message deprecates something
     */
    private record DeprecatesParameters(String name, String message, boolean expected) {}

    /**
     * One commit message and the description the changelog shows for it.
     *
     * @param name what the case shows
     * @param message the full commit message
     * @param expected the description without prefix or pull request reference
     */
    private record DescriptionParameters(String name, String message, String expected) {}

    /**
     * One commit message and the pull request number extracted from it.
     *
     * @param name what the case shows
     * @param message the full commit message
     * @param expected the number, or an empty string
     */
    private record PullRequestParameters(String name, String message, String expected) {}

    static Stream<DeprecatesParameters> deprecates__footer() {
        return Stream.of(
                new DeprecatesParameters("upper case footer", "feat: add y\n\nDEPRECATED: use y instead", true),
                new DeprecatesParameters("lower case footer", "feat: add y\n\ndeprecated: use y instead", true),
                new DeprecatesParameters(
                        "footer after a body",
                        "feat: add y\n\nThe old form goes away soon.\n\nDEPRECATED: use y instead",
                        true
                ),
                new DeprecatesParameters("other footer", "feat: add y\n\nBREAKING CHANGE: x is gone", false),
                new DeprecatesParameters("word in the subject only", "feat: deprecated: add y", false),
                new DeprecatesParameters("no footer", "feat: add y", false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void deprecates__footer(DeprecatesParameters parameters) {
        // ACT //
        boolean actual = ChangelogHelpers.deprecates(parameters.message());

        // ASSERT //
        assertEquals(parameters.expected(), actual, parameters.name());
    }

    static Stream<DescriptionParameters> description__cases() {
        return Stream.of(
                new DescriptionParameters("type and scope", "feat(api): add thing (#12)", "add thing"),
                new DescriptionParameters("breaking marker", "feat!: drop thing (#3)", "drop thing"),
                new DescriptionParameters("no pull request", "fix: repair thing", "repair thing"),
                new DescriptionParameters("reference mid sentence", "fix: mention (#4) inline", "mention (#4) inline"),
                new DescriptionParameters("not conventional", "Update the readme", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void description__cases(DescriptionParameters parameters) {
        // ACT //
        String actual = ChangelogHelpers.description(parameters.message());

        // ASSERT //
        assertEquals(parameters.expected(), actual, parameters.name());
    }

    private static Helper<?> helper(List<HelperParam> helpers, String name) {
        return helpers.stream().filter(param -> param.getName().equals(name)).findFirst().orElseThrow().getHelper();
    }

    @Test
    void helpers__exposeTheSixNamesTheTemplateCalls() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Property<String> repoUrl = project.getObjects().property(String.class);
        RegularFileProperty initialRelease = project.getObjects().fileProperty();

        // ACT //
        List<HelperParam> helpers = ChangelogHelpers.helpers(repoUrl, initialRelease);

        // ASSERT //
        assertEquals(
                List.of(
                        "description",
                        "pullRequest",
                        "ifContainsDeprecated",
                        "ifCommitDeprecated",
                        "repoUrl",
                        "initialRelease"
                ),
                helpers.stream().map(HelperParam::getName).toList()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void helpers__initialReleaseIsEmptyUntilSet(@TempDir Path directory) throws Exception {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        RegularFileProperty initialRelease = project.getObjects().fileProperty();
        Helper<Object> helper = (Helper<Object>) helper(
                ChangelogHelpers.helpers(project.getObjects().property(String.class), initialRelease),
                "initialRelease"
        );
        Path file = Files.writeString(directory.resolve("initial.md"), "## [0.1.0]\n\n");

        // ACT //
        Object unset = helper.apply(null, null);
        initialRelease.set(file.toFile());
        Object set = helper.apply(null, null);

        // ASSERT //
        assertEquals("", unset);
        assertEquals("## [0.1.0]", set);
    }

    @Test
    @SuppressWarnings("unchecked")
    void helpers__repoUrlIsReadWhenTheTemplateRenders() throws Exception {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Property<String> repoUrl = project.getObjects().property(String.class);
        Helper<Object> helper = (Helper<Object>) helper(
                ChangelogHelpers.helpers(repoUrl, project.getObjects().fileProperty()),
                "repoUrl"
        );

        // ACT //
        repoUrl.set("https://github.com/coordinatekit/crf");
        Object actual = helper.apply(null, null);

        // ASSERT //
        assertEquals("https://github.com/coordinatekit/crf", actual);
    }

    @Test
    void initialRelease__dropsTrailingWhitespace(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Path file = Files.writeString(directory.resolve("initial.md"), "## [0.1.0]\n\nFirst.\n\n\n");

        // ACT //
        String actual = ChangelogHelpers.initialRelease(file);

        // ASSERT //
        assertEquals("## [0.1.0]\n\nFirst.", actual);
    }

    @Test
    void initialRelease__failsWhenTheFileIsMissing(@TempDir Path directory) {
        // ARRANGE //
        Path missing = directory.resolve("missing.md");

        // ACT //
        UncheckedIOException thrown = assertThrows(
                UncheckedIOException.class,
                () -> ChangelogHelpers.initialRelease(missing)
        );

        // ASSERT //
        assertEquals(true, thrown.getMessage().contains(missing.toString()));
    }

    static Stream<PullRequestParameters> pullRequest__cases() {
        return Stream.of(
                new PullRequestParameters("trailing reference", "feat: add thing (#42)", "42"),
                new PullRequestParameters("no reference", "feat: add thing", ""),
                new PullRequestParameters("issue without parentheses", "feat: add thing #42", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void pullRequest__cases(PullRequestParameters parameters) {
        // ACT //
        String actual = ChangelogHelpers.pullRequest(parameters.message());

        // ASSERT //
        assertEquals(parameters.expected(), actual, parameters.name());
    }
}

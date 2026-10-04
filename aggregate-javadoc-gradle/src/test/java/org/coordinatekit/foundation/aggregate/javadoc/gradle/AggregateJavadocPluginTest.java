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
package org.coordinatekit.foundation.aggregate.javadoc.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.coordinatekit.foundation.aggregate.javadoc.gradle.AggregateJavadocPlugin.Module;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Unit and functional tests for {@link AggregateJavadocPlugin}. The defaults, the task's
 * configuration, and the failure a build gets for leaving out its title are read off a
 * {@link ProjectBuilder} project, and the ordering of the module groups is checked on the pure
 * function that computes it. What only a real Javadoc run can show, that the groups sort each
 * module's packages onto its own tab and that a module outside the selection is built but not
 * documented, is covered by one TestKit build in {@link #apply__groupsPackagesByModule}.
 *
 * <p>
 * The fixture build applies the plugin through {@code plugins { id ... }} and
 * {@code withPluginClasspath()}, so it also exercises the descriptor {@code java-gradle-plugin}
 * generates.
 */
class AggregateJavadocPluginTest {
    /**
     * One set of module names and the group labels it should produce, in order.
     *
     * @param name what the case shows
     * @param modules the modules, in the order they are passed in
     * @param wordForms the display forms passed in
     * @param expected the labels, in registration order
     */
    private record GroupsParameters(
            String name,
            List<Module> modules,
            Map<String, String> wordForms,
            List<String> expected
    ) {}

    /** The id the plugin is applied under. */
    private static final String PLUGIN_ID = "org.coordinatekit.foundation.aggregate-javadoc";

    /** Matches one tab button of the generated index, capturing the tab's number and its label. */
    private static final Pattern TAB = Pattern
            .compile("<button id=\"all-packages-table-tab(\\d+)\"[^>]*>([^<]*)</button>");

    @Test
    void apply__configuresTaskFromExtension(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.setGroup("org.example");
        project.setVersion("1.2.3");
        Project a = subproject(project, "a");
        subproject(project, "a-b");
        Project support = subproject(project, "support");
        a.getDependencies().add("implementation", support);
        project.getPluginManager().apply(PLUGIN_ID);
        AggregateJavadocExtension extension = project.getExtensions().getByType(AggregateJavadocExtension.class);
        extension.getTitle().set("Example");
        extension.getProjects().set(Set.of(a, project.project(":a-b")));
        extension.getLinks().set(List.of("https://docs.example.org/api/"));
        extension.getWordForms().set(Map.of("b", "Bee"));

        // ACT //
        Javadoc task = (Javadoc) project.getTasks().getByName("aggregateJavadoc");
        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
        Path optionFile = directory.resolve("javadoc.options");
        options.write(optionFile.toFile());

        // ASSERT //
        assertEquals("documentation", task.getGroup());
        assertEquals("aggregateJavadoc", task.getDestinationDir().getName());
        assertEquals("Example 1.2.3 API", task.getTitle());
        assertEquals("Example API", options.getWindowTitle());
        assertEquals(List.of("https://docs.example.org/api/"), options.getLinks());
        assertEquals("UTF-8", options.getEncoding());
        assertEquals("UTF-8", options.getDocEncoding());
        assertEquals("UTF-8", options.getCharSet());
        assertTrue(
                Files.readAllLines(optionFile).stream().map(String::strip).anyMatch("-Werror"::equals),
                Files.readString(optionFile)
        );
        assertEquals(List.of("A Bee Module", "A Module"), List.copyOf(options.getGroups().keySet()));
        assertTrue(
                task.getTaskDependencies()
                        .getDependencies(task)
                        .stream()
                        .anyMatch(dependency -> dependency.getPath().startsWith(":support:")),
                "expected the classpath to carry a :support build task, got "
                        + task.getTaskDependencies().getDependencies(task)
        );
    }

    @Test
    void apply__defaultsSelectJavaSubprojects() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Project a = subproject(project, "a");
        ProjectBuilder.builder().withName("docs").withParent(project).build();

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);
        AggregateJavadocExtension extension = project.getExtensions().getByType(AggregateJavadocExtension.class);

        // ASSERT //
        assertEquals(Set.of(a), extension.getProjects().get());
        assertEquals(List.of(), extension.getLinks().get());
        assertEquals(Map.of(), extension.getWordForms().get());
        assertFalse(extension.getTitle().isPresent());
    }

    @Test
    void apply__failsForProjectWithoutJava() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Project plain = ProjectBuilder.builder().withName("plain").withParent(project).build();
        project.getPluginManager().apply(PLUGIN_ID);
        AggregateJavadocExtension extension = project.getExtensions().getByType(AggregateJavadocExtension.class);
        extension.getTitle().set("Example");
        extension.getProjects().set(Set.of(plain));

        // ACT //
        Exception thrown = assertThrows(Exception.class, () -> project.getTasks().getByName("aggregateJavadoc"));

        // ASSERT //
        assertTrue(causes(thrown).contains(":plain"), causes(thrown));
        assertTrue(causes(thrown).contains("aggregateJavadoc.projects"), causes(thrown));
    }

    @Test
    void apply__failsWithoutTitle() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(PLUGIN_ID);

        // ACT //
        Exception thrown = assertThrows(Exception.class, () -> ((ProjectInternal) project).evaluate());

        // ASSERT //
        assertTrue(
                causes(thrown).contains("aggregateJavadoc.title"),
                "expected the failure to name the missing property, got: " + causes(thrown)
        );
    }

    @Test
    void apply__groupsFollowEachModulesGroup() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Project a = subproject(project, "a");
        a.setGroup("org.example");
        project.getPluginManager().apply(PLUGIN_ID);
        AggregateJavadocExtension extension = project.getExtensions().getByType(AggregateJavadocExtension.class);
        extension.getTitle().set("Example");

        // ACT //
        Javadoc task = (Javadoc) project.getTasks().getByName("aggregateJavadoc");
        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();

        // ASSERT //
        assertEquals(List.of(List.of("org.example.a*")), List.copyOf(options.getGroups().values()));
    }

    @Test
    void apply__groupsPackagesByModule(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(directory);
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withArguments("aggregateJavadoc", "--configuration-cache");

        // ACT //
        BuildResult first = runner.build();
        BuildResult second = runner.build();

        // ASSERT //
        assertNotNull(first.task(":support:compileJava"), "the unselected module should still be built");
        assertEquals(TaskOutcome.SUCCESS, first.task(":support:compileJava").getOutcome());
        Path pages = directory.resolve("build/docs/aggregateJavadoc");
        assertFalse(Files.exists(pages.resolve("fixture/support")), "an unselected module should not be documented");
        String index = Files.readString(pages.resolve("index.html"));
        assertEquals("A Module", tabOf(index, "fixture/a/package-summary.html"));
        assertEquals("A B Module", tabOf(index, "fixture/a/b/package-summary.html"));
        assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":aggregateJavadoc").getOutcome());
    }

    @Test
    void apply__ordersSelectionByPath() throws IOException {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Set<Project> reversed = new LinkedHashSet<>();
        for (String name : List.of("c", "b", "a")) {
            Project child = subproject(project, name);
            String type = name.toUpperCase(Locale.ROOT);
            Path sources = Files.createDirectories(child.getProjectDir().toPath().resolve("src/main/java"));
            Files.writeString(sources.resolve(type + ".java"), "class " + type + " {}");
            reversed.add(child);
        }
        project.getPluginManager().apply(PLUGIN_ID);
        AggregateJavadocExtension extension = project.getExtensions().getByType(AggregateJavadocExtension.class);
        extension.getTitle().set("Example");
        extension.getProjects().set(reversed);
        Javadoc task = (Javadoc) project.getTasks().getByName("aggregateJavadoc");

        // ACT //
        List<String> files = task.getSource().getFiles().stream().map(file -> file.getName()).toList();

        // ASSERT //
        assertEquals(List.of("A.java", "B.java", "C.java"), files);
    }

    /**
     * Flattens an exception and everything that caused it into one string. Gradle wraps a failure
     * thrown from {@code afterEvaluate} in a configuration exception, so the message under test is
     * never the one on top.
     *
     * @param thrown the exception to flatten
     * @return every message in the cause chain, newline-separated
     */
    private static String causes(Throwable thrown) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }

    static Stream<GroupsParameters> groups__labels() {
        return Stream.of(
                new GroupsParameters(
                        "prefix after its extension",
                        List.of(module("a"), module("a-b")),
                        Map.of(),
                        List.of("A B Module", "A Module")
                ),
                new GroupsParameters(
                        "input order ignored",
                        List.of(module("a-b"), module("a")),
                        Map.of(),
                        List.of("A B Module", "A Module")
                ),
                new GroupsParameters(
                        "word form replaces capitalisation",
                        List.of(module("cli-brand")),
                        Map.of("cli", "CLI"),
                        List.of("CLI Brand Module")
                ),
                new GroupsParameters(
                        "equal lengths sorted by name",
                        List.of(module("b"), module("a")),
                        Map.of(),
                        List.of("A Module", "B Module")
                )
        );
    }

    @MethodSource
    @ParameterizedTest
    void groups__labels(GroupsParameters parameters) {
        // ACT //
        Map<String, String> groups = AggregateJavadocPlugin.groups(parameters.modules(), parameters.wordForms());

        // ASSERT //
        assertEquals(parameters.expected(), List.copyOf(groups.keySet()), parameters.name());
    }

    @Test
    void groups__patternFollowsModuleGroup() {
        // ACT //
        Map<String, String> groups = AggregateJavadocPlugin
                .groups(List.of(new Module("org.y", "b"), new Module("org.x", "a")), Map.of());

        // ASSERT //
        assertEquals(List.of("org.x.a*", "org.y.b*"), List.copyOf(groups.values()));
    }

    @Test
    void groups__patternFollowsModuleName() {
        // ACT //
        Map<String, String> groups = AggregateJavadocPlugin.groups(List.of(module("cli-brand")), Map.of());

        // ASSERT //
        assertEquals(List.of("org.example.cli.brand*"), List.copyOf(groups.values()));
    }

    /**
     * Builds a module in the group {@code org.example}.
     *
     * @param name the module's name
     * @return the module
     */
    private static Module module(String name) {
        return new Module("org.example", name);
    }

    /**
     * Adds a child project that applies the {@code java} plugin.
     *
     * @param parent the project to add the child to
     * @param name the child's name
     * @return the child
     */
    private static Project subproject(Project parent, String name) {
        Project child = ProjectBuilder.builder().withName(name).withParent(parent).build();
        child.getPluginManager().apply("java");
        return child;
    }

    /**
     * Finds the tab a package's row sits under in a generated overview page.
     *
     * @param index the contents of {@code index.html}
     * @param packagePage the package's summary page, relative to the overview
     * @return the label of the tab the package is listed under
     */
    private static String tabOf(String index, String packagePage) {
        Matcher tabs = TAB.matcher(index);
        Map<String, String> labels = new HashMap<>();
        while (tabs.find()) {
            labels.put(tabs.group(1), tabs.group(2));
        }
        Matcher row = Pattern
                .compile("class=\"[^\"]*all-packages-table-tab(\\d+)[^\"]*\"><a href=\"" + Pattern.quote(packagePage))
                .matcher(index);
        assertTrue(row.find(), "expected a row for " + packagePage + " in:\n" + index);
        return labels.get(row.group(1));
    }

    /**
     * Writes a consumer build with three modules, {@code a}, {@code a-b}, and {@code support}, where
     * {@code a} depends on {@code support} and only the first two are selected. Every fixture source is
     * fully documented, because the task fails on a Javadoc warning.
     *
     * <p>
     * When the test task sets {@code testKit.fixtureJvmArgs}, the fixture also gets a
     * {@code gradle.properties} passing it to the daemon as {@code org.gradle.jvmargs}. That is how the
     * build records coverage from the fixture builds; without it, as under an IDE's own test runner,
     * the fixture runs the same and records nothing.
     *
     * @param directory the project directory to write into
     * @throws IOException if the fixture cannot be written
     */
    private static void writeFixture(Path directory) throws IOException {
        Files.writeString(directory.resolve("settings.gradle"), """
                rootProject.name = "fixture"
                include "a", "a-b", "support"
                """);
        String jvmArgs = System.getProperty("testKit.fixtureJvmArgs");
        if (jvmArgs != null) {
            Properties properties = new Properties();
            properties.setProperty("org.gradle.jvmargs", jvmArgs);
            try (OutputStream file = Files.newOutputStream(directory.resolve("gradle.properties"))) {
                properties.store(file, null);
            }
        }
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id "org.coordinatekit.foundation.aggregate-javadoc"
                }

                allprojects {
                    group = "fixture"
                }

                subprojects {
                    apply plugin: "java-library"
                }

                project(":a") {
                    dependencies {
                        implementation project(":support")
                    }
                }

                aggregateJavadoc {
                    projects = [project(":a"), project(":a-b")]
                    title = "Fixture"
                }
                """);

        writeSource(directory, "support", "fixture.support", "Support", """
                /** A type the selected modules depend on. */
                public class Support {
                    /** Creates the type. */
                    public Support() {}
                }
                """);
        writeSource(directory, "a", "fixture.a", "A", """
                import fixture.support.Support;

                /** The first documented type. */
                public class A {
                    /** Creates the type. */
                    public A() {}

                    /**
                     * Returns the supporting type.
                     *
                     * @return a new supporting type
                     */
                    public Support support() {
                        return new Support();
                    }
                }
                """);
        writeSource(directory, "a-b", "fixture.a.b", "B", """
                /** The second documented type. */
                public class B {
                    /** Creates the type. */
                    public B() {}
                }
                """);
    }

    /**
     * Writes one source file into a fixture module.
     *
     * @param directory the fixture's root directory
     * @param module the module to write into
     * @param packageName the package the type belongs to
     * @param type the simple name of the type
     * @param body everything in the file after the package declaration
     * @throws IOException if the file cannot be written
     */
    private static void writeSource(Path directory, String module, String packageName, String type, String body)
            throws IOException {
        Path sources = directory.resolve(module).resolve("src/main/java").resolve(packageName.replace('.', '/'));
        Files.createDirectories(sources);
        Files.writeString(sources.resolve(type + ".java"), "package " + packageName + ";\n\n" + body);
    }
}

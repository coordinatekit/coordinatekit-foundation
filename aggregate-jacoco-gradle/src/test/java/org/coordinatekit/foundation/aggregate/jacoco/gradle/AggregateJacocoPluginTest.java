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
package org.coordinatekit.foundation.aggregate.jacoco.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.File;
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

/**
 * Unit and functional tests for {@link AggregateJacocoPlugin}. The selection, the task's
 * configuration, and the failure for a project that cannot contribute are read off a
 * {@link ProjectBuilder} project. What only a real test run can show, that every selected module's
 * coverage reaches one report and an unselected module's does not, is covered by one TestKit build
 * in {@link #apply__reportsEverySelectedModule}.
 *
 * <p>
 * The fixture build applies the plugin through {@code plugins { id ... }} and
 * {@code withPluginClasspath()}, so it also exercises the descriptor {@code java-gradle-plugin}
 * generates.
 */
class AggregateJacocoPluginTest {
    /** The id the plugin is applied under. */
    private static final String PLUGIN_ID = "org.coordinatekit.foundation.aggregate-jacoco";

    @Test
    void apply__defaultsSelectJacocoSubprojects() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Project a = subproject(project, "a", true);
        subproject(project, "plain", false);

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);
        AggregateJacocoExtension extension = project.getExtensions().getByType(AggregateJacocoExtension.class);

        // ASSERT //
        assertEquals(Set.of(a), extension.getProjects().get());
        assertTrue(project.getPlugins().hasPlugin("jacoco"), "the root should get the jacoco plugin");
    }

    @Test
    void apply__failsForProjectWithoutJacocoTestReport() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Project plain = subproject(project, "plain", false);
        project.getPluginManager().apply(PLUGIN_ID);
        project.getExtensions().getByType(AggregateJacocoExtension.class).getProjects().set(Set.of(plain));
        JacocoReport task = (JacocoReport) project.getTasks().getByName("aggregateJacocoReport");

        // ACT //
        Exception thrown = assertThrows(Exception.class, () -> task.getExecutionData().getFiles());

        // ASSERT //
        String messages = causes(thrown);
        assertTrue(messages.contains(":plain"), "expected the failure to name the project, got: " + messages);
        assertTrue(
                messages.contains("aggregateJacoco.projects"),
                "expected the failure to name the property to change, got: " + messages
        );
    }

    @Test
    void apply__failsForProjectWithoutJava() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Project plain = ProjectBuilder.builder().withName("plain").withParent(project).build();
        project.getPluginManager().apply(PLUGIN_ID);
        project.getExtensions().getByType(AggregateJacocoExtension.class).getProjects().set(Set.of(plain));
        JacocoReport task = (JacocoReport) project.getTasks().getByName("aggregateJacocoReport");

        // ACT //
        Exception thrown = assertThrows(Exception.class, () -> task.getExecutionData().getFiles());

        // ASSERT //
        String messages = causes(thrown);
        assertTrue(messages.contains(":plain"), "expected the failure to name the project, got: " + messages);
        assertTrue(
                messages.contains("aggregateJacoco.projects"),
                "expected the failure to name the property to change, got: " + messages
        );
    }

    @Test
    void apply__ordersSelectionByPath() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Set<Project> reversed = new LinkedHashSet<>();
        for (String name : List.of("c", "b", "a")) {
            reversed.add(subproject(project, name, true));
        }
        project.getPluginManager().apply(PLUGIN_ID);
        project.getExtensions().getByType(AggregateJacocoExtension.class).getProjects().set(reversed);
        JacocoReport task = (JacocoReport) project.getTasks().getByName("aggregateJacocoReport");

        // ACT //
        List<String> modules = task.getClassDirectories()
                .getFiles()
                .stream()
                .map(
                        file -> file.getPath().contains("/a/build/") ? "a"
                                : file.getPath().contains("/b/build/") ? "b" : "c"
                )
                .distinct()
                .toList();

        // ASSERT //
        assertEquals(List.of("a", "b", "c"), modules);
    }

    @Test
    void apply__registersVerificationReport() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        subproject(project, "a", true);
        project.getPluginManager().apply(PLUGIN_ID);

        // ACT //
        JacocoReport task = (JacocoReport) project.getTasks().getByName("aggregateJacocoReport");

        // ASSERT //
        assertEquals("verification", task.getGroup());
        assertTrue(task.getReports().getXml().getRequired().get(), "the XML report should be required");
        assertTrue(task.getReports().getHtml().getRequired().get(), "the HTML report should be required");
        assertTrue(
                task.getTaskDependencies()
                        .getDependencies(task)
                        .stream()
                        .anyMatch(dependency -> dependency.getPath().equals(":a:test")),
                "expected a dependency on :a:test, got " + task.getTaskDependencies().getDependencies(task)
        );
    }

    @Test
    void apply__reportsEverySelectedModule(@TempDir Path directory)
            throws IOException, ParserConfigurationException, SAXException {
        // ARRANGE //
        writeFixture(directory);

        GradleRunner runner = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withArguments("aggregateJacocoReport", "--configuration-cache");

        // ACT //
        runner.build();
        BuildResult second = runner.build();

        // ASSERT //
        assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":aggregateJacocoReport").getOutcome());
        Path report = directory.resolve("build/reports/jacoco/aggregateJacocoReport");
        Map<String, Integer> coveredLines = coveredLines(report.resolve("aggregateJacocoReport.xml"));
        assertTrue(
                coveredLines.getOrDefault("fixture/a", 0) > 0,
                "expected covered lines in fixture/a: " + coveredLines
        );
        assertTrue(
                coveredLines.getOrDefault("fixture/b", 0) > 0,
                "expected covered lines in fixture/b: " + coveredLines
        );
        assertFalse(
                coveredLines.containsKey("fixture/plain"),
                "an unselected module should not be reported: " + coveredLines
        );
        assertTrue(Files.exists(report.resolve("html/fixture.a/A.java.html")), "expected an HTML page for A.java");
    }

    @Test
    void apply__takesExecutionDataFromEachJacocoTestReport(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        Project a = subproject(project, "a", true);
        File extra = Files.createFile(directory.resolve("extra.exec")).toFile();
        ((JacocoReport) a.getTasks().getByName("jacocoTestReport")).getExecutionData().from(extra);
        project.getPluginManager().apply(PLUGIN_ID);

        // ACT //
        JacocoReport task = (JacocoReport) project.getTasks().getByName("aggregateJacocoReport");

        // ASSERT //
        assertTrue(
                task.getExecutionData().getFiles().contains(extra),
                "expected " + extra + " in " + task.getExecutionData().getFiles()
        );
    }

    /**
     * Flattens an exception and everything that caused it into one string. Gradle wraps a failure
     * thrown while resolving a file collection, so the message under test is not always the one on top.
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

    /**
     * Counts the covered lines of each package in a JaCoCo XML report.
     *
     * @param xml the report file
     * @return the number of covered lines, keyed by package name
     * @throws IOException if the report cannot be read
     * @throws ParserConfigurationException if the parser cannot be configured
     * @throws SAXException if the report is not well-formed
     */
    private static Map<String, Integer> coveredLines(Path xml)
            throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The report names an external DTD that is not there to load.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document document;
        try (var stream = Files.newInputStream(xml)) {
            document = factory.newDocumentBuilder().parse(stream);
        }

        Map<String, Integer> lines = new HashMap<>();
        NodeList packages = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < packages.getLength(); i++) {
            if (packages.item(i)instanceof Element pkg && pkg.getTagName().equals("package")) {
                NodeList counters = pkg.getChildNodes();
                for (int j = 0; j < counters.getLength(); j++) {
                    Node counter = counters.item(j);
                    if (counter instanceof Element element && element.getTagName().equals("counter")
                            && element.getAttribute("type").equals("LINE")) {
                        lines.put(pkg.getAttribute("name"), Integer.parseInt(element.getAttribute("covered")));
                    }
                }
            }
        }
        return lines;
    }

    /**
     * Adds a child project that applies the {@code java} plugin, and optionally {@code jacoco}.
     *
     * @param parent the project to add the child to
     * @param name the child's name
     * @param jacoco whether the child applies {@code jacoco}
     * @return the child
     */
    private static Project subproject(Project parent, String name, boolean jacoco) {
        Project child = ProjectBuilder.builder().withName(name).withParent(parent).build();
        child.getPluginManager().apply("java");
        if (jacoco) {
            child.getPluginManager().apply("jacoco");
        }
        return child;
    }

    /**
     * Writes a consumer build with three modules. {@code a} and {@code b} apply {@code jacoco} and have
     * one JUnit test each, and {@code plain} has neither.
     *
     * <p>
     * The JUnit version the fixtures resolve is the one this test runs against, read from the jar's
     * manifest, so it needs no wiring of its own and is already in the Gradle cache.
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
        String junit = Test.class.getPackage().getImplementationVersion();
        assertNotNull(junit, "the JUnit jar's manifest should carry its version");

        Files.writeString(directory.resolve("settings.gradle"), """
                rootProject.name = "fixture"
                include "a", "b", "plain"
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
                    id "org.coordinatekit.foundation.aggregate-jacoco"
                }

                allprojects {
                    repositories {
                        mavenCentral()
                    }
                }

                subprojects {
                    apply plugin: "java-library"
                }

                configure([project(":a"), project(":b")]) {
                    apply plugin: "jacoco"

                    dependencies {
                        testImplementation platform("org.junit:junit-bom:%s")
                        testImplementation "org.junit.jupiter:junit-jupiter"
                        testRuntimeOnly "org.junit.platform:junit-platform-launcher"
                    }

                    test {
                        useJUnitPlatform()
                    }
                }
                """.formatted(junit));

        for (String module : new String[] {"a", "b"}) {
            String type = module.toUpperCase(Locale.ROOT);
            writeSource(directory, module, "main", type, """
                    package fixture.%s;

                    public class %s {
                        public int one() {
                            return 1;
                        }
                    }
                    """.formatted(module, type));
            writeSource(directory, module, "test", type + "Test", """
                    package fixture.%s;

                    import static org.junit.jupiter.api.Assertions.assertEquals;

                    import org.junit.jupiter.api.Test;

                    class %sTest {
                        @Test
                        void one() {
                            assertEquals(1, new %s().one());
                        }
                    }
                    """.formatted(module, type, type));
        }
        writeSource(directory, "plain", "main", "Plain", """
                package fixture.plain;

                public class Plain {}
                """);
    }

    /**
     * Writes one source file into a fixture module.
     *
     * @param directory the fixture's root directory
     * @param module the module to write into
     * @param sourceSet the source set to write into, {@code main} or {@code test}
     * @param type the simple name of the type
     * @param source the whole file
     * @throws IOException if the file cannot be written
     */
    private static void writeSource(Path directory, String module, String sourceSet, String type, String source)
            throws IOException {
        Path sources = directory.resolve(module)
                .resolve("src")
                .resolve(sourceSet)
                .resolve("java/fixture")
                .resolve(module);
        Files.createDirectories(sources);
        Files.writeString(sources.resolve(type + ".java"), source);
    }
}

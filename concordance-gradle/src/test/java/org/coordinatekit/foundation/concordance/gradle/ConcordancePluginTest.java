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
package org.coordinatekit.foundation.concordance.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.ltgt.gradle.errorprone.CheckSeverity;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unit and functional tests for {@link ConcordancePlugin}. The two defaults that matter, the
 * severity and the empty exemption lists, are read straight off the extension through
 * {@link ProjectBuilder}, as is the failure a build gets for forgetting
 * {@code net.ltgt.errorprone}. Everything the plugin can only be shown to do by running a
 * compilation, resolving the check jar and turning the extension into {@code -Xep} flags, is
 * covered by one TestKit build in {@link #apply__failsCompilationOnMisorderedMembers}.
 *
 * <p>
 * That fixture build resolves the check from the {@code concordance-errorprone} module's own
 * {@code build/libs}, not from Maven Central, so it tests the check as it is in the working tree
 * rather than as it was last released. The directory and the Error Prone versions to pin arrive as
 * system properties the test task sets.
 */
class ConcordancePluginTest {
    /**
     * One severity string and the severity it should parse to.
     *
     * @param name what the case shows
     * @param value the configured string
     * @param expected the severity it maps to
     */
    private record SeverityParameters(String name, String value, CheckSeverity expected) {}

    /** The id the plugin is applied under. */
    private static final String PLUGIN_ID = "org.coordinatekit.foundation.concordance";

    @Test
    void apply__defaultsExemptNothingAtError() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);
        ConcordanceExtension extension = project.getExtensions().getByType(ConcordanceExtension.class);

        // ASSERT //
        assertEquals("ERROR", extension.getSeverity().get());
        assertEquals(List.of(), extension.getLifecycleAnnotations().get());
        assertEquals(List.of(), extension.getScaffoldingFieldTypes().get());
    }

    @Test
    void apply__failsCompilationOnMisorderedMembers(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(directory, """
                package fixture;

                public class Fixture {
                    static final int ZEBRA = 1;

                    static final Scaffold scaffold = null;

                    void zeta() {}

                    void alpha() {}

                    @Lifecycle
                    void aardvark() {}
                }
                """);

        // ACT //
        BuildResult result = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withArguments("compileJava", "-Pscaffold=fixture.Scaffold")
                .buildAndFail();

        // ASSERT //
        String output = result.getOutput();
        assertTrue(
                output.contains("error: [Concordance] method alpha() out of order with method zeta()"),
                "expected a Concordance error naming both methods, got:\n" + output
        );
        assertFalse(
                output.contains("constant scaffold"),
                "the configured scaffolding field type should be invisible to the check, got:\n" + output
        );
        assertFalse(
                output.contains("method aardvark()"),
                "a method carrying the configured lifecycle annotation should be invisible to the check, got:\n"
                        + output
        );
    }

    @Test
    void apply__failsWithoutErrorPronePlugin() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply(PLUGIN_ID);

        // ACT //
        Exception thrown = assertThrows(Exception.class, () -> ((ProjectInternal) project).evaluate());

        // ASSERT //
        assertTrue(
                causes(thrown).contains("net.ltgt.errorprone"),
                "expected the failure to name the missing plugin, got: " + causes(thrown)
        );
    }

    @Test
    void apply__rerunsCompilationWhenExemptionChanges(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(directory, """
                package fixture;

                public class Fixture {
                    static final int ZEBRA = 1;

                    static final Scaffold scaffold = null;
                }
                """);
        GradleRunner runner = GradleRunner.create().withProjectDir(directory.toFile());

        // ACT //
        runner.withArguments("compileJava", "--configuration-cache", "-Pscaffold=fixture.Scaffold").build();
        BuildResult result = runner.withArguments("compileJava", "--configuration-cache").buildAndFail();

        // ASSERT //
        assertTrue(
                result.getOutput().contains("constant scaffold"),
                "dropping the exemption should recompile and report the field, got:\n" + result.getOutput()
        );
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

    /**
     * Returns the plugin under test as a Groovy list literal of file paths, read from the metadata file
     * {@code java-gradle-plugin} generates onto the test classpath.
     *
     * <p>
     * The fixture puts it on its own {@code buildscript} classpath rather than taking it from
     * {@code GradleRunner.withPluginClasspath()}, because that injects the plugin into a classloader of
     * its own, where it cannot see the Error Prone plugin's types. A consumer's {@code plugins} block
     * puts both in one scope, and this is the shape of that.
     *
     * @return a Groovy list literal naming every entry of the plugin's runtime classpath
     * @throws IOException if the metadata file cannot be read
     */
    private static String pluginClasspath() throws IOException {
        Properties metadata = new Properties();
        try (InputStream entries = ConcordancePluginTest.class.getClassLoader()
                .getResourceAsStream("plugin-under-test-metadata.properties")) {
            metadata.load(
                    Objects.requireNonNull(
                            entries,
                            "plugin-under-test-metadata.properties is not on the "
                                    + "test classpath; the pluginUnderTestMetadata task should have put it there"
                    )
            );
        }
        return Arrays.stream(metadata.getProperty("implementation-classpath").split(File.pathSeparator))
                .map(entry -> "\"" + entry + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    static Stream<SeverityParameters> severity__accepted() {
        return Stream.of(
                new SeverityParameters("upper case", "ERROR", CheckSeverity.ERROR),
                new SeverityParameters("lower case", "warn", CheckSeverity.WARN),
                new SeverityParameters("mixed case", "Off", CheckSeverity.OFF)
        );
    }

    @MethodSource
    @ParameterizedTest
    void severity__accepted(SeverityParameters parameters) {
        // ACT //
        CheckSeverity severity = ErrorProneConfiguration.severity(parameters.value());

        // ASSERT //
        assertEquals(parameters.expected(), severity, parameters.name());
    }

    @Test
    void severity__rejectsUnknownName() {
        // ACT //
        InvalidUserDataException thrown = assertThrows(
                InvalidUserDataException.class,
                () -> ErrorProneConfiguration.severity("loud")
        );

        // ASSERT //
        assertTrue(thrown.getMessage().contains("concordance.severity must be one of"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("got: loud"), thrown.getMessage());
    }

    /**
     * Writes a consumer build that applies both plugins and compiles the given {@code Fixture} class.
     * The build lists {@code fixture.Scaffold} as a scaffolding field type only when the
     * {@code scaffold} Gradle property is set, so a test chooses the exemption per invocation.
     *
     * @param directory the project directory to write into
     * @param fixtureSource the source of {@code fixture.Fixture}
     * @throws IOException if the fixture cannot be written
     */
    private static void writeFixture(Path directory, String fixtureSource) throws IOException {
        Path sources = directory.resolve("src/main/java/fixture");
        Files.createDirectories(sources);

        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = \"fixture\"\n");
        Files.writeString(
                directory.resolve("build.gradle"),
                """
                        buildscript {
                            repositories {
                                gradlePluginPortal()
                            }
                            dependencies {
                                classpath "net.ltgt.gradle:gradle-errorprone-plugin:%s"
                                classpath files(%s)
                            }
                        }

                        apply plugin: "java"
                        apply plugin: "net.ltgt.errorprone"
                        apply plugin: "org.coordinatekit.foundation.concordance"

                        repositories {
                            mavenCentral()
                            flatDir { dirs "%s" }
                        }

                        dependencies {
                            errorprone "com.google.errorprone:error_prone_core:%s"
                        }

                        concordance {
                            lifecycleAnnotations = ["fixture.Lifecycle"]
                            scaffoldingFieldTypes = providers.gradleProperty("scaffold").map { [it] }.orElse([])
                        }

                        tasks.withType(JavaCompile).configureEach {
                            options.compilerArgs += "-XDaddTypeAnnotationsToSymbol=true"
                        }
                        """.formatted(
                        System.getProperty("concordance.errorPronePluginVersion"),
                        pluginClasspath(),
                        System.getProperty("concordance.checkJarDirectory"),
                        System.getProperty("concordance.errorProneVersion")
                )
        );

        Files.writeString(sources.resolve("Lifecycle.java"), """
                package fixture;

                public @interface Lifecycle {}
                """);
        Files.writeString(sources.resolve("Scaffold.java"), """
                package fixture;

                public class Scaffold {}
                """);
        Files.writeString(sources.resolve("Fixture.java"), fixtureSource);
    }
}

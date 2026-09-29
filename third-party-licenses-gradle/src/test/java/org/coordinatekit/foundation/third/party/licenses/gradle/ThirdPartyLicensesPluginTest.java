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
package org.coordinatekit.foundation.third.party.licenses.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.jk1.license.LicenseReportExtension;
import com.github.jk1.license.filter.LicenseBundleNormalizer;
import com.github.jk1.license.render.JsonReportRenderer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unit and functional tests for {@link ThirdPartyLicensesPlugin}. The defaults, the values the
 * plugin copies into the license report's extension, and the check that the allowlist and the
 * registered licenses agree are read off a {@link ProjectBuilder} project. Everything that can only
 * be shown by resolving dependencies and running the license report, the failing check, the two
 * failures that stop the render, and the rendered file itself, is covered by TestKit builds against
 * a Maven repository the test writes to disk.
 *
 * <p>
 * The fixture puts the plugin under test on its own {@code buildscript} classpath rather than
 * taking it from {@code GradleRunner.withPluginClasspath()}, which loads it in a classloader of its
 * own where it cannot see the license report plugin's types.
 */
class ThirdPartyLicensesPluginTest {

    /**
     * One allowlist and the entries of it that name an unregistered license.
     *
     * @param name what the case shows
     * @param allowlist the allowlist file contents
     * @param expected the {@code module (license)} entries reported
     */
    private record AllowlistParameters(String name, String allowlist, List<String> expected) {}

    /**
     * One module the fixture repository serves.
     *
     * @param artifact the artifact id, under the group {@code example}
     * @param license the license name the POM declares, or {@code null} to declare none
     * @param entries the entries to put in the module's jar, by path
     */
    private record FixtureModule(String artifact, @Nullable String license, Map<String, String> entries) {}

    /**
     * One set of reciprocal labels and the clause they join into.
     *
     * @param name what the case shows
     * @param labels the labels handed to the clause, in any order and possibly repeated
     * @param expected the clause expected
     */
    private record ReciprocalClauseParameters(String name, List<String> labels, String expected) {}

    /** The Apache 2.0 license name the fixtures register. */
    private static final String APACHE = "Apache License, Version 2.0";

    /** The LGPL license name the fixtures register. */
    private static final String LGPL = "GNU Lesser General Public License, Version 2.1";

    /** The MIT license name the fixtures register. */
    private static final String MIT = "MIT License";

    /** The id the plugin is applied under. */
    private static final String PLUGIN_ID = "org.coordinatekit.foundation.third-party-licenses";

    /**
     * Renders an allowlist in the license report's format.
     *
     * @param licenseByModule the license each module may carry, keyed by {@code group:artifact}
     * @return the allowlist file contents
     */
    private static String allowlist(Map<String, String> licenseByModule) {
        return licenseByModule.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(
                        entry -> "{\"moduleName\": \"" + entry.getKey() + "\", \"moduleLicense\": \"" + entry.getValue()
                                + "\"}"
                )
                .collect(Collectors.joining(", ", "{\"allowedLicenses\": [", "]}"));
    }

    @Test
    void apply__copiesExtensionIntoReport(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Files.writeString(directory.resolve("allowed-licenses.json"), "{\"allowedLicenses\": []}");
        Files.writeString(directory.resolve("license-normalizer-bundle.json"), "{\"bundles\": []}");
        Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(PLUGIN_ID);
        ThirdPartyLicensesExtension extension = project.getExtensions().getByType(ThirdPartyLicensesExtension.class);
        extension.getExcludeBoms().set(true);
        extension.getExcludeGroups().set(List.of("org.example", "org.example.more"));

        // ACT //
        ((ProjectInternal) project).evaluate();

        // ASSERT //
        LicenseReportExtension report = project.getExtensions().getByType(LicenseReportExtension.class);
        assertEquals(List.of("runtimeClasspath"), Arrays.asList(report.configurations));
        assertEquals(List.of("org.example", "org.example.more"), Arrays.asList(report.excludeGroups));
        assertTrue(report.excludeBoms);
        assertEquals(project.file("allowed-licenses.json"), report.allowedLicensesFile);
        assertEquals(1, report.renderers.length);
        JsonReportRenderer renderer = assertInstanceOf(JsonReportRenderer.class, report.renderers[0]);
        assertFalse(renderer.getOnlyOneLicensePerModuleCache(), "the task reads every license of a module");
        assertEquals(directory.toRealPath().resolve("build/reports/dependency-license").toString(), report.outputDir);
        GenerateThirdPartyLicenses generate = assertInstanceOf(
                GenerateThirdPartyLicenses.class,
                project.getTasks().getByName("generateThirdPartyLicenses")
        );
        assertEquals(
                new File(report.outputDir, renderer.getFileNameCache()),
                generate.getReport().get().getAsFile(),
                "the task should read the file the renderer writes"
        );
        assertEquals(1, report.filters.length);
        assertTrue(report.filters[0] instanceof LicenseBundleNormalizer, "expected the bundle normalizer");
    }

    @Test
    void apply__defaultsToProjectFilesAndNoExclusions() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);
        ThirdPartyLicensesExtension extension = project.getExtensions().getByType(ThirdPartyLicensesExtension.class);
        extension.getLicenses().register(MIT);

        // ASSERT //
        assertEquals(project.file("allowed-licenses.json"), extension.getAllowedLicensesFile().get().getAsFile());
        assertEquals(
                project.file("license-normalizer-bundle.json"),
                extension.getNormalizerBundleFile().get().getAsFile()
        );
        assertFalse(extension.getExcludeBoms().get());
        assertEquals(List.of(), extension.getExcludeGroups().get());
        assertEquals(Map.of(), extension.getLicenseOverrides().get());
        assertEquals(Map.of(), extension.getCopyrightNotices().get());
        LicenseDefinition license = extension.getLicenses().getByName(MIT);
        assertFalse(license.getIncludeNoticeFile().get());
        assertFalse(license.getNoticeRequired().get());
        assertFalse(license.getReciprocalLabel().isPresent());
    }

    @Test
    void apply__failsWhenAllowlistNamesUnregisteredLicense(@TempDir Path directory) throws IOException {
        // ARRANGE //
        Files.writeString(
                directory.resolve("allowed-licenses.json"),
                "{\"allowedLicenses\": [{\"moduleName\": \"a:a\", \"moduleLicense\": \"Weird License\"}]}"
        );
        Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(PLUGIN_ID);

        // ACT //
        Exception thrown = assertThrows(Exception.class, () -> ((ProjectInternal) project).evaluate());

        // ASSERT //
        assertTrue(causes(thrown).contains("a:a (Weird License)"), "got: " + causes(thrown));
    }

    @Test
    void apply__leavesNormalizerOffWithoutBundleFile(@TempDir Path directory) {
        // ARRANGE //
        Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(PLUGIN_ID);

        // ACT //
        ((ProjectInternal) project).evaluate();

        // ASSERT //
        assertEquals(0, project.getExtensions().getByType(LicenseReportExtension.class).filters.length);
    }

    @Test
    void apply__wiresCheckAndDocumentationTasks() {
        // ARRANGE //
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");

        // ACT //
        project.getPluginManager().apply(PLUGIN_ID);

        // ASSERT //
        assertTrue(project.getPlugins().hasPlugin("com.github.jk1.dependency-license-report"));
        Task check = project.getTasks().getByName("check");
        assertTrue(
                check.getTaskDependencies()
                        .getDependencies(check)
                        .contains(project.getTasks().getByName("checkLicense")),
                "check should run checkLicense"
        );
        assertEquals("documentation", project.getTasks().getByName("generateThirdPartyLicenses").getGroup());
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

    @Test
    void check__failsOnDisallowedLicense(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(
                directory,
                List.of(
                        new FixtureModule("apache-lib", APACHE, Map.of()),
                        new FixtureModule("lgpl-lib", LGPL, Map.of())
                ),
                allowlist(Map.of("example:apache-lib", APACHE)),
                registeredLicenses("")
        );

        // ACT //
        BuildResult result = runner(directory, "check").buildAndFail();

        // ASSERT //
        assertEquals(TaskOutcome.FAILED, Objects.requireNonNull(result.task(":checkLicense")).getOutcome());
        assertTrue(
                result.getOutput().contains("lgpl-lib"),
                "expected the failure to name the module:\n" + result.getOutput()
        );
    }

    @Test
    void generateThirdPartyLicenses__failsOnMissingRequiredNotice(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(
                directory,
                List.of(new FixtureModule("mit-lib", MIT, Map.of())),
                allowlist(Map.of("example:mit-lib", MIT)),
                registeredLicenses("")
        );

        // ACT //
        BuildResult result = runner(directory, "generateThirdPartyLicenses").buildAndFail();

        // ASSERT //
        assertTrue(
                result.getOutput().contains("No copyright notice supplied for: example:mit-lib:1.0"),
                "got:\n" + result.getOutput()
        );
    }

    @Test
    void generateThirdPartyLicenses__failsOnUnregisteredLicense(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(
                directory,
                List.of(new FixtureModule("bare-lib", null, Map.of())),
                allowlist(Map.of("example:bare-lib", "")),
                registeredLicenses("")
        );

        // ACT //
        BuildResult result = runner(directory, "generateThirdPartyLicenses").buildAndFail();

        // ASSERT //
        assertTrue(
                result.getOutput().contains("No license text registered for: example:bare-lib:1.0 (no license)"),
                "got:\n" + result.getOutput()
        );
    }

    @Test
    void generateThirdPartyLicenses__rendersAttributionIntoDistribution(@TempDir Path directory) throws IOException {
        // ARRANGE //
        writeFixture(
                directory,
                List.of(
                        new FixtureModule(
                                "apache-lib",
                                APACHE,
                                Map.of("META-INF/NOTICE", "Apache notice line one\nline two\n")
                        ),
                        new FixtureModule("apache-other", APACHE, Map.of()),
                        new FixtureModule("apache-txt", APACHE, Map.of("NOTICE.txt", "Root notice\n")),
                        new FixtureModule("bare-lib", null, Map.of()),
                        new FixtureModule("lgpl-lib", LGPL, Map.of()),
                        new FixtureModule("mit-lib", MIT, Map.of("META-INF/NOTICE", "MIT jar notice\n"))
                ),
                allowlist(
                        Map.of(
                                "example:apache-lib",
                                APACHE,
                                "example:apache-other",
                                APACHE,
                                "example:apache-txt",
                                APACHE,
                                "example:bare-lib",
                                "",
                                "example:lgpl-lib",
                                LGPL,
                                "example:mit-lib",
                                MIT
                        )
                ),
                registeredLicenses("""
                        copyrightNotices = [
                            'example:mit-lib': 'Copyright (c) Example MIT',
                            'example:bare-lib': 'Copyright (c) Example Bare'
                        ]
                        licenseOverrides = ['example:bare-lib': '%s']
                        """.formatted(MIT))
        );

        // ACT //
        BuildResult result = runner(directory, "distZip").build();

        // ASSERT //
        String attribution = readFromDistribution(directory, "THIRD-PARTY-LICENSES.txt");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":distZip")).getOutcome());
        assertTrue(attribution.contains("example:apache-lib:1.0 — " + APACHE + "\n"), attribution);
        assertTrue(attribution.contains("example:bare-lib:1.0 — " + MIT + "\n"), "override applied:\n" + attribution);
        assertTrue(attribution.contains("    Copyright (c) Example MIT\n"), attribution);
        assertTrue(
                attribution.contains("    NOTICE:\n        Apache notice line one\n        line two\n"),
                "the notice file should be indented under its entry:\n" + attribution
        );
        assertTrue(
                attribution.contains("example:apache-txt:1.0 — " + APACHE + "\n    NOTICE:\n        Root notice\n"),
                "a root-level NOTICE.txt should be read too:\n" + attribution
        );
        assertFalse(attribution.contains("example:apache-other:1.0 — " + APACHE + "\n    NOTICE"), attribution);
        assertFalse(
                attribution.contains("MIT jar notice"),
                "a notice is copied only under a license with includeNoticeFile:\n" + attribution
        );
        assertEquals(2, occurrences(attribution, "    NOTICE:\n"), attribution);
        assertTrue(
                attribution.contains("(the GNU Lesser General Public License), that separation"),
                "the reciprocal clause should name the registered label:\n" + attribution
        );
        for (String text : List.of("Apache text", "MIT text", "LGPL text")) {
            assertEquals(1, occurrences(attribution, text), text + " should appear once:\n" + attribution);
        }
        assertTrue(attribution.endsWith("-".repeat(80) + "\n"), attribution);
    }

    /**
     * Counts non-overlapping occurrences of a string.
     *
     * @param text the text to search
     * @param target the string to count
     * @return how many times {@code target} occurs in {@code text}
     */
    private static int occurrences(String text, String target) {
        int count = 0;
        for (int at = text.indexOf(target); at >= 0; at = text.indexOf(target, at + target.length())) {
            count++;
        }
        return count;
    }

    /**
     * Returns the plugin under test as a Groovy list literal of file paths, read from the metadata file
     * {@code java-gradle-plugin} generates onto the test classpath.
     *
     * @return a Groovy list literal naming every entry of the plugin's runtime classpath
     * @throws IOException if the metadata file cannot be read
     */
    private static String pluginClasspath() throws IOException {
        Properties metadata = new Properties();
        try (InputStream entries = ThirdPartyLicensesPluginTest.class.getClassLoader()
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

    @Test
    void preamble__namesReciprocalClause() {
        // ACT //
        String preamble = GenerateThirdPartyLicenses.preamble("the GNU Lesser General Public License");

        // ASSERT //
        assertTrue(preamble.contains("(the GNU Lesser General Public License), that separation"), preamble);
        assertTrue(preamble.contains("The corresponding source is available"), preamble);
    }

    @Test
    void preamble__omitsSourceSentenceWithoutReciprocalClause() {
        // ACT //
        String preamble = GenerateThirdPartyLicenses.preamble("");

        // ASSERT //
        assertTrue(preamble.contains("separate jar under lib/"), preamble);
        assertFalse(preamble.contains("()"), preamble);
        assertFalse(preamble.contains("reciprocal"), preamble);
        assertFalse(preamble.contains("corresponding source"), preamble);
    }

    /**
     * Reads one file out of the distribution archive the fixture built.
     *
     * @param directory the fixture project directory
     * @param fileName the name of the file, wherever it sits in the archive
     * @return the file's contents
     * @throws IOException if the archive cannot be read
     */
    private static String readFromDistribution(Path directory, String fileName) throws IOException {
        try (ZipFile zip = new ZipFile(directory.resolve("build/distributions/fixture.zip").toFile())) {
            ZipEntry entry = zip.stream()
                    .filter(candidate -> candidate.getName().endsWith("/" + fileName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(fileName + " is not in the distribution"));
            try (InputStream contents = zip.getInputStream(entry)) {
                return new String(contents.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    static Stream<ReciprocalClauseParameters> reciprocalClause__cases() {
        String eclipse = "the Eclipse Public License";
        String gnu = "the GNU Lesser General Public License";
        String mozilla = "the Mozilla Public License";
        return Stream.of(
                new ReciprocalClauseParameters("no labels", List.of(), ""),
                new ReciprocalClauseParameters("one label alone", List.of(gnu), gnu),
                new ReciprocalClauseParameters(
                        "two labels joined by and",
                        List.of(gnu, eclipse),
                        eclipse + " and " + gnu
                ),
                new ReciprocalClauseParameters(
                        "three labels as a serial list",
                        List.of(mozilla, gnu, eclipse),
                        eclipse + ", " + gnu + ", and " + mozilla
                ),
                new ReciprocalClauseParameters(
                        "repeated labels named once",
                        List.of(gnu, eclipse, gnu, eclipse),
                        eclipse + " and " + gnu
                )
        );
    }

    @MethodSource("reciprocalClause__cases")
    @ParameterizedTest
    void reciprocalClause__cases(ReciprocalClauseParameters parameters) {
        // ACT //
        String clause = GenerateThirdPartyLicenses.reciprocalClause(parameters.labels());

        // ASSERT //
        assertEquals(parameters.expected(), clause, parameters.name());
    }

    /**
     * Builds the {@code thirdPartyLicenses} block registering the three licenses the fixtures use.
     *
     * @param extraProperties further lines for the top of the block
     * @return the block, as build script source
     */
    private static String registeredLicenses(String extraProperties) {
        return """
                thirdPartyLicenses {
                    %s
                    licenses {
                        register('%s') { text = file('licenses/Apache-2.0.txt'); includeNoticeFile = true }
                        register('%s') { text = file('licenses/MIT.txt'); noticeRequired = true }
                        register('%s') {
                            text = file('licenses/LGPL-2.1.txt')
                            reciprocalLabel = 'the GNU Lesser General Public License'
                        }
                    }
                }
                """.formatted(extraProperties, APACHE, MIT, LGPL);
    }

    /**
     * Creates a runner for a fixture build.
     *
     * @param directory the fixture project directory
     * @param arguments the Gradle arguments
     * @return the runner
     */
    private static GradleRunner runner(Path directory, String... arguments) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withArguments(arguments);
    }

    static Stream<AllowlistParameters> unregisteredAllowedLicenses__cases() {
        String comment = "\"_comment\": \"ignored\", ";
        return Stream.of(
                new AllowlistParameters(
                        "every license registered",
                        "{" + comment + "\"allowedLicenses\": [{\"moduleName\": \"a:a\", \"moduleLicense\": \"" + MIT
                                + "\"}]}",
                        List.of()
                ),
                new AllowlistParameters(
                        "unregistered license named with its module",
                        "{\"allowedLicenses\": [{\"moduleName\": \"a:a\", \"moduleLicense\": \"" + MIT
                                + "\"}, {\"moduleName\": \"b:b\", \"moduleLicense\": \"Weird License\"}]}",
                        List.of("b:b (Weird License)")
                ),
                new AllowlistParameters(
                        "empty license skipped",
                        "{\"allowedLicenses\": [{\"moduleName\": \"a:a\", \"moduleLicense\": \"\"}]}",
                        List.of()
                ),
                new AllowlistParameters("no allowedLicenses key", "{" + comment + "\"other\": []}", List.of())
        );
    }

    @MethodSource("unregisteredAllowedLicenses__cases")
    @ParameterizedTest
    void unregisteredAllowedLicenses__cases(AllowlistParameters parameters) {
        // ACT //
        List<String> unregistered = ThirdPartyLicensesPlugin
                .unregisteredAllowedLicenses(parameters.allowlist(), Set.of(MIT, APACHE));

        // ASSERT //
        assertEquals(parameters.expected(), unregistered, parameters.name());
    }

    /**
     * Writes a consumer build that applies the plugin to an application depending on the given modules,
     * served from a Maven repository written beside it, along with the license texts and the allowlist.
     *
     * <p>
     * When the test task sets {@code testKit.fixtureJvmArgs}, the fixture also gets a
     * {@code gradle.properties} passing it to the daemon as {@code org.gradle.jvmargs}. That is how the
     * build records coverage from the fixture builds; without it, as under an IDE's own test runner,
     * the fixture runs the same and records nothing.
     *
     * @param directory the project directory to write into
     * @param modules the modules the build depends on
     * @param allowlist the contents of {@code allowed-licenses.json}
     * @param extensionBlock the {@code thirdPartyLicenses} block, as build script source
     * @throws IOException if the fixture cannot be written
     */
    private static void writeFixture(
            Path directory,
            List<FixtureModule> modules,
            String allowlist,
            String extensionBlock
    ) throws IOException {
        Path repository = directory.resolve("repository");
        for (FixtureModule module : modules) {
            Path version = repository.resolve("example/" + module.artifact() + "/1.0");
            Files.createDirectories(version);
            String licenses = module.license() == null ? ""
                    : "<licenses><license><name>" + module.license() + "</name></license></licenses>";
            Files.writeString(
                    version.resolve(module.artifact() + "-1.0.pom"),
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>"
                            + "<groupId>example</groupId><artifactId>" + module.artifact() + "</artifactId>"
                            + "<version>1.0</version>" + licenses + "</project>"
            );
            writeJar(version.resolve(module.artifact() + "-1.0.jar"), module.entries());
        }

        Path texts = Files.createDirectories(directory.resolve("licenses"));
        Files.writeString(texts.resolve("Apache-2.0.txt"), "Apache text\n");
        Files.writeString(texts.resolve("LGPL-2.1.txt"), "LGPL text\n");
        // No trailing newline, so the render has to supply one before the closing rule.
        Files.writeString(texts.resolve("MIT.txt"), "MIT text");
        Files.writeString(directory.resolve("allowed-licenses.json"), allowlist);

        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = \"fixture\"\n");
        String jvmArgs = System.getProperty("testKit.fixtureJvmArgs");
        if (jvmArgs != null) {
            Properties properties = new Properties();
            properties.setProperty("org.gradle.jvmargs", jvmArgs);
            try (OutputStream file = Files.newOutputStream(directory.resolve("gradle.properties"))) {
                properties.store(file, null);
            }
        }
        String dependencies = modules.stream()
                .map(module -> "    implementation \"example:" + module.artifact() + ":1.0\"")
                .collect(Collectors.joining("\n"));
        Files.writeString(directory.resolve("build.gradle"), """
                buildscript {
                    dependencies {
                        classpath files(%s)
                    }
                }

                apply plugin: "application"
                apply plugin: "org.coordinatekit.foundation.third-party-licenses"

                application {
                    mainClass = "fixture.Main"
                }

                repositories {
                    maven { url = uri("repository") }
                }

                dependencies {
                %s
                }

                %s
                """.formatted(pluginClasspath(), dependencies, extensionBlock));
    }

    /**
     * Writes a jar with the given entries.
     *
     * @param jar where to write it
     * @param entries the contents of each entry, by path
     * @throws IOException if the jar cannot be written
     */
    private static void writeJar(Path jar, Map<String, String> entries) throws IOException {
        Map<String, String> withManifest = new LinkedHashMap<>();
        withManifest.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
        withManifest.putAll(entries);
        try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, String> entry : withManifest.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}

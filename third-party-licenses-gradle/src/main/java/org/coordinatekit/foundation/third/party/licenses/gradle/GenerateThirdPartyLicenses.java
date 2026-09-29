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

import groovy.json.JsonSlurper;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Renders {@value #OUTPUT_FILE_NAME} from the license report's JSON inventory and the license texts
 * the build registered. The file lists every runtime dependency with its license, then prints each
 * distinct license text once.
 *
 * <p>
 * The task fails rather than write an attribution it knows to be incomplete: a dependency whose
 * license is not registered stops it, and so does a dependency under a license that requires a
 * copyright notice when none was supplied. Both messages name the {@code thirdPartyLicenses} block
 * that fixes them.
 *
 * <p>
 * The preamble is fixed. It says each dependency ships as a separate jar under {@code lib/}, and
 * when any dependency carries a reciprocal license, it adds the sentence that separation preserves
 * the right to replace a covered jar. That sentence names the licenses actually present, taken from
 * the registered {@link LicenseDefinition#getReciprocalLabel() reciprocal labels}, so it cannot
 * drift as dependencies change.
 *
 * @see ThirdPartyLicensesExtension
 */
@DisableCachingByDefault(because = "Renders one small text file, so restoring it from a cache saves nothing")
public abstract class GenerateThirdPartyLicenses extends DefaultTask {
    /** The rule that sets the heading lines off from the text below them. */
    private static final String HEADING_RULE = "=".repeat(80);

    /** Matches the jar entry that holds a dependency's own notice, in either of its usual places. */
    private static final Pattern NOTICE_ENTRY = Pattern
            .compile("(META-INF/)?NOTICE(\\.(txt|md))?", Pattern.CASE_INSENSITIVE);

    /** The name of the file the task writes into its output directory. */
    static final String OUTPUT_FILE_NAME = "THIRD-PARTY-LICENSES.txt";

    /** The rule that closes each license text. */
    private static final String TEXT_RULE = "-".repeat(80);

    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public GenerateThirdPartyLicenses() {}

    /**
     * Casts a parsed JSON array.
     *
     * @param json a value the JSON parser returned
     * @return the value as a list
     */
    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object json) {
        return (List<Object>) json;
    }

    /**
     * Casts a parsed JSON object.
     *
     * @param json a value the JSON parser returned
     * @return the value as a map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object json) {
        return (Map<String, Object>) json;
    }

    /**
     * Returns the first non-empty license the report lists for a dependency.
     *
     * @param dependency the dependency's entry in the report
     * @return the license name, or {@code null} when the report lists none
     */
    private static @Nullable String firstLicense(Map<String, Object> dependency) {
        Object licenses = dependency.get("moduleLicenses");
        if (licenses == null) {
            return null;
        }
        for (Object entry : asList(licenses)) {
            Object name = asMap(entry).get("moduleLicense");
            if (name instanceof String license && !license.isEmpty()) {
                return license;
            }
        }
        return null;
    }

    /**
     * Renders the attribution file, then writes it to the output directory.
     *
     * @throws IOException if a license text or a jar cannot be read, or the output cannot be written
     */
    @TaskAction
    public void generate() throws IOException {
        Map<String, LicenseDefinition> definitions = new HashMap<>();
        getLicenses().forEach(definition -> definitions.put(definition.getName(), definition));

        List<Dependency> dependencies = readDependencies(definitions);
        requireRegistered(dependencies, definitions);
        requireNotices(dependencies, definitions);

        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve(OUTPUT_FILE_NAME), render(dependencies, definitions));
    }

    /**
     * The copyright notice to print under a dependency's entry, keyed by {@code group:artifact}.
     *
     * @return the notice text for each module
     */
    @Input
    public abstract MapProperty<String, String> getCopyrightNotices();

    /**
     * The license to report for a dependency in place of what its POM declares, keyed by
     * {@code group:artifact}.
     *
     * @return the license name for each overridden module
     */
    @Input
    public abstract MapProperty<String, String> getLicenseOverrides();

    /**
     * The licenses the build registered, by report name.
     *
     * @return the registered licenses
     */
    @Nested
    public abstract NamedDomainObjectContainer<LicenseDefinition> getLicenses();

    /**
     * The directory the attribution file is written into.
     *
     * @return the output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * The license report's JSON inventory of the runtime dependencies, in its all-licenses-per-module
     * form.
     *
     * @return the report file
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getReport();

    /**
     * The resolved runtime artifacts, by which each dependency's jar is found to read its own notice
     * file. Not an input itself, because {@link #getRuntimeClasspath()} fingerprints the same jars.
     *
     * @return the runtime artifacts
     */
    @Internal
    public abstract SetProperty<ResolvedArtifactResult> getRuntimeArtifacts();

    /**
     * The jars the runtime artifacts resolve to, so a changed dependency re-renders the attribution.
     *
     * @return the runtime classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /**
     * Maps each resolved runtime module to its jar, so a dependency's own notice file can be read from
     * it. When a module resolves to several artifacts, the last one wins.
     *
     * @return the jar of each module, keyed by {@code group:artifact}
     */
    private Map<String, File> jarByModule() {
        Map<String, File> jars = new HashMap<>();
        for (ResolvedArtifactResult artifact : getRuntimeArtifacts().get()) {
            if (artifact.getId().getComponentIdentifier()instanceof ModuleComponentIdentifier module) {
                jars.put(module.getGroup() + ":" + module.getModule(), artifact.getFile());
            }
        }
        return jars;
    }

    /**
     * Reads the text of the first notice file in a jar.
     *
     * @param jar the dependency's jar, or {@code null} when the build resolved none for it
     * @return the notice text, or {@code null} when there is no jar or it carries no notice file
     * @throws IOException if the jar cannot be read
     */
    private static @Nullable String noticeFileText(@Nullable File jar) throws IOException {
        if (jar == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(jar)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (NOTICE_ENTRY.matcher(entry.getName()).matches()) {
                    try (InputStream contents = zip.getInputStream(entry)) {
                        return new String(contents.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Builds the preamble paragraph.
     *
     * @param reciprocalClause the clause naming the reciprocal licenses, or an empty string for none
     * @return the paragraph, without a trailing newline
     */
    static String preamble(String reciprocalClause) {
        String preamble = "Each dependency is shipped as a separate jar under lib/ (this distribution builds "
                + "no fat jar).";
        if (!reciprocalClause.isEmpty()) {
            preamble += " For the reciprocal and copyleft licenses below (" + reciprocalClause + "), that "
                    + "separation preserves your right to replace a covered jar with a modified build of the "
                    + "same library. The corresponding source is available from each library's project, "
                    + "reachable from the Maven coordinate shown.";
        }
        return preamble;
    }

    /**
     * Reads the dependencies out of the license report, applying the overrides and the copyright
     * notices, and returns them sorted by coordinate. A dependency's license is its override if it has
     * one, and otherwise the first non-empty license the report lists for it.
     *
     * @param definitions the registered licenses by name
     * @return every dependency in the report
     * @throws IOException if a dependency's jar cannot be read
     */
    private List<Dependency> readDependencies(Map<String, LicenseDefinition> definitions) throws IOException {
        Map<String, File> jars = jarByModule();
        Map<String, String> overrides = getLicenseOverrides().get();
        Map<String, String> notices = getCopyrightNotices().get();

        Object report = new JsonSlurper().parse(getReport().get().getAsFile());
        List<Dependency> dependencies = new ArrayList<>();
        for (Object entry : asList(asMap(report).get("dependencies"))) {
            Map<String, Object> dependency = asMap(entry);
            String moduleName = (String) dependency.get("moduleName");

            String license = overrides.get(moduleName);
            if (license == null || license.isEmpty()) {
                license = firstLicense(dependency);
            }

            LicenseDefinition definition = license == null ? null : definitions.get(license);
            boolean includeNotice = definition != null && definition.getIncludeNoticeFile().getOrElse(false);
            dependencies.add(
                    new Dependency(
                            moduleName + ":" + dependency.get("moduleVersion"),
                            license,
                            notices.get(moduleName),
                            includeNotice ? noticeFileText(jars.get(moduleName)) : null
                    )
            );
        }
        dependencies.sort(Comparator.comparing(Dependency::gav));
        return dependencies;
    }

    /**
     * Joins reciprocal license labels into the clause the preamble names them by: one label alone, two
     * joined by {@code and}, three or more as a serial list. Labels are sorted and each is named once.
     *
     * @param labels the reciprocal labels of the licenses present, in any order and possibly repeated
     * @return the clause naming the reciprocal licenses, or an empty string when there are none
     */
    static String reciprocalClause(Collection<String> labels) {
        List<String> sorted = new ArrayList<>(new TreeSet<>(labels));
        if (sorted.size() <= 2) {
            return String.join(" and ", sorted);
        }
        return String.join(", ", sorted.subList(0, sorted.size() - 1)) + ", and " + sorted.get(sorted.size() - 1);
    }

    /**
     * Collects the reciprocal label of each dependency whose license has one.
     *
     * @param dependencies the dependencies being attributed
     * @param definitions the registered licenses by name
     * @return the labels, one per reciprocal dependency
     */
    private static List<String> reciprocalLabels(
            List<Dependency> dependencies,
            Map<String, LicenseDefinition> definitions
    ) {
        List<String> labels = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            LicenseDefinition definition = definitions.get(dependency.license());
            if (definition != null && definition.getReciprocalLabel().isPresent()) {
                labels.add(definition.getReciprocalLabel().get());
            }
        }
        return labels;
    }

    /**
     * Renders the attribution file.
     *
     * @param dependencies the dependencies to attribute, in output order
     * @param definitions the registered licenses by name
     * @return the file contents
     * @throws IOException if a license text cannot be read
     */
    private static String render(List<Dependency> dependencies, Map<String, LicenseDefinition> definitions)
            throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("Third-party license attribution\n").append(HEADING_RULE).append("\n\n");
        builder.append("This distribution bundles the third-party dependencies listed below. Each is shown\n");
        builder.append("with its license; the full license texts follow, one copy per distinct license.\n\n");
        builder.append(preamble(reciprocalClause(reciprocalLabels(dependencies, definitions)))).append("\n\n");

        for (Dependency dependency : dependencies) {
            // U+2014, escaped so the output does not depend on the compiler's source encoding.
            builder.append(dependency.gav()).append(" — ").append(dependency.license()).append('\n');
            if (dependency.notice() != null && !dependency.notice().isEmpty()) {
                builder.append("    ").append(dependency.notice()).append('\n');
            }
            String noticeFileText = dependency.noticeFileText();
            if (noticeFileText != null && !noticeFileText.isEmpty()) {
                builder.append("    NOTICE:\n");
                noticeFileText.lines().forEach(line -> builder.append("        ").append(line).append('\n'));
            }
        }

        builder.append("\nFull license texts\n").append(HEADING_RULE).append('\n');
        TreeSet<String> licenses = dependencies.stream()
                .map(Dependency::license)
                .collect(Collectors.toCollection(TreeSet::new));
        for (String license : licenses) {
            String text = Files.readString(definitions.get(license).getText().get().getAsFile().toPath());
            builder.append('\n').append(license).append("\n\n").append(text);
            if (!text.endsWith("\n")) {
                builder.append('\n');
            }
            builder.append(TEXT_RULE).append('\n');
        }
        return builder.toString();
    }

    /**
     * Fails when a dependency's license needs a copyright notice that was not supplied.
     *
     * @param dependencies the dependencies to attribute
     * @param definitions the registered licenses by name
     */
    private static void requireNotices(List<Dependency> dependencies, Map<String, LicenseDefinition> definitions) {
        List<String> missing = dependencies.stream().filter(dependency -> {
            LicenseDefinition definition = definitions.get(dependency.license());
            return definition != null && definition.getNoticeRequired().getOrElse(false)
                    && (dependency.notice() == null || dependency.notice().isEmpty());
        }).map(Dependency::gav).toList();
        if (!missing.isEmpty()) {
            throw new GradleException(
                    "No copyright notice supplied for: " + String.join(", ", missing)
                            + ". Add each one to copyrightNotices in the thirdPartyLicenses block."
            );
        }
    }

    /**
     * Fails when a dependency has no license, or one that was never registered.
     *
     * @param dependencies the dependencies to attribute
     * @param definitions the registered licenses by name
     */
    private static void requireRegistered(List<Dependency> dependencies, Map<String, LicenseDefinition> definitions) {
        List<String> unregistered = dependencies.stream()
                .filter(dependency -> dependency.license() == null || !definitions.containsKey(dependency.license()))
                .map(
                        dependency -> dependency.gav() + " ("
                                + (dependency.license() == null ? "no license" : dependency.license()) + ")"
                )
                .toList();
        if (!unregistered.isEmpty()) {
            throw new GradleException(
                    "No license text registered for: " + String.join(", ", unregistered)
                            + ". Register each license in the licenses block of thirdPartyLicenses, or map a"
                            + " dependency that declares none to one in licenseOverrides."
            );
        }
    }

    /**
     * One dependency as the attribution lists it.
     *
     * @param gav the {@code group:artifact:version} coordinate
     * @param license the license name, or {@code null} when the dependency has none
     * @param notice the copyright notice, or {@code null} when none was supplied
     * @param noticeFileText the text of the dependency's own notice file, or {@code null} when none is
     *        wanted or found
     */
    private record Dependency(
            String gav,
            @Nullable String license,
            @Nullable String notice,
            @Nullable String noticeFileText
    ) {}
}

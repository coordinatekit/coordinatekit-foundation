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

import com.github.jk1.license.LicenseReportExtension;
import com.github.jk1.license.LicenseReportPlugin;
import com.github.jk1.license.filter.DependencyFilter;
import com.github.jk1.license.filter.LicenseBundleNormalizer;
import com.github.jk1.license.render.JsonReportRenderer;
import com.github.jk1.license.render.ReportRenderer;
import groovy.json.JsonException;
import groovy.json.JsonParserType;
import groovy.json.JsonSlurper;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.distribution.DistributionContainer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks a build's runtime dependency licenses and renders the attribution file that ships with its
 * distribution. Applying this plugin applies the {@code com.github.jk1.dependency-license-report}
 * plugin and configures it from the {@link ThirdPartyLicensesExtension}, so a consumer never
 * touches that plugin's own block. Its {@code checkLicense} task fails the build when a
 * dependency's license is not on the allowlist, and is wired into {@code check}. The
 * {@code generateThirdPartyLicenses} task, a {@link GenerateThirdPartyLicenses}, renders the
 * attribution, and under the {@code application} plugin it is added to the main distribution.
 *
 * <p>
 * The plugin reads the {@code runtimeClasspath} configuration, so it registers
 * {@code generateThirdPartyLicenses} once the {@code java} plugin is applied, in whichever order
 * the two are applied. Every license fact, the texts, the allowlist, the overrides, and the
 * copyright notices, stays with the consumer, and the plugin holds only the logic that combines
 * them. The allowlist and the registered licenses are checked against each other when the project
 * is evaluated, so a license that one names and the other lacks fails before any dependency
 * resolves.
 *
 * @see ThirdPartyLicensesExtension
 */
public class ThirdPartyLicensesPlugin implements Plugin<Project> {
    /** The name of the license report task that fails on a disallowed license. */
    private static final String CHECK_LICENSE_TASK_NAME = "checkLicense";

    /** The name of the block a build configures the plugin through. */
    private static final String EXTENSION_NAME = "thirdPartyLicenses";

    /** The name of the license report task that writes the JSON inventory. */
    private static final String GENERATE_LICENSE_REPORT_TASK_NAME = "generateLicenseReport";

    /** The name of the task that renders the attribution file. */
    private static final String GENERATE_TASK_NAME = "generateThirdPartyLicenses";

    /** Where the license report writes, relative to the build directory. */
    private static final String REPORT_DIRECTORY = "reports/dependency-license";

    /** The name of the JSON inventory the license report writes into its directory. */
    private static final String REPORT_FILE_NAME = "third-party-report.json";

    /** The configuration that holds what the distribution bundles. */
    private static final String RUNTIME_CLASSPATH = "runtimeClasspath";

    /** Instantiated by Gradle when a build applies the plugin. */
    public ThirdPartyLicensesPlugin() {}

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(LicenseReportPlugin.class);

        ThirdPartyLicensesExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, ThirdPartyLicensesExtension.class);
        extension.getAllowedLicensesFile()
                .convention(project.getLayout().getProjectDirectory().file("allowed-licenses.json"));
        extension.getExcludeBoms().convention(false);
        extension.getExcludeGroups().convention(List.of());
        extension.getNormalizerBundleFile()
                .convention(project.getLayout().getProjectDirectory().file("license-normalizer-bundle.json"));
        extension.getLicenses().configureEach(license -> {
            license.getIncludeNoticeFile().convention(false);
            license.getNoticeRequired().convention(false);
        });

        project.getPluginManager()
                .withPlugin(
                        "lifecycle-base",
                        applied -> project.getTasks()
                                .named("check")
                                .configure(check -> check.dependsOn(CHECK_LICENSE_TASK_NAME))
                );
        // runtimeClasspath exists only once java is applied, so plugin order in the consumer's build
        // must not matter.
        project.getPluginManager().withPlugin("java", applied -> {
            TaskProvider<GenerateThirdPartyLicenses> generate = registerGenerate(project, extension);
            project.getPluginManager()
                    .withPlugin(
                            "application",
                            distributing -> project.getExtensions()
                                    .getByType(DistributionContainer.class)
                                    .getByName("main")
                                    .getContents()
                                    .from(generate, spec -> spec.include(GenerateThirdPartyLicenses.OUTPUT_FILE_NAME))
                    );
        });

        // jk1's extension is a bag of eager fields rather than properties, so it can only be filled
        // once the build script has set ours.
        project.afterEvaluate(evaluated -> configureReport(evaluated, extension));
    }

    /**
     * Copies the extension's values into the license report's own extension, and fails if the allowlist
     * names a license nothing registered.
     *
     * @param project the evaluated project
     * @param extension the values the build set
     */
    private static void configureReport(Project project, ThirdPartyLicensesExtension extension) {
        File allowlist = extension.getAllowedLicensesFile().get().getAsFile();
        if (allowlist.isFile()) {
            requireRegistered(allowlist, extension);
        }

        LicenseReportExtension report = project.getExtensions().getByType(LicenseReportExtension.class);
        report.outputDir = reportDirectory(project).get().getAsFile().getAbsolutePath();
        report.configurations = new String[] {RUNTIME_CLASSPATH};
        report.excludeGroups = extension.getExcludeGroups().get().toArray(new String[0]);
        report.excludeBoms = extension.getExcludeBoms().get();
        report.allowedLicensesFile = allowlist;
        report.renderers = new ReportRenderer[] {new JsonReportRenderer(REPORT_FILE_NAME, false)};

        File bundle = extension.getNormalizerBundleFile().get().getAsFile();
        report.filters = bundle.isFile() ? new DependencyFilter[] {new LicenseBundleNormalizer(bundle.getPath(), true)}
                : new DependencyFilter[0];
    }

    /**
     * Returns whether an allowlist rule can be satisfied by a registered license, by the license
     * report's own test: {@code .*} always, otherwise a registered name that equals the rule or that
     * the rule matches in full as a regular expression. A rule that is not a valid expression can only
     * match literally, as it could not in the report either.
     *
     * @param rule an allowlist {@code moduleLicense}
     * @param registered the names of the registered licenses
     * @return whether some registered license satisfies the rule
     */
    private static boolean isRegistered(String rule, Set<String> registered) {
        if (rule.equals(".*") || registered.contains(rule)) {
            return true;
        }
        try {
            Pattern pattern = Pattern.compile(rule);
            return registered.stream().anyMatch(name -> pattern.matcher(name).matches());
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * Registers the task that renders the attribution, wired to the license report's output and to the
     * runtime classpath by name, so nothing resolves until the task runs.
     *
     * @param project the project applying the plugin
     * @param extension the values the build sets
     * @return the registered task
     */
    private static TaskProvider<GenerateThirdPartyLicenses> registerGenerate(
            Project project,
            ThirdPartyLicensesExtension extension
    ) {
        Provider<ArtifactCollection> runtime = project.getConfigurations()
                .named(RUNTIME_CLASSPATH)
                .map(configuration -> configuration.getIncoming().getArtifacts());
        return project.getTasks().register(GENERATE_TASK_NAME, GenerateThirdPartyLicenses.class, task -> {
            task.setGroup("documentation");
            task.setDescription(
                    "Renders THIRD-PARTY-LICENSES.txt from the license report and the registered license texts."
            );
            task.dependsOn(GENERATE_LICENSE_REPORT_TASK_NAME, CHECK_LICENSE_TASK_NAME);
            task.getCopyrightNotices().set(extension.getCopyrightNotices());
            task.getLicenseOverrides().set(extension.getLicenseOverrides());
            task.getLicenses().addAllLater(project.provider(() -> List.copyOf(extension.getLicenses())));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("reports/third-party-licenses"));
            task.getReport().set(reportDirectory(project).map(directory -> directory.file(REPORT_FILE_NAME)));
            task.getRuntimeArtifacts().set(runtime.flatMap(ArtifactCollection::getResolvedArtifacts));
            task.getRuntimeClasspath().from(runtime.map(ArtifactCollection::getArtifactFiles));
        });
    }

    /**
     * Returns the directory the license report writes its inventory into.
     *
     * @param project the project applying the plugin
     * @return the report directory
     */
    private static Provider<Directory> reportDirectory(Project project) {
        return project.getLayout().getBuildDirectory().dir(REPORT_DIRECTORY);
    }

    /**
     * Fails when the allowlist names a license that is not registered.
     *
     * @param allowlist the allowlist file
     * @param extension the values the build set
     */
    private static void requireRegistered(File allowlist, ThirdPartyLicensesExtension extension) {
        String json;
        try {
            json = Files.readString(allowlist.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + allowlist, e);
        }
        List<String> unregistered;
        try {
            unregistered = unregisteredAllowedLicenses(json, extension.getLicenses().getNames());
        } catch (IllegalArgumentException e) {
            throw new GradleException("Cannot read " + allowlist.getName() + ": " + e.getMessage(), e);
        }
        if (!unregistered.isEmpty()) {
            throw new GradleException(
                    allowlist.getName() + " allows licenses that are not registered in thirdPartyLicenses.licenses: "
                            + String.join(", ", unregistered)
                            + ". Register each one, or correct the name to match the license report's."
            );
        }
    }

    /**
     * Returns the licenses an allowlist names that no registered license matches, each with the module
     * that names it. The file is read the way the license report's {@code checkLicense} reads it:
     * leniently parsed, and each {@code moduleLicense} taken as a regular expression that has to match
     * a whole name, with {@code .*} accepted outright. An empty {@code moduleLicense} is skipped,
     * because it is the report's rule for a dependency whose POM declares no license, and an override
     * supplies the real one.
     *
     * @param allowlistJson the contents of the allowlist file
     * @param registered the names of the registered licenses
     * @return one {@code module (license)} entry for each allowlisted license that no registered name
     *         satisfies
     * @throws IllegalArgumentException if the contents do not parse, or are not an object whose
     *         {@code allowedLicenses} is a list of objects
     */
    static List<String> unregisteredAllowedLicenses(String allowlistJson, Set<String> registered) {
        Object allowlist;
        try {
            allowlist = new JsonSlurper().setType(JsonParserType.LAX).parseText(allowlistJson);
        } catch (JsonException e) {
            throw new IllegalArgumentException("it is not valid JSON: " + e.getMessage(), e);
        }
        if (!(allowlist instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("its top level is not an object");
        }
        Object entries = root.get("allowedLicenses");
        List<String> unregistered = new ArrayList<>();
        if (entries == null) {
            return unregistered;
        }
        if (!(entries instanceof List<?> list)) {
            throw new IllegalArgumentException("allowedLicenses is not a list");
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> allowed)) {
                throw new IllegalArgumentException("allowedLicenses holds an entry that is not an object: " + entry);
            }
            if (allowed.get("moduleLicense")instanceof String license && !license.isEmpty()
                    && !isRegistered(license, registered)) {
                unregistered.add(allowed.get("moduleName") + " (" + license + ")");
            }
        }
        return unregistered;
    }
}

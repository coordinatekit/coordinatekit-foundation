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

import net.ltgt.gradle.errorprone.CheckSeverity;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Everything that names a type from the {@code net.ltgt.errorprone} plugin. Those types are a
 * {@code compileOnly} dependency, because the consumer owns Error Prone rather than this plugin,
 * which means they are absent from the build script classpath of a project that has not applied
 * that plugin.
 *
 * <p>
 * Keeping them out of {@link ConcordancePlugin} is what lets that class still load there. Gradle
 * decorates a plugin class by generating a subclass, which resolves every method descriptor it
 * declares, so a single method returning {@code CheckSeverity} would make the plugin fail to
 * instantiate with a {@link NoClassDefFoundError} rather than with the message naming the missing
 * plugin. Nothing here is touched until {@code net.ltgt.errorprone} has been applied.
 */
final class ErrorProneConfiguration {
    /**
     * Passes the exemption lists to the check as {@code -XepOpt} flags, leaving out a list the build
     * left empty so that the command line says what the build said. Reading the lists when the
     * arguments are requested rather than when the task is configured is what lets a consumer's
     * {@code concordance} block, which is evaluated after this plugin is applied, take effect.
     */
    private static final class ExemptionFlags implements CommandLineArgumentProvider {
        /** The lifecycle annotation names the build configured. */
        private final ListProperty<String> lifecycleAnnotations;

        /** The scaffolding field type names the build configured. */
        private final ListProperty<String> scaffoldingFieldTypes;

        /**
         * Creates a provider over the extension's two lists.
         *
         * @param lifecycleAnnotations the lifecycle annotation names
         * @param scaffoldingFieldTypes the scaffolding field type names
         */
        ExemptionFlags(ListProperty<String> lifecycleAnnotations, ListProperty<String> scaffoldingFieldTypes) {
            this.lifecycleAnnotations = lifecycleAnnotations;
            this.scaffoldingFieldTypes = scaffoldingFieldTypes;
        }

        /**
         * Appends {@code -XepOpt:name=a,b} to {@code arguments} unless {@code names} is empty.
         *
         * @param arguments the arguments collected so far
         * @param name the check flag
         * @param names the configured names
         */
        private static void addFlag(List<String> arguments, String name, List<String> names) {
            if (!names.isEmpty()) {
                arguments.add("-XepOpt:" + name + "=" + String.join(",", names));
            }
        }

        @Override
        public Iterable<String> asArguments() {
            List<String> arguments = new ArrayList<>();
            addFlag(arguments, LIFECYCLE_ANNOTATIONS, getLifecycleAnnotations().get());
            addFlag(arguments, SCAFFOLDING_FIELD_TYPES, getScaffoldingFieldTypes().get());
            return arguments;
        }

        /**
         * Returns the lifecycle annotation names as a tracked task input.
         *
         * @return the configured names
         */
        @Input
        ListProperty<String> getLifecycleAnnotations() {
            return lifecycleAnnotations;
        }

        /**
         * Returns the scaffolding field type names as a tracked task input.
         *
         * @return the configured names
         */
        @Input
        ListProperty<String> getScaffoldingFieldTypes() {
            return scaffoldingFieldTypes;
        }
    }

    /** The published module holding the check, which is resolved at this plugin's own version. */
    private static final String CHECK_COORDINATE = "org.coordinatekit.foundation:concordance-errorprone";

    /** The check's name in diagnostics, {@code -Xep} flags, and {@code @SuppressWarnings}. */
    private static final String CHECK_NAME = "Concordance";

    /** The check flag carrying the lifecycle annotation names. */
    private static final String LIFECYCLE_ANNOTATIONS = "Concordance:LifecycleAnnotations";

    /** The check flag carrying the scaffolding field type names. */
    private static final String SCAFFOLDING_FIELD_TYPES = "Concordance:ScaffoldingFieldTypes";

    /** Not instantiated; everything here is static. */
    private ErrorProneConfiguration() {}

    /**
     * Returns the coordinate of the check jar at this plugin's own version.
     *
     * @return the dependency notation for the check
     * @throws GradleException if the plugin jar carries no {@code Implementation-Version}, which means
     *         it was loaded from class files rather than from a published jar
     */
    private static String checkDependency() {
        String version = ConcordancePlugin.class.getPackage().getImplementationVersion();
        if (version == null) {
            throw new GradleException(
                    "Cannot resolve " + CHECK_COORDINATE
                            + ": the Concordance plugin was loaded from class files rather than from a jar,"
                            + " so it cannot read its own version."
            );
        }
        return CHECK_COORDINATE + ":" + version;
    }

    /**
     * Adds the check to Error Prone's classpath and points every Java compilation at it.
     *
     * @param project the project being configured
     * @param extension the block the check reads its configuration from
     */
    static void configure(Project project, ConcordanceExtension extension) {
        project.getDependencies().add(ErrorPronePlugin.CONFIGURATION_NAME, checkDependency());

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            ErrorProneOptions options = ((ExtensionAware) task.getOptions()).getExtensions()
                    .getByType(ErrorProneOptions.class);
            options.check(CHECK_NAME, extension.getSeverity().map(ErrorProneConfiguration::severity));
            options.getErrorproneArgumentProviders()
                    .add(new ExemptionFlags(extension.getLifecycleAnnotations(), extension.getScaffoldingFieldTypes()));
        });
    }

    /**
     * Parses the configured severity, accepting any case so that {@code severity = "warn"} reads as
     * naturally in a build script as {@code "WARN"}.
     *
     * @param name the configured severity
     * @return the matching severity
     * @throws InvalidUserDataException if {@code name} is not one of Error Prone's severities
     */
    private static CheckSeverity severity(String name) {
        try {
            return CheckSeverity.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new InvalidUserDataException(
                    "concordance.severity must be one of " + Arrays.toString(CheckSeverity.values()) + ", got: " + name,
                    cause
            );
        }
    }
}

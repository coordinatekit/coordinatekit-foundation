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
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Arrays;
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
            options.option(
                    LIFECYCLE_ANNOTATIONS,
                    extension.getLifecycleAnnotations().map(names -> String.join(",", names))
            );
            options.option(
                    SCAFFOLDING_FIELD_TYPES,
                    extension.getScaffoldingFieldTypes().map(names -> String.join(",", names))
            );
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

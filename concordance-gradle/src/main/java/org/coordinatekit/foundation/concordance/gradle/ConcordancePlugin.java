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

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.List;

/**
 * Wires the Concordance member-order check into a build that already has Error Prone. Applying this
 * plugin adds the check jar to the {@code errorprone} configuration and configures every
 * {@link JavaCompile} task from the {@link ConcordanceExtension}, so a consumer's whole integration
 * is the {@code plugins} block, the {@code errorprone} dependency it already declares, and an
 * optional {@code concordance} block.
 *
 * <p>
 * Ownership of Error Prone itself stays with the consumer. This plugin reacts to
 * {@code net.ltgt.errorprone} rather than applying it, so the consumer keeps its own Error Prone
 * version, its {@code error_prone_core} coordinate, and its compiler arguments. A build that
 * forgets the line fails at the end of evaluation with a message naming the missing plugin, so the
 * omission surfaces as an error rather than as a check that quietly never ran.
 *
 * <p>
 * The check jar is resolved at this plugin's own version, read from the
 * {@code Implementation-Version} attribute of the jar this class was loaded from, so the check and
 * the plugin that configures it are always the same release.
 *
 * @see ErrorProneConfiguration
 */
public class ConcordancePlugin implements Plugin<Project> {
    /** The id of the Error Prone plugin this one configures but never applies. */
    private static final String ERROR_PRONE_PLUGIN_ID = "net.ltgt.errorprone";

    /** The name of the block a build configures the check through. */
    private static final String EXTENSION_NAME = "concordance";

    /** Instantiated by Gradle when a build applies the plugin. */
    public ConcordancePlugin() {}

    @Override
    public void apply(Project project) {
        ConcordanceExtension extension = project.getExtensions().create(EXTENSION_NAME, ConcordanceExtension.class);
        extension.getLifecycleAnnotations().convention(List.of());
        extension.getScaffoldingFieldTypes().convention(List.of());
        extension.getSeverity().convention("ERROR");

        project.getPlugins()
                .withId(ERROR_PRONE_PLUGIN_ID, applied -> ErrorProneConfiguration.configure(project, extension));

        project.afterEvaluate(evaluated -> {
            if (!evaluated.getPlugins().hasPlugin(ERROR_PRONE_PLUGIN_ID)) {
                throw new GradleException(
                        "The " + EXTENSION_NAME + " plugin needs the " + ERROR_PRONE_PLUGIN_ID
                                + " plugin, which it does not apply itself so that the build keeps ownership of"
                                + " its Error Prone version and arguments. Add it to the plugins block of "
                                + evaluated.getPath() + "."
                );
            }
        });
    }
}

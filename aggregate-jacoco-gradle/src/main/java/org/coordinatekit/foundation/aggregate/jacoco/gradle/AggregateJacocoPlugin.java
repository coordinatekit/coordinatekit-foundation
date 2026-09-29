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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Registers {@code aggregateJacocoReport}, one JaCoCo report over the tests of every selected
 * project, and the {@code aggregateJacoco} block that selects them. The report is written to
 * {@code build/reports/jacoco/aggregateJacocoReport}, as XML and as HTML.
 *
 * <p>
 * The task runs each selected project's {@code test} task and reads the execution data that
 * project's own {@code jacocoTestReport} reads, so any extra data a build adds there reaches the
 * aggregate too. A selected project without a {@code jacocoTestReport} task fails the build with a
 * message that names the project.
 *
 * <p>
 * Applying this plugin also applies {@code jacoco} to the root project, which supplies the Ant
 * tasks the report runs on. They resolve from the root project's repositories, so the root needs
 * {@code mavenCentral()} even when it has no dependencies of its own.
 *
 * @see AggregateJacocoExtension
 */
public class AggregateJacocoPlugin implements Plugin<Project> {
    /** The name of the block a build selects the covered projects through. */
    private static final String EXTENSION_NAME = "aggregateJacoco";

    /** The id of the plugin whose projects are selected by default. */
    private static final String JACOCO_PLUGIN_ID = "jacoco";

    /** The name of the task that generates the report. */
    private static final String TASK_NAME = "aggregateJacocoReport";

    /** Instantiated by Gradle when a build applies the plugin. */
    public AggregateJacocoPlugin() {}

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JacocoPlugin.class);

        AggregateJacocoExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, AggregateJacocoExtension.class);
        extension.getProjects()
                .convention(
                        project.provider(
                                () -> project.getSubprojects()
                                        .stream()
                                        .filter(subproject -> subproject.getPlugins().hasPlugin(JACOCO_PLUGIN_ID))
                                        .collect(Collectors.toSet())
                        )
                );

        project.getTasks().register(TASK_NAME, JacocoReport.class, task -> configure(extension, task));
    }

    /**
     * Configures the aggregate task. Everything that depends on the selected projects goes through a
     * {@link Callable}, so the selection is read when the task graph is built, after every subproject
     * has been evaluated.
     *
     * @param extension the block the build configured
     * @param task the task to configure
     */
    private static void configure(AggregateJacocoExtension extension, JacocoReport task) {
        task.setDescription("Generates one JaCoCo coverage report for every selected module.");
        task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);

        Callable<Set<Project>> selected = () -> extension.getProjects().get();
        task.dependsOn(
                (Callable<Object>) () -> selected.call()
                        .stream()
                        .map(selectedProject -> selectedProject.getTasks().named("test"))
                        .toList()
        );
        task.getExecutionData()
                .from(
                        (Callable<Object>) () -> selected.call()
                                .stream()
                                .map(selectedProject -> jacocoTestReport(selectedProject).getExecutionData())
                                .toList()
                );
        task.getSourceDirectories()
                .from(
                        (Callable<Object>) () -> selected.call()
                                .stream()
                                .map(selectedProject -> mainSourceSet(selectedProject).getAllSource().getSrcDirs())
                                .toList()
                );
        task.getClassDirectories()
                .from(
                        (Callable<Object>) () -> selected.call()
                                .stream()
                                .map(selectedProject -> mainSourceSet(selectedProject).getOutput())
                                .toList()
                );

        task.getReports().getXml().getRequired().set(true);
        task.getReports().getHtml().getRequired().set(true);
    }

    /**
     * Looks up the {@code jacocoTestReport} task of a selected project.
     *
     * @param project a project selected for the aggregate report
     * @return the project's {@code jacocoTestReport} task
     * @throws UnknownTaskException if the project has none
     */
    private static JacocoReport jacocoTestReport(Project project) {
        TaskProvider<JacocoReport> report;
        try {
            report = project.getTasks().named("jacocoTestReport", JacocoReport.class);
        } catch (UnknownTaskException e) {
            throw new UnknownTaskException(
                    "Project " + project.getPath() + " is selected for " + TASK_NAME + " but has no"
                            + " jacocoTestReport task. Apply the " + JACOCO_PLUGIN_ID + " and java plugins to it,"
                            + " or leave it out of " + EXTENSION_NAME + ".projects.",
                    e
            );
        }
        return report.get();
    }

    /**
     * Looks up a project's {@code main} source set.
     *
     * @param project a project that applies the {@code java} plugin
     * @return the project's {@code main} source set
     */
    private static SourceSet mainSourceSet(Project project) {
        return project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }
}

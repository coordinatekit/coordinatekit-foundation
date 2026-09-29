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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Registers {@code aggregateJavadoc}, one Javadoc task over the main sources of every selected
 * project, and the {@code aggregateJavadoc} block that configures it. The pages land in
 * {@code build/docs/aggregateJavadoc}, and the task fails on any Javadoc warning.
 *
 * <p>
 * Each selected project gets a tab of its own. A project's package is the build's group plus its
 * name with the dashes turned into dots, so {@code cli-brand} in group {@code org.example} is
 * documented under {@code org.example.cli.brand}. Javadoc puts a package in the first group whose
 * pattern matches it, so the groups are registered longest module name first: without that, the
 * pattern for {@code concordance} would also claim the packages of {@code concordance-gradle}.
 *
 * <p>
 * The task's classpath is the compile classpath of every selected project, which also builds any
 * project the selected ones depend on without documenting it. The selection is read when the task
 * is realized, so nothing in the applying build may realize it before the subprojects have been
 * evaluated. {@code tasks.named} with a configuration action is safe; {@code tasks.getByName} and
 * {@code tasks.all} in the root project are not.
 *
 * @see AggregateJavadocExtension
 */
public class AggregateJavadocPlugin implements Plugin<Project> {
    /** The encoding of the sources, of the pages, and of the character set the pages declare. */
    private static final String ENCODING = "UTF-8";

    /** The name of the block a build configures the task through. */
    private static final String EXTENSION_NAME = "aggregateJavadoc";

    /** The id of the plugin whose projects are selected by default. */
    private static final String JAVA_PLUGIN_ID = "java";

    /** The name of the task that generates the pages. */
    private static final String TASK_NAME = "aggregateJavadoc";

    /** Instantiated by Gradle when a build applies the plugin. */
    public AggregateJavadocPlugin() {}

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        AggregateJavadocExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, AggregateJavadocExtension.class);
        extension.getLinks().convention(List.of());
        extension.getWordForms().convention(Map.of());
        extension.getProjects()
                .convention(
                        project.provider(
                                () -> project.getSubprojects()
                                        .stream()
                                        .filter(subproject -> subproject.getPlugins().hasPlugin(JAVA_PLUGIN_ID))
                                        .collect(Collectors.toSet())
                        )
                );

        project.afterEvaluate(evaluated -> {
            if (!extension.getTitle().isPresent()) {
                throw new InvalidUserDataException(
                        "The " + EXTENSION_NAME + " plugin needs " + EXTENSION_NAME + ".title, the name the page"
                                + " and window titles are built from. Set it in the " + EXTENSION_NAME + " block of "
                                + evaluated.getPath() + "."
                );
            }
        });

        project.getTasks().register(TASK_NAME, Javadoc.class, task -> configure(project, extension, task));
    }

    /**
     * Configures the aggregate task. Runs when the task is realized, which is when the title and the
     * module groups are read. The sources and the classpath are read later, when the task graph is
     * built, through a {@link Callable}.
     *
     * @param project the project the plugin is applied to
     * @param extension the block the build configured
     * @param task the task to configure
     */
    private static void configure(Project project, AggregateJavadocExtension extension, Javadoc task) {
        task.setDescription("Generates the Javadoc of every selected module as one set of pages.");
        task.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
        // JavaBasePlugin defaults every Javadoc task to build/docs/javadoc, the root project's own
        // javadoc task included.
        task.setDestinationDir(
                new File(project.getLayout().getBuildDirectory().get().getAsFile(), "docs/" + TASK_NAME)
        );

        Callable<Set<Project>> selected = () -> extension.getProjects().get();
        task.source(
                (Callable<Object>) () -> selected.call()
                        .stream()
                        .map(selectedProject -> mainSourceSet(selectedProject).getAllJava())
                        .toList()
        );
        task.setClasspath(
                project.files(
                        (Callable<Object>) () -> selected.call()
                                .stream()
                                .map(selectedProject -> mainSourceSet(selectedProject).getCompileClasspath())
                                .toList()
                )
        );

        String title = extension.getTitle().get();
        task.setTitle(title + " " + project.getVersion() + " API");

        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
        options.setWindowTitle(title + " API");
        options.setLinks(extension.getLinks().get());
        options.setEncoding(ENCODING);
        options.setDocEncoding(ENCODING);
        options.setCharSet(ENCODING);
        options.addBooleanOption("Werror", true);

        List<String> moduleNames = extension.getProjects().get().stream().map(Project::getName).toList();
        groups(project.getGroup().toString(), moduleNames, extension.getWordForms().get())
                .forEach((label, pattern) -> options.group(label, List.of(pattern)));
    }

    /**
     * Computes the Javadoc groups for a set of modules, longest module name first. Each pattern ends in
     * {@code *}, and Javadoc puts a package in the first group that matches it, so a module whose name
     * prefixes another's has to be registered after it.
     *
     * @param group the build's group, which prefixes every module's package
     * @param moduleNames the names of the modules to document
     * @param wordForms the display form of each name segment that is not just the segment capitalised
     * @return each group's label mapped to its package pattern, in registration order
     */
    static Map<String, String> groups(String group, Collection<String> moduleNames, Map<String, String> wordForms) {
        Map<String, String> groups = new LinkedHashMap<>();
        moduleNames.stream()
                .sorted(
                        Comparator.<String>comparingInt(String::length)
                                .reversed()
                                .thenComparing(Comparator.naturalOrder())
                )
                .forEach(
                        name -> groups
                                .put(label(name, wordForms) + " Module", group + "." + name.replace('-', '.') + "*")
                );
        return groups;
    }

    /**
     * Builds the display name of a module from its dash-separated name.
     *
     * @param name the module name
     * @param wordForms the display form of each segment that is not just the segment capitalised
     * @return the segments' display forms, separated by spaces
     */
    private static String label(String name, Map<String, String> wordForms) {
        return Arrays.stream(name.split("-"))
                .map(
                        segment -> wordForms.getOrDefault(
                                segment,
                                segment.isEmpty() ? segment
                                        : segment.substring(0, 1).toUpperCase(Locale.ROOT) + segment.substring(1)
                        )
                )
                .collect(Collectors.joining(" "));
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

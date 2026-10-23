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
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Registers {@code aggregateJavadoc}, one Javadoc task over the main sources of every selected
 * project, and the {@code aggregateJavadoc} block that configures it. The pages land in
 * {@code build/docs/aggregateJavadoc}, and the task fails on any Javadoc warning.
 *
 * <p>
 * The plugin applies {@link JavaBasePlugin} to the root project so the Javadoc tool comes from the
 * root's {@link org.gradle.jvm.toolchain.JavaToolchainService toolchain}. A consumer that sets a
 * toolchain only in {@code subprojects { }} will run the task with the daemon's JDK instead.
 *
 * <p>
 * Each selected project gets a tab of its own. A project's package is the project's own group plus
 * its name with the dashes turned into dots, so {@code cli-brand} in group {@code org.example} is
 * documented under {@code org.example.cli.brand}. A group set only in {@code subprojects { }} is
 * enough. Javadoc puts a package in the first group whose pattern matches it, so the groups are
 * registered longest module name first: without that, the pattern for {@code concordance} would
 * also claim the packages of {@code concordance-gradle}.
 *
 * <p>
 * The task's classpath covers everything the selected projects compile against: their
 * {@code implementation} and {@code compileOnly} dependencies, the BOMs those name through
 * {@code platform}, and the projects they depend on, which are built but not documented. The root
 * resolves it through its own {@code aggregateJavadocClasspath} configuration, because a task that
 * resolved another project's configuration would fail in a parallel build. Each selected project
 * offers its dependencies as a variant under the {@code aggregate-javadoc-classpath} capability.
 * Only the root asks for that capability, so a project that depends on a selected one still gets
 * its {@code apiElements}. The root has to reach every repository those dependencies come from, as
 * it does when they are declared in {@code allprojects { }} or in
 * {@code dependencyResolutionManagement}.
 *
 * <p>
 * The settings that depend on the selection are read once every project has been evaluated, so
 * realizing the task early, as {@code allprojects { tasks.withType(Javadoc) { } }} does, is safe.
 *
 * <p>
 * The title, the links, and the groups come only from the {@code aggregateJavadoc} block. A
 * {@code tasks.named} action that sets the title is overwritten.
 *
 * @see AggregateJavadocExtension
 */
public class AggregateJavadocPlugin implements Plugin<Project> {
    /**
     * A module to document.
     *
     * @param group the module's own group, which prefixes its packages
     * @param name the module's project name
     */
    record Module(String group, String name) {}

    /**
     * The feature whose capability marks the variant each selected project offers the root, so that
     * only the root's dependencies, which ask for it, select that variant.
     */
    private static final String CLASSPATH_FEATURE = "aggregate-javadoc-classpath";

    /** The name of the root's configuration that resolves the task's classpath. */
    private static final String CLASSPATH_NAME = "aggregateJavadocClasspath";

    /** The name of the root's configuration that declares a dependency on each selected project. */
    private static final String DEPENDENCIES_NAME = "aggregateJavadoc";

    /** The name of the configuration each selected project offers its compile dependencies through. */
    private static final String ELEMENTS_NAME = "aggregateJavadocClasspathElements";

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

        NamedDomainObjectProvider<DependencyScopeConfiguration> dependencies = project.getConfigurations()
                .dependencyScope(DEPENDENCIES_NAME);
        project.getConfigurations().resolvable(CLASSPATH_NAME, classpath -> classpath.extendsFrom(dependencies.get()));

        project.getTasks().register(TASK_NAME, Javadoc.class, task -> configure(project, extension, task));

        project.getGradle().projectsEvaluated(gradle -> {
            extension.getProjects().finalizeValue();
            extension.getTitle().finalizeValue();
            extension.getLinks().finalizeValue();
            extension.getWordForms().finalizeValue();
            project.getTasks()
                    .named(TASK_NAME, Javadoc.class)
                    .configure(task -> configureSelection(project, extension, task));
        });
    }

    /**
     * Configures the settings of the aggregate task that do not depend on the selection. Runs when the
     * task is realized, which may be before the subprojects have been evaluated. The sources are read
     * later, when the task graph is built, through a {@link Callable}. The classpath is the root's
     * {@code aggregateJavadocClasspath} configuration, which {@link #configureClasspath(Project, List)}
     * fills once the selection is final.
     *
     * @param project the project the plugin is applied to
     * @param extension the block the build configured
     * @param task the task to configure
     */
    private static void configure(Project project, AggregateJavadocExtension extension, Javadoc task) {
        task.setDescription("Generates the Javadoc of every selected module as one set of pages.");
        task.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
        task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("docs/" + TASK_NAME));

        Callable<List<Project>> selected = () -> selection(extension);
        task.source(
                (Callable<Object>) () -> selected.call()
                        .stream()
                        .map(selectedProject -> mainSourceSet(selectedProject).getAllJava())
                        .toList()
        );
        task.setClasspath(project.files(project.getConfigurations().named(CLASSPATH_NAME)));

        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
        options.setEncoding(ENCODING);
        options.setDocEncoding(ENCODING);
        options.setCharSet(ENCODING);
        options.addBooleanOption("Werror", true);
    }

    /**
     * Wires the root's {@code aggregateJavadocClasspath} configuration to the selected projects. Each
     * selected project gets an {@code aggregateJavadocClasspathElements} configuration that extends its
     * {@code implementation} and {@code compileOnly}, carries the attributes of its compile classpath,
     * and is offered only under the {@code aggregate-javadoc-classpath} capability. The root depends on
     * each selected project through that capability and requests the same attributes, with the highest
     * Java version among the selection.
     *
     * @param project the project the plugin is applied to
     * @param selected the selected projects, each applying the {@code java} plugin
     */
    private static void configureClasspath(Project project, List<Project> selected) {
        AttributeContainer requested = project.getConfigurations().getByName(CLASSPATH_NAME).getAttributes();
        Integer jvmVersion = null;
        for (Project selectedProject : selected) {
            SourceSet main = mainSourceSet(selectedProject);
            ConfigurationContainer configurations = selectedProject.getConfigurations();
            AttributeContainer compileClasspath = configurations.getByName(main.getCompileClasspathConfigurationName())
                    .getAttributes();

            ConsumableConfiguration elements = configurations.consumable(ELEMENTS_NAME).get();
            elements.extendsFrom(
                    configurations.getByName(main.getImplementationConfigurationName()),
                    configurations.getByName(main.getCompileOnlyConfigurationName())
            );
            compileClasspath.keySet().forEach(key -> copyAttribute(key, compileClasspath, elements.getAttributes()));
            elements.getOutgoing()
                    .capability(
                            selectedProject.getGroup() + ":" + selectedProject.getName() + "-" + CLASSPATH_FEATURE + ":"
                                    + selectedProject.getVersion()
                    );

            compileClasspath.keySet().forEach(key -> copyAttribute(key, compileClasspath, requested));
            Integer projectJvmVersion = compileClasspath.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE);
            if (projectJvmVersion != null && (jvmVersion == null || projectJvmVersion > jvmVersion)) {
                jvmVersion = projectJvmVersion;
            }

            ProjectDependency dependency = (ProjectDependency) project.getDependencies()
                    .project(Map.of("path", selectedProject.getPath()));
            dependency.capabilities(capabilities -> capabilities.requireFeature(CLASSPATH_FEATURE));
            project.getDependencies().add(DEPENDENCIES_NAME, dependency);
        }
        if (jvmVersion != null) {
            requested.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, jvmVersion);
        }
    }

    /**
     * Configures what depends on the selection or on the block: the titles, the links, the groups, and
     * the classpath. Runs once every project has been evaluated, or when the task is realized if that
     * comes later, so a build that never realizes the task adds no configuration to any project.
     *
     * @param project the project the plugin is applied to
     * @param extension the block the build configured
     * @param task the task to configure
     */
    private static void configureSelection(Project project, AggregateJavadocExtension extension, Javadoc task) {
        String title = extension.getTitle().get();
        task.setTitle(title + " " + project.getVersion() + " API");

        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
        options.setWindowTitle(title + " API");
        options.setLinks(extension.getLinks().get());

        List<Project> selected = selection(extension);
        List<Module> modules = selected.stream()
                .map(selectedProject -> new Module(selectedProject.getGroup().toString(), selectedProject.getName()))
                .toList();
        groups(modules, extension.getWordForms().get())
                .forEach((label, pattern) -> options.group(label, List.of(pattern)));
        configureClasspath(project, selected);
    }

    /**
     * Copies one attribute from one container to another, if the source has it.
     *
     * @param <T> the type of the attribute's value
     * @param key the attribute to copy
     * @param source the container to read the value from
     * @param target the container to write the value to
     */
    private static <T> void copyAttribute(Attribute<T> key, AttributeContainer source, AttributeContainer target) {
        T value = source.getAttribute(key);
        if (value != null) {
            target.attribute(key, value);
        }
    }

    /**
     * Computes the Javadoc groups for a set of modules, longest module name first. Each pattern ends in
     * {@code *}, and Javadoc puts a package in the first group that matches it, so a module whose name
     * prefixes another's has to be registered after it.
     *
     * @param modules the modules to document
     * @param wordForms the display form of each name segment that is not just the segment capitalised
     * @return each group's label mapped to its package pattern, in registration order
     */
    static Map<String, String> groups(Collection<Module> modules, Map<String, String> wordForms) {
        Map<String, String> groups = new LinkedHashMap<>();
        modules.stream()
                .sorted(
                        Comparator.<Module>comparingInt(module -> module.name().length())
                                .reversed()
                                .thenComparing(Module::name)
                                .thenComparing(Module::group)
                )
                .forEach(
                        module -> groups.put(
                                label(module.name(), wordForms) + " Module",
                                module.group() + "." + module.name().replace('-', '.') + "*"
                        )
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

    /**
     * Reads the selected projects in path order, so the task's inputs do not depend on the iteration
     * order of the underlying set.
     *
     * @param extension the block the build configured
     * @return the selected projects, sorted by path
     * @throws InvalidUserDataException if a selected project does not apply the {@code java} plugin
     */
    private static List<Project> selection(AggregateJavadocExtension extension) {
        List<Project> selected = extension.getProjects()
                .get()
                .stream()
                .sorted(Comparator.comparing(Project::getPath))
                .toList();
        for (Project selectedProject : selected) {
            if (!selectedProject.getPlugins().hasPlugin(JAVA_PLUGIN_ID)) {
                throw new InvalidUserDataException(
                        "Project " + selectedProject.getPath() + " is selected for " + TASK_NAME + " but does not"
                                + " apply the " + JAVA_PLUGIN_ID + " plugin. Apply it, or leave the project out of "
                                + EXTENSION_NAME + ".projects."
                );
            }
        }
        return selected;
    }
}

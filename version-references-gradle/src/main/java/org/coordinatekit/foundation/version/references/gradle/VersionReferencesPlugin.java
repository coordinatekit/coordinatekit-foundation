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
package org.coordinatekit.foundation.version.references.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.List;

/**
 * Registers the {@code rewriteVersionReferences} task on the root project and the
 * {@code foundationVersionReferences} block that configures it. A consumer's whole integration is
 * the {@code plugins} line, and a build that has files to leave alone adds the block.
 *
 * <p>
 * The task's two versions default to the {@code release.releaseVersion} and
 * {@code release.newVersion} project properties, which gradle-release passes into its nested
 * builds, and an option on the command line overrides them. The group is read lazily, because a
 * build commonly sets it after its {@code plugins} block, in {@code allprojects} or from a
 * property. The subproject names, the root project's name, and the root directory are read when the
 * plugin is applied, by which time Gradle knows every project.
 *
 * @see RewriteVersionReferences
 * @see VersionReferencesExtension
 */
public class VersionReferencesPlugin implements Plugin<Project> {
    /** The name of the block a build configures the rewrite through. */
    private static final String EXTENSION_NAME = "foundationVersionReferences";

    /** The name of the task. */
    private static final String TASK_NAME = "rewriteVersionReferences";

    /** Instantiated by Gradle when a build applies the plugin. */
    public VersionReferencesPlugin() {}

    @Override
    public void apply(Project project) {
        if (project.getParent() != null) {
            throw new GradleException(
                    "org.coordinatekit.foundation.version-references belongs on the root project, but it was applied to "
                            + project.getPath() + ". The rewrite reads every tracked file in the repository once."
            );
        }
        VersionReferencesExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, VersionReferencesExtension.class);

        List<String> modules = project.getSubprojects().stream().map(Project::getName).sorted().toList();
        project.getTasks().register(TASK_NAME, RewriteVersionReferences.class, task -> {
            task.setGroup("release");
            task.setDescription(
                    "Rewrites the version references in every tracked file that names the project."
                            + " Pass --release=<version> and --next=<version>-SNAPSHOT."
            );
            task.getRelease().convention(project.getProviders().gradleProperty("release.releaseVersion"));
            task.getNext().convention(project.getProviders().gradleProperty("release.newVersion"));
            task.getProjectGroup().set(project.provider(() -> String.valueOf(project.getGroup())));
            task.getModules().set(modules);
            task.getRootProjectName().set(project.getName());
            task.getRootDirectory().set(project.getProjectDir());
            task.getExcludes().set(extension.getExcludes());
        });
    }
}

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
package org.coordinatekit.foundation.bump.version.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.List;

/**
 * Registers the {@code bumpVersion} task on the root project and the {@code foundationVersion}
 * block that configures it. A consumer's whole integration is the {@code plugins} line, and a build
 * that has files to leave alone adds the block.
 *
 * <p>
 * The group and the version are read lazily, because a build commonly sets both after its
 * {@code plugins} block, in {@code allprojects} or from a property. The subproject names, the root
 * project's name, and the root directory are read when the plugin is applied, by which time Gradle
 * knows every project.
 *
 * @see BumpVersion
 * @see BumpVersionExtension
 */
public class BumpVersionPlugin implements Plugin<Project> {
    /** The name of the block a build configures the bump through. */
    private static final String EXTENSION_NAME = "foundationVersion";

    /** The name of the task. */
    private static final String TASK_NAME = "bumpVersion";

    /** Instantiated by Gradle when a build applies the plugin. */
    public BumpVersionPlugin() {}

    @Override
    public void apply(Project project) {
        if (project.getParent() != null) {
            throw new GradleException(
                    "org.coordinatekit.foundation.bump-version belongs on the root project, but it was applied to "
                            + project.getPath() + ". The bump reads every tracked file in the repository once."
            );
        }
        BumpVersionExtension extension = project.getExtensions().create(EXTENSION_NAME, BumpVersionExtension.class);

        List<String> modules = project.getSubprojects().stream().map(Project::getName).sorted().toList();
        project.getTasks().register(TASK_NAME, BumpVersion.class, task -> {
            task.setGroup("release");
            task.setDescription(
                    "Moves the build's version across every tracked file that names it. Pass --to=<version>."
            );
            task.getBuildVersion().set(project.provider(() -> String.valueOf(project.getVersion())));
            task.getProjectGroup().set(project.provider(() -> String.valueOf(project.getGroup())));
            task.getModules().set(modules);
            task.getRootProjectName().set(project.getName());
            task.getRootDirectory().set(project.getProjectDir());
            task.getExcludes().set(extension.getExcludes());
        });
    }
}

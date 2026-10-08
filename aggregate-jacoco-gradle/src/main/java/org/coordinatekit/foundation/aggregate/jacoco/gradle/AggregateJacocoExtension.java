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

import org.gradle.api.Project;
import org.gradle.api.provider.SetProperty;

/**
 * The {@code aggregateJacoco} block a build selects the aggregated coverage report's modules
 * through. The plugin works without it, so a build only writes the block to narrow or widen the
 * default selection.
 *
 * <pre>
 * aggregateJacoco {
 *     projects = subprojects.findAll { it.name != "conventions" }
 * }
 * </pre>
 *
 * @see AggregateJacocoPlugin
 */
public abstract class AggregateJacocoExtension {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public AggregateJacocoExtension() {}

    /**
     * The projects the report covers. By default, every subproject that applies the {@code jacoco}
     * plugin. Each one needs a {@code jacocoTestReport} task, which that plugin registers for a project
     * with the {@code java} plugin.
     *
     * @return the projects to cover
     */
    public abstract SetProperty<Project> getProjects();
}

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

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * The {@code aggregateJavadoc} block a build configures the aggregated Javadoc through. Only
 * {@code title} is required; the other properties have defaults.
 *
 * <pre>
 * aggregateJavadoc {
 *     title = "Example"
 *     links = ["https://docs.oracle.com/en/java/javase/21/docs/api/"]
 *     wordForms = ["cli": "CLI"]
 * }
 * </pre>
 *
 * @see AggregateJavadocPlugin
 */
public abstract class AggregateJavadocExtension {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public AggregateJavadocExtension() {}

    /**
     * The URLs of external Javadoc that references in the sources link to, such as the JDK's. Empty by
     * default.
     *
     * @return the external Javadoc URLs
     */
    public abstract ListProperty<String> getLinks();

    /**
     * The projects whose sources the pages document. By default, every subproject that applies the
     * {@code java} plugin.
     *
     * @return the projects to document
     */
    public abstract SetProperty<Project> getProjects();

    /**
     * The name of the documented library, which the page title and the window title are built from.
     * Required: a build that applies the plugin without setting it fails at the end of evaluation.
     *
     * @return the title of the generated pages, without the version or the word "API"
     */
    public abstract Property<String> getTitle();

    /**
     * The display forms of module name segments that are not just the segment capitalised, such as
     * {@code cli} for {@code CLI}. A module's tab is titled by its dash-separated name segments, each
     * looked up here first. Empty by default.
     *
     * @return the display form of each segment that needs one, keyed by segment
     */
    public abstract MapProperty<String, String> getWordForms();
}

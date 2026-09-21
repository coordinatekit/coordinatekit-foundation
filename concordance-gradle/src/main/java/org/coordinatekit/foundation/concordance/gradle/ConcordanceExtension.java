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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code concordance} block a build configures the member-order check through. Every property
 * has a default, so a build that applies the plugin and says nothing else gets the check at
 * {@code ERROR} with no exemptions.
 *
 * <pre>
 * concordance {
 *     scaffoldingFieldTypes = ["org.slf4j.Logger"]
 * }
 * </pre>
 *
 * @see ConcordancePlugin
 */
public abstract class ConcordanceExtension {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public ConcordanceExtension() {}

    /**
     * The fully qualified annotation names that mark a method as lifecycle scaffolding, such as JUnit's
     * {@code org.junit.jupiter.api.BeforeEach}. A method carrying one of them is invisible to the
     * check, so a build can put its fixture setup where it reads best. Empty by default.
     *
     * @return the lifecycle annotation names
     */
    public abstract ListProperty<String> getLifecycleAnnotations();

    /**
     * The fully qualified field types that are scaffolding rather than state, such as
     * {@code org.slf4j.Logger}. A field of one of these types is invisible to the check. Matching is by
     * resolved type, so a name that the compilation's classpath does not carry matches nothing. Empty
     * by default.
     *
     * @return the scaffolding field type names
     */
    public abstract ListProperty<String> getScaffoldingFieldTypes();

    /**
     * The severity the check reports at, one of {@code DEFAULT}, {@code OFF}, {@code WARN}, or
     * {@code ERROR}, matched without regard to case. The default is {@code ERROR}, because a build that
     * applies this plugin has adopted the rule; the check's own {@code @BugPattern} declares
     * {@code WARNING} so that it never breaks a build it arrives in unasked.
     *
     * @return the severity to report findings at
     */
    public abstract Property<String> getSeverity();
}

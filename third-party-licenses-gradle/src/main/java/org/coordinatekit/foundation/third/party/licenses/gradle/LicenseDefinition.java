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
package org.coordinatekit.foundation.third.party.licenses.gradle;

import org.gradle.api.Named;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * One license a build's dependencies may carry, registered by the name the license report gives it,
 * such as {@code Apache License, Version 2.0}. The name has to match the normalized display name
 * exactly, because that is the key the report is looked up by. Only {@link #getText()} is required.
 *
 * <pre>
 * licenses {
 *     register('MIT License') { text = file('licenses/MIT.txt'); noticeRequired = true }
 * }
 * </pre>
 *
 * @see ThirdPartyLicensesExtension#getLicenses()
 */
public abstract class LicenseDefinition implements Named {
    /** Instantiated by Gradle, which generates the implementation of every property below. */
    public LicenseDefinition() {}

    /**
     * Whether a dependency under this license has its own {@code NOTICE} file, at
     * {@code META-INF/NOTICE} or {@code NOTICE} in its jar, reproduced under its entry. Apache License
     * 2.0 asks for this. A jar without such a file is not an error. {@code false} by default.
     *
     * @return whether to copy each dependency's own notice file into the attribution
     */
    @Input
    public abstract Property<Boolean> getIncludeNoticeFile();

    @Override
    @Input
    public abstract String getName();

    /**
     * Whether every dependency under this license needs an entry in
     * {@link ThirdPartyLicensesExtension#getCopyrightNotices()}. MIT and the BSD licenses make keeping
     * the copyright notice the obligation, so their holder-free text alone is not enough attribution.
     * {@code false} by default.
     *
     * @return whether a missing copyright notice fails the build
     */
    @Input
    public abstract Property<Boolean> getNoticeRequired();

    /**
     * The name of this license's family as the source-availability sentence spells it, such as
     * {@code the Eclipse Public License}. Registering a label marks the license as reciprocal or
     * copyleft. Two licenses that share a label are named once. Unset by default, which marks the
     * license as permissive.
     *
     * @return the family label, or unset for a permissive license
     */
    @Input
    @Optional
    public abstract Property<String> getReciprocalLabel();

    /**
     * The canonical text of the license, copied once into the attribution for the whole build however
     * many dependencies carry it.
     *
     * @return the file holding the license text
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getText();
}

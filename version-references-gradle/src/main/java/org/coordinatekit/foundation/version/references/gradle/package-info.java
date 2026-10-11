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

/**
 * The Gradle plugin that rewrites the version references in a build's documents and build files.
 *
 * <p>
 * {@link org.coordinatekit.foundation.version.references.gradle.VersionReferencesPlugin} is applied
 * to the root project under the id {@code org.coordinatekit.foundation.version-references}. It
 * registers the {@code rewriteVersionReferences} task, which takes the version being released with
 * {@code --release} and the snapshot that follows it with {@code --next}, lists the files Git
 * tracks, and rewrites each place a file names this project's version: published coordinates, jar
 * and archive filenames, plugin ids, the version line a command prints, and Maven dependency
 * blocks. The build's own version declaration is left to the release tooling. Every anchor comes
 * from the Gradle model, namely the root group, the subproject names, and the root project's name,
 * so a repository configures nothing but the paths to leave alone, through the
 * {@link org.coordinatekit.foundation.version.references.gradle.VersionReferencesExtension}.
 *
 * <p>
 * The task changes nothing unless the whole rewrite is sound. A line that names the project but
 * that no rule recognises fails the build with its {@code file:line}, so a new document format is a
 * missing rule to add here and not a stale version left behind. README.md has the recipe.
 */
@NullMarked
package org.coordinatekit.foundation.version.references.gradle;

import org.jspecify.annotations.NullMarked;

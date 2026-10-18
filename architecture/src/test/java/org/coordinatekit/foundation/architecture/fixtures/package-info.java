/**
 * Classes the architecture rules are run against, each one a deliberate violation or a deliberate
 * pass.
 *
 * <p>
 * The package is excluded from the module's own check, because the violating fixtures would fail
 * it. A fixture that carries JUnit annotations is abstract, so no engine runs it, and only the
 * fixtures for {@code TEST_CLASS_VISIBILITY} end in {@code Test}.
 */
@NullMarked
package org.coordinatekit.foundation.architecture.fixtures;

import org.jspecify.annotations.NullMarked;

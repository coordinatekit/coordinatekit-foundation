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
package org.coordinatekit.foundation.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads the classes of the {@code fixtures} subpackage by simple name, so a test can reach a
 * package-private fixture that {@code .class} cannot name from here.
 */
final class Fixtures {
    /** The package that holds every fixture. */
    static final String PACKAGE = Fixtures.class.getPackageName() + ".fixtures";

    private Fixtures() {}

    /**
     * Returns the fully qualified name of a fixture.
     *
     * @param simpleName the fixture's simple name
     * @return the name, including the fixtures package
     */
    static String fullName(String simpleName) {
        return PACKAGE + "." + simpleName;
    }

    /**
     * Imports fixtures together with other classes, such as a JDK class that supplies a second
     * classpath root.
     *
     * @param others classes to import beside the fixtures
     * @param simpleNames the simple names of the fixtures
     * @return the imported classes
     */
    static JavaClasses importWith(List<Class<?>> others, String... simpleNames) {
        List<Class<?>> classes = new ArrayList<>(others);
        for (String simpleName : simpleNames) {
            classes.add(load(simpleName));
        }
        return new ClassFileImporter().importClasses(classes);
    }

    /** Loads a fixture without initializing it, so a static initializer does not run. */
    private static Class<?> load(String simpleName) {
        try {
            return Class.forName(fullName(simpleName), false, Fixtures.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("No fixture named " + simpleName, e);
        }
    }

    /**
     * Imports fixtures and nothing else.
     *
     * @param simpleNames the simple names of the fixtures
     * @return the imported classes
     */
    static JavaClasses of(String... simpleNames) {
        return importWith(List.of(), simpleNames);
    }
}

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
package org.coordinatekit.foundation.concordance.errorprone;

import static com.google.errorprone.BugPattern.LinkType.CUSTOM;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;

import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Reports class members declared out of the order CoordinateKit's Java sources follow: enum
 * constants, then constants, then fields, then constructors, then methods, with each category
 * sorted alphabetically and case-insensitively inside itself, and constructors sorted by ascending
 * parameter count instead.
 *
 * <p>
 * One walk over {@link ClassTree#getMembers()} does the whole check. Members arrive in source
 * order, so each one only has to be compared against the last member that was visible to the check:
 * a category that sorts before its predecessor's is a sequence violation, and a member that sorts
 * before its predecessor within the same category is an ordering violation. Anything invisible to
 * the check, whether a compiler-generated member, an exempted one, a nested type, or an initializer
 * block, is skipped without becoming the predecessor, so the members on either side of it compare
 * with each other. The check also applies to anonymous class bodies and enum constant bodies, which
 * javac presents as class trees.
 *
 * <p>
 * Exemptions come from two places. The two annotations,
 * {@code org.coordinatekit.foundation.concordance.IntentionalOrder} on a type and
 * {@code org.coordinatekit.foundation.concordance.IgnoreOrder} on a member, are recognised by fully
 * qualified name through javac's annotation mirrors, so this check never depends on the annotations
 * jar; the consumer puts it on its own compile classpath. The scaffolding field types and lifecycle
 * annotations come from the {@code Concordance:ScaffoldingFieldTypes} and
 * {@code Concordance:LifecycleAnnotations} flags, both empty unless a build sets them, so nothing
 * is exempt by default.
 */
// spotless:off
// The Eclipse profile does not wrap annotation arguments, and left alone it folds this into one
// 200-column line.
@BugPattern(
        summary = "Class members are declared in category order, and alphabetically within each category",
        severity = WARNING,
        linkType = CUSTOM,
        link = "https://github.com/coordinatekit/coordinatekit-foundation#concordance")
// spotless:on
public final class Concordance extends BugChecker implements BugChecker.ClassTreeMatcher {
    /**
     * The categories a member is sorted into, in the order the categories must be declared, mirroring
     * {@code org.coordinatekit.foundation.concordance.MemberCategory} constant for constant. The copy
     * is what keeps this check off the annotations jar: {@code @IntentionalOrder} arrives as constant
     * names read from an annotation mirror, never as loaded enum values. {@code ConcordanceTest} pins
     * the two enums to the same names so they cannot drift apart unnoticed.
     */
    enum Category {
        /** Enum constants, which Java already requires ahead of every other member. */
        ENUM_CONSTANT,

        /** Fields that are both static and final, including a field of an interface. */
        CONSTANT,

        /** Instance fields, and static fields that are not final. */
        FIELD,

        /** Constructors, ordered by ascending parameter count rather than alphabetically. */
        CONSTRUCTOR,

        /** Methods, static and instance alike, in one sequence. */
        METHOD
    }

    /**
     * One member the check can see, reduced to what deciding its place needs: the category it belongs
     * to, the key it sorts by within that category, and the phrase a finding names it with.
     *
     * @param category the category this member belongs to
     * @param sortKey the member's name, lower-cased, which is what makes the ordering case-insensitive;
     *        empty for a constructor, which sorts by {@code arity} instead
     * @param arity the parameter count, which is a constructor's sort key; zero for every other
     *        category
     * @param description the phrase a finding names this member with, such as {@code method size()}
     */
    record Member(Category category, String sortKey, int arity, String description) implements Comparable<Member> {
        @Override
        public int compareTo(Member other) {
            return category == Category.CONSTRUCTOR ? Integer.compare(arity, other.arity)
                    : sortKey.compareTo(other.sortKey);
        }
    }

    /** The annotation that takes one member out of the check entirely. */
    private static final String IGNORE_ORDER = "org.coordinatekit.foundation.concordance.IgnoreOrder";

    /** The name javac gives every constructor. */
    private static final String INIT = "<init>";

    /** The annotation that exempts whole categories of a type's members. */
    private static final String INTENTIONAL_ORDER = "org.coordinatekit.foundation.concordance.IntentionalOrder";

    /** The flag naming the annotations that mark a method as test or fixture lifecycle scaffolding. */
    static final String LIFECYCLE_ANNOTATIONS = "Concordance:LifecycleAnnotations";

    /** The flag naming the field types that are scaffolding rather than state, such as a logger. */
    static final String SCAFFOLDING_FIELD_TYPES = "Concordance:ScaffoldingFieldTypes";

    /** The fully qualified lifecycle annotation names the build configured, empty when it set none. */
    private final List<String> lifecycleAnnotations;

    /** The fully qualified scaffolding field types the build configured, empty when it set none. */
    private final List<String> scaffoldingFieldTypes;

    /** Creates a check that exempts nothing, which is what an unconfigured build gets. */
    public Concordance() {
        this(ErrorProneFlags.empty());
    }

    /**
     * Creates a check configured from the build's {@code -XepOpt} flags. Error Prone calls this
     * constructor in preference to the no-argument one whenever it can.
     *
     * @param flags the flags the compilation was invoked with
     */
    @Inject
    public Concordance(ErrorProneFlags flags) {
        this.lifecycleAnnotations = names(flags, LIFECYCLE_ANNOTATIONS);
        this.scaffoldingFieldTypes = names(flags, SCAFFOLDING_FIELD_TYPES);
    }

    /**
     * Returns the annotation of the given fully qualified name carried by {@code element}, or
     * {@code null} if it carries none. Matching the mirror's type name rather than loading the
     * annotation class is what lets a source-retained annotation from a jar this module does not depend
     * on still be recognised.
     *
     * @param element the annotated element
     * @param name the fully qualified name of the annotation to look for
     * @return the matching annotation, or {@code null}
     */
    private static @Nullable AnnotationMirror annotation(Element element, String name) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(name)) {
                return mirror;
            }
        }
        return null;
    }

    /**
     * Returns what the check sees when it looks at one member of a class body, or {@code null} for a
     * member it is blind to: an initializer block, a nested type, a compiler-generated member, a member
     * annotated {@code @IgnoreOrder}, a configured scaffolding field, or a configured lifecycle method.
     *
     * @param tree the member to classify
     * @param state the visitor state
     * @return the member's category and sort key, or {@code null} if it is invisible to the check
     */
    private @Nullable Member classify(Tree tree, VisitorState state) {
        if (generated(tree, state)) {
            return null;
        }
        return switch (tree.getKind()) {
            case METHOD -> method((MethodTree) tree);
            case VARIABLE -> variable((VariableTree) tree, state);
            default -> null;
        };
    }

    /**
     * Adds every {@code MemberCategory} constant named by an {@code @IntentionalOrder} annotation value
     * to {@code categories}. The value is an array in the general case and a bare constant when a type
     * exempts one category, so both shapes are unwrapped here.
     *
     * @param value the value of the annotation's {@code members} element
     * @param categories the set to add the named categories to
     */
    private static void collectCategories(AnnotationValue value, Set<Category> categories) {
        Object unwrapped = value.getValue();
        if (unwrapped instanceof List<?> values) {
            for (Object element : values) {
                if (element instanceof AnnotationValue nested) {
                    collectCategories(nested, categories);
                }
            }
        } else if (unwrapped instanceof VariableElement constant) {
            String name = constant.getSimpleName().toString();
            for (Category category : Category.values()) {
                if (category.name().equals(name)) {
                    categories.add(category);
                }
            }
        }
    }

    /**
     * Builds the finding for a member that sits where it should not, naming both members so a reader
     * knows what to move without opening the file.
     *
     * @param tree the member to report the finding against
     * @param current the member that sits out of place
     * @param previous the member it should have preceded
     * @param relation the phrase joining the two, either {@code declared after} for a category out of
     *        sequence or {@code out of order with} for a member out of order within its category
     * @return the finding
     */
    private Description describe(Tree tree, Member current, Member previous, String relation) {
        return buildDescription(tree).setMessage(current.description() + " " + relation + " " + previous.description())
                .build();
    }

    /**
     * Returns the categories {@code tree} declares in an order of its own, which are exempt from both
     * their position in the category sequence and their order within the category.
     *
     * @param tree the type whose {@code @IntentionalOrder} annotation to read
     * @return the exempt categories, empty when the type carries no annotation
     */
    private static Set<Category> exemptCategories(ClassTree tree) {
        Set<Category> categories = EnumSet.noneOf(Category.class);
        Symbol.ClassSymbol symbol = ASTHelpers.getSymbol(tree);
        AnnotationMirror mirror = symbol == null ? null : annotation(symbol, INTENTIONAL_ORDER);
        if (mirror == null) {
            return categories;
        }
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> element : mirror.getElementValues()
                .entrySet()) {
            if (element.getKey().getSimpleName().contentEquals("members")) {
                collectCategories(element.getValue(), categories);
            }
        }
        return categories;
    }

    /**
     * Returns whether javac synthesised {@code tree} rather than reading it from the source file. A
     * record's component fields and accessors, an enum's {@code values} and {@code valueOf}, and a
     * class's implicit constructor all reach {@link ClassTree#getMembers()} the same way a declared
     * member does, and none of them is anything a reader could reorder.
     *
     * @param tree the member to test
     * @param state the visitor state, which holds the source positions
     * @return whether the member has no source of its own
     */
    private static boolean generated(Tree tree, VisitorState state) {
        return ASTHelpers.getStartPosition(tree) == -1 || state.getEndPosition(tree) == -1;
    }

    /**
     * Returns whether {@code element} carries {@code @IgnoreOrder}.
     *
     * @param element the member to test
     * @return whether the member has been taken out of the check
     */
    private static boolean ignored(Element element) {
        return annotation(element, IGNORE_ORDER) != null;
    }

    /**
     * Returns whether {@code element} carries one of the configured lifecycle annotations, such as
     * JUnit's {@code @BeforeEach}, whose position a reader is meant to choose.
     *
     * @param element the method to test
     * @return whether the method is lifecycle scaffolding
     */
    private boolean lifecycle(Element element) {
        for (String name : lifecycleAnnotations) {
            if (annotation(element, name) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        Set<Category> exempt = exemptCategories(tree);
        Member previous = null;
        for (Tree raw : tree.getMembers()) {
            Member current = classify(raw, state);
            if (current == null || exempt.contains(current.category())) {
                continue;
            }
            if (previous != null) {
                if (current.category().compareTo(previous.category()) < 0) {
                    state.reportMatch(describe(raw, current, previous, "declared after"));
                } else if (current.category() == previous.category() && current.compareTo(previous) < 0) {
                    state.reportMatch(describe(raw, current, previous, "out of order with"));
                }
            }
            previous = current;
        }
        return Description.NO_MATCH;
    }

    /**
     * Classifies a method or constructor declaration.
     *
     * @param tree the declaration to classify
     * @return the member, or {@code null} if it is invisible to the check
     */
    private @Nullable Member method(MethodTree tree) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        if (symbol == null || ASTHelpers.isGeneratedConstructor(tree) || ignored(symbol) || lifecycle(symbol)) {
            return null;
        }
        if (tree.getName().contentEquals(INIT)) {
            int arity = tree.getParameters().size();
            String parameters = arity == 1 ? "1 parameter" : arity + " parameters";
            return new Member(Category.CONSTRUCTOR, "", arity, "constructor with " + parameters);
        }
        return new Member(Category.METHOD, sortKey(tree.getName()), 0, "method " + tree.getName() + "()");
    }

    /**
     * Reads a list-valued flag, trimming each entry and dropping blank ones. An empty flag value such
     * as {@code -XepOpt:Concordance:ScaffoldingFieldTypes=} arrives as a single empty entry, which
     * would otherwise be looked up as a type name.
     *
     * @param flags the flags the compilation was invoked with
     * @param key the flag to read
     * @return the non-blank entries, in order
     */
    private static List<String> names(ErrorProneFlags flags, String key) {
        return flags.getListOrEmpty(key).stream().map(String::trim).filter(name -> !name.isEmpty()).toList();
    }

    /**
     * Returns whether {@code field} is the implicit field javac declares for a record component. These
     * are the one kind of generated member with a source position of their own, the component in the
     * record header, so the position test every other synthesised member fails does not catch them.
     *
     * @param field the field to test
     * @return whether the field backs a record component
     */
    private static boolean recordComponent(VariableElement field) {
        if (!(field.getEnclosingElement()instanceof TypeElement enclosing)
                || enclosing.getKind() != ElementKind.RECORD) {
            return false;
        }
        for (RecordComponentElement component : enclosing.getRecordComponents()) {
            if (component.getSimpleName().contentEquals(field.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the declared type of {@code tree} is one of the configured scaffolding types,
     * such as an SLF4J logger. A configured name that does not resolve on the compilation's classpath
     * matches nothing, so a build can list a type it does not always depend on.
     *
     * @param tree the field to test
     * @param state the visitor state, which resolves a name to a type
     * @return whether the field is scaffolding rather than state
     */
    private boolean scaffolding(VariableTree tree, VisitorState state) {
        if (scaffoldingFieldTypes.isEmpty()) {
            return false;
        }
        Type declared = ASTHelpers.getType(tree.getType());
        if (declared == null) {
            return false;
        }
        for (String name : scaffoldingFieldTypes) {
            Type configured = state.getTypeFromString(name);
            if (configured != null && ASTHelpers.isSameType(declared, configured, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the sort key for a member named {@code name}: the name lower-cased, which is what makes
     * the ordering case-insensitive.
     *
     * @param name the member's declared name
     * @return the key the member sorts by within its category
     */
    private static String sortKey(CharSequence name) {
        return name.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Classifies a field or enum constant declaration. Modifiers come from the symbol rather than the
     * declaration, so that a field of an interface, which is implicitly static and final without saying
     * so, still counts as a constant. A record's component fields need a test of their own: javac gives
     * each one the source position of the component in the record header, so unlike the accessors it
     * generates alongside them they do not look synthesised.
     *
     * @param tree the declaration to classify
     * @param state the visitor state
     * @return the member, or {@code null} if it is invisible to the check
     */
    private @Nullable Member variable(VariableTree tree, VisitorState state) {
        Symbol.VarSymbol symbol = ASTHelpers.getSymbol(tree);
        if (symbol == null || ignored(symbol) || recordComponent(symbol)) {
            return null;
        }
        CharSequence name = tree.getName();
        if (symbol.getKind() == ElementKind.ENUM_CONSTANT) {
            return new Member(Category.ENUM_CONSTANT, sortKey(name), 0, "enum constant " + name);
        }
        if (scaffolding(tree, state)) {
            return null;
        }
        Set<Modifier> modifiers = symbol.getModifiers();
        boolean constant = modifiers.contains(Modifier.STATIC) && modifiers.contains(Modifier.FINAL);
        Category category = constant ? Category.CONSTANT : Category.FIELD;
        return new Member(category, sortKey(name), 0, (constant ? "constant " : "field ") + name);
    }
}

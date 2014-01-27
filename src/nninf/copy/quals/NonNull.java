package nninf.copy.quals;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import javax.lang.model.type.TypeKind;

import nninf.copy.quals.Nullable;

import checkers.quals.ImplicitFor;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType;

import com.sun.source.tree.Tree;

/**
 * @see Nullable
 * @see checkers.nullness.quals.NonNull
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@TypeQualifier
@SubtypeOf(Nullable.class)
@ImplicitFor(
    types={TypeKind.PACKAGE},
    typeClasses={AnnotatedPrimitiveType.class},
    trees={
        Tree.Kind.NEW_CLASS,
        Tree.Kind.NEW_ARRAY,
        Tree.Kind.PLUS,         // for String concatenation
        // All literals except NULL_LITERAL:
        Tree.Kind.BOOLEAN_LITERAL,
        Tree.Kind.CHAR_LITERAL,
        Tree.Kind.DOUBLE_LITERAL,
        Tree.Kind.FLOAT_LITERAL,
        Tree.Kind.INT_LITERAL,
        Tree.Kind.LONG_LITERAL,
        Tree.Kind.STRING_LITERAL
    })
public @interface NonNull {}

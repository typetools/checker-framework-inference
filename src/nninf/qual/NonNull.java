package nninf.qual;

import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeKind;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;

/**
 * @see Nullable
 * @see checkers.nullness.quals.NonNull
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf(Nullable.class)
@ImplicitFor(
    types={TypeKind.PACKAGE},
    typeNames = {
            AnnotatedPrimitiveType.class,
            NewClassTree.class, 
            NewArrayTree.class,
            // for String concatenation
            BinaryTree.class }, 
    literals = {
            // All literals except NULL_LITERAL:
            LiteralKind.BOOLEAN,
            LiteralKind.CHAR,
            LiteralKind.DOUBLE,
            LiteralKind.FLOAT,
            LiteralKind.INT,
            LiteralKind.LONG,
            LiteralKind.STRING
    })
public @interface NonNull {}
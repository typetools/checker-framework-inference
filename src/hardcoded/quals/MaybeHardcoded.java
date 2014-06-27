package hardcoded.quals;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sun.source.tree.Tree;
import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

import javax.lang.model.type.TypeKind;

/**
 * Represents data that may have been hardcoded.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE_USE, ElementType.TYPE_PARAMETER })
@TypeQualifier
@SubtypeOf({})
@DefaultQualifierInHierarchy
@ImplicitFor(
        trees={
                Tree.Kind.BOOLEAN_LITERAL,
                Tree.Kind.CHAR_LITERAL,
                Tree.Kind.DOUBLE_LITERAL,
                Tree.Kind.FLOAT_LITERAL,
                Tree.Kind.INT_LITERAL,
                Tree.Kind.LONG_LITERAL,
                Tree.Kind.STRING_LITERAL,
        },
        types={
                TypeKind.BOOLEAN,
                TypeKind.CHAR,
                TypeKind.DOUBLE,
                TypeKind.FLOAT,
                TypeKind.INT,
                TypeKind.LONG,
        })
public @interface MaybeHardcoded {}

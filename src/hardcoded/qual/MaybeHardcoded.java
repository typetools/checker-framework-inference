package hardcoded.qual;

import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeKind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents data that may have been hardcoded.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf({})
@DefaultQualifierInHierarchy
@ImplicitFor(
        literals={
                LiteralKind.BOOLEAN,
                LiteralKind.CHAR,
                LiteralKind.DOUBLE,
                LiteralKind.FLOAT,
                LiteralKind.INT,
                LiteralKind.LONG,
                LiteralKind.STRING,
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

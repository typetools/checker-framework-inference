package hardcoded.qual;

import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.QualifierForLiterals;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeKind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents data that is not hardcoded.
 *
 * @see MaybeHardcoded
 * @see PolyHardcoded
 * @see trusted.qual.Trusted
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf({MaybeHardcoded.class})
@QualifierForLiterals(LiteralKind.NULL)
@DefaultFor(typeKinds={ TypeKind.NULL })
public @interface NotHardcoded {}

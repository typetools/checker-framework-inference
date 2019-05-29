package trusted.qual;

import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.QualifierForLiterals;
import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A type annotation indicating that the contained value is to be trusted.<p/>
 *
 * It is up to the user to determine what, exactly, she wants {@code Trusted} to
 * represent. Similar type systems with prescribed meanings are available in
 * other packages.<p/>
 *
 * All literals are {@code Trusted} by default.<p/>
 *
 * The concatenation of two {@code Trusted} Strings with the + operator is
 * itself a {@code Trusted} String.<p/>
 *
 * Addition of other {@code Trusted} types also results in a {@code Trusted}
 * type. For example, (trusted int + trusted int) gives a trusted int. For
 * consistency, we should either restrict this behavior to Strings, or extend it
 * to include other operators.<p/>
 *
 * @see Untrusted
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf(Untrusted.class)
@QualifierForLiterals({
        LiteralKind.BOOLEAN,
        LiteralKind.CHAR,
        LiteralKind.DOUBLE,
        LiteralKind.FLOAT,
        LiteralKind.INT,
        LiteralKind.LONG,
        LiteralKind.NULL,
        LiteralKind.STRING,
    })
public @interface Trusted {}

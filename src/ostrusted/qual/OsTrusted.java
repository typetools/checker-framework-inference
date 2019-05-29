package ostrusted.qual;

import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.QualifierForLiterals;
import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents data that has been verified as suitable to pass to OS commands,
 * such as exec.<p/>
 *
 * The included JDK stub file requires that arguments to certain JDK methods be
 * {@code OsTrusted Strings}.<p/>
 *
 * The user must determine for himself what constitutes a verified piece of
 * data. For example, it is a good idea to consider data coming from the network
 * to be {@link OsUntrusted}, and subject it to some sort of verification
 * routine to sanitize it.<p/>
 *
 * This type annotation is intended to be used primarily with {@code String}s,
 * but it is conceivable that it could be useful for other types.<p/>
 *
 * All literals are considered {@code OsTrusted}.<p/>
 *
 * The concatenation of two {@code OsTrusted String}s via the + operator results
 * in an {@code OsTrusted String}. The use of the + operator on other {@code
 * OsTrusted} types also results in an {@code OsTrusted} type. This behavior
 * should be made consistent, either by restricting it to {@code String}s only
 * or by extending it to also apply to other arithmetic operations.<p/>
 *
 * @see OsUntrusted
 * @see PolyOsTrusted
 * @see trusted.qual.Trusted
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf({OsUntrusted.class})

// SHOULD WE HAVE A WAY TO SPECIFY ENUMS
@QualifierForLiterals({
        LiteralKind.BOOLEAN,
        LiteralKind.CHAR,
        LiteralKind.DOUBLE,
        LiteralKind.FLOAT,
        LiteralKind.INT,
        LiteralKind.LONG,
        LiteralKind.NULL,
        LiteralKind.STRING
    })
public @interface OsTrusted {}

package ostrusted.quals;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import checkers.quals.ImplicitFor;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;

import com.sun.source.tree.Tree;

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
 * @see trusted.quals.Trusted
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@TypeQualifier
@SubtypeOf({OsUntrusted.class})
@ImplicitFor(
    trees={
        Tree.Kind.BOOLEAN_LITERAL,
        Tree.Kind.CHAR_LITERAL,
        Tree.Kind.DOUBLE_LITERAL,
        Tree.Kind.FLOAT_LITERAL,
        Tree.Kind.INT_LITERAL,
        Tree.Kind.LONG_LITERAL,
        Tree.Kind.NULL_LITERAL,
        Tree.Kind.STRING_LITERAL,
    })
public @interface OsTrusted {}

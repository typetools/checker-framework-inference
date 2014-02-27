package trusted.quals;

import java.lang.annotation.*;

import checkers.quals.DefaultQualifierInHierarchy;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;

/**
 * A type annotation indicating that the contained value cannot be proven to be
 * trustworthy.<p/>
 *
 * Variables with no annotation are considered {@code Untrusted}.<p/>
 *
 * It is up to the user to determine what, exactly, she wants {@code Untrusted}
 * to represent. Similar type systems with prescribed meanings are available in
 * other packages.<p/>
 *
 * @see Trusted
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE_USE, ElementType.TYPE_PARAMETER })
@TypeQualifier
@SubtypeOf({})
@DefaultQualifierInHierarchy
public @interface Untrusted {}

package ostrusted.quals;

import java.lang.annotation.*;

import checkers.quals.DefaultQualifierInHierarchy;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;

/**
 * Represents data that may not be suitable to pass to OS commands such as
 * exec.<p/>
 *
 * Types are implicitly {@code OsUntrusted}.
 *
 * @see OsTrusted
 * @see PolyOsTrusted
 * @see trusted.quals.UnTrusted
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE_USE, ElementType.TYPE_PARAMETER })
@TypeQualifier
@SubtypeOf({})
@DefaultQualifierInHierarchy
public @interface OsUntrusted {}

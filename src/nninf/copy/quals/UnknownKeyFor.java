package nninf.copy.quals;

import java.lang.annotation.Target;

import checkers.quals.DefaultQualifierInHierarchy;
import checkers.quals.InvisibleQualifier;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;

/**
 * A reference for which we don't know whether it's a key for a map or not.
 *
 * <p>
 * Programmers cannot write this in source code.
 */
@TypeQualifier
@InvisibleQualifier
@SubtypeOf({})
@Target({}) // empty target prevents programmers from writing this in a program
@DefaultQualifierInHierarchy
public @interface UnknownKeyFor {}

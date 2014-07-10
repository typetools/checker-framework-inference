package nninf.quals;

import java.lang.annotation.Target;

import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.InvisibleQualifier;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

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

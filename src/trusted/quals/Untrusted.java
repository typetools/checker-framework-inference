package trusted.quals;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

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

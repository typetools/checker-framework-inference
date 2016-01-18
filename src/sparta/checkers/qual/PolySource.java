package sparta.checkers.qual;

import org.checkerframework.framework.qual.PolymorphicQualifier;

import java.lang.annotation.*;

/**
 * Polymorphic qualifier for flow sources.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE_PARAMETER, ElementType.TYPE_USE,
/* The following only added to make Eclipse work. */
ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.LOCAL_VARIABLE })
@PolymorphicQualifier(Source.class)
public @interface PolySource {
}

package sparta.checkers.qual;

import java.lang.annotation.*;
import org.checkerframework.framework.qual.PolymorphicQualifier;

/** Polymorphic qualifier for flow sources. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
@PolymorphicQualifier(Source.class)
public @interface PolySource {}

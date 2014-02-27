package ostrusted.quals;

import checkers.quals.PolymorphicQualifier;
import checkers.quals.TypeQualifier;

import java.lang.annotation.*;

/**
 * A Polymorphic qualifier for {@code OsTrusted}.<p/>
 *
 * See {@link
 * http://types.cs.washington.edu/checker-framework/current/checkers-manual.html#qualifier-polymorphism}
 * for information on the semantics of polymorphic qualifiers in the checker
 * framework.<p/>
 *
 * @see OsTrusted
 * @see OsUntrusted
 */
@Documented
@TypeQualifier
@PolymorphicQualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PolyOsTrusted {}

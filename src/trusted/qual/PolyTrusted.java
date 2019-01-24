package trusted.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.PolymorphicQualifier;

/**
 * A Polymorphic qualifier for {@code Trusted}.
 *
 * <p>See {@link
 * http://types.cs.washington.edu/checker-framework/current/checkers-manual.html#qualifier-polymorphism}
 * for information on the semantics of polymorphic qualifiers in the checker framework.
 *
 * @see Trusted
 * @see Untrusted
 */
@Documented
@PolymorphicQualifier(Untrusted.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PolyTrusted {}

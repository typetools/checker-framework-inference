package nninf.qual;

import java.lang.annotation.*;
import org.checkerframework.framework.qual.PolymorphicQualifier;

/**
 * A polymorphic qualifier for the non-null type system.
 *
 * <p>Any method written using {@link PolyNull} conceptually has two versions: one in which every
 * instance of {@link PolyNull} has been replaced by {@link
 * org.checkerframework.checker.nullness.qual.NonNull}, and one in which every instance of {@link
 * PolyNull} has been replaced by {@link org.checkerframework.checker.nullness.qual.Nullable}.
 *
 * @checker_framework_manual #nullness-checker Nullness Checker
 */
@Documented
@PolymorphicQualifier(Nullable.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PolyNull {}

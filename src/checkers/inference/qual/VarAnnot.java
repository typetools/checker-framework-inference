package checkers.inference.qual;

import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Checker Inference Framework's primary intent is to take a unannotated or partially annotated program and infer
 * annotations for specific type system.  VarAnnots represent locations in which an annotation can be written and does
 * not already contain an annotation for the target type system.  If a location which can have an annotation ALREADY
 * has an annotation then it will remain (and be converted into a Constant for generating constraints).  However, if
 * a location DOES NOT have an annotation from the target type system then a VarAnnot is generated.  Some locations
 * which only implicitly exist in the source code will also have VarAnnots generated for their values.
 * e.g.
 *   class MyClass
 * becomes:
 *   class MyClass extends @VarAnnot(id) Object
 *
 * When annotations are inserted back into source code they usually are placed in the location an @VarAnnot annotation
 * was generated for.  If the VarAnnot was generated for an implicit location then source code should be generated to
 * construct the appropriate location.
 */
@Retention(RetentionPolicy.RUNTIME)
@SubtypeOf({})
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface VarAnnot {
    int value();
}
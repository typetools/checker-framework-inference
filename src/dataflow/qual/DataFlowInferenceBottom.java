package dataflow.qual;

import org.checkerframework.framework.qual.InvisibleQualifier;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TargetLocations;
import org.checkerframework.framework.qual.TypeUseLocation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotation for inferring dataflow type system.
 * @author jianchu
 *
 */
@InvisibleQualifier
@SubtypeOf({ DataFlow.class })
@Target({ ElementType.TYPE_USE })
@TargetLocations({ TypeUseLocation.EXPLICIT_LOWER_BOUND })
public @interface DataFlowInferenceBottom {

}

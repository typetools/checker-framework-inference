package dataflow.qual;

import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.InvisibleQualifier;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TargetLocations;
import org.checkerframework.framework.qual.TypeUseLocation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@DefaultQualifierInHierarchy
@InvisibleQualifier
@SubtypeOf({})
@Target({ ElementType.TYPE_USE })
@TargetLocations({ TypeUseLocation.EXPLICIT_UPPER_BOUND })
public @interface DataFlowTop {

}
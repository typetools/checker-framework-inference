package checkers.inference.quals;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;
import org.checkerframework.framework.qual.Unqualified;

/**
 * CombVarAnnot represents variables which are a combination of other variables, literals, or constants.  These
 * annotations identify a unique CombVar object stored in the list combVariables in the SlotManager
 * See @VarAnnot
 */

@Retention(RetentionPolicy.RUNTIME)
@TypeQualifier
@SubtypeOf({Unqualified.class})
public @interface CombVarAnnot {
    int value();
}
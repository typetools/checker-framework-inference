package checkers.inference.quals;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;
import checkers.quals.Unqualified;

/**
 * RefinementVarAnnot represents values whose type has been refined from their declared type via assignment.  Every
 * RefinementVarAnnot has one VarAnnot that corresponds to the declaration type of the variable being refined.
 * A VarAnnot may have multiple RefinementVarAnnots that represent the value of its variable after an assignment
 * occurs.  RefinementVarAnnots are converted to RefinementVariables by the SlotManager and are stored in
 * SlotManager.refVariables.
 * see @VarAnnot
 *
 * If inference determines that a RefinementVarAnnot needs to have a particular value then a cast should be
 * inserted on the assignment that generated the RefinementVarAnnot.
 */

@Retention(RetentionPolicy.RUNTIME)
@TypeQualifier
@SubtypeOf({Unqualified.class})
public @interface RefineVarAnnot {
    int value();
}
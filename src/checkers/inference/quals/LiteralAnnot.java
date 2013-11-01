package checkers.inference.quals;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;
import checkers.quals.Unqualified;
import com.sun.source.tree.Tree.Kind;

/**
 * Literal annotations are used to represent literal values as well as receivers whose values are the
 * "this" reference (either implied or excplicitly written).  Literal annotations are converted into
 * one of the Literal slot types but are NOT stored in SlotMgr like other slots.
 * See @VarAnnot
 */

@Retention(RetentionPolicy.SOURCE)
@TypeQualifier
@SubtypeOf({ Unqualified.class })
public @interface LiteralAnnot {
    Kind kind();
    String literal();
}
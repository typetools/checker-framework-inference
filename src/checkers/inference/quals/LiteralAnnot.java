package checkers.inference.quals;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;
import checkers.quals.Unqualified;
import com.sun.source.tree.Tree.Kind;

@Retention(RetentionPolicy.SOURCE)
@TypeQualifier
@SubtypeOf({ Unqualified.class })
public @interface LiteralAnnot {
    Kind kind();
    String literal();
}
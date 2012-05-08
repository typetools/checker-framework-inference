package checkers.inference.quals;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;
import checkers.quals.Unqualified;

@Retention(RetentionPolicy.RUNTIME)
@TypeQualifier
@SubtypeOf({Unqualified.class})
public @interface CombVarAnnot {
    int value();
}
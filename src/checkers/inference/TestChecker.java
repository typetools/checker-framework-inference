package checkers.inference;

import javax.annotation.processing.ProcessingEnvironment;

import checkers.basetype.BaseTypeChecker;
import checkers.inference.quals.VarAnnot;
import checkers.quals.TypeQualifiers;
import checkers.quals.Unqualified;

@TypeQualifiers({Unqualified.class, VarAnnot.class})
public class TestChecker extends BaseTypeChecker {

    @Override
    public void initChecker(ProcessingEnvironment env) {
        super.initChecker(env);
        System.out.println("Here!");
    }
}
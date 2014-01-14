package nninf.copy;

import java.lang.annotation.Annotation;
import java.util.Set;

import checkers.basetype.BaseTypeChecker;
import checkers.inference.InferrableChecker;
import checkers.inference.InferenceVisitor;

import com.sun.source.util.Trees;

public abstract class GameChecker extends BaseTypeChecker implements InferrableChecker {

    @Override
    public void initChecker() {
        //In between these brackets, is code copied directly from SourceChecker
        //except for the last line assigning the visitor
        {
            Trees trees = Trees.instance(processingEnv);
            assert( trees != null ); /*nninvariant*/
            this.trees = trees;

            this.messager = processingEnv.getMessager();
            this.messages = getMessages();

            this.visitor = createVisitor(null, createRealTypeFactory(), false);
        }
    }

//    @Override
//    public Set<Class<? extends Annotation>> getSupportedTypeQualifiers() {
//        return ((InferenceVisitor) visitor).getTypeFactory().getSupportedTypeQualifiers();
//    }

//    @Override
//    public AnnotatedTypeFactory getTypeFactory() {
//        return ( (InferenceVisitor<?,?>) this.visitor ).getTypeFactory();
//    }
}

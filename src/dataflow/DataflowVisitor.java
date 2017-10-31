package dataflow;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;

import checkers.inference.InferenceChecker;
import checkers.inference.InferenceVisitor;

/**
 * Don't generate any special constraint for Dataflow type system.
 * 
 * @author jianchu
 *
 */
public class DataflowVisitor extends InferenceVisitor<DataflowChecker, BaseAnnotatedTypeFactory> {

    public DataflowVisitor(DataflowChecker checker, InferenceChecker ichecker,
            BaseAnnotatedTypeFactory factory, boolean infer) {
        super(checker, ichecker, factory, infer);
    }
}

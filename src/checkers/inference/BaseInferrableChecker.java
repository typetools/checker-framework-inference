package checkers.inference;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.framework.flow.CFAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.javacutil.Pair;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.VariableElement;

import checkers.inference.dataflow.InferenceAnalysis;
import checkers.inference.dataflow.InferenceTransfer;
import checkers.inference.model.ConstraintManager;

import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;

/**
 * Default implementation of InferrableChecker.
 *
 */
public abstract class BaseInferrableChecker extends InferenceChecker implements InferrableChecker {

    @Override
    public void initChecker() {
        // In between these brackets, is code copied directly from SourceChecker
        // except for the last line assigning the visitor
        {
            Trees trees = Trees.instance(processingEnv);
            assert( trees != null ); /*nninvariant*/
            this.trees = trees;

            this.messager = processingEnv.getMessager();
            this.messages = getMessages();

            this.visitor = createVisitor(null, createRealTypeFactory(), false);
        }
    }

    @Override
    public InferenceVisitor<?, ?> createVisitor(InferenceChecker ichecker, BaseAnnotatedTypeFactory factory, boolean infer) {
        return new InferenceVisitor<>(this, ichecker, factory, infer);
    }

    @Override
    public BaseAnnotatedTypeFactory createRealTypeFactory() {
        return new BaseAnnotatedTypeFactory(this);
    }

    @Override
    public CFAnalysis createInferenceAnalysis(
                    InferenceChecker checker,
                    GenericAnnotatedTypeFactory<CFValue, CFStore, CFTransfer, CFAnalysis> factory,
                    List<Pair<VariableElement, CFValue>> fieldValues,
                    SlotManager slotManager,
                    ConstraintManager constraintManager,
                    InferrableChecker realChecker) {

        return new InferenceAnalysis(checker, factory, fieldValues, slotManager, constraintManager, realChecker);
    }

    @Override
    public CFTransfer createInferenceTransferFunction(InferenceAnalysis analysis) {
        return new InferenceTransfer(analysis);
    }

    @Override
    public boolean withCombineConstraints() {
        return false;
    }

    @Override
    public boolean isConstant(Tree node) {
        return false;
    }

    @Override
    public boolean shouldStoreConstantSlots() {
        return true;
    }

    @Override
    public InferenceAnnotatedTypeFactory createInferenceATF(InferenceChecker inferenceChecker,
            InferrableChecker realChecker, BaseAnnotatedTypeFactory realTypeFactory,
            SlotManager slotManager, ConstraintManager constraintManager) {
        InferenceAnnotatedTypeFactory InferenceAFT = new InferenceAnnotatedTypeFactory(
                inferenceChecker, realChecker.withCombineConstraints(), realTypeFactory, realChecker,
                slotManager, constraintManager);
        return InferenceAFT;
    }

    @Override
    public boolean isInsertMainModOfLocalVar() {
        return false;
    }

    @Override
    public Set<Class<? extends Annotation>> additionalAnnotationsForJaifHeaderInsertion() {
        return Collections.emptySet();
    }
}

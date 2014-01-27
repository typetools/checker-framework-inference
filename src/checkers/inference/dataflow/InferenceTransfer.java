package checkers.inference.dataflow;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import annotations.io.ASTPath;
import checkers.flow.CFStore;
import checkers.flow.CFTransfer;
import checkers.flow.CFValue;
import checkers.inference.InferenceAnnotatedTypeFactory;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.ASTPathUtil;
import checkers.inference.util.InferenceUtil;
import checkers.types.AnnotatedTypeMirror;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import dataflow.analysis.RegularTransferResult;
import dataflow.analysis.TransferInput;
import dataflow.analysis.TransferResult;
import dataflow.cfg.node.AssignmentNode;
import dataflow.cfg.node.FieldAccessNode;
import dataflow.cfg.node.LocalVariableNode;
import dataflow.cfg.node.Node;
import dataflow.cfg.node.TernaryExpressionNode;

/**
 * InferenceTransfer extends the default data flow transfer function of the Checker Framework flow sensitive
 * type refinement.  It does so in order to generate refinement variables.  When generating variables/constraints
 * we essentially want the variables to be in static single assignment form.  Therefore, for every assignment that
 * does not occur in the declaration of a variable, we generate a "RefinementVariable".
 * RefinementVariables will always have one Variable it refines though a Variable may have many
 * RefinementVariables (one for each assignment in which it is involved).
 *
 * Note:  RefinementVariables correctly appear in constraints because we always visit the AST for a class at least
 * twice.  The first time we generate variables/refinementVariables and the second time we generate constraints.
 *
 *  // TODO: Need to handle CompoundAssignmentTree and UnaryTr
 *  // TODO: Need to handle CompoundAssignmentTree and UnaryTree
 *
 * @param analysis
 */
public class InferenceTransfer extends CFTransfer {

    private static final Logger logger = LoggerFactory.getLogger(InferenceTransfer.class);

    private Map<Tree, RefinementVariableSlot> createdRefinementVariables = new HashMap<Tree, RefinementVariableSlot>();


    public InferenceTransfer(InferenceAnalysis analysis) {
        super(analysis);
    }

    private InferenceAnalysis getInferenceAnalysis() {
        return (InferenceAnalysis) analysis;
    }

    /**
     * Super implementation might replace refinement variables on equality. Which we do not want.
     *
     * Example of what could go wrong:
     * @VarAnnot(1) a
     * @VarAnnot(2) b
     * if (a == b) {
     *   @VarAnnot(1) a
     *   @VarAnnot(1) b
     * }
     *
     * A better fix might be updating CFAbstractValue.mostSpecific, which would
     * depend on getting subtype relationships between Slots correct.
     *
     */
    @Override
    protected TransferResult<CFValue, CFStore> strengthenAnnotationOfEqualTo(TransferResult<CFValue, CFStore> res,
            Node firstNode, Node secondNode, CFValue firstValue, CFValue secondValue, boolean notEqualTo) {

        return res;
    }

    /**
     * A CombVariable from the results of ternary will be created already by the visiting stage.
     * For RefinementVariables, we don't want to try to get the result value nor LUB between sides.
     *
     */
    @Override
    public RegularTransferResult<CFValue, CFStore> visitTernaryExpression(TernaryExpressionNode n, TransferInput<CFValue, CFStore> p) {
        CFStore store = p.getRegularStore();
        return new RegularTransferResult<CFValue, CFStore>(finishValue(null, store), store);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitAssignment(AssignmentNode assignmentNode, TransferInput<CFValue, CFStore> transferInput) {

        Node lhs = assignmentNode.getTarget();
        AssignmentTree assignmentTree = (AssignmentTree) assignmentNode.getTree();
        CFStore store = transferInput.getRegularStore();
        InferenceAnnotatedTypeFactory typeFactory = (InferenceAnnotatedTypeFactory) analysis.getTypeFactory();
        AnnotatedTypeMirror atm = typeFactory.getAnnotatedType(assignmentNode.getTree());

        if (InferenceUtil.isDetachedVariable(assignmentTree) ||
                getInferenceAnalysis().getRealChecker().isConstant(atm)) {

            return super.visitAssignment(assignmentNode, transferInput);

        } else if (isDeclarationWithInitializer(assignmentNode, assignmentTree)) {

            // Add declarations with initializers to the store.
            // This is needed to trigger a merge refinement variable creation when two stores are merged:
            // String a = null; // Add VarAnno to store
            // if ( ? ) {
            //   a = ""; // Add RefVar to store
            // }
            // // merge RefVar will be created only if VarAnno and RefVar are both in store
            // a.toString()

            return handleDeclaration(lhs, assignmentTree, store, typeFactory);

        } else if (lhs.getTree().getKind() == Tree.Kind.IDENTIFIER
                || lhs.getTree().getKind() == Tree.Kind.MEMBER_SELECT) {

            // TODO: Need to handle CompoundAssignmentTree and UnaryTree
            assert !((assignmentTree instanceof UnaryTree
                    || assignmentTree instanceof CompoundAssignmentTree));

            return createRefinementVar(assignmentNode, transferInput, lhs,
                    assignmentTree, store, atm);

        } else {

            // TODO: What other cases are there;
            assert false;
            return super.visitAssignment(assignmentNode, transferInput);
        }
    }

    private TransferResult<CFValue, CFStore> createRefinementVar(
            AssignmentNode assignmentNode,
            TransferInput<CFValue, CFStore> transferInput, Node lhs,
            AssignmentTree assignmentTree, CFStore store,
            AnnotatedTypeMirror atm) {

        Slot slotToRefine = getInferenceAnalysis().getSlotManager().getSlot(atm);
        if (slotToRefine instanceof ConstantSlot) {
            assert false; // TODO: When does this happen?
            super.visitAssignment(assignmentNode, transferInput);
        }

        logger.debug("Creating refinement variable for tree: " + assignmentTree);
        RefinementVariableSlot refVar;
        if (createdRefinementVariables.containsKey(assignmentTree)) {
            refVar = createdRefinementVariables.get(assignmentTree);
        } else {
            ASTPath path = ASTPathUtil.getASTPathToNode(analysis.getTypeFactory(), assignmentTree);
            refVar = new RefinementVariableSlot(path,
                    getInferenceAnalysis().getSlotManager().nextId(), (VariableSlot) slotToRefine);

            getInferenceAnalysis().getSlotManager().addVariable(refVar);
            createdRefinementVariables.put(assignmentTree, refVar);
        }

        atm.clearAnnotations();
        atm.addAnnotation(getInferenceAnalysis().getSlotManager().getAnnotation(refVar));

        // add refinement variable value to output
        CFValue result = analysis.createAbstractValue(atm);
        // TODO:
        // This is a total hack, but we want the LHS to now get this annotation.
        // I am trying to replace replaceWithRefVar
        // ===
        getInferenceAnalysis().getNodeValues().put(lhs, result);
        // ===
        store.updateForAssignment(lhs, result);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
    }

    private TransferResult<CFValue, CFStore> handleDeclaration(Node lhs,
            AssignmentTree assignmentTree, CFStore store,
            InferenceAnnotatedTypeFactory typeFactory) {
        AnnotatedTypeMirror atm = typeFactory.getAnnotatedType(assignmentTree);
        CFValue result = analysis.createAbstractValue(atm);
        store.updateForAssignment(lhs, result);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
    }

    private boolean isDeclarationWithInitializer(AssignmentNode assignmentNode,
            AssignmentTree assignmentTree) {
        return (assignmentTree.getKind() == Tree.Kind.VARIABLE
                && ((VariableTree) assignmentTree).getInitializer() != null)
                    && (assignmentNode.getTarget() instanceof LocalVariableNode
                            || assignmentNode.getTarget() instanceof FieldAccessNode);
    }
}

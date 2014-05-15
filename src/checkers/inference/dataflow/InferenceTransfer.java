package checkers.inference.dataflow;

import java.util.HashMap;
import java.util.Map;

import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.ErrorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import annotations.io.ASTIndex.ASTRecord;
import checkers.inference.InferenceAnnotatedTypeFactory;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.ASTPathUtil;
import checkers.inference.util.InferenceUtil;

import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;

/**
 *
 * InferenceTransfer extends CFTransfer for inference.
 *
 * InferenceTransfer overrides CFTransfer methods to create refinement variables
 * and to maintain the refinement variables for the analysis.
 *
 * See InferenceAnalysis for an overview of dataflow for inference.
 *
 * Note: RefinementVariables correctly appear in constraints because we always visit the AST for a class at least
 * twice. The first time we generate variables/refinementVariables and the second time we generate constraints.
 *
 * @param analysis
 */
public class InferenceTransfer extends CFTransfer {

    private static final Logger logger = LoggerFactory.getLogger(InferenceTransfer.class);

    // Keep a cache of tree's that we have created refinement variables so that we do
    // not create multiple. A tree can be evaluated multiple times due to loops.
    private Map<Tree, RefinementVariableSlot> createdRefinementVariables = new HashMap<>();

    public InferenceTransfer(InferenceAnalysis analysis) {
        super(analysis);
    }

    private InferenceAnalysis getInferenceAnalysis() {
        return (InferenceAnalysis) analysis;
    }

    /**
     * A CombVariable from the results of ternary will be created already by the visiting stage.
     * For RefinementVariables, we don't want to try to get the result value nor LUB between sides.
     *
     */
    @Override
    public RegularTransferResult<CFValue, CFStore> visitTernaryExpression(TernaryExpressionNode n,
            TransferInput<CFValue, CFStore> p) {

        CFStore store = p.getRegularStore();
        return new RegularTransferResult<CFValue, CFStore>(finishValue(null, store), store);
    }

    /**
     * Create refinement variables on assignments.
     */
    @Override
    public TransferResult<CFValue, CFStore> visitAssignment(AssignmentNode assignmentNode, TransferInput<CFValue, CFStore> transferInput) {

        Node lhs = assignmentNode.getTarget();
        CFStore store = transferInput.getRegularStore();
        InferenceAnnotatedTypeFactory typeFactory = (InferenceAnnotatedTypeFactory) analysis.getTypeFactory();

        // Target tree is null for field access's
        Tree targetTree = assignmentNode.getTarget().getTree();

        AnnotatedTypeMirror atm;
        if (targetTree != null) {
            // Try to use the target tree if possible.
            // Getting the Type of a tree for a desugared compound assignment returns a comb variable
            // which is not what we want to make a refinement variable of.
            atm = typeFactory.getAnnotatedType(targetTree);
        } else {
            // Target trees can be null for refining library fields.
            atm = typeFactory.getAnnotatedType(assignmentNode.getTree());
        }

        if (targetTree != null && targetTree.getKind() == Tree.Kind.ARRAY_ACCESS) {
            // Don't create refinement variables on array assignments.

            CFValue result = analysis.createAbstractValue(atm);
            return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);

        } else if (targetTree != null && InferenceUtil.isDetachedVariable(targetTree)) {
            // Don't create refinement variables for detached.

            CFValue result = analysis.createAbstractValue(atm);
            return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);

        } else if (isDeclarationWithInitializer(assignmentNode)) {
            // Add declarations with initializers to the store.

            // This is needed to trigger a merge refinement variable creation when two stores are merged:
            // String a = null; // Add VarAnno to store
            // if ( ? ) {
            //   a = ""; // Add RefVar to store
            // }
            // // merge RefVar will be created only if VarAnno and RefVar are both in store
            // a.toString()

            // TODO: Remove after establishing there are no other cases.
            if (! (assignmentNode.getTarget() instanceof LocalVariableNode
                    || assignmentNode.getTarget() instanceof FieldAccessNode)) {
                assert false;
            }

            return storeDeclaration(lhs, (VariableTree) assignmentNode.getTree(), store, typeFactory);

        } else if (lhs.getTree().getKind() == Tree.Kind.IDENTIFIER
                || lhs.getTree().getKind() == Tree.Kind.MEMBER_SELECT) {
            // Create Refinement Variable

            // TODO: We do not currently refine UnaryTrees and Compound Assignments
            // See the note on InferenceVisitor.visitCompoundAssignment
            if (assignmentNode.getTree() instanceof CompoundAssignmentTree
                    || assignmentNode.getTree() instanceof UnaryTree) {
                CFValue result = analysis.createAbstractValue(atm);
                return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
            }

            return createRefinementVar(assignmentNode.getTarget(), assignmentNode.getTree(), store, atm);

        } else {
            ErrorReporter.errorAbort("Unexpected tree kind in visit assignment:" + assignmentNode.getTree());
            return null; // dead
        }
    }

    /**
     * Create a refinement variable for atm type. This inserts the refinement variable
     * into the store as the value for the lhs cfg node.
     *
     * @param lhs The node being assigned
     * @param assignmentTree The tree for the assignment
     * @param store The store to update
     * @param atm The type of the variable being refined
     * @return
     */
    private TransferResult<CFValue, CFStore> createRefinementVar(Node lhs,
            Tree assignmentTree, CFStore store,
            AnnotatedTypeMirror atm) {

        Slot slotToRefine = getInferenceAnalysis().getSlotManager().getSlot(atm);

        logger.debug("Creating refinement variable for tree: " + assignmentTree);
        RefinementVariableSlot refVar;
        if (createdRefinementVariables.containsKey(assignmentTree)) {
            refVar = createdRefinementVariables.get(assignmentTree);
        } else {
            ASTRecord record = ASTPathUtil.getASTRecordForNode(analysis.getTypeFactory(), assignmentTree);
            refVar = new RefinementVariableSlot(record,
                    getInferenceAnalysis().getSlotManager().nextId(), slotToRefine);

            // Fields from library methods can be refined, but the slotToRefine is a ConstantSlot 
            // which does not have a refined slots field.
            if (slotToRefine instanceof VariableSlot) {
                ((VariableSlot) slotToRefine).getRefinedToSlots().add(refVar);
            }
            getInferenceAnalysis().getSlotManager().addVariable(refVar);
            createdRefinementVariables.put(assignmentTree, refVar);
        }

        atm.clearAnnotations();
        atm.addAnnotation(getInferenceAnalysis().getSlotManager().getAnnotation(refVar));

        // add refinement variable value to output
        CFValue result = analysis.createAbstractValue(atm);

        // This is a bit of a hack, but we want the LHS to now get the refinement annotation.
        // So change the value for LHS that is already in the store.
        getInferenceAnalysis().getNodeValues().put(lhs, result);

        store.updateForAssignment(lhs, result);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
    }

    /**
     * Put the VarAnnot for the LHS into the store.
     * This is needed to trigger merges between stores.
     *
     * @param lhs
     * @param assignmentTree
     * @param store
     * @param typeFactory
     * @return
     */
    private TransferResult<CFValue, CFStore> storeDeclaration(Node lhs,
            VariableTree assignmentTree, CFStore store,
            InferenceAnnotatedTypeFactory typeFactory) {

        AnnotatedTypeMirror atm = typeFactory.getAnnotatedType(assignmentTree);
        CFValue result = analysis.createAbstractValue(atm);
        store.updateForAssignment(lhs, result);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
    }

    private boolean isDeclarationWithInitializer(AssignmentNode assignmentNode) {
        return (assignmentNode.getTree().getKind() == Tree.Kind.VARIABLE);
    }
}

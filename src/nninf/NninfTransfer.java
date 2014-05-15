package nninf;

import checkers.inference.dataflow.InferenceAnalysis;
import checkers.inference.dataflow.InferenceTransfer;

public class NninfTransfer extends InferenceTransfer {

    public NninfTransfer(InferenceAnalysis analysis) {
        super(analysis);
    }
}


//package nninf
//
//import checkers.flow._
//import dataflow.cfg.node._
//import dataflow.analysis.{RegularTransferResult, ConditionalTransferResult, TransferResult, TransferInput, FlowExpressions}
//import com.sun.source.tree._
//
//import checkers.inference.InferenceMain.{slotMgr, constraintMgr}
//import javacutils.{TreeUtils, AnnotationUtils}
//import scala.collection.JavaConversions._
//import dataflow.cfg.UnderlyingAST
//import dataflow.cfg.UnderlyingAST.{CFGMethod, Kind}
//import javax.lang.model.`type`.TypeMirror
//import javax.lang.model.element.AnnotationMirror
//import com.sun.source.tree.AssignmentTree
//import com.sun.source.tree.Tree
//import checkers.inference._
//
///**
// * NninfTransfer creates a BallSizeTestConstraint constraint when a variable is compared
// * to a literal null with == or !=.
// *
// * A new RefinementVariable is created for each of the then and else stores. Blocks processed
// * after the current block will use the respective RefinementVariable from the store
// * they inherit.
// *
// * Example:
// * if (a == null) {
// *   a.toString() // Uses then store's RefinementVariable
// * } else {
// *   a.toString() // Uses else store's RefinementVariable
// * }
// *
// */
//
//class NninfTransferImpl(analysis : CFAbstractAnalysis[CFValue, CFStore, CFTransfer]) extends InferenceTransfer(analysis) {
//
//  override def strengthenAnnotationOfEqualTo(res: TransferResult[CFValue,CFStore], 
//      firstNode: Node, secondNode: Node, 
//      firstValue: CFValue, secondValue: CFValue, notEqualTo: Boolean) : TransferResult[CFValue, CFStore] = {
//
//    val infChecker = InferenceMain.inferenceChecker
//    val typeFactory = analysis.getTypeFactory.asInstanceOf[InferenceAnnotatedTypeFactory]
//    val tree = secondNode.getTree
//    val varAtm = typeFactory.getAnnotatedType(tree)
//    val receiver = FlowExpressions.internalReprOf(analysis.getTypeFactory(), secondNode)
//    if (firstNode.isInstanceOf[NullLiteralNode]
//        && InferenceMain.getRealChecker.needsAnnotation(varAtm)
//        && (receiver.isInstanceOf[FlowExpressions.FieldAccess]
//            || receiver.isInstanceOf[FlowExpressions.LocalVariable])) {
//
//      var inputVar = slotMgr.extractSlot(varAtm).asInstanceOf[AbstractVariable]
//      var thenStore = res.getThenStore()
//      var elseStore = res.getElseStore()
//      var anno1: AnnotationMirror = null
//      var anno2: AnnotationMirror = null
//      // If we have already created a BSTest, use the same RefinementVariables
//      if (!infChecker.ballSizeTestCache.contains(tree)) {
//        val astPathStr = InferenceUtils.convertASTPathToAFUFormat(InferenceUtils.getASTPathToNode(typeFactory, tree))
//        anno1 = slotMgr.createRefinementVariableAnnotation(typeFactory, tree, astPathStr, true)
//        anno2 = slotMgr.createRefinementVariableAnnotation(typeFactory, tree, astPathStr, true)
//        val subtypeSlot = slotMgr.extractSlot(anno1).asInstanceOf[RefinementVariable]
//        val supertypeSlot = slotMgr.extractSlot(anno2).asInstanceOf[RefinementVariable]
//        infChecker.ballSizeTestCache += (tree -> new BallSizeTestConstraint(
//            input = inputVar, supertype = supertypeSlot, subtype = subtypeSlot))
//      } else {
//        // Already created a BSTest
//        // Then and else branch variables should remain the same,
//        // but we should update the input variable if it changed.
//        // The input variable changing means a cycle exists between the input
//        // of the bstest and the output's.
//        val bsTest = infChecker.ballSizeTestCache.get(tree).get
//        anno1 = bsTest.subtype.getAnnotation
//        anno2 = bsTest.supertype.getAnnotation
//        if (inputVar != bsTest.input) {
//          infChecker.ballSizeTestCache += (tree -> new BallSizeTestConstraint(
//              input = inputVar, supertype = bsTest.supertype, subtype = bsTest.subtype, cycle = true))
//        }
//      }
//
//      val subtypeAtm = varAtm.getCopy(true)
//      val supertypeAtm = varAtm.getCopy(true)
//      subtypeAtm.clearAnnotations()
//      subtypeAtm.addAnnotation(anno1)
//      supertypeAtm.clearAnnotations()
//      supertypeAtm.addAnnotation(anno2)
//      if (notEqualTo) {
//        // (a != null) { NonNull } else { Nullable }
//        thenStore.replaceValue(FlowExpressions.internalReprOf(typeFactory, secondNode), analysis.createAbstractValue(subtypeAtm))
//        elseStore.replaceValue(FlowExpressions.internalReprOf(typeFactory, secondNode), analysis.createAbstractValue(supertypeAtm))
//      } else {
//        // (a == null) { Nullable } else { NonNull }
//        thenStore.replaceValue(FlowExpressions.internalReprOf(typeFactory, secondNode), analysis.createAbstractValue(supertypeAtm))
//        elseStore.replaceValue(FlowExpressions.internalReprOf(typeFactory, secondNode), analysis.createAbstractValue(subtypeAtm))
//      }
//      return new ConditionalTransferResult(res.getResultValue(), thenStore, elseStore);
//    }
//
//    return res
//  }
//}
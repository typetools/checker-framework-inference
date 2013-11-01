package checkers.inference

import checkers.flow._
import dataflow.cfg.node._
import dataflow.analysis.{RegularTransferResult, ConditionalTransferResult, TransferResult, TransferInput, FlowExpressions}
import com.sun.source.tree._

import InferenceMain.{slotMgr, constraintMgr}
import javacutils.{TreeUtils, AnnotationUtils}
import scala.collection.JavaConversions._
import dataflow.cfg.UnderlyingAST
import dataflow.cfg.UnderlyingAST.{CFGMethod, Kind}
import javax.lang.model.`type`.TypeMirror
import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.Tree

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
 * @param analysis
 */

class  InferenceTransfer(analysis : CFAbstractAnalysis[CFValue, CFStore, CFTransfer]) extends CFTransfer(analysis) {

  /**
   * super implementation might replace refinement variables on equality. Which we do not want.
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
  override def strengthenAnnotationOfEqualTo(res: TransferResult[CFValue,CFStore],
      firstNode: Node, secondNode: Node,
      firstValue: CFValue, secondValue: CFValue, notEqualTo: Boolean) : TransferResult[CFValue, CFStore] = {

    res
  }

  /**
   * A comb constraint from the results of ternary will be created already by the visiting stage.
   * For RefinementVariables, we don't want to try to get the result value nor LUB between sides.
   *
   */
  override def visitTernaryExpression(n : TernaryExpressionNode, p : TransferInput[CFValue, CFStore]) : RegularTransferResult[CFValue, CFStore] = {
    val store = p.getRegularStore()
    return new RegularTransferResult(finishValue(null, store), store)
  }

  def variableIsDetached( assignmentNode : AssignmentNode ) : Boolean = {
   val targetTree = assignmentNode.getTarget().getTree

   val nameOpt =
     targetTree match {
       case varTree : VariableTree   => Some( varTree.getName )
       case idTree  : IdentifierTree => Some( idTree.getName  )
       case _ => None
     }

   nameOpt.map( name =>
     InferenceMain.DetachedVarSymbols
       .find( name.toString.startsWith _ )
       .isDefined
   ).getOrElse(false)
 }

  override def visitAssignment( assignmentNode : AssignmentNode,
                                transferInput  : TransferInput[CFValue, CFStore]) : TransferResult[CFValue,CFStore] = {

    val lhs  = assignmentNode.getTarget();
    val rhs  = assignmentNode.getExpression();
    val store    = transferInput.getRegularStore()
    val rhsValue = transferInput.getValueOfSubNode(rhs)

    if (assignmentNode.getTree.isInstanceOf[VariableTree] &&
        assignmentNode.getTree.asInstanceOf[VariableTree].getInitializer() != null &&
        (assignmentNode.getTarget.isInstanceOf[LocalVariableNode] ||
            assignmentNode.getTarget.isInstanceOf[FieldAccessNode])) {

        // Add declarations with initializers to the store.
        // This is needed to trigger a merge refinement variable creation when two stores are merged:
        // String a = null; // Add VarAnno to store
        // if ( ? ) {
        //   a = ""; // Add RefVar to store
        // }
        // // merge RefVar will be created only if VarAnno and RefVar are both in store
        // a.toString()

        val typeFactory    = analysis.getFactory.asInstanceOf[InferenceAnnotatedTypeFactory[_]]
        val atm = typeFactory.getAnnotatedType(assignmentNode.getTree)
        val result = analysis.createAbstractValue(atm)
        store.updateForAssignment(lhs, result)  // TODO: Field
        new RegularTransferResult[CFValue, CFStore](finishValue(result, store), store);

    } else if( assignmentNode.getTarget.getTree.getKind == Tree.Kind.IDENTIFIER &&
        !assignmentNode.getTree.isInstanceOf[CompoundAssignmentTree]     &&
        !assignmentNode.getTree.isInstanceOf[UnaryTree] &&
        !variableIsDetached(assignmentNode) ) {

      //TODO TRAN1: What about compound assignments?
      // Add a refinement variable to store.
      val assignmentTree = assignmentNode.getTree.asInstanceOf[AssignmentTree]
      val typeFactory    = analysis.getFactory.asInstanceOf[InferenceAnnotatedTypeFactory[_]]
      val atm = typeFactory.getAnnotatedType(assignmentTree)
      if (InferenceMain.getRealChecker.needsAnnotation(atm)) {
        println("Create new refinement variable " + assignmentNode.toString)

        // TODO: This is another place that makes curTreesRefVar functionally important and so should not be a "cache" anymore.
        // The other being inferenceAnnotatedTypeFactory.
        if (slotMgr.curtreesRefVar.contains(assignmentTree)) {
          // RefVar already exists
          val anno = slotMgr.curtreesRefVar.get(assignmentTree).get.getAnnotation
          atm.clearAnnotations()
          atm.addAnnotation(anno)
        } else {
          // Create new RefVar
          val astPathToNode = InferenceUtils.getASTPathToNode(typeFactory, assignmentTree.getExpression)
          val astPathStr =
            if( astPathToNode != null ){
              InferenceUtils.convertASTPathToAFUFormat(astPathToNode)
            } else {
              null //TODO: REPORT THIS
            }
          val anno = slotMgr.createRefinementVariableAnnotation( typeFactory, assignmentTree, astPathStr, false )
          atm.clearAnnotations()
          atm.addAnnotation(anno)

          //TODO TRAN2: Why do we both use the TREECACHE FROM the InferenceAnnotatedTypeFactory and SLOT MANAGER
          slotMgr.addTreeToRefVar(assignmentTree, anno)

          //In order to correctly apply constraints for the actual declaration we need to make sure
          //we can match the identifier tree to this anno, see AnnotatedTypeFactory#annotatedImplicitWithFlow and
          //SlotManager#replaceWithRefVar
          slotMgr.addTreeToRefVar(lhs.getTree, anno)
        }

        val result = analysis.createAbstractValue(atm)
        store.updateForAssignment(lhs, result)
        new RegularTransferResult[CFValue, CFStore](finishValue(result, store), store);

      } else {
        super.visitAssignment(assignmentNode, transferInput)
      }
    } else {
      super.visitAssignment(assignmentNode, transferInput)
    }
  }

  override def initialStore(underlyingAST : UnderlyingAST, parameters : java.util.List[LocalVariableNode]) = {

    //For methods, force this method to first get the type of all methodTrees of a class in order to correctly apply
    //variable positions for the method parameters.  Otherwise, when the super of this method gets the
    //types of the methods parameters, without visiting the method itself, we end up visiting them as variables
    //which causes the InferenceTreeAnnotator to label them as LocalVariables rather than method parameters.
    if ( underlyingAST.getKind() == Kind.METHOD ) {
      val methodAST = underlyingAST.asInstanceOf[CFGMethod]

      val factory = analysis.getFactory()
      val classTree = TreeUtils.enclosingClass( analysis.getFactory().getPath(methodAST.getMethod) )
      val allMethods = classTree.getMembers.filter( _.getKind == Tree.Kind.METHOD )
      allMethods.foreach( factory.getAnnotatedType _ )
    }

    super.initialStore(underlyingAST, parameters)
  }

}

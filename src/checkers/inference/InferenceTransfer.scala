package checkers.inference

import checkers.flow._
import dataflow.cfg.node._
import dataflow.analysis.{RegularTransferResult, TransferResult, TransferInput}
import com.sun.source.tree._

import InferenceMain.slotMgr
import javacutils.{TreeUtils, AnnotationUtils}
import scala.collection.JavaConversions._
import dataflow.cfg.UnderlyingAST
import dataflow.cfg.UnderlyingAST.{CFGMethod, Kind}
import javax.lang.model.`type`.TypeMirror
import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.Tree
import annotator.find._

class  InferenceTransfer(analysis : CFAbstractAnalysis[CFValue, CFStore, CFTransfer]) extends CFTransfer(analysis) {

  override def visitAssignment( assignmentNode : AssignmentNode,
                                transferInput  : TransferInput[CFValue, CFStore]) : TransferResult[CFValue,CFStore] = {

    val lhs  = assignmentNode.getTarget();
    val rhs  = assignmentNode.getExpression();
    val store    = transferInput.getRegularStore()
    val rhsValue = transferInput.getValueOfSubNode(rhs)

    if( assignmentNode.getTarget.getTree.getKind == Tree.Kind.IDENTIFIER &&
        !assignmentNode.getTree.isInstanceOf[CompoundAssignmentTree]     &&
        !lhs.isInstanceOf[FieldAccessNode] ) {
      println("Create new refinement variable " + assignmentNode.toString)

      //TODO: What about compound assignments?
      val assignmentTree = assignmentNode.getTree.asInstanceOf[AssignmentTree]
      val typeFactory    = analysis.getFactory.asInstanceOf[InferenceAnnotatedTypeFactory[_]]

      val atm = typeFactory.getAnnotatedType(assignmentTree)
      if ( InferenceMain.getRealChecker.needsAnnotation(atm) ) {
        val astPathStr = if (!(assignmentNode.getTree.isInstanceOf[UnaryTree])) {
          InferenceUtils.convertASTPathToAFUFormat(InferenceUtils.getASTPathToNode(typeFactory, assignmentTree.getExpression))
        } else {
          //TODO: Handle i++, I believe it should just generate a new refinment variable but think if there is any special casing
          null
        }

        val anno = slotMgr.createRefinementVariableAnnotation( typeFactory, assignmentTree, astPathStr )

        atm.clearAnnotations()
        atm.addAnnotation(anno)

        //TODO: Why do we both use the TREECACHE FROM the InferenceAnnotatedTypeFactory and SLOT MANAGER
        slotMgr.addTreeToRefVar(assignmentTree, anno)

        //In order to correctly apply constraints for the actual declaration we need to make sure
        //we can match the identifier tree to this anno, see AnnotatedTypeFactory#annotatedImplicitWithFlow and
        //SlotManager#replaceWithRefVar
        slotMgr.addTreeToRefVar(lhs.getTree, anno)

        store.updateForAssignment(lhs, new CFValue(analysis, atm))

        new RegularTransferResult[CFValue, CFStore](finishValue(rhsValue, store), store);
      } else {
        super.visitAssignment(assignmentNode, transferInput)
      }
    } else {
      super.visitAssignment(assignmentNode, transferInput)
    }
  }


  //TODO: Disable this and see if the character issue still happens
  override def initialStore(underlyingAST : UnderlyingAST, parameters : java.util.List[LocalVariableNode]) = {

    //For methods, force this method to first get the type of all methodTrees of a class in order to correctly apply
    //variable positions for the method parameters.  Otherwise, when the super of this method gets the
    //types of the methods parameters, without visiting the method itself, we end up calling them
    //local variables rather than parameters
    if ( underlyingAST.getKind() == Kind.METHOD ) {
      val methodAST = underlyingAST.asInstanceOf[CFGMethod]

      val factory = analysis.getFactory()
      val classTree = TreeUtils.enclosingClass( analysis.getFactory().getPath(methodAST.getMethod) )
      val allMethods = classTree.getMembers.filter( _.getKind == Tree.Kind.METHOD )
      allMethods.foreach( factory.getAnnotatedType _ )
    }

    super.initialStore(underlyingAST, parameters)
  }

  //TODO: Kludge to get around isValidTypes problem
  override def getValueWithSameAnnotations(typeMirror : TypeMirror, annotatedValue : CFValue) = {
    val factory = analysis.getFactory.asInstanceOf[InferenceAnnotatedTypeFactory[_]]
    val at = factory.toAnnotatedType(typeMirror);
    if(annotatedValue != null) {
      at.replaceAnnotations(annotatedValue.getType().getAnnotations());
    }

    analysis.createAbstractValue(at);
  }
}

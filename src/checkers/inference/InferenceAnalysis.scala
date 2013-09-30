package checkers.inference

import checkers.flow.CFAnalysis
import checkers.types.AbstractBasicAnnotatedTypeFactory
import java.util.List

import javacutils.AnnotationUtils
import javacutils.Pair

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement

import checkers.basetype.BaseTypeChecker
import checkers.flow.CFAbstractAnalysis
import checkers.flow.CFAnalysis
import checkers.flow.CFStore
import checkers.flow.CFTransfer
import checkers.flow.CFValue
import checkers.types.AbstractBasicAnnotatedTypeFactory
import checkers.types.AnnotatedTypeMirror
import javax.lang.model.element.AnnotationMirror
import checkers.util.AnnotatedTypes
import dataflow.cfg.block.Block
import dataflow.analysis.Store
import dataflow.cfg.node.Node
import dataflow.cfg.node.TernaryExpressionNode
import dataflow.analysis.FlowExpressions
import dataflow.analysis.FlowExpressions.ArrayAccess

/**
 * InferenceAnalysis specifies InferenceValue and InferenceStore should be used by Dataflow.
 */

class InferenceAnalysis[Checker <: BaseTypeChecker[_]](
        factory: AbstractBasicAnnotatedTypeFactory[Checker, CFValue, CFStore, CFTransfer, CFAnalysis],
        env: ProcessingEnvironment, checker: Checker, fieldValues: List[Pair[VariableElement, CFValue]])
    extends CFAnalysis(factory, env, checker, fieldValues) {

  override def defaultCreateAbstractValue(analysis: CFAbstractAnalysis[CFValue, _, _], aType: AnnotatedTypeMirror) : CFValue = {
    if (!AnnotatedTypes.isValidType(qualifierHierarchy, aType)) {
            // If the type is not valid, we return null, which is the same as
            // 'no information'.
      println("InferenceAnalysis returning null for invalid type: " + aType)
      return null;
    }
    new InferenceValue(analysis, aType);
  }

  override def createEmptyStore(sequentialSemantics : Boolean) = {
    InferenceStore(this, sequentialSemantics)
  }

  override def createCopiedStore(s :CFStore) = {
    InferenceStore(this, s)
  }
}

trait InferenceStore extends CFStore {
  override def removeConflicting(fieldAccess: FlowExpressions.FieldAccess ,  value: CFValue) {
    // Don't want to remove any conflicts. Keep the refinement variables created on declaration.
  }
}

object InferenceStore {
  def apply(analysis: InferenceAnalysis[_], sequentialSemantics: Boolean) = new CFStore(analysis, sequentialSemantics) with InferenceStore
  def apply(analysis: InferenceAnalysis[_], other: CFStore) = new CFStore(analysis, other) with InferenceStore
}

/**
 * Inference value creates merge refinement variables on a least upper bounds between
 * two difference RefVars and VarAnnos, that have not been merged before.
 *
 * This happens when Stores are merged at the end of block processing in the dataflow framework.
 *
 */

class InferenceValue(analysis: CFAbstractAnalysis[CFValue,_,_], aType : AnnotatedTypeMirror)
    extends CFValue(analysis, aType) {

  override def leastUpperBound(other: CFValue) : CFValue = {
    if (other == null) {
      return this
    }

    val otherType = other.getType()
    val thisType = getType()
    val lubAnnotatedType = leastUpperBound(thisType, otherType)

    // Create merged refinement variable during flow.
    val slot1 = InferenceMain.slotMgr.extractSlot(thisType)
    val slot2 = InferenceMain.slotMgr.extractSlot(otherType)

    def createMergeVar(var1: AbstractVariable, var2: AbstractVariable) = {
      if (var1 == var2) {
        var1
      } else if (var1.mergedTo.intersect(var2.mergedTo).size > 0) {
        // Have these been merged before?
        var1.mergedTo.intersect(var2.mergedTo).head
      } else if (var1.isMergedTo(var2)) {
        var2
      } else if (var2.isMergedTo(var1)) {
        var1
      } else {
        // TODO: DAM This most definitely has the variable wrong position.
        // var1 is arbitrary. Both have wrong position.
        val merge = InferenceMain.slotMgr.createRefinementVariableAsDuplicate(if (var1.isInstanceOf[RefinementVariable]) { var1 } else { var2 })
        val mergeRefVar = InferenceMain.slotMgr.extractSlot(merge).asInstanceOf[RefinementVariable]
        InferenceMain.inferenceChecker.mergeRefinementConstraintCache +=
            ((mergeRefVar, scala.List(new SubtypeConstraint(var1, mergeRefVar), new SubtypeConstraint(var2, mergeRefVar))))
        var1.mergedTo += mergeRefVar
        var2.mergedTo += mergeRefVar
        mergeRefVar
      }
    }

    val newAnno = (slot1, slot2) match {
      case (var1, var2) if (var1 == var2) => var1
      case (var1 : RefinementVariable, var2: AbstractVariable) => createMergeVar(var1, var2)
      case (var1 : AbstractVariable, var2: RefinementVariable) => createMergeVar(var1, var2)
      case _ => throw new RuntimeException("Attempted to create merge variable from unsupported slot combination: " + slot1 + " and " + slot2)
    }
    lubAnnotatedType.replaceAnnotation(newAnno.getAnnotation())

    return analysis.createAbstractValue(lubAnnotatedType);
  }
}
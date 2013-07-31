package checkers.inference

import checkers.types.AnnotatedTypeMirror
import checkers.inference.quals.VarAnnot
import javax.lang.model.element.AnnotationMirror
import scala.collection.mutable.ListBuffer
import checkers.inference.InferenceMain._
import checkers.types.AnnotatedTypeMirror._
import checkers.inference.util.TraversalUtil

object InferenceAnnotationUtils {

  /**
   * Need to find a better location for this
   * @param atv1
   * @param atv2
   */
  def traverseAndSubtype (  atv1 : AnnotatedTypeMirror, atv2 : AnnotatedTypeMirror ) = {
    val subtype = (atm1 : AnnotatedTypeMirror, atm2 : AnnotatedTypeMirror) => {

      val subOpt = Option( atm1.getAnnotation(classOf[VarAnnot]) )
      val supOpt = Option( atm2.getAnnotation(classOf[VarAnnot]) )

      (subOpt, supOpt) match {
        case (Some(sub : AnnotationMirror), Some(sup : AnnotationMirror)) =>
          InferenceMain.constraintMgr.addSubtypeConstraint( sub, sup )
        case _ =>
      }
    }

    TraversalUtil.traverseDeclTypes( atv1, atv2, subtype )
  }
}

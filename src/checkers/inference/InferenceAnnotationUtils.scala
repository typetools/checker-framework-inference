package checkers.inference

import checkers.types.AnnotatedTypeMirror
import checkers.inference.quals.VarAnnot
import javax.lang.model.element.AnnotationMirror
import checkers.inference.util.TraversalUtil

object InferenceAnnotationUtils {

  /**
   * //TODO: THIS SHOULD PROBABLY JUST USE THE SUBTYPING VISITOR, BUT THE SUBTYPING VISITOR ISN'T YET DESIGNED TO SUPPORT THIS
   * Need to find a better location for this
   * @param atv1 - subtype
   * @param atv2 - supertype
   */
  def traverseAndSubtype (  atv1 : AnnotatedTypeMirror, atv2 : AnnotatedTypeMirror ) = {
    val subtype = (atm1 : AnnotatedTypeMirror, atm2 : AnnotatedTypeMirror) => {

      //TODO: Do an Option(slotManager. extractSlot )
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

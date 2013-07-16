package checkers.inference.util

import checkers.types.AnnotatedTypeMirror.{AnnotatedNullType, AnnotatedDeclaredType, AnnotatedTypeVariable}
import checkers.inference.{Slot, InferenceMain}
import javax.lang.model.element.{AnnotationMirror, TypeParameterElement}
import checkers.types.AnnotatedTypeMirror
import checkers.inference.quals.VarAnnot

object TraversalUtil {

  /**
   * Given two AnnotatedTypeVariables with the same structure (i.e. one is assignable to the other).
   * This does not call func on the top-level AnnotatedTypeVariables; you must do this manually
   * before or after calling traverseTypeVariables.
   *
   * @param atv1
   * @param atv2
   */                                //sub to super?//
  def traverseTypeVariables( atv1 : AnnotatedTypeMirror, atv2 : AnnotatedTypeMirror)  {
    var i = 0
    val func = (atm1 : AnnotatedTypeMirror, atm2 : AnnotatedTypeMirror) => {
      println("\nIter " + i)
      println("ATM1")
      println(atm1 + "\n")

      println("ATM2")
      println(atm2 + "\n")
      i = i + 1

      val subOpt = Option( atm1.getAnnotation(classOf[VarAnnot]) )
      val supOpt = Option( atm2.getAnnotation(classOf[VarAnnot]) )

      (subOpt, supOpt) match {
        case (Some(sub : AnnotationMirror), Some(sup : AnnotationMirror)) =>
          InferenceMain.constraintMgr.addSubtypeConstraint( sub, sup )
        case _ =>
      }

      println( atm1.getAnnotation(classOf[VarAnnot]) + " <: " + atm2.getAnnotation(classOf[VarAnnot]) )
    }
    val tupledFunc = Function.tupled( func )

    /**
     * What trees can ATVs have in them?
     */
    def traverseTypes( atms : (AnnotatedTypeMirror, AnnotatedTypeMirror) ) {
      /*AnnotatedReferenceType
      AnnotatedDeclaredType
      AnnotatedExecutableType
      AnnotatedArrayType
      AnnotatedTypeVariable
      AnnotatedNoType
      AnnotatedNullType
      AnnotatedPrimitiveType
      AnnotatedWildcardType
      AnnotatedIntersectionType [Intersection Types?]*/

      atms match {
        case ( ant : AnnotatedNullType, _ ) =>
          println(" Doing nothing boss ")

        case ( _, ant : AnnotatedNullType ) =>
          println(" Doing nothing boss ")
          //do nothing

        case ( innerAtv1 : AnnotatedTypeVariable, innerAtv2 : AnnotatedTypeVariable ) =>
          val uppers  = ( innerAtv1.getUpperBound, innerAtv2.getUpperBound )
          val lowers  = ( innerAtv1.getLowerBound, innerAtv2.getLowerBound )
          tupledFunc( uppers )
          tupledFunc( lowers )

          traverseTypes( uppers )
          traverseTypes( lowers )

        case ( innerAtv1 : AnnotatedTypeVariable, innerAtv2 : AnnotatedDeclaredType ) =>
          val uppers = ( innerAtv1.getUpperBound, innerAtv2 )
          val lowers = ( innerAtv1.getLowerBound, innerAtv2 )
          tupledFunc( uppers )
          tupledFunc( lowers )

          traverseTypes( innerAtv1.getUpperBound, innerAtv2 )

        case ( innerAtv1 : AnnotatedDeclaredType, innerAtv2 : AnnotatedTypeVariable ) =>
          val uppers = ( innerAtv2, innerAtv2.getUpperBound )
          val lowers = ( innerAtv2, innerAtv2.getLowerBound )
          tupledFunc( uppers )
          tupledFunc( lowers )

          traverseTypes( innerAtv1, innerAtv2.getLowerBound )

        case ( innerAtv1 : AnnotatedDeclaredType, innerAtv2 : AnnotatedDeclaredType ) =>
          func( innerAtv1, innerAtv2 )


        case other =>
          println("OTHER! " + other._1.getClass + ", " + other._2.getClass )
          tupledFunc(other)
          println("END OTHER")

      }
    }

    traverseTypes( (atv1, atv2) )



  }

}

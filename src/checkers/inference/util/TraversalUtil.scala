package checkers.inference.util

import checkers.types.AnnotatedTypeMirror._
import checkers.inference.{AbstractVariable, Slot, InferenceMain}
import javax.lang.model.element.{AnnotationMirror, TypeParameterElement}
import checkers.types.AnnotatedTypeMirror
import checkers.inference.quals.VarAnnot
import scala.collection.mutable.ListBuffer
import checkers.inference.InferenceMain._
import scala.Some
import scala.Some
import scala.Some
import scala.Some
import scala.Some

object TraversalUtil {

  /**
   * Given two AnnotatedTypeVariables with the same structure (i.e. one is assignable to the other)
   * traverse the types and apply func to types that should correspond to each other
   * This method is currently designed to visit only types you would find in a type declaration
   *
   * @param atv1
   * @param atv2
   */                                //sub to super?//
  def traverseDeclTypes( atv1 : AnnotatedTypeMirror, atv2 : AnnotatedTypeMirror, func : (AnnotatedTypeMirror, AnnotatedTypeMirror) => Unit)  {
    val tupledFunc = Function.tupled( func )

    /**
     * What trees can ATVs have in them?
     */
    def traverseTypes( atms : (AnnotatedTypeMirror, AnnotatedTypeMirror) ) {
      /*AnnotatedReferenceType
      AnnotatedExecutableType
      AnnotatedArrayType
      AnnotatedNoType
      AnnotatedNullType
      AnnotatedPrimitiveType
      AnnotatedWildcardType
      AnnotatedIntersectionType [Intersection Types?]*/

      atms match {
        case ( ant : AnnotatedNullType, _ ) =>
          //do nothing

        case ( _, ant : AnnotatedNullType ) =>
          //do nothing

        //TODO: Need to handle annotated array types

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
          tupledFunc(other)
      }
    }

    traverseTypes( (atv1, atv2) )
  }

  //TODO: Call traverse declared type?
  /*private def traverseFieldType(atm : AnnotatedTypeMirror, func : (AnnotatedTypeMirror => Unit), traverseBounds : Boolean) {
    import scala.collection.JavaConversions._

    Option( atm ).map(

      _ match {

        case aat : AnnotatedArrayType =>
          func(aat)
          traverseFieldType( aat.getComponentType )

        case awt : AnnotatedWildcardType =>
          func(awt)
          traverseFieldType( awt.getSuperBound )
          traverseFieldType( awt.getExtendsBound )

        case atv : AnnotatedTypeVariable =>
          if( traverseBounds ) {
            func( atv.getEffectiveLowerBound )
            traverseType( atv.getUpperBound )
          } else {
            func( atv )
          }

        case adt : AnnotatedDeclaredType =>
          func(adt)
          adt.getTypeArguments.foreach( (typeArg : AnnotatedTypeMirror) => traverseFieldType( typeArg ) )

        case apt: AnnotatedPrimitiveType =>
          func( apt )

        case ait : AnnotatedIntersectionType =>
          func( ait )

        case atm : AnnotatedTypeMirror if atm.isInstanceOf[AnnotatedNoType] | atm.isInstanceOf[AnnotatedNullType] =>
          func( atm )
        //TODO JB: Anything todo here?

        case atm : AnnotatedTypeMirror =>
          throw new RuntimeException("Unhandled annotated type mirror " + atm.getClass.getCanonicalName)
      }

    )
  } */


}

package checkers.inference.util

import checkers.types.AnnotatedTypeMirror
import checkers.inference.{InferenceAnnotatedTypeFactory, InferenceMain, Slot}
import scala.collection.mutable.ListBuffer
import checkers.inference.InferenceMain._
import checkers.types.AnnotatedTypeMirror._
import java.util.{LinkedHashMap => JLinkedMap}
import javax.lang.model.element.{AnnotationMirror, TypeParameterElement}

object SlotUtil {

  /**
   * Upper Bounds of type parameters may have multiple parameters (as they have a tree associated with them),
   * Lower Bounds do not.  When we visit a type param we first visit the upper bounds (the entire tree) then
   * the lower bounds.  This method takes a  List[List[All variables in a single upper bound]] and a corresponding
   * List[lower bounds] and orders them so that all variables in both lists are in traversal order.
   *
   * e.g.
   * assume @X => @VarAnnot(X)
   * we have a class:
   * class MyClass< @3 T1 extends @1 List< @2 String>, @5 T2 extends @4 Object, @8 T3 extends @6 Set< @7 String> >
   *
   * val upperBounds = List(List(@1, @2), List(@4), List( @6, @7 ))
   * val lowerBounds = List(@3, @5, @8 )
   *
   * A call:
   * interlaceTypeParamBounds( upperBounds, lowerBounds )
   *
   * would equal:
   * List(@1, @2, @3, @4, @5, @6, @7, @8)
   *
   * Note: Some times we want to interlace type arguments and lower bounds.  In this case the type arguments
   * must be subtypes of the lowerBounds (and therefore have the same number of variables).  Type args are
   * just passed to the upperBounds.
   *
   * @param upperBounds
   * @param lowerBounds
   * @return interlaced upper and lower bounds
   */
  def interlaceTypeParamBounds[SLOT_TYPE <: Slot]( upperBounds : List[List[SLOT_TYPE]], lowerBounds : List[SLOT_TYPE] ) : List[SLOT_TYPE] = {
    assert( upperBounds.length == lowerBounds.length )
    val slotBuffer = new ListBuffer[SLOT_TYPE]
    for( (upperBound, lowerBound) <- upperBounds.zip( lowerBounds ) ) {
      slotBuffer ++= upperBound
      slotBuffer +=  lowerBound
    }
    slotBuffer.toList
  }


  /**
   * Given the type parameters:
   *  <S extends T>
   *  <T extends U>
   *  <U extends @L1 Map<@L2 String, @L3 String>>
   *
   * and a use:
   * @S1 S
   *
   * This method called on S will return:
   * @S1 Map<@L2 String, @L3 String>
   *
   * @param atv
   * @return
   */
  def typeUseToUpperBound( atv : AnnotatedTypeVariable ) : Either[AnnotatedIntersectionType, AnnotatedDeclaredType] = {
    def getUpperBound( atv : AnnotatedTypeVariable ) :  AnnotatedTypeMirror = {
      val typeParamElement = atv.getUnderlyingType.asElement().asInstanceOf[TypeParameterElement]
      val bounds = InferenceMain.inferenceChecker.getTypeParamBounds( typeParamElement )
      val upperBound = bounds._1.asInstanceOf[AnnotatedTypeVariable].getEffectiveUpperBound
      return upperBound
    }

    val primaryAnno = if( atv.getAnnotations.isEmpty ) None else Some( slotMgr.extractSlot( atv ).getAnnotation() )

    var upperBound : AnnotatedTypeMirror = atv
    Iterator.continually({ upperBound = getUpperBound( upperBound.asInstanceOf[AnnotatedTypeVariable] ); upperBound })
      .find( bound => !bound.isInstanceOf[AnnotatedTypeVariable] )
      .get

    primaryAnno.map( primAnno => {
      upperBound.clearAnnotations()
      upperBound.addAnnotation( primAnno )
    })

    upperBound match {
      case ubAtd : AnnotatedDeclaredType     => Right( ubAtd )
      case ubInt : AnnotatedIntersectionType => Left( ubInt )
      case _ => throw new RuntimeException("Unhandled type use upper bound: " + upperBound )
    }
  }

  def wildcardToUpperBound( wtc : AnnotatedWildcardType ) : Either[AnnotatedIntersectionType, AnnotatedDeclaredType] = {

    val extendsBound = wtc.getExtendsBound
    val primaryAnno = slotMgr.extractSlot( extendsBound ).getAnnotation()

    def clearAndAdd[ATM <: AnnotatedTypeMirror]( atm : ATM, primaryAnno : AnnotationMirror ) : ATM = {
      atm.clearAnnotations()
      atm.addAnnotation( primaryAnno )
      atm
    }

    val upperBound = extendsBound match {
      case extAtd : AnnotatedDeclaredType     => Right( clearAndAdd( extAtd, primaryAnno ) )
      case extInt : AnnotatedIntersectionType => Left(  clearAndAdd( extInt, primaryAnno ) )
      case extAtv : AnnotatedTypeVariable     => typeUseToUpperBound( clearAndAdd( extAtv, primaryAnno ) )
      case _ => throw new RuntimeException("Unhandled type use upper bound: " + extendsBound )
    }

    return upperBound
  }
}

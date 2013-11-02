package checkers.inference.util

import checkers.inference._
import checkers.types.visitors.AnnotatedTypeVisitor

import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror._
import javax.lang.model.`type`.TypeKind
import javax.lang.model.element.TypeParameterElement
import scala.collection.mutable.{HashSet => MutHashSet, HashMap => MutHashMap }
import scala.collection.JavaConversions._
import SlotUtil.typeUseToUpperBound
import checkers.util.AnnotatedTypes
import `type`.DeclaredType
import javacutils.AnnotationUtils

case class SubtypingResult (
  val subtypes    : List[(Slot, Slot)],
  val equality    : Set[(Slot, Slot)],
  val lowerBounds : Set[(Slot, Slot)]
) {

  def merge( other : SubtypingResult ) = {
    SubtypingResult( subtypes    ++ other.subtypes,
                     equality    ++ other.equality,
                     lowerBounds ++ other.lowerBounds )
  }

  def addTo( conMan : ConstraintManager ) = {
    subtypes.map({
      case ( supertype, subtype ) => conMan.addSubtypeConstraint( subtype, supertype )
    })
    equality.map({
      case ( left, right ) => conMan.addEqualityConstraint( left, right )
    })
    lowerBounds.map({
      case ( bounded, lowerBound ) => conMan.addSubtypeConstraint( bounded, lowerBound )
    })
  }
}

object SubtypingVisitor {

  def subtype( supertype : AnnotatedTypeMirror, subtype : AnnotatedTypeMirror,
               slotMgr : SlotManager, infChecker : InferenceChecker,
               infAtf : InferenceAnnotatedTypeFactory ) : SubtypingResult = {
    val visitor = new SubtypingVisitor( slotMgr, infChecker, infAtf )
    visitor.visitTopLevel( supertype, subtype )
    return visitor.getResult
  }

  val excludedTypes = List( TypeKind.WILDCARD )
  def isExcluded( typeKind : TypeKind )       : Boolean = excludedTypes.contains( typeKind )
  def isExcluded( atm : AnnotatedTypeMirror ) : Boolean = {
    //seems like this could just be taken care of by type kind
    isExcluded( atm.getKind() ) || atm.isInstanceOf[AnnotatedIntersectionType] || atm.isInstanceOf[AnnotatedUnionType]
  }

}

import SubtypingVisitor._

class SubtypingVisitor( val slotMgr    : SlotManager,
                        val infChecker : InferenceChecker,
                        val infAtf : InferenceAnnotatedTypeFactory ) {

  val subtypes = new ListBuffer[(Slot, Slot)]
  val equality = new MutHashSet[(Slot, Slot)]
  val lowerBounds = new MutHashSet[(Slot, Slot)]

  /**
   * The top-level visit method should only be called on covariant types (usually just the top-level types)
   *
   * @param supertype
   * @param subtype
   */
  def visitTopLevel(supertype : AnnotatedTypeMirror, subtype : AnnotatedTypeMirror) {
    try {

      if( isExcluded( supertype ) || isExcluded( subtype ) ) {
        //handleWildcard( supertype, subtype )
      } else {
        ( supertype, subtype ) match {

          case ( superAtv : AnnotatedTypeVariable, subAtv : AnnotatedTypeVariable ) =>
            val superBounds = atvToBounds( superAtv )
            val subBounds   = atvToBounds( subAtv   )

            val ( superUpper, subUpper ) =
              if( superAtv == subAtv ) { //Happens when we access fields or methods inside other members
                assert( superBounds._1 == subBounds._1,
                        "The same ATVs have different bounds!" + "( super= " + superAtv + ", sub=" + subAtv + " )" )
                ( superBounds._1, subBounds._1 )
              } else {
                val supUpper = typeUseToUpperBound( superAtv )
                (supUpper, asSuper( typeUseToUpperBound( subAtv ), supUpper ) )
              }

            addLowerBound( subUpper,     superBounds._2 )
            addLowerBound( subBounds._2, superBounds._2 )

            visitTopLevel( superUpper, subUpper )

          case ( _, subAtv : AnnotatedTypeVariable ) =>
            val subAtvUb = typeUseToUpperBound( subAtv )
            visitTopLevel( supertype, subAtvUb )

          //TODO: THese cases I think are missing some equality constraints
          case ( superAtv : AnnotatedTypeVariable, notAtv : AnnotatedTypeMirror )   =>
            val bounds = atvToBounds( superAtv )
            addLowerBound( notAtv, bounds._2 )

            val superUpper = typeUseToUpperBound( superAtv )
            visitTopLevel( superUpper, notAtv )

          case ( superAtd : AnnotatedDeclaredType, subAtd : AnnotatedDeclaredType ) =>
            val subAsSuper = asSuper( supertype, subtype )
            addSubtypes( supertype, subAsSuper.asInstanceOf[AnnotatedDeclaredType] )
            visitTypeArgs( superAtd, subAsSuper.asInstanceOf[AnnotatedDeclaredType], new MutHashSet[(AnnotatedTypeMirror, AnnotatedTypeMirror)] )

          case ( _, annoNullType : AnnotatedNullType ) =>
            addSubtypes( supertype, annoNullType )

          //An array is an Object , that should be the only case where this can occur
          case ( superAtd : AnnotatedDeclaredType, subAtd : AnnotatedArrayType ) =>
            addSubtypes( supertype, subAtd )

          case ( superArrAtm : AnnotatedArrayType, subArrAtm :AnnotatedArrayType ) =>
            val subAsAsuper = asSuper( superArrAtm, subArrAtm ).asInstanceOf[AnnotatedArrayType]
            addSubtypes( supertype, subAsAsuper )
            visitTopLevel( superArrAtm.getComponentType, subArrAtm.getComponentType )  //Array components are covariant

          case ( leftApt : AnnotatedPrimitiveType, _  ) => addSubtypes( supertype, subtype )
          case ( _, rightApt : AnnotatedPrimitiveType ) => addSubtypes( supertype, subtype )

          case ( annotatedArrayType : AnnotatedArrayType, _ ) =>
              println("TODO: Unhandled case, varArgs. ( super=" + supertype + " , sub=" + subtype + ") ")

          case _ =>
            //UNIONS and INTERSECTIONS will do this
            throw new RuntimeException("Unhandled top level case: ( super=" + supertype + " , sub=" + subtype + ") ")
        }
      }
    } catch {
      case ste : StackOverflowError =>
        throw new RuntimeException("Stack overflow at: " + "\n\nsuperAtd:\n" + supertype + "\n\nsubAtd:\n" + subtype )
    }
  }

  private def visitTypeArgs( superAtd : AnnotatedDeclaredType, subAtd : AnnotatedDeclaredType, visited : MutHashSet[(AnnotatedTypeMirror, AnnotatedTypeMirror)]) {
    val superTypeParams = superAtd.getTypeArguments
    val subTypeParams   = subAtd.getTypeArguments

    if( visited.contains( (superAtd, subAtd) ) ) {
      return
    }
    visited += ( superAtd -> subAtd )

    if( superTypeParams.find( _.isInstanceOf[AnnotatedWildcardType]).isDefined && superTypeParams.size != subTypeParams.size ) {
      println( "TODO: We can have raw types assigned to <?> ( super=" + superAtd + ", subtype=" + subAtd + " )" );
      return
    }

    if ( superTypeParams.size == 0 && subTypeParams.size > 0 ) {
      println("TODO: Left side is raw! ( super=" + superAtd + ", subtype=" + subAtd + " )");
      return
    } else if( superTypeParams.size > 0 && subTypeParams.size == 0 ) {
      println("TODO: Right side is raw! ( super=" + superAtd + ", subtype=" + subAtd + " ). Can happen ith suppress warnings.");
    } else {
      assert (superTypeParams.size == subTypeParams.size,
              "Mismatching type argument list! super=( " + superAtd + " ) " + "sub=( " + subAtd + " )" )
    }

    superTypeParams.zip( subTypeParams )
      .foreach(
        _ match {
          case ( superAtm : AnnotatedTypeMirror, subAtm : AnnotatedTypeMirror ) if isExcluded( superAtm ) | isExcluded( subAtm ) =>
            //Do nothing if either type is a type we don't handle at the moment

          case ( awc : AnnotatedWildcardType, _ ) =>  //DO NOTHING FOR WILDCARDS FOR NOW
          case ( _, awc : AnnotatedWildcardType ) =>

          case ( superAtd : AnnotatedDeclaredType, subAtd : AnnotatedDeclaredType ) =>
            addEquality( superAtd, subAtd )

          case ( superAtv : AnnotatedTypeVariable, subAtv : AnnotatedTypeVariable ) =>
            //The only way this can happen NOT at the top level is in instances where subAtv type parameter
            //is the argument to superAtv and we have a Generic<superAtv> formal parameter, in this case we only
            //want to ensure that the primary annotations are equal ( not on the bounds but on the actual type which should be exact )
            //This is a type-use so it will need to change when generics are fixed
            addEquality( superAtv, subAtv )

          case ( superAtd : AnnotatedDeclaredType, subAtv : AnnotatedTypeVariable ) => //Twould be a type-use
            addEquality( superAtd, subAtv )
            val subAsUpper = typeUseToUpperBound( subAtv )
            val subAsSuper = asSuper( superAtd, subAsUpper ).asInstanceOf[AnnotatedDeclaredType]
            visitTypeArgs( superAtd, subAsSuper, visited )

          case ( superAtv : AnnotatedTypeVariable, notAtv : AnnotatedTypeMirror )   =>
            addEquality( superAtv, notAtv )
            val superAsUpper = typeUseToUpperBound( superAtv )
            val otherAsUpper = asSuper( superAsUpper, notAtv ).asInstanceOf[AnnotatedDeclaredType] //Could there be an int as an argument to S extends Integer (weird case))
            visitTypeArgs( superAsUpper, otherAsUpper, visited )

          case ( superArrAtm : AnnotatedArrayType, subArrAtm :AnnotatedArrayType ) =>
            addEquality( superArrAtm, subArrAtm )

          //An array is an Object , that should be the only case where this can occur
          case ( superAtd : AnnotatedDeclaredType, subArray : AnnotatedArrayType ) =>
            addEquality( superAtd, subArray )

          //case ( leftApt : AnnotatedPrimitiveType, other : AnnotatedTypeMirror  ) => addEquality( leftApt, other  )  //TODO: Can this happen?
          //case ( other : AnnotatedTypeMirror, rightApt : AnnotatedPrimitiveType ) => addEquality( other, rightApt )
          case( atm1 : AnnotatedTypeMirror, atm2 : AnnotatedTypeMirror ) =>
            throw new RuntimeException("Unhandled lower level case: ( super=" + atm1 + " , sub=" + atm2 + ") ")
      })
  }

  private def atvToBounds( atv : AnnotatedTypeVariable ) = {
    val typeParamElement = atv.getUnderlyingType.asElement().asInstanceOf[TypeParameterElement]
    val bounds = infChecker.getTypeParamBounds( typeParamElement )
    ( bounds._1.getEffectiveUpperBound, bounds._2 )
  }

  private def addEquality( atm1 : AnnotatedTypeMirror, atm2 : AnnotatedTypeMirror ) {
    val slots = ( slotMgr.extractSlot( atm1 ), slotMgr.extractSlot( atm2 ) )
    if( slots._1 != slots._2 ) {
      equality += slots
    }
  }

  private def addLowerBound( boundedAtm : AnnotatedTypeMirror, lowerBound : AnnotatedTypeMirror ) = {
    val slots = ( slotMgr.extractSlot( boundedAtm ), slotMgr.extractSlot( lowerBound ) )
    if( slots._1 != slots._2 ) {
      lowerBounds += slots
    }
  }

  private def addSubtypes( superAtm : AnnotatedTypeMirror, subAtm : AnnotatedTypeMirror ) {

    val slots = ( slotMgr.extractSlot( superAtm ), slotMgr.extractSlot( subAtm ) )
    if( slots._1 != slots._2 ) {
      subtypes += slots
    }
  }

  private def  asSuper[T <: AnnotatedTypeMirror] ( supertype : T, subtype : AnnotatedTypeMirror ) : T = {

    //TODO: Temporary kludge to get past the fact that @Anno instances don't have a direct super type of Annotation
    //TODO: This seems like a bug
/*    if( supertype.isInstanceOf[AnnotatedDeclaredType] ) {
      val asString =
        supertype.asInstanceOf[AnnotatedDeclaredType]
          .getUnderlyingType.asElement.toString
      if( asString.equals("java.lang.annotation.Annotation") ) {
        val newSub = AnnotatedTypes.deepCopy( supertype )
        newSub.clearAnnotations()
        newSub.addAnnotations( subtype.getAnnotations )
        return newSub
      }
    }*/

    val typeUtils = InferenceMain.inferenceChecker.getProcessingEnvironment.getTypeUtils
    val sup = AnnotatedTypes.asSuper( typeUtils, infAtf, subtype, supertype )
    sup.asInstanceOf[T]
  }

  def getResult = SubtypingResult( subtypes.toList, equality.toSet, lowerBounds.toSet )
}

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
import scala.Predef._
import scala.Right

object SubtypingResult {
  val empty = new SubtypingResult( List.empty[(Slot,Slot)],
                                   Set.empty[(Slot,Slot)],
                                   Set.empty[(Slot,Slot)],
                                   List.empty[(Slot,Slot)] )
}

case class SubtypingResult (
  val subtypes    : List[(Slot, Slot)],
  val equality    : Set[(Slot, Slot)],
  val lowerBounds : Set[(Slot, Slot)],

  //Kind of a hack, the set of equality constraints that represent inputs into a SubboardCall and the
  //total set of equalities aren't the same, need to keep track of which ones are which
  val methodInputs : List[(Slot, Slot)]
) {

  def merge( other : SubtypingResult ) = {
    SubtypingResult( subtypes    ++ other.subtypes,
                     equality    ++ other.equality,
                     lowerBounds ++ other.lowerBounds,
                     methodInputs ++ other.methodInputs )
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
   * Records subtype/equality relationships that are captured by subboard calls in Verigames.  Not all
   * subtypes/equalities that are implied by pseudo-assignments are captured in subboard calls ( hence the need
   * to use a separate list rather than just equality/subtypes ).  This list also preserves the order, so that
   * all slots are returned in order of increasing port numbers for a subboard call.
   */
  val methodInputs = new ListBuffer[(Slot, Slot)]

  /**
   * When we are visiting type-variables that do not represent parameter declaration (i.e. type-uses), then
   * we don't want to record nested equality constraints as inputs to a SubboardCall
   * e.g
   * void <T> method(T t)
   * When a type is passed to  <T>:
   *   e.g.  String in myObj.<String>method("str")
   *   we want to record all methodInputs (i.e. all constraints generated)
   * When a type is passed to T t:
   *   e.g. "str" in myObj.<String>method("str")
   *   we want to record only the constraints between the primary annotations
   */
  private var recordEqualsInputs = true

  /**
   * This is a hacky way to avoid generating methodInputs for type uses in method arguments
   */
  var typeUse : Boolean = false

  def clearResult() {
    subtypes.clear()
    equality.clear()
    lowerBounds.clear()
    methodInputs.clear()
  }

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
                //Convert type uses to their upper bound, if either is an AnnotatedIntersectionType return
                ( typeUseToUpperBound( superAtv ), typeUseToUpperBound( subAtv ) ) match {
                  case ( Right( superAtd : AnnotatedDeclaredType), Right( subAtd : AnnotatedDeclaredType ) ) =>
                    ( superAtd, asSuper( superAtd, subAtd ) )

                  case _ =>
                    return //CAREFUL: Exit point on AnnotatedIntersectionTypes

                }
              }

            addLowerBound( subUpper,     superBounds._2 )
            addLowerBound( subBounds._2, superBounds._2 )

            val originalRecEquals = recordEqualsInputs
            if( typeUse ) {
              recordEqualsInputs = false
            }
            visitTopLevel( superUpper, subUpper )
            recordEqualsInputs = originalRecEquals

          case ( _, subAtv : AnnotatedTypeVariable ) =>
            val subAtvUb = typeUseToUpperBound( subAtv )
            subAtvUb match {
              case Right( subAtdUb : AnnotatedDeclaredType ) =>
                visitTopLevel( supertype, subAtdUb )

              case _ => //DO NOTHING ON INTERSECTION TYPES
            }


          //TODO: THese cases I think are missing some equality constraints
          case ( superAtv : AnnotatedTypeVariable, notAtv : AnnotatedTypeMirror )   =>
            val bounds = atvToBounds( superAtv )
            addLowerBound( notAtv, bounds._2 )

            val superUpper = typeUseToUpperBound( superAtv )
            superUpper match {
              case Right( superAtdUb : AnnotatedDeclaredType ) =>
                val originalRecEquals = recordEqualsInputs
                if( typeUse ) {
                  recordEqualsInputs = false
                }
                visitTopLevel( superAtdUb, notAtv )
                recordEqualsInputs = originalRecEquals

              case _ => //DO NOTHING ON INTERSECTION TYPES
            }

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
        throw new RuntimeException("Stack overflow at: " + "\n\nsuperAtd:\n" + supertype + "\n\nsubAtd:\n" + subtype  )

      case exc : Throwable =>
        println( "Exception when comparing: " + "\n\nsuperAtd:\n" + supertype + "\n\nsubAtd:\n" + subtype + "\n" )
        exc.printStackTrace()
        //throw new RuntimeException("Exception when comparing: " + "\n\nsuperAtd:\n" + supertype + "\n\nsubAtd:\n" + subtype, exc )
        return
    }
  }

  private def visitTypeArgs( superAtd : AnnotatedDeclaredType, subAtd : AnnotatedDeclaredType, visited : MutHashSet[(AnnotatedTypeMirror, AnnotatedTypeMirror)]) {
    var superTypeParams = superAtd.getTypeArguments
    var subTypeParams   = subAtd.getTypeArguments

    if( visited.contains( (superAtd, subAtd) ) ) {
      return
    }
    visited += ( superAtd -> subAtd )

    if( superTypeParams.find( _.isInstanceOf[AnnotatedWildcardType]).isDefined && superTypeParams.size != subTypeParams.size ) {
      println( "TODO: We can have raw types assigned to <?> ( super=" + superAtd + ", subtype=" + subAtd + " )" );
      return
    }

    //IMPORTANT: In the case of rawness we make the supertype or subtype params equal (depending on which one is raw)
    //This is done solely so that methodInputs will result in the right number of inputs.  It clearly will generate
    //superfluous subtype/equality constraints ( which will be filtered out except for in methodInputs )
    if ( superTypeParams.size == 0 && subTypeParams.size > 0 ) {
      println("TODO: Left side is raw! ( super=" + superAtd + ", subtype=" + subAtd + " )");
      superTypeParams = subTypeParams

    } else if( superTypeParams.size > 0 && subTypeParams.size == 0 ) {
      println("TODO: Right side is raw! ( super=" + superAtd + ", subtype=" + subAtd + " ). Can happen ith suppress warnings.");
      subTypeParams = superTypeParams

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

            subAsUpper match {
              case Right( subAtdUb : AnnotatedDeclaredType ) =>
                val subAsSuper = asSuper( superAtd, subAtdUb )
                visitTypeArgs( superAtd, subAsSuper, visited )

              case _ => //DO NOTHING ON INTERSECTION TYPES
            }


          case ( superAtv : AnnotatedTypeVariable, notAtv : AnnotatedTypeMirror )   =>
            addEquality( superAtv, notAtv )
            val superAsUpper = typeUseToUpperBound( superAtv )

            superAsUpper match {
              case Right( superAtdUb : AnnotatedDeclaredType ) =>
                val otherAsUpper = asSuper( superAtdUb, notAtv )
                visitTypeArgs( superAtdUb, otherAsUpper, visited )

              case _ => //DO NOTHING ON INTERSECTION TYPES
            }

          case ( superArrAtm : AnnotatedArrayType, subArrAtm :AnnotatedArrayType ) =>
            addEquality( superArrAtm, subArrAtm )
            addEquality( superArrAtm.getComponentType, subArrAtm.getComponentType )

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
    if( atm1 == null || atm2 == null ) {
      println( "Null pointer when adding equality ( super=" + atm1 + ", subAtm=" + atm2 + " )" )
      return
    }

    val slots = ( slotMgr.extractSlot( atm1 ), slotMgr.extractSlot( atm2 ) )
    if( slots._1 != slots._2  ) {
      equality += slots
    }
    if( recordEqualsInputs ) {
      methodInputs += slots
    }
  }

  private def addLowerBound( boundedAtm : AnnotatedTypeMirror, lowerBound : AnnotatedTypeMirror ) {
    if( boundedAtm == null || lowerBound == null ) {
      println( "Null pointer when adding lower bounds ( super=" + boundedAtm + ", subAtm=" + lowerBound + " )" )
      return
    }

    val slots = ( slotMgr.extractSlot( boundedAtm ), slotMgr.extractSlot( lowerBound ) )
    if( slots._1 != slots._2  ) {
      lowerBounds += slots
    }
  }

  private def addSubtypes( superAtm : AnnotatedTypeMirror, subAtm : AnnotatedTypeMirror ) {
    if( superAtm == null || subAtm == null ) {
      println( "Null pointer when adding subtypes ( super=" + superAtm + ", subAtm=" + subAtm + " )" )
      return
    }

    val slots = ( slotMgr.extractSlot( superAtm ), slotMgr.extractSlot( subAtm ) )
    if( slots._1 != slots._2 ) {
      subtypes += slots
    }
    methodInputs += slots
  }

  private def  asSuper[T <: AnnotatedTypeMirror] ( supertype : T, subtype : AnnotatedTypeMirror ) : T = {

    //TODO: Temporary kludge to get past the fact that @Anno instances don't have a direct super type of Annotation
    //TODO: This seems like a bug
    if( supertype.isInstanceOf[AnnotatedDeclaredType] ) {
      val asString =
        supertype.asInstanceOf[AnnotatedDeclaredType]
          .getUnderlyingType.asElement.toString
      if( asString.equals("java.lang.annotation.Annotation") ) {
        val newSub = AnnotatedTypes.deepCopy( supertype )
        newSub.clearAnnotations()
        newSub.addAnnotations( subtype.getAnnotations )
        return newSub
      }
    }

    val typeUtils = InferenceMain.inferenceChecker.getProcessingEnvironment.getTypeUtils
    val sup = AnnotatedTypes.asSuper( typeUtils, infAtf, subtype, supertype )
    sup.asInstanceOf[T]
  }

  def getResult = SubtypingResult( subtypes.toList, equality.toSet, lowerBounds.toSet, methodInputs.toList )
}

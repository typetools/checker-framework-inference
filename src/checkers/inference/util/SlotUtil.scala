package checkers.inference.util

import checkers.types.AnnotatedTypeMirror
import checkers.inference.Slot
import scala.collection.mutable.ListBuffer
import checkers.inference.InferenceMain._
import checkers.types.AnnotatedTypeMirror._
import java.util.{LinkedHashMap => JLinkedMap}

object SlotUtil {

  def listSecondaryDeclVariables( atm : AnnotatedTypeMirror ) : (List[Slot], List[Slot]) = {
    val primary   = new ListBuffer[Slot]
    val secondary = new ListBuffer[Slot]
    listDeclVariables( atm, Some( primary ), secondary )

    ( primary.toList, secondary.toList )
  }

  def topToNestedDeclVariables( atms : List[AnnotatedTypeMirror] ) : List[(List[Slot], List[Slot])] = {
    val topToDeclVars = new ListBuffer[ (List[Slot], List[Slot]) ]

    val top    = new ListBuffer[Slot]
    val nested = new ListBuffer[Slot]

    atms.foreach( atm => {
      top.clear()
      nested.clear()
      listDeclVariables( atm, Some( top ), nested )
      topToDeclVars += ( top.toList -> nested.toList )
    })

    topToDeclVars.toList
  }

  def listDeclVariables(atm : AnnotatedTypeMirror) : List[Slot] = {
    val variables = new ListBuffer[Slot]
    listDeclVariables( atm, None, variables )
    variables.toList
  }

  /**
   * This is intended to be used on type-uses not declarations.
   *
   * @0 Map<@1 Integer, @2 List<@3 T> > => List(@0, @1, @2, @3) as a list of slots
   *  //TODO SU2
   * //TODO: tailrec it by visit node/opposite subtree first and then reverse the order?  Or just create a map of (TopLevel, Position) -> Variable
   * Do a depth first search of all Variables introduced by the given field ExpressionTree
   * //TODO: At the moment this is the reverse of the normal depth first search we would do (then it's reversed in
   * //TODO: listVariables.  I don't think the ordering matters because each variable already has a POS but
   * //TODO: it might prove important to have the right ordering in the game solver in order to match
   * //TODO: FieldAccesses and method call wiring correctly, however, I think we should be able to order
   * //TODO: by pos in worst case
   * //TODO: Previously we used this only in locations in which we expected no slots
   * //TODO: That weren't abstract variables, does that matter now?
   */
  private def listDeclVariables(atm : AnnotatedTypeMirror, primary : Option[ListBuffer[Slot]], variables : ListBuffer[Slot]) {
    import scala.collection.JavaConversions._

    def primaryToSlot(atm : AnnotatedTypeMirror) = {
      Option( slotMgr.extractSlot( atm ) )
    }

    /**
     * If primary is defined it means that top-level variables should be placed in the primary buffer
     * and all others should be placed in the variables list.  Notice, we always pass primary=None to the
     * recursive listDeclVariables below except for the upper bound on wildcard types
     * @param slot extracted slot that needs to be added to output
     * @return
     */
    def addVariable( slot : Option[Slot] ) = {
      if( primary.isDefined ) {
        primary.get ++= slot
      } else {
        variables ++= slot
      }
    }

    Option( atm ).map(

      _ match {

        case aat : AnnotatedArrayType =>
          addVariable( primaryToSlot(aat) )
          listDeclVariables(aat.getComponentType, None, variables)

        case awt : AnnotatedWildcardType =>
          addVariable( primaryToSlot(awt) )
        //TODO: WILDCARDS DO NOT SEEM TO BE HANDLED APPROPRIATELY

          //listDeclVariables( awt.getSuperBound,   None,    variables )
          //listDeclVariables( awt.getExtendsBound, primary, variables )

        case atv : AnnotatedTypeVariable =>
          addVariable( primaryToSlot( atv.getEffectiveLowerBound ) )
        //TODO SU3: For now, to avoid the bug in upper/lower bounds we do not visit the same variable twice

        case adt : AnnotatedDeclaredType =>
          addVariable( primaryToSlot(adt) )
          adt.getTypeArguments.foreach( (typeArg : AnnotatedTypeMirror) => listDeclVariables(typeArg, None, variables) )

        case apt: AnnotatedPrimitiveType =>
          addVariable( primaryToSlot(apt) )

        case ait : AnnotatedIntersectionType =>
          addVariable( primaryToSlot(ait) )

        case atm : AnnotatedTypeMirror if atm.isInstanceOf[AnnotatedNoType] |
                                          atm.isInstanceOf[AnnotatedNullType] =>
          addVariable( primaryToSlot(atm) ) //This may happen if we visit a constructor return type

        case atm : AnnotatedTypeMirror =>
          throw new RuntimeException("Unhandled annotated type mirror " + atm.getClass.getCanonicalName)
      }
    )
  }
}

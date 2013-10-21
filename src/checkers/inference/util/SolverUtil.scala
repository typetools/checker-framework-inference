package checkers.inference.util

import checkers.inference._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable

/**
 * A set of constraints useful to GameSolver's and any solver interested in replacing Verigames constraints
 * with their non-verigames equivalents (i.e. replacing SubboardCallConstraints with SubtypeConstraints and
 * EqualityConstraints ).
 */
object SolverUtil {

  /**
   * Given a list of ALL Variables generated from a program and a list of constraints, remove
   * all descendants of SubboardCallConstraints and replace them with the simpler Subtype and Equality
   * constraints that they represent.
   * 
   * @param variables ALL variables generated from a program
   * @param constraints All or a subset of all constraints generated from a program
   * @return The original list of constraints with any SubboardCallConstraints replaced by the
   *         equivalent subtype and equality constraints
   */
  def convertSubboardCalls( variables : List[Variable], constraints : List[Constraint], fieldReceiverDefault : Slot ) : List[Constraint] = {
    val signatureToGameBoard = extractDeclarations( variables, fieldReceiverDefault )

    constraints.foldLeft( List.empty[Constraint] )(
      (acc : List[Constraint], current : Constraint ) => {
        if( current.isInstanceOf[SubboardCallConstraint[_]] ) {
          acc ++ convertSubboardCall( current.asInstanceOf[SubboardCallConstraint[_]], signatureToGameBoard )
        } else {
          acc :+ current
        }
      })
  }

  /**
   * An analog to the SubboardCallConstraint but instead of holding
   * arguments to a method this object holds the declaration variables
   * of the method.  This class is intended to hold the information a Board would hold
   * via Graph elements
   * @param methodSignature
   */
  private class GameBoard( val className : String, val methodSignature : String, val isStatic : Boolean ) {
    var receiver : Slot = null

    val methodTypeParams = new ListBuffer[Slot]
    val classTypeParams  = new ListBuffer[Slot]
    val params = new ListBuffer[Slot]
    val result = new ListBuffer[Slot]
    
    def toInputSlots  : List[Slot] = ( ( Option( receiver ) ++: classTypeParams ) ++ methodTypeParams ++ params ).toList
    def toOutputSlots : List[Slot] = ( toInputSlots ++ result ).toList

    def inputsAndOutputs  = ( toInputSlots, toOutputSlots )

    //val slotToBounds    : Map[Slot, Option[(Slot, Slot)]]
    //val equivalentSlots : Set[(Slot, Slot)],
  }

  /**
   * For all variables in the program, extract any that represent part of the upper/lower bounds on
   * a classes type parameter.  Return a Map[Fully Qualified Class Name, Ordered list of type parameter bounds]
   *
   * e.g.
   * let @X => @VarAnnot(X)
   *
   * If the program contained three class
   *
   * class NonGeneric {...}
   * class Gen1<@3 T extends @1 List< @2 String>, @5 E extends @4 Object> {...}
   * class Gen1<@7 T extends @8 Object> {...}
   *
   * The returned map would be:
   *
   * Let <pacakgeName> be the fully qualified path to the class which follows it
   * Map( "<packageName>.NonGeneric" -> List(),
   *      "<packageName>.Gen1"       -> List( @1, @2, @3, @4, @5 )
   *      "<packageName>.Gen2"       -> Lis(@7, @8)
   *
   * @param variables ALL variables generated for a program
   * @return          Map[Fully qualified class name, Visit Ordered List[type parameter bounds]]
   */
  private def createClassToTypeParams( variables : List[Variable] ) : Map[String, List[Slot]] = {
    val classNameToTypeParams = new mutable.HashMap[String, ListBuffer[Slot]]()

    variables
      .filter( variable => variable.varpos.isInstanceOf[ClassTypeParameterVP]      ||
                           variable.varpos.isInstanceOf[ClassTypeParameterBoundVP] )
      .foreach( ( variable : Variable ) => {
        val className = variable.varpos.asInstanceOf[WithinClassVP].getFQClassName
        val typeParamBounds =
          classNameToTypeParams.get( className )
            .getOrElse({
            val bounds = new ListBuffer[Slot]
            classNameToTypeParams += ( className -> bounds )
            bounds
          })

         typeParamBounds += variable
      })

    classNameToTypeParams
      .map({ case (key, value) => key -> value.toList })
      .toMap
  }

  private def getOrCreateMethodGameBoard( methodVp : WithinMethodVP, sigToGameBoard : mutable.HashMap[String, GameBoard] ) =
    getOrCreateBoard( methodVp.getFQClassName, methodVp.getMethodSignature, methodVp.isMethodStatic, sigToGameBoard)

  private def getOrCreateFieldGameBoard( fieldVp : FieldVP, suffix : String, sigToGameBoard : mutable.HashMap[String, GameBoard] ) =
    getOrCreateBoard( fieldVp.getFQClassName, fieldVp.getFQName + suffix, false, sigToGameBoard )

  /**
   * The GameSolver creates method boards when it encounters a variable from a method it hasn't
   * created a board for yet.   It then adds this variable to the method board in the appropriate
   * location depending on VariablePosition.  Any new variables encountered for an existing method
   * are just added to the board in its appropriate position.
   * 
   * This method simulates that process but instead creates a list of GameBoards which are used
   * for all types for of Boards (static field boards, instance method boards, etc...).
   * @param variables
   * @return
   */
  private def extractDeclarations( variables : List[Variable], fieldReceiverDefault : Slot ) : Map[String, GameBoard] = {
    val classNameToTypeParams = createClassToTypeParams( variables )
    val sigToGameBoard = new mutable.HashMap[String, GameBoard]()

    variables.foreach( variable => {
      variable.varpos match {

        case returnVp : ReturnVP =>
          val gameBoard = getOrCreateMethodGameBoard( returnVp, sigToGameBoard )
          gameBoard.result += variable

        case recVp : ReceiverParameterVP    =>
          val gameBoard = getOrCreateMethodGameBoard( recVp, sigToGameBoard )
          assert( gameBoard.receiver == null,
            "Multiple variables with a ReceiverParameterVP to the same method.\n" +
            "\tPrevious VP ( " + gameBoard.receiver + " )\n" +
            "\tCurrent VP  ( " + recVp +               ")\n"
          )

          gameBoard.receiver = variable

        case paramVp : ParameterVP =>
          val gameBoard = getOrCreateMethodGameBoard( paramVp, sigToGameBoard  )
          gameBoard.params += variable

        case methodVp : WithinMethodVP if methodVp.isInstanceOf[ClassTypeParameterVP] |
                                          methodVp.isInstanceOf[ClassTypeParameterBoundVP] =>
          val gameBoard = getOrCreateMethodGameBoard( methodVp, sigToGameBoard  )
          gameBoard.methodTypeParams += variable

        case fvp :   FieldVP =>
          val getterBoard = getOrCreateFieldGameBoard( fvp, GetterSuffix, sigToGameBoard )
          getterBoard.result += variable

          val setterBoard = getOrCreateFieldGameBoard( fvp, SetterSuffix, sigToGameBoard )
          setterBoard.params += variable

          if( !fvp.isStatic ) {
             getterBoard.receiver = fieldReceiverDefault
             setterBoard.receiver = fieldReceiverDefault
          }

        case _ =>

      }
    })

    //add class type params for each non-static gameboard
    sigToGameBoard.values
      .filter( !_.isStatic )
      .foreach( gameBoard => gameBoard.classTypeParams ++= classNameToTypeParams.get( gameBoard.className ).flatten )

    sigToGameBoard.toMap
  }

  private def getOrCreateBoard( className : String, methodSignature : String, isStatic : Boolean,
                                sigToGameBoard : mutable.HashMap[String, GameBoard] ) : GameBoard = {
    sigToGameBoard
      .get( methodSignature )
      .getOrElse({
      val gameBoard = new GameBoard( className, methodSignature, isStatic )
      sigToGameBoard += ( methodSignature -> gameBoard )
      gameBoard
    })
  }

  /**
   * For a SINGLE subboard call return the Subtype and Equality constraints that it represents.
   * @param subboardCall A subboard call to convert
   * @param signatureToGameBoard A map of all fully qualified method signatures to the GameBoards that represent them
   * @return The Subtype and Equality constraints that subboardCall represents
   */
  private def convertSubboardCall( subboardCall : SubboardCallConstraint[_], signatureToGameBoard : Map[String, GameBoard] ) : List[Constraint] = {

    try {
      if( subboardCall.isLibraryCall ) {
        val ( declInputs, declOutputs )     = listStubBoardUseSlots( subboardCall.stubBoardUse.get )
        val ( actualInputs, actualOutputs ) = listSubboardCallSlots( subboardCall )
        createEqualityConstraints( declInputs, actualInputs )

      } else {
        val calledVp = subboardCall.calledVp.get
        val methodSigature =
          subboardCall match {
            case fieldAccess : FieldAccessConstraint     => getFieldAccessorName( fieldAccess.calledVp.get )
            case fieldAssign : FieldAssignmentConstraint => getFieldSetterName(   fieldAssign.calledVp.get )
            case _ => calledVp.asInstanceOf[CalledMethodPos].getMethodSignature
          }

        val ( declInputs,   declOutputs   ) = signatureToGameBoard( methodSigature ).inputsAndOutputs
        val ( actualInputs, actualOutputs ) = listSubboardCallSlots( subboardCall )
        createEqualityConstraints( declInputs, actualInputs )

      }
    } catch {
      case throwable : Throwable =>
        throw new RuntimeException( "\n\nException when convrting subboard call: \n" + subboardCall + "\n\n", throwable )
    }
  }

  /**
   * Pair-wise over the two input lists, create an equality constraint where the members of the second list (actuals)
   * are subtypes of the members of the first list (declareds)
   * @param declareds A list of supertypes
   * @param actuals   A list of subtypes
   * @return A list of subtype constraints between members of declareds and subtypes based on the order of the input
   *         lists
   */
  private def createEqualityConstraints( declareds : List[Slot], actuals : List[Slot] ) : List[Constraint] = {
    assert( declareds.size == actuals.size, "Declared.size( " + declareds.size + " ) != actuals.size( " + actuals.size + " )" +
                                            "Declared( " + declareds.mkString(", ") + ") " +
                                            "Actual( " + actuals.mkString(", ") + " )" )
    declareds.zip( actuals )
      .map({ case ( declared, actual ) => new SubtypeConstraint( actual, declared ) })
  }

  /**
   * Extract all of the input and output slots of a stub board and return them as a tuple
   * @param stubBoardUse A stub board for which we want in the input/output slots
   * @return ( stubBoardUse's inputs, stubBoardUse's outputs )
   */
  private def listStubBoardUseSlots( stubBoardUse : StubBoardUseConstraint ) : ( List[Slot], List[Slot] ) = {
    val classTypeParams  = SlotUtil.interlaceTypeParamBounds( stubBoardUse.classTypeParamUBs,   stubBoardUse.classTypeParamLBs  )
    val methodTypeParams = SlotUtil.interlaceTypeParamBounds( stubBoardUse.methodTypeParamUBs,  stubBoardUse.methodTypeParamLBs )

    val inputSlots  = ( Option( stubBoardUse.receiver ) ++: classTypeParams ) ++ methodTypeParams ++ stubBoardUse.args
    val outputSlots = inputSlots ++ stubBoardUse.result

    ( inputSlots, outputSlots )
  }

  //TODO: Perhaps create a common subclass to SubboardCall and StubBoardUse
  /**
   * Extract all of the input and output slots of a subboard call and return them as a tuple
   * @param subboardCall A subboardCall for which we want in the input/output slots
   * @return ( subboardCall's inputs, subboardCall's outputs )
   */
  private def listSubboardCallSlots( subboardCall : SubboardCallConstraint[_] ) : ( List[Slot], List[Slot] ) = {
    val classTypeParams  = SlotUtil.interlaceTypeParamBounds( subboardCall.classTypeArgs,  subboardCall.classTypeParamLBs  )
    val methodTypeParams = SlotUtil.interlaceTypeParamBounds( subboardCall.methodTypeArgs, subboardCall.methodTypeParamLBs )

    val inputSlots  = ( Option( subboardCall.receiver ) ++: classTypeParams ) ++ methodTypeParams ++ subboardCall.args
    val outputSlots = inputSlots ++ subboardCall.result

    ( inputSlots, outputSlots )
  }

  /**
   * Appended to the names of synthetic getter boards that represent field accesses in Verigames
   */
  val GetterSuffix = "--GET"

  /**
   * Appended to the names of synthetic setter boards that represent field assignments in Verigames
   */
  val SetterSuffix = "--SET"

  /**
   * Helper method to determine the name of the subboard used for a field getter.
   */
  def getFieldAccessorName(fvp: FieldVP): String = {
    getFieldAccessorName( fvp.getFQName )
  }

  /**
   * Public library fields will not have a FieldVP
   */
  def getFieldAccessorName( fqName : String): String = {
    fqName + GetterSuffix
  }

  /**
   * Helper method to determine the name of the subboard used for a field setter.
   */
  def getFieldSetterName(fvp: FieldVP): String = {
    getFieldSetterName(fvp.getFQName)
  }

  /**
   * Helper method to determine the name of the subboard used for a field setter.
   */
  def getFieldSetterName(fqName : String): String = {
    fqName + SetterSuffix
  }

  /**
   * If name ends in the field setter suffix than it is considered a Field Setter Name
   * @param name
   * @return
   */
  def isFieldSetterName( name : String ) = name.endsWith( SetterSuffix )

  /*
   * If name ends in the field setter suffix than it is considered a Field Setter Name   Name
   * @param name
   * @return
   */
  def isFieldGetterName( name : String ) = name.endsWith( GetterSuffix )

}

package checkers.inference.util

import java.io.{FileWriter, BufferedWriter, File}
import checkers.inference._
import checkers.inference.Variable
import checkers.inference.RefinementVariable
import checkers.inference.CombVariable
import scala.io.Source
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions._
import java.util

/**
 * This file defines a set of classes useful for debugging a run of the inference framework from the
 * Scala REPL.  In InferenceMain, if the system property DEBUG_FILE is specified then all variables/constraints
 * will be converted into the classes below and then written to the file specified by DEBUG_FILE.
 *
 * If you then provide the checker framework inference jar to your Scala REPL's class path you can run:
 * import checkers.inference.util.DebugUtil._
 *
 * If you have specified DEBUG_FILE as an environment variable the method read() will then read the DEBUG_FILE
 * and load all of the output variables/constraints into the cfiStats in the singleton of DebugUtil in
 * the REPL's memory.  You can then use the methods in DebugUtil and cfiStats to query the variables.
 *
 * (e.g.
 *  If I ran:
 *    import checkers.inference.util.DebugUtil._
 *    read()
 *    variable(13)
 *
 *  This would display the basic information about variable 13 (i.e. it's variable position and what type of variable
 *  it was). The command:
 *    constraints(13)
 *
 *  displays all constraints in which variable 13 appears.
 *
 * TODO JB: The verigames constraints (e.g. CallInstanceMethodConstraint) are not currently integrated with this script.
 */
object SlotRep {
  val fieldDelimiter = "_&_"
  def apply( str : String ) : SlotRep = {
    val className = getClass.getName + "="
    if(!str.startsWith( className )) {
      throw new RuntimeException("Invalid slot string. " + str)
    }

    val tokens = str.substring( className.length ).split(fieldDelimiter)
    if(tokens.size != 3) {
      throw new RuntimeException("Invalid slot string. " + str)
    }

    SlotRep( tokens(0).toInt, tokens(1), tokens(2) )
  }

  def apply( vari : AbstractVariable ) : SlotRep = {
    SlotRep( vari.id, vari.getClass.getName, vari.toString.replace("\n", " _n_ ")  )
  }
}

case class SlotRep( id : Int, name : String, tree : String ) {
  override def toString = SlotRep.getClass.getName + "=" + List(id.toString, name, tree).mkString(SlotRep.fieldDelimiter)
}

object ConstraintRep {
  val fieldDelimiter = "_&_"
  val setDelimiter  = "_,_"

  def stringToSet[T]( str : String, convert : (String => T) ) = {
    if( str.trim.isEmpty ) {
      Set.empty[T]
    } else {
      str.split( setDelimiter ).map( s => convert(s) ).toSet
    }
  }

  def setToString[T]( set : Set[T] ) = {
    set.mkString(setDelimiter)
  }

  def apply( str : String ) : ConstraintRep = {
    val className = getClass.getName + "="
    if(!str.startsWith( className )) {
      throw new RuntimeException("Invalid constraint string. " + str)
    }

    val tokens = str.substring( className.length ).split( fieldDelimiter )
    if( tokens.size != 5 ) {
      throw new RuntimeException("Invalid constraint string. " + str)
    }

    ConstraintRep( tokens(0), stringToSet( tokens(1), _.toInt ), stringToSet( tokens(2), _.toInt ), tokens(3), tokens(4) )
  }

  def apply( constraint : Constraint ) : ConstraintRep = {
    val name = constraint.getClass.getName
    constraint match {
      case SubtypeConstraint(sub: Slot, sup: Slot) =>
        ConstraintRep( name, slotsToSet(sub), slotsToSet(sup), shortStr(sub) + " <: " + shortStr(sup), toCleanStr( constraint ) )

      case CombineConstraint(target: Slot, decl: Slot, res: Slot) =>
        ConstraintRep( name, slotsToSet(target), slotsToSet(decl, res), shortStr(target) + " = " + shortStr(decl) + " + " + shortStr(res), toCleanStr( constraint ) )

      case EqualityConstraint(ell: Slot, elr: Slot) =>
        ConstraintRep( name, slotsToSet(ell), slotsToSet(elr), shortStr(ell) + " == " + shortStr(elr), toCleanStr( constraint ) )

      case InequalityConstraint(context: VariablePosition, ell: Slot, elr: Slot) =>
        ConstraintRep( name, slotsToSet(ell), slotsToSet(elr), shortStr(ell) + " != " + shortStr(elr), toCleanStr( constraint ) )

      case ComparableConstraint(ell: Slot, elr: Slot) =>
        ConstraintRep( name, slotsToSet(ell), slotsToSet(elr), shortStr(ell) + " <=> " + shortStr(elr), toCleanStr( constraint ) )

      case subboard : SubboardCallConstraint[_] =>
        val nameToSlots = List (
          "args" -> subboard.args,
          "classTypeArgs"      -> subboard.classTypeArgs,
          "classTypeParamLBs"  -> subboard.classTypeParamLBs,
          "methodTypeArgs"     -> subboard.methodTypeArgs,
          "methodTypeParamLBs" -> subboard.methodTypeParamLBs,
          "result"             -> subboard.result,
          "receiver"           -> List( subboard.receiver ).filter( _ != null ) )
        val slots = nameToSlots.map( _._2 ).flatten

        val shortName = name.split("\\.").last

        val slotSummaries =
          nameToSlots
            .filterNot( _._2.isEmpty )
            .map( { case (name, slots) => slotsWithName(name, slots) } )
            .mkString("_n_  ")

        val fullSummary = shortName +
          "(_n_  context = " + subboard.contextVp +
          "_n_  called  = " + subboard.calledVp +
          ( if( !slotSummaries.isEmpty ) "_n_  " + slotSummaries else "" ) +
          "_n_)"

        ConstraintRep( shortName, slotsToSet(slots :_* ),  Set[Int](), fullSummary, toCleanStr( constraint ) )

      case _ => println("NEED TO DO ADD " + constraint + " TO DEBUG UTIL")
                null
    }
  }

  def toPrintStr( str : String ) = str.replace(" _n_ ", "\n")
  def toCleanStr( obj : Object ) = obj.toString.replace("\n", " _n_ ")

  def slotsWithName( name : String, slots : List[Slot]) = {
      name + " = " + slotsToSet( slots : _* ).mkString("[", ",", "]")
  }

  def shortStr ( slot : Slot ) = {
    if( slot.isInstanceOf[AbstractVariable] )
      slot.asInstanceOf[AbstractVariable].id
    else
      slot.toString
  }

  def slotsToSet( slots : Slot* ) = {
    slots.filter( _.isInstanceOf[AbstractVariable] ).map( _.asInstanceOf[AbstractVariable].id ).toSet
  }
}

case class ConstraintRep( name : String, leftIds : Set[Int], rightIds : Set[Int], shortRep : String, longRep : String ) {

  val allIds = leftIds ++ rightIds
  override def toString = ConstraintRep.getClass.getName + "=" +
    List( name,
          ConstraintRep.setToString(leftIds), ConstraintRep.setToString(rightIds),
          shortRep, longRep
    ).mkString( ConstraintRep.fieldDelimiter )
}

case class CfiStats( vars : List[SlotRep], combVars : List[SlotRep], refVars : List[SlotRep], constraints : List[ConstraintRep]) {
  val allVars           = vars ++ combVars ++ refVars
  val idToVars          = allVars.map( slotRep => slotRep.id -> slotRep ).toMap
  val varsToConstraints = mapVarsToConstraints( constraints )

  private def mapVarsToConstraints( constraints : List[ConstraintRep] ) = {
    val mutMap = new util.HashMap[Int, ListBuffer[ConstraintRep]]

    for( con <- constraints ) {
      for( id <- con.allIds ) {
        val buff =
          if( !mutMap.containsKey( id ) ) {
            val lb = new ListBuffer[ConstraintRep]
            mutMap.put( id, lb )
            lb
          } else {
            mutMap.get( id )
          }

        buff += con
      }
    }

    mutMap.map( (tup : (Int, ListBuffer[ConstraintRep])) => (tup._1 -> tup._2.toList) ).toMap
  }
}



object DebugUtil {
  val defaultFile = Option(System.getenv("DEBUG_FILE")).getOrElse("./debugFile.txt")
  private var _cfiStats : CfiStats = null
  def cfiStats = _cfiStats

  def write( file : File,
             vars : List[Variable], combVars : List[CombVariable], refVars : List[RefinementVariable],
             constraints : List[Constraint]) {
    val slotReps = ( vars ++ combVars ++ refVars )
      .map( _.asInstanceOf[AbstractVariable])
      .map( SlotRep.apply _ )

    val constraintReps = constraints.map( ConstraintRep.apply _ ).filter( _ != null )

    val writer = new BufferedWriter( new FileWriter( file ))

    ( slotReps ++ constraintReps ).foreach( (rep : Object) => {
      writer.write( rep.toString )
      writer.newLine()
    })

    writer.flush()
    writer.close();
  }

  def readVariables( file : File ) {
    val slotBuffer = new ListBuffer[SlotRep]
    val constraintBuffer = new ListBuffer[ConstraintRep]

    val source = Source.fromFile( file )
    source.getLines().foreach(line => {
      if( line.startsWith( SlotRep.getClass.getName ) ) {
        slotBuffer += SlotRep( line )

      } else if( line.startsWith( ConstraintRep.getClass.getName ) ) {
        constraintBuffer += ConstraintRep( line )

      } else {
        throw new RuntimeException("Unexpected line start: " + line)
      }
    })
    source.close()

    val slotsByName = slotBuffer.toList.groupBy( _.name )
    //TODO FIX THESE NOT TO BE HARDCODED
    _cfiStats = CfiStats( slotsByName.get( "checkers.inference.Variable"       ).toList.flatten,
                          slotsByName.get( "checkers.inference.CombVariable"   ).toList.flatten,
                          slotsByName.get( "checkers.inference.RefinementVariable" ).toList.flatten,
                          constraintBuffer.toList )

  }

  def read( filePath : String = defaultFile ) {
    readVariables( new File( filePath ) )
  }

  def constraints( id : Int ) {
    if( !cfiStats.idToVars.contains(id) ) {
      println( "No such variable with id " + id)
    } else {
      println( "Constraints for variable " + id )
      if( !cfiStats.varsToConstraints.contains(id) ) {
        println( "No constraints for variable " + id )
      } else {
        cfiStats.varsToConstraints(id).foreach( printConstraint _ )
      }
    }
  }

  def showConstraints {
    cfiStats.constraints.foreach( printConstraint _ )
  }

  def showVariables {
    cfiStats.allVars.foreach( printSlot _ )
  }

  def variable( id : Int ) {
    if( !cfiStats.idToVars.contains(id) ) {
      println( "No such variable with id " + id)
    } else {
      printSlot( cfiStats.idToVars(id) )
    }
  }

  def printSlot( slotRep : SlotRep ) {
    println( slotRep.name + " #" + slotRep.id )
    println( slotRep.tree.replace("_n_", "\n") )
  }

  def printConstraint( conRep : ConstraintRep ) {
    println( conRep.shortRep.replace("_n_", "\n") )
  }

  def variableWithText( strs : String* ) = {
    var vars = cfiStats.allVars
    for( str <- strs ) {
      vars = vars.filter( _.toString().contains(str) )
    }
    vars.foreach( variable => printSlot( variable ) )
  }

  def constraintWithText( strs : String* ) = {
    var constraints = cfiStats.constraints
    for( str <- strs) {
      constraints = constraints.filter( _.toString().contains( str ) )
    }
    constraints.foreach( printConstraint _ )
  }
}

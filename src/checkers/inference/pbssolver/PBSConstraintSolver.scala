package checkers.inference
package pbssolver

import javax.lang.model.element.AnnotationMirror
import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import javacutils.AnnotationUtils

abstract class PBSConstraintSolver extends ConstraintSolver {

  /** All variables used in this program. */
  var variables: List[Variable] = null

  /** Empty, b/c there is no viewpoint adaptation (yet?). */
  var combvariables: List[CombVariable] = null

  /** All constraints that have to be fulfilled. */
  var constraints: List[Constraint] = null

  /** Weighting information. Currently empty & ignored, as a human solves the game. */
  var weights: List[WeightInfo] = null

  /** The command-line parameters. */
  var params: TTIRun = null

  val NRBITS: Int

  var t_start: Long = 0
  var t_decl: Long = 0
  var t_encode: Long = 0
  var t_cnf: Long = 0
  var t_weight: Long = 0
  var t_end: Long = 0

  def solve(variables: List[Variable],
    combvariables: List[CombVariable],
    refinementVariables : List[RefinementVariable],
    constraints: List[Constraint],
    weights: List[WeightInfo],
    params: TTIRun): Option[Map[AbstractVariable, AnnotationMirror]] = {
    if (InferenceMain.TIMING) {
      t_start = System.currentTimeMillis()
    }

    this.variables = variables
    this.combvariables = combvariables
    this.constraints = constraints
    this.weights = weights
    this.params = params

    var ands: AndOfOrs = new AndOfOrs()
    val bools = collection.mutable.ListBuffer[BVar]()

    for (v <- (variables ++ combvariables)) {
      val (a, b) = decl(v)
      if (a != null) ands.and(a)
      if (b != null) bools.appendAll(b)
    }

    if (InferenceMain.TIMING) {
      t_decl = System.currentTimeMillis()
    }

    for (cst <- constraints) {
      val (a, b) = encode(cst)
      if (a != null) ands.and(a)
      if (b != null) bools.appendAll(b)
    }

    if (InferenceMain.TIMING) {
      t_encode = System.currentTimeMillis()
    }

    val cnf: StringBuilder = toWCNF(ands, bools.toList, weights.size)
    // println("CNF: " + cnf)

    if (InferenceMain.TIMING) {
      t_cnf = System.currentTimeMillis()
    }

    // comments not supported by MUS solver
    cnf.append("c Weights:\n")
    for (w <- weights) {
      cnf.append(w.weight + " " + bit(w.theVar, w.cons) + " 0\n")
    }
    // println(wghtcnf)

    if (InferenceMain.TIMING) {
      t_weight = System.currentTimeMillis()
    }

    val solveroutput = solveCNF(cnf.toString)

    if (InferenceMain.TIMING) {
      t_end = System.currentTimeMillis()
    }

    solveroutput match {
      case None => {
        // TODO handle errors
        None
      }
      case Some(ssolveroutput) => {
        val solution = decode(ssolveroutput)

        solution match {
          case Some(ssolution) => {
            val combsol = ssolution.filterKeys(_.isInstanceOf[CombVariable])
            if (!combsol.isEmpty) {
              println("Annotations of combination variables:")
              println(combsol.mkString("\n"))
              println
            }

            // We don't care about the annotations for the combination variables
            Some(ssolution.filterKeys(!_.isInstanceOf[CombVariable]))
          }
          case None => {
            None
          }
        }
      }
    }
  }

  def timing: String = {
    "Total time in PBSConstraintSolver: " + (t_end - t_start) +
      "\nGenerating declarations: " + (t_decl - t_start) +
      "\nGenerating constraints: " + (t_encode - t_decl) +
      "\nGenerating CNF: " + (t_cnf - t_encode) +
      "\nGenerating Weights: " + (t_weight - t_cnf) +
      "\nSAT solver: " + (t_end - t_weight)
  }

  def version: String = "PBSConstraintSolver version 0.2"

  def decode(cnfout: List[String]): Option[Map[AbstractVariable, AnnotationMirror]] = {
    val sum = cnfout.find(_.startsWith("s "))

    print("CNF solution summary: ")
    sum match {
      case Some("s UNSATISFIABLE") => {
        println("unsatisfiable!")
        return None
      }
      case Some("s UNKNOWN") => {
        println("unknown problem!")
        return None
      }
      case Some(s) => {
        // more cases?
        println("satisfied!")
      }
      case None => {
        println("none found! Quitting.")
        return None
      }
    }

    val rawsol = cnfout.find(_.startsWith("v "))

    val solline = rawsol match {
      case Some(s) => s.drop(2)
      case None => {
        println("The solver said satisifed, but I didn't find the solution!")
        println("Solver replied: " + (cnfout mkString "\n"))
        return None
      }
    }

    val solarr: Array[Int] = solline.split(' ').map(_.toInt).filterNot(_ == 0)
    val solconst = solarr.map(unbit(_))
    // TODO: do you want to ensure that every variable is true only once?
    val onlytrue = solconst.filter(_ match {
      case EqConst(v, c) => true
      case Not(x) => false
      case BVar(v) => {
        // ignore the BVars
        false
      }
    })

    // println("Solution: " + onlytrue.mkString("\n"))

    val varmap = new collection.mutable.HashMap[AbstractVariable, AnnotationMirror]
    for (eq <- onlytrue) {
      eq match {
        case EqConst(v, c) => varmap += (v -> c.an)
        case _ => {
          // the filter above ensures that only EqConst are in onlytrue
        }
      }
    }
    Some(varmap.toMap)
  }
  // TODO: allow repeating runs, excluding the last result.
  // From the string v 1 2 3 ... we create the single line -1 -2 -3, the or-ing of the negations.
  // Should this be done at this low level or at the higher decoded level?

  def solveCNF(cnf: String): Option[List[String]] = {

    val cnfFile: PrintWriter = new PrintWriter(new FileWriter(new File(InferenceMain.options.optWCNFFile), false))

    if (cnf.size < 200000) {
      cnfFile.print(cnf)
    } else {
      val chksize = 65536
      var chunk: String = null

      val blocks = cnf.size / chksize
      for (i <- 0 until blocks) {
        // println("Printing from " + i*chksize + " to " + ((i+1)*chksize-1) % cnf.size)
        chunk = cnf.substring(i * chksize, (i + 1) * chksize)
        cnfFile.print(chunk)
      }
      if (blocks * chksize < cnf.size) {
        chunk = cnf.substring(blocks * chksize, cnf.size)
        cnfFile.print(chunk)
      }
    }
    cnfFile.close()

    // TODO: add timing code to measure the time spent in the different parts of the program
    // To add a timeout, add something like this (time in seconds): -t 600
    val cmd: String = InferenceMain.options.optWCNFSolver + " " + InferenceMain.options.optWCNFFile

    val p = Runtime.getRuntime().exec(cmd)

    var inReply = List[String]()
    val inReader: Thread = new Thread(new Runnable() {
      def run() {
        val in: BufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()))
        var s: String = in.readLine()
        while (s != null) {
          // println("Read: " + s)
          inReply = inReply :+ s
          s = in.readLine()
        }

      }
    });
    inReader.start();

    var errReply = List[String]()
    val errReader: Thread = new Thread(new Runnable() {
      def run() {
        val err: BufferedReader = new BufferedReader(new InputStreamReader(p.getErrorStream()))
        var s: String = err.readLine()
        while (s != null) {
          errReply = inReply :+ s
          s = err.readLine()
        }

      }
    });
    errReader.start();

    p.waitFor
    inReader.join()
    errReader.join()

    if (InferenceMain.DEBUG(this)) {
      println("CNF solver reply: " + inReply.mkString("\n"))
    }

    if (errReply.size > 0) {
      println("CNF solver errors:\n" + errReply.mkString("\n   "))
    }

    if (inReply != null && !inReply.isEmpty) {
      Some(inReply)
    } else {
      None
    }
  }

  def toWCNF(and: AndOfOrs, bools: List[BVar], numWeights: Int): StringBuilder = {
    val res = new StringBuilder()

    val bits = (variables.size + combvariables.size) * NRBITS + bools.size
    val clauses = and.ors.size + numWeights

    // TODO: we want this for the statistics table. Is there a nicer place to put this?
    println("CNF sizes: bools & clauses: " + bits + " & " + clauses)

    // comments not supported by MUS solver
    res.append("c Generated WCNF File:\n")
    res.append("p wcnf " + bits + " " + clauses + " 10000\n")

    for (o <- and.ors) {
      toWCNF(res, o)
    }

    res
  }

  def toWCNF(res: StringBuilder, or: OrOfElems) {
    res.append("10000 ")
    for (e <- or.elems) {
      toCNF(res, e)
    }
    res.append(" 0\n")
  }

  def toCNF(res: StringBuilder, el: Elem) {
    // println("ToCNF: " + el)
    el match {
      case Not(nel) => {
        res.append("-")
        toCNF(res, nel)
      }
      case bv: BVar => {
        res.append(bvarBit(bv))
      }
      case EqConst(v, c) => {
        res.append(bit(v, c))
      }
    }
    res.append(" ")
  }

  def bit(v: AbstractVariable, c: Constant): String
  def bvarBit(bv: BVar): String
  def unbit(in: Int): Elem

  def decl(v: AbstractVariable): (AndOfOrs, List[BVar])

  def encodeSubtype(sub: Slot, sup: Slot): (AndOfOrs, List[BVar])
  def encodeCombine(target: Slot, decl: Slot, res: Slot): (AndOfOrs, List[BVar])
  def encodeEquality(ell: Slot, elr: Slot): (AndOfOrs, List[BVar])
  def encodeInequality(ell: Slot, elr: Slot): (AndOfOrs, List[BVar])
  def encodeComparable(ell: Slot, elr: Slot): (AndOfOrs, List[BVar])

  def encode(c: Constraint): (AndOfOrs, List[BVar]) = {
    c match {
      case SubtypeConstraint(sub, sup) => {
        encodeSubtype(sub, sup)
      }
      case CombineConstraint(target, decl, res) => {
        encodeCombine(target, decl, res)
      }
      case EqualityConstraint(ell, elr) => {
        encodeEquality(ell, elr)
      }
      case InequalityConstraint(ctx, ell, elr) => {
        encodeInequality(ell, elr)
      }
      case ComparableConstraint(ell, elr) => {
        encodeComparable(ell, elr)
      }
      case _ => {
        // Nothing to do.
        (null, null)
      }
    }
  }

}

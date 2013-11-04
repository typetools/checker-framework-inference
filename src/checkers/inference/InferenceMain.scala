package checkers.inference

import checkers.basetype.BaseTypeChecker
import com.sun.source.tree.CompilationUnitTree
import javax.lang.model.element.AnnotationMirror
import java.io.FileOutputStream
import java.io.File
import javacutils.AnnotationUtils
import java.io.StringWriter
import java.io.PrintWriter
import collection.mutable.ListBuffer
import util.DebugUtil
import checkers.flow._
import java.util.{List => JavaList}
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement
import checkers.types.AbstractBasicAnnotatedTypeFactory

/*
TODO MAIN1: improve statistics:
  output annotation counts/ CNF counts
  output annotations only for fields/parameters/etc.
  option to output as LaTeX
*/

object InferenceMain {
  def DEBUG(cls: AnyRef): Boolean = {
    options.optDebug || (options.optDebugClass match {
      case Some(s) => { s.contains(cls.toString) }
      case _ => false
    })
  }

  val DetachedVarSymbols = List("index#num", "iter#num", "assertionsEnabled#num", "array#num")

  def booleanPropOrEnv( propName : String, default : Boolean=true ) = {
    propOrEnvValue(propName).map(_ == "true").getOrElse(default)
  }

  def propOrEnvValue( propName : String ) = {
    ( Option( System.getProperty(propName) ) ) match {
      case Some( value : String ) => Some( value )
      case None => Option( System.getenv(propName) )
    }
  }

  def propOrEnvValues( propNames : String*) = {
    propNames
      .map( propOrEnvValue _ )
      .find( _.isDefined )
      .getOrElse( None )
  }


  private var _performingFlow : Boolean = true

  def setPerformingFlow(performingFlow : Boolean) { _performingFlow = performingFlow }
  def isPerformingFlow = _performingFlow

  /**
   * This flag is set to false when we want to generate boards and allow some defects in output
   */
  lazy val STRICT = booleanPropOrEnv("STRICT")
  lazy val FAIL_FAST = booleanPropOrEnv("FAIL_FAST", false)
  lazy val PRINT_BOARDS_ON_ERROR  = booleanPropOrEnv( "PRINT_BOARDS_ON_ERROR", false )
  lazy val DO_LAYOUT  = booleanPropOrEnv( "DO_LAYOUT" )
  lazy val DEBUG_FILE = propOrEnvValue( "DEBUG_FILE"  )

  val TIMING = true
  var t_start: Long = 0
  var t_javac: Long = 0
  var t_solver: Long = 0
  var t_end: Long = 0

  var inferenceChecker: InferenceChecker = null
  var realChecker: InferenceTypeChecker = null
  var constraintMgr: ConstraintManager = null
  var slotMgr: SlotManager = null

  var options: TTIRun = null

  def run(params: TTIRun) {
    if (TIMING) {
      t_start = System.currentTimeMillis()
    }
    this.options = params

    val solver = createSolver()

    if (options.optVersion) {
      println("Checker Inference Framework version 0.2")
      println(solver.version)
      println
    }

    // TODO MAIN2: Note that also the class path used by the scala interpreter is
    // important. Maybe we don't need the path here at all?
    val infArgs = Array[String](
      "-processor", "checkers.inference.InferenceChecker", // TODO MAIN3: parameterize to allow specialization of ATF
      "-proc:only", // don't compile classes to save time
      "-encoding", "ISO8859-1", // TODO MAIN4: needed for JabRef only, make optional
      "-Xmaxwarns", "1000",
      "-AprintErrorStack",
      "-Astubs=" + options.optStubFiles,
      // "-Aflowdotdir=dotfiles/",
      // "-Ashowchecks",
      "-Awarns")

    // This should work, if we are using the JSR308 javac.
    // However, in Eclipse it does not :-(
    // TODO MAIN5: how do I get the scala Eclipse plug-in to use a different JDK?
    // val l = java.lang.annotation.ElementType.TYPE_USE

    val newArgs: Array[String] = new Array[String](infArgs.length + params.residualArgs.length)

    System.arraycopy(infArgs, 0, newArgs, 0, infArgs.length);
    System.arraycopy(params.residualArgs.toArray, 0, newArgs, infArgs.length, params.residualArgs.length);

    var javacoutput = new StringWriter()
    var compiler = new com.sun.tools.javac.main.Main("javac", new PrintWriter(javacoutput, true));
    val compres = compiler.compile(newArgs);

    if (DEBUG(this)) {
      println("javac output: " + javacoutput)
    }

    if (TIMING) {
      t_javac = System.currentTimeMillis()
    }

    if (compres != com.sun.tools.javac.main.Main.Result.OK) {
      println("Error return code from javac! Quitting.")
      if (!DEBUG(this)) {
        println("javac output: " + javacoutput)
      }
      System.exit(1)
      return
    }

    // maybe this helps garbage collection
    javacoutput = null
    compiler = null

    println

    if (slotMgr == null) {
      // The slotMgr is still null if the init method is not called.
      // Something strange happened then.
      println("The system is not configured correctly. Try again.")
      System.exit(1)
      return
    }

    if (slotMgr.variables.isEmpty) {
      println("No variables found! Stopping!")
      System.exit(1)
      return
    }

    println( "All " + slotMgr.variables.size + " variables:" )
    for (varval <- slotMgr.variables.values) {
      println(varval)
    }


    println( "\n" + slotMgr.combvariables.size + " CombVariables:" )
    for (varval <- slotMgr.combvariables.values) {
      println(varval)
    }


    println( "\n" + slotMgr.refVariables.size + " RefinementVariables:" )
    for (varval <- slotMgr.refVariables.values) {
      println(varval)
    }

    println
    if (constraintMgr.constraints.isEmpty) {
      println("No constraints!")
    } else {
      println("All " + constraintMgr.constraints.size + " constraints:")
      for (const <- constraintMgr.constraints) {
        println(const)
      }
    }

    val allVars = slotMgr.variables.values.toList

    // Inserts the subtype constraints for a merge variable before the first use of that merge variable.
    // This is less important now that merges and refinement variables are START_PIPE_DEPENDENT_BALL.
    var orderedConstraints = constraintMgr.constraints.toList
    var mergedReversed = List[(RefinementVariable, List[SubtypeConstraint])]()
    inferenceChecker.mergeRefinementConstraintCache.foreach(kv => {mergedReversed = kv +: mergedReversed})
    mergedReversed.foreach(kv => {
      val firstConstraint = orderedConstraints.find(_.slots.contains(kv._1))
      if (firstConstraint.isDefined) {
        // Insert constraints before first use
        orderedConstraints = insertIntoList(orderedConstraints, kv._2) { _ == firstConstraint.get }
      } else {
        // If a merge is never used, drop it.
      }
    })

    val allCstr = orderedConstraints
    val allCombVars = slotMgr.combvariables.values.toList
    val allRefVars  = slotMgr.refVariables.values.filter(refVar => orderedConstraints.find(_.slots.contains(refVar)).isDefined).toList
    val theAFUAnnotHeader = getAFUAnnotationsHeader

    val weighter = createWeightManager
    // TODO do this nicer
    val allWeights = if (weighter != null) weighter.weight(allVars, allCstr) else null

    println("DEBUG: " + DEBUG_FILE)
    DEBUG_FILE.map( (debugFile : String) => {
      DebugUtil.write( new File(debugFile), params.originalArgs, allVars, allCombVars, allRefVars, allCstr )
    })

    println

    val solution = solver.solve(allVars, allCombVars, allRefVars, allCstr, allWeights, params)

    if (TIMING) {
      t_solver = System.currentTimeMillis()
    }
    //Note: With the advent of using AnnotatedTypeVariables in the game solver we were forced
    //to move cleanup to after the solve phase.  We can move to "SlotGroups" rather than
    //annotated type mirrors and this would allow us to free befroe the solver
    // free whatever we can from the compilation phase
    this.cleanUp()

    var solved = true
    solution match {
      case Some(ssolution) => {
        val solAFU = theAFUAnnotHeader +
          ssolution.keySet.map((v) => v.toAFUString(ssolution(v))).mkString("\n")
        println("Solution:\n" + solAFU)

        val jaifFile = new File(params.optJaifFileName)
        // check for existing file?
        val output = new PrintWriter(new FileOutputStream(jaifFile))
        output.write(solAFU)
        output.close()

        // this doesn't work b/c the compiler is loaded by a different classloader
        // and an instanceof fails :-(
        // annotator.Main.main(Array("--abbreviate=false", "-d", "inference-output", jaifFileName) ++ args)
      }
      case None => {
        solved = false
        println("No solution found. Sorry!")
      }
    }

    if (TIMING) {
      t_end = System.currentTimeMillis()

      println("Number of variables: " + allVars.size)
      println("Number of constraints: " + allCstr.size)
      println
      println("Total running time: " + (t_end - t_start))
      println("Generation: " + (t_javac - t_start))
      println("Solving: " + (t_solver - t_javac))
      println("Output: " + (t_end - t_solver))

      val sol = solver.timing;
      if (sol!=null) {
        println
        println("Solver: ")
        println(sol)
      }
    }
    if (!solved) {
      System.exit(2)
    }
  }

  // The checker gets created by javac at some point. It calls us back, don't worry.
  def init(checker: InferenceChecker) {
    inferenceChecker = checker
    constraintMgr = new ConstraintManager()
    slotMgr = new SlotManager()
  }

  def cleanUp() {
    inferenceChecker.cleanUp()
    inferenceChecker = null
    constraintMgr.cleanUp()
    constraintMgr = null
    slotMgr.cleanUp()
    slotMgr = null
  }

  def createSolver(): ConstraintSolver = {
    try {
      Class.forName(options.optSolver)
        .getConstructor()
        .newInstance().asInstanceOf[ConstraintSolver]
    } catch {
      case th: Throwable =>
        println("Error instantiating solver class \"" + options.optSolver + "\".")
        th.printStackTrace()
        System.exit(5)
        null
    }
  }

  def createWeightManager: WeightManager = {
    // new gut.GUTWeightManager()

    if (options.optWeightManager != "") {
      try {
        Class.forName(options.optWeightManager).newInstance().asInstanceOf[WeightManager]
      } catch {
        case th: Throwable =>
          println("Error instantiating weight manager class \"" + options.optWeightManager + "\".")
          th.printStackTrace()
          System.exit(5)
          null
      }
    } else {
      null
    }
  }

  def getRealChecker: InferenceTypeChecker = {
    if (realChecker == null) {
      try {
        realChecker = Class.forName(options.optChecker).newInstance().asInstanceOf[InferenceTypeChecker];
        realChecker.init(inferenceChecker.getProcessingEnvironment)        //should there really be an init checker
      } catch {
        case th: Throwable =>
          println("Error instantiating checker class \"" + options.optChecker + "\".")
          println(throwableToStackTrace(th))
          System.exit(5)
      }

      // TODO MAIN6: set the boolean flags of the checker here.
      // But this would create a dependency on GUT.
      // Instead, change the options to a single String and pass it?
    }
    realChecker
  }

  def createVisitors(): InferenceVisitor[_ <: BaseTypeChecker, InferenceAnnotatedTypeFactory] = {

    // We pass the inferenceChecker, not the getRealChecker, as checker argument.
    // This ensures that the InferenceAnnotatedTypeFactory will be used by the visitor.
    var checkerClass = Class.forName(options.optChecker)
    var visitorName = options.optVisitor
    val errorMsgs = new ListBuffer[String]()

    def makeErrorStr(msg : String, throwable : Option[Throwable] = None) = {
      "Error instantiating visitor class \"" + options.optVisitor + "\":\n" +
        msg + throwable.map(th => "\n" + throwableToStackTrace(th)).getOrElse("")
    }

    def checkerClassToVisitor(chClass : Class[_]) : Option[InferenceVisitor[_ <: BaseTypeChecker, InferenceAnnotatedTypeFactory]] = {
      val paramTypes : Array[Class[_]]   =  Array(chClass, classOf[InferenceChecker], classOf[Boolean])
      val args : Array[java.lang.Object] =  Array(getRealChecker, inferenceChecker, true.asInstanceOf[AnyRef])
      val invocationDescription = "BaseTypeChecker.invokeConstructorFor(" +
          List(visitorName, "(" + paramTypes.mkString(",") + ")", "(" + args.mkString(", ") + ")" ).mkString(", ") + ")"

      try {
        val visitor =
          BaseTypeChecker.invokeConstructorFor(visitorName, paramTypes, args)
            .asInstanceOf[InferenceVisitor[_ <: BaseTypeChecker, InferenceAnnotatedTypeFactory]]
        if(visitor != null) {
          Some(visitor)
        } else {
          errorMsgs += makeErrorStr("Error in " + invocationDescription)
          None
        }
      } catch {
        case th : Throwable => errorMsgs += makeErrorStr("Exception in " + invocationDescription, Some(th))
        None
      }
    }

    //A lazy iterator of ancestor classes
    val ancestors   = Iterator.iterate[Class[_]](checkerClass)(_.getSuperclass).takeWhile( _ != classOf[BaseTypeChecker] )

    //A lazy iterator of either (None(I.e. failed invocation or exception), or Some(Visitor))
    val invocationResults : Option[Option[InferenceVisitor[_ <: BaseTypeChecker,InferenceAnnotatedTypeFactory]]] =
      ancestors.map(checkerClassToVisitor _ )
        .find( _.isDefined )

    //Find the first
    invocationResults match {
      case Some(Some(visitor : InferenceVisitor[_,_])) =>
        visitor.asInstanceOf[InferenceVisitor[_ <: BaseTypeChecker, InferenceAnnotatedTypeFactory]]

      case _ =>
        println("Error instantiating visitor class \"" + options.optVisitor + "\":\n" + errorMsgs.mkString("\n"))
        System.exit(5)
        null
    }
  }

  /**
   * Create a transfer function based on command line argument --transfer.
   *
   */
  def createInferenceTransfer(analysis : CFAbstractAnalysis[CFValue, CFStore, CFTransfer]): InferenceTransfer = {
    var transferName = options.optTransfer
    val paramTypes : Array[Class[_]]   =  Array(classOf[CFAbstractAnalysis[CFValue, CFStore, CFTransfer]])
    val args : Array[java.lang.Object] =  Array(analysis)
    val inferenceTransfer = BaseTypeChecker.invokeConstructorFor(transferName, paramTypes, args).asInstanceOf[InferenceTransfer]
    inferenceTransfer
  }

  /**
   * Create analysis based on command line argument --analysis.
   *
   */
  def createFlowAnalysis(checker : InferenceChecker, fieldValues : JavaList[javacutils.Pair[VariableElement, CFValue]],
      env: ProcessingEnvironment, typeFactory: InferenceAnnotatedTypeFactory) = {
    var analysisName = options.optAnalysis
    val paramTypes : Array[Class[_]]   =  Array(classOf[AbstractBasicAnnotatedTypeFactory[_,_,_,_]], classOf[ProcessingEnvironment],
        classOf[BaseTypeChecker], classOf[JavaList[javacutils.Pair[VariableElement, CFValue]]])
    val args : Array[java.lang.Object] =  Array(typeFactory, env, checker, fieldValues)
    BaseTypeChecker.invokeConstructorFor(analysisName, paramTypes, args).asInstanceOf[CFAnalysis]
  }

  def throwableToStackTrace(th : Throwable) = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    th.printStackTrace(pw)
    pw.flush()
    val str = sw.toString
    sw.close()
    str
  }

  def getAFUAnnotationsHeader: String = {
    def findPackage(am: AnnotationMirror): String = {

      val elems = inferenceChecker.getProcessingEnv.getElementUtils
      elems.getPackageOf(am.getAnnotationType.asElement).toString
    }
    def findAnnot(am: AnnotationMirror): String = {
      // println("Annotation: " + am.getAnnotationType.asElement.getAnnotationMirrors)
      am.getAnnotationType.asElement.getSimpleName.toString
    }
    // "package GUT.quals:\n" +
    // "annotation @Any: @Retention(value=RUNTIME) @java.lang.annotation.Target(value={TYPE_USE})\n" +
    // "annotation @Peer: @Retention(value=RUNTIME) @java.lang.annotation.Target(value={TYPE_USE})\n" +
    // "annotation @Rep: @Retention(value=RUNTIME) @java.lang.annotation.Target(value={TYPE_USE})\n\n"
    // currently we do not ouptut the annotations on the annotation; still seems to work
    import scala.collection.JavaConversions._
    ((for (am <- inferenceChecker.REAL_QUALIFIERS.values) yield {
      ("package " + findPackage(am) + ":\n" +
        "annotation @" + findAnnot(am) + ":")
    }) mkString ("\n")) + "\n\n"

  }

  def insertIntoList[A](xs: List[A], extra: List[A])(p: A => Boolean) = {
    xs.map(x => if (p(x)) extra ::: List(x) else List(x)).flatten
  }
}

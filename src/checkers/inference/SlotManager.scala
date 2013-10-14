package checkers.inference

import checkers.types.AnnotatedTypeMirror.AnnotatedNullType
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType
import javax.lang.model.element._
import com.sun.javadoc.FieldDoc
import com.sun.tools.javac.code.Symbol.VarSymbol
import javacutils.AnnotationUtils
import javacutils.TreeUtils
import com.sun.source.tree._
import javax.annotation.processing.ProcessingEnvironment
import com.sun.source.tree.Tree.Kind
import checkers.inference.InferenceMain._
import quals.VarAnnot
import annotator.scanner.StaticInitScanner
import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import scala.Some
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.VariableElement
import javax.lang.model.element.AnnotationValue
;

/**
 * Maintains a list of Variables, CombVariables, and RefinementVariables.  This class handles the
 * creation and manipulation of Slots and has helper methods for dealing with slots.
 */
class SlotManager {
  private var nextId: Int = 0

  //TODO: Perhaps have multiple submanagers per variable type?

  val variables     = new scala.collection.mutable.LinkedHashMap[Int, Variable]()
  val combvariables = new scala.collection.mutable.LinkedHashMap[Int, CombVariable]()
  val refVariables  = new scala.collection.mutable.LinkedHashMap[Int, RefinementVariable]()

  // TODO: is caching the trees a problem for big projects?
  // Is a weak hashmap good? We might then create a new Variable,
  // which is also bad...
  // TODO: what is the relation to the Elements cached in InferenceChecker?
  val curtreesvar     = new scala.collection.mutable.WeakHashMap[Tree, Variable]()
  val curtreescombvar = new scala.collection.mutable.WeakHashMap[Tree, CombVariable]()
  val curtreesRefVar  = new scala.collection.mutable.WeakHashMap[Tree, RefinementVariable]()

  def cleanUp() {
    variables.clear
    combvariables.clear
    curtreesvar.clear
    curtreescombvar.clear
  }

  private def createVariableAnnotationImpl(varPos: VariablePosition, atf: InferenceAnnotatedTypeFactory[_],
    topTree: Tree, curTreeOpt: Option[Tree], pos: List[(Int, Int)]): AnnotationMirror = {

    val vari = new Variable( varPos, nextId )
    vari.setTypePosition(topTree, curTreeOpt.getOrElse(null), pos)

    variables   += (nextId  -> vari)

    curTreeOpt.map( curTree =>
      curtreesvar += (curTree -> vari)
    )

    nextId += 1

    vari.getAnnotation
  }

  /**
   * Some variables correspond to trees that need to be written into source code.  To do this we
   * create a variable with an empty current tree.
   *
   * WARNING: You cannot then pull this variable from getVariable because it uses curTree as an
   * index into the curtreesvar map.  All missing tree variables need to be cached in some other manner
   * and pulled from that cache.  Variables can be extracted from the return type of this method
   * @param varPos
   * @param atf
   * @param topTree
   * @param pos
   */
  def createMissingTreeVariable( varPos : VariablePosition, atf : InferenceAnnotatedTypeFactory[_],
                                 topTree : Tree, pos : List[(Int, Int)]) : AnnotationMirror = {
    createVariableAnnotationImpl(varPos, atf, topTree, None, pos)
  }

  def createVariableAnnotation(varPos: VariablePosition, atf: InferenceAnnotatedTypeFactory[_],
                               topTree: Tree, curTree: Tree, pos: List[(Int, Int)]): AnnotationMirror = {

    if( curTree == null ) {
      throw new RuntimeException( "curTree cannot be null! varPos=( " + varPos + ") " +
                                  "topTree=( " + topTree + " ) pos=( " + pos.mkString(", ") + " )" )
    }

    createVariableAnnotationImpl(varPos, atf, topTree, Some(curTree), pos)
  }

  def createCombVariable(): CombVariable = {
    val vari = new CombVariable(nextId)

    combvariables += (nextId -> vari)

    nextId += 1

    vari
  }

  /**
   * Create a RefinementVariable.
   */
  def createRefinementVariableAnnotation(typeFactory : InferenceAnnotatedTypeFactory[_],
      assignmentTree : Tree, astPathStr : String, bsConstraint : Boolean = false) : AnnotationMirror = {

    val currentType = typeFactory.getAnnotatedType(assignmentTree)

    //TODO: Duplicate of InferenceTreeAnnotator code

    val varPos = if (InferenceUtils.isWithinMethod(typeFactory, assignmentTree)) {
      new RefinementInMethodVP(astPathStr)
    } else if (InferenceUtils.isWithinStaticInit(typeFactory, assignmentTree)) {
      val blockId = StaticInitScanner.indexOfStaticInitTree(typeFactory.getPath(assignmentTree))
      new RefinementInStaticInitVP(blockId, astPathStr)
    } else {
      throw new RuntimeException("Refinement variable in impossible position!" + assignmentTree)
    }
    varPos.init(typeFactory, assignmentTree)

    //The old variable being refined
    //Always use the main qualifier
    val refinedVariable = extractSlot( currentType.getAnnotationInHierarchy(inferenceChecker.VAR_ANNOT) )

    //TODO: Instead get the declared type of the tree?  I guess there is no reason
    val declVar = refinedVariable match {
      case declVar : Variable           => declVar
      case refVar  : RefinementVariable => refVar.declVar
      case null                         => throw new RuntimeException( "Variable is null: " + assignmentTree.toString )
      case _ => throw new RuntimeException("Uncaught case! " + refinedVariable.toString)
    }

    //The new variable derived from the original
    val refinementVar = RefinementVariable( nextId, varPos, declVar, bsConstraint )
    refVariables += (nextId -> refinementVar)

    nextId += 1

    refinementVar.getAnnotation()
  }

  /**
   * Create a refinement variable with the same properties as sourceVar but a new ID.
   */
  def createRefinementVariableAsDuplicate(sourceVar : AbstractVariable) : AnnotationMirror = {
    var newRefVar = sourceVar match {
      case refVar : RefinementVariable => {
        RefinementVariable(nextId, refVar.varpos, refVar.declVar, false)
      }
      case variableVar : Variable => {
        RefinementVariable(nextId, sourceVar.varpos, variableVar, false)
      }
      case _ => {
        throw new RuntimeException("Attempted to create duplicate RefinementVariable of unhanlded type: " + sourceVar.toString)
      }
    }
    refVariables += (nextId -> newRefVar)
    nextId += 1
    newRefVar.getAnnotation()
  }

  def getOrCreateCombVariable(curtree: Tree): CombVariable = {
    if (curtreescombvar.contains(curtree)) {
      curtreescombvar(curtree)
    } else {
      val cv = createCombVariable

      curtreescombvar += (curtree -> cv)

      cv
    }
  }

  /**
   * If the given tree is actually typed as a RefinementVariable, replace
   * the annotation with the Ref
   */
  def replaceWithRefVar(atm : AnnotatedTypeMirror, tree : Tree) {
    val annoMirror = Option(atm.getAnnotationInHierarchy(inferenceChecker.VAR_ANNOT))
    annoMirror.map( anno => {
      if( inferenceChecker.isVarAnnot(anno) && curtreesRefVar.contains(tree) ) {
        atm.clearAnnotations()
        atm.addAnnotation(curtreesRefVar(tree).getAnnotation)
      }
    })
  }

  /*
  def getOrCreateRefVariable(curTree : AssignmentTree, atf : InferenceAnnotatedTypeFactory[_]) : RefinementVariable = {
    curtreesRefVar.getOrElse(curTree, {
      val refVar = createRefinmentVariable(curTree, atf)
      curtreesRefVar += (curTree -> refVar)
      refVar
    })
  } */

  def getVariable(id: Int): Option[Variable] = {
    variables.get(id)
  }

  def getRefVariable(id: Int): Option[RefinementVariable] = {
    refVariables.get(id)
  }

  def getCombVariable(id: Int): Option[CombVariable] = {
    combvariables.get(id)
  }

  def getVariable(curtree: Tree): Option[Variable] = {
    curtreesvar.get(curtree)
  }

  def getOrCreateVariable(varPos : VariablePosition, atf: InferenceAnnotatedTypeFactory[_],
                          topTree: Tree, curTree: Tree, pos: List[(Int, Int)]) = {
    getCachedVariableAnnotation(curTree).getOrElse(
      createVariableAnnotation(varPos, atf, topTree, curTree, pos)
    )
  }

  def getCachedVariableAnnotation(curtree: Tree): Option[AnnotationMirror] = {
    //  println("GetCached: " + curtree.getClass())
    if(curtreesRefVar.contains(curtree)) {
      Some(curtreesRefVar(curtree).getAnnotation())
    } else if (curtreesvar.contains(curtree)) {
      // println("cached")
      Some(curtreesvar(curtree).getAnnotation())
    } else {
      // println("not cached")
      if (curtree.getKind() == Kind.VARIABLE) {
        val curvartree = curtree.asInstanceOf[VariableTree]
        val curvarelem = TreeUtils.elementFromDeclaration(curvartree)
        println("TODO: Should we try to use: " + curvarelem)
        None
      } else {
        None
      }
    }
  }

  def addTreeToRefVar(tree : Tree, anno : AnnotationMirror) {
    val refVar = extractSlot(anno).asInstanceOf[RefinementVariable]
    curtreesRefVar(tree) = refVar
  }

  def extractSlot(from: AnnotatedTypeMirror): Slot = {
    //TODO JB: Is this reasonable?  Were there places where we were checking this and doing nothing? for primitives in
    //TODO JB: in Nninf for example
    /**if (!InferenceMain.getRealChecker.needsAnnotation(from)) {
      return null
    }**/

    val afroms = from.getAnnotations()
    val afrom = if (afroms.size > 0) afroms.iterator.next else null

    if ( afrom == null ) {
      // println("SlotManager.extractSlot: no annotations found in type: " + from +
      //  " of type: " + (if (from != null) from.getClass else null))
      // This should only happen when the source code is not
      // available, i.e. for libraries.
      // Therefore, use the default annotation for the type.
      // TODO: before defaulting, look for other omissions.
      return Constant(InferenceMain.getRealChecker.defaultQualifier)
    }

    extractSlot(afrom)
  }

  //Intended for when you know you have a constant
  def extractConstant(a: AnnotationMirror): Constant = {
    extractSlot( a ).asInstanceOf[Constant]
  }

  //Intended for when you know you have a constant
  def extractConstant(atm: AnnotatedTypeMirror): Constant = {
    extractSlot( atm ).asInstanceOf[Constant]
  }


  def extractSlot(a: AnnotationMirror): Slot = {
    val opt = if (a == null) None else extractSlotImpl(a)
    opt match {
      case None => {
        // This should only happen for primitive types, see the version of extractSlot with AnnotatedType Mirror
        // Here we cannot decide whether this happened. Therefore, be silent.
        // println("SlotManager::extractSlot: Extracting element from annotation failed: " + a)
        null
      }
      case Some(a) => a
    }
  }

  private def extractSlotImpl(a: AnnotationMirror): Option[Slot] = {
    //TODO: create the string once and compare it once and perhaps do a annotationType to InferenceVar

    if (("" + a.getAnnotationType()) == InferenceMain.inferenceChecker.VAR_ANNOT.getAnnotationType().toString) {
      if(a.getElementValues.isEmpty ) {
        return None
      }

      val av: AnnotationValue = a.getElementValues.values.iterator.next
      val v: Int = av.getValue().toString.toInt
      getVariable(v)
    } else if (("" + a.getAnnotationType) == InferenceMain.inferenceChecker.REFVAR_ANNOT.getAnnotationType.toString) {
      if(a.getElementValues.isEmpty ) {
        return None
      }

      val av: AnnotationValue = a.getElementValues.values.iterator.next
      val v: Int = av.getValue().toString.toInt
      getRefVariable(v)

    } else if (("" + a.getAnnotationType()) == InferenceMain.inferenceChecker.COMBVAR_ANNOT.getAnnotationType().toString) {
      if(a.getElementValues.isEmpty ) {
        return None
      }

      val av: AnnotationValue = a.getElementValues.values.iterator.next
      val v: Int = av.getValue().toString.toInt
      getCombVariable(v)
    } else if (("" + a.getAnnotationType()) == InferenceMain.inferenceChecker.LITERAL_ANNOT.getAnnotationType().toString) {
      val avs = a.getElementValues

      if(avs.isEmpty ) {
        return None
      }

      import scala.collection.JavaConversions._

      var strki: String = null
      var lit: String = null

      for ((key, va) <- avs) {
        // TODO: is there a nicer way??? How do I create an ExecutableElement?
        key.toString match {
          case "kind()" => strki = va.getValue().asInstanceOf[VariableElement].getSimpleName().toString()
          case "literal()" => lit = va.getValue().asInstanceOf[String]
        }
      }

      var ki: Kind = null
      Kind.values().find(_.toString() == strki) match {
        case Some(x) => ki = x
        case None => println("SlotManager couldn't find the Kind. Strange!")
      }

      // Keep this in sync with the literal types
      if (ki == Kind.OTHER && lit.equals("this")) {
        Some(LiteralThis)
      } else if (ki == Kind.OTHER && lit.equals("super")) {
        Some(LiteralSuper)
      } else if (ki == Kind.NULL_LITERAL) {
        Some(LiteralNull)
      } else {
        Some(new Literal(ki, lit))
      }
    } else {
      var found: Boolean = false
      var res: Option[Slot] = None

      //TODO: Use map/find rather then keyset, import inferenceChecker
      import scala.collection.JavaConversions._
      for (ra <- InferenceMain.inferenceChecker.REAL_QUALIFIERS.keySet()) {
        if (("" + a.getAnnotationType()) == ra) { // ra.getAnnotationType().toString) {
          res = Some(new Constant(InferenceMain.inferenceChecker.REAL_QUALIFIERS.get(ra)))
          found = true
        }
      }
      if (!found) {
        // TODO: handle constants
        if( !AnnotationUtils.annotationName(a).equals("checkers.quals.Unqualified") ) {
            println("SlotManager unknown annotation: " + a)
        }
      }
      res
    }
  }


  //TODO JB: We likely want to unify this so you only have to pass the classElement
  //TODO JB: This is extremely ugly.  In the future we are also going to want to compare
  //TODO JB: annotations on the type parameters in the receiver tree and the class/impls tree
  //TODO JB: so this method will be less useful as we'll need to act on the entire
  //TODO JB: annotated type mirror when it is found in extImplsTreeCache
  /**
   * If the classTree has an extends clause
   * then: it is looked up in extImplsTreeCache and the primary annotation is returned
   * else: we look for it in the classToMissingExtCache
   *
   * @param classTree
   * @return
   */
  def getPrimaryExtendsAnno( classTree : ClassTree ) : Option[Slot] = {

    classTree.getExtendsClause match {
      case null =>
        val classElem = TreeUtils.elementFromDeclaration( classTree )
        InferenceMain.inferenceChecker.classToMissingExtCache.get(classElem).map( extractSlot _ )

      case tree : Tree =>
        InferenceMain.inferenceChecker.extImplsTreeCache.get( tree ).map( extractSlot _ )
    }
  }

}

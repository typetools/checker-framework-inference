package checkers.inference

import checkers.types.AnnotatedTypeMirror.AnnotatedNullType
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType
import javax.lang.model.element.VariableElement
import com.sun.javadoc.FieldDoc
import com.sun.tools.javac.code.Symbol.VarSymbol
import javax.lang.model.element.AnnotationMirror
import checkers.util.AnnotationUtils
import checkers.util.TreeUtils
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import javax.annotation.processing.ProcessingEnvironment
import com.sun.source.tree.LiteralTree
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.AnnotationMirror
import com.sun.source.tree.Tree.Kind;

class SlotManager {
  private var nextId: Int = 0

  val variables = new scala.collection.mutable.HashMap[Int, Variable]()
  val combvariables = new scala.collection.mutable.HashMap[Int, CombVariable]()

  // TODO: is caching the trees a problem for big projects?
  // Is a weak hashmap good? We might then create a new Variable,
  // which is also bad...
  // TODO: what is the relation to the Elements cached in InferenceChecker?
  val curtreesvar = new scala.collection.mutable.WeakHashMap[Tree, Variable]()
  val curtreescombvar = new scala.collection.mutable.WeakHashMap[Tree, CombVariable]()

  def cleanUp() {
    variables.clear
    combvariables.clear
    curtreesvar.clear
    curtreescombvar.clear
  }

  def createVariableAnnotation(varpos: VariablePosition, atf: InferenceAnnotatedTypeFactory,
    toptree: Tree, curtree: Tree, pos: List[Int]): AnnotationMirror = {

    val vari = new Variable(varpos, nextId)
    vari.setTypePosition(toptree, curtree, pos)

    variables += (nextId -> vari)
    curtreesvar += (curtree -> vari)

    nextId += 1

    vari.getAnnotation
  }

  def createCombVariable(): CombVariable = {
    val vari = new CombVariable(nextId)

    combvariables += (nextId -> vari)

    nextId += 1

    vari
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

  def getVariable(id: Int): Option[Variable] = {
    variables.get(id)
  }

  def getCombVariable(id: Int): Option[CombVariable] = {
    combvariables.get(id)
  }

  def getVariable(curtree: Tree): Option[Variable] = {
    curtreesvar.get(curtree)
  }

  def getCachedVariableAnnotation(curtree: Tree): Option[AnnotationMirror] = {
    //  println("GetCached: " + curtree.getClass())
    if (curtreesvar.contains(curtree)) {
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

  def extractSlot(from: AnnotatedTypeMirror): Slot = {
    if (!InferenceMain.getRealChecker.needsAnnotation(from)) {
      return null
    }

    val afroms = from.getAnnotations()
    val afrom = if (afroms.size > 0) afroms.iterator.next else null

    if (afrom == null) {
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
    if (("" + a.getAnnotationType()) == InferenceMain.inferenceChecker.VAR_ANNOT.getAnnotationType().toString) {
      val av: AnnotationValue = a.getElementValues.values.iterator.next
      val v: Int = av.getValue().toString.toInt
      getVariable(v)
    } else if (("" + a.getAnnotationType()) == InferenceMain.inferenceChecker.COMBVAR_ANNOT.getAnnotationType().toString) {
      val av: AnnotationValue = a.getElementValues.values.iterator.next
      val v: Int = av.getValue().toString.toInt
      getCombVariable(v)
    } else if (("" + a.getAnnotationType()) == InferenceMain.inferenceChecker.LITERAL_ANNOT.getAnnotationType().toString) {
      val avs = a.getElementValues
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

      import scala.collection.JavaConversions._
      for (ra <- InferenceMain.inferenceChecker.REAL_QUALIFIERS.keySet()) {
        if (("" + a.getAnnotationType()) == ra) { // ra.getAnnotationType().toString) {
          res = Some(new Constant(InferenceMain.inferenceChecker.REAL_QUALIFIERS.get(ra)))
          found = true
        }
      }
      if (!found) {
        // TODO: handle constants
        println("SlotManager unknown annotation: " + a)
      }
      res
    }
  }

}

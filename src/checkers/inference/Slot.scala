package checkers.inference

import quals.{RefineVarAnnot, LiteralAnnot, VarAnnot, CombVarAnnot}

import javax.lang.model.element.AnnotationMirror
import com.sun.source.tree.Tree.Kind
import com.sun.source.tree.Tree
import checkers.util.AnnotationBuilder
import InferenceUtils.hashcodeOrElse

sealed abstract trait Slot {
  def getAnnotation(): AnnotationMirror
}

sealed abstract class AbstractVariable(val varpos: VariablePosition, val id: Int) extends Slot {
  // TODO: don't store a reference to the tree, let javac get rid of it
  // just get whatever info you need; Or clean the cache in Main at the end?

  // TODO: which exact type is it? which instanceof in the body? Need to add a lot more info
  // add a Tree.Kind?
  def setTypePosition(toptree: Tree, curtree: Tree, pos: List[Int]) {
    stoptree = toptree.toString
    scurtree = curtree.toString
    this.pos = pos

    if (InferenceMain.DEBUG(this)) {
      println(this.toString())
    }
  }

  var stoptree: String = null
  var scurtree: String = null
  var pos: List[Int] = null

  override def toString: String = {
    val postree = if (pos != null && pos.size > 0)
      " at position " + pos.mkString("(", ", ", ")")
    else
      ""
    val subtree = if (scurtree != stoptree)
      " within " + stoptree
    else
      ""

    "Variable " + id + " at " + varpos + "; tree " + scurtree + postree + subtree
  }

  // generate the Annotation-File-Utilities representation of the solution
  def toAFUString(sol: AnnotationMirror): String = {
    varpos.toAFUString(pos) + sol + "\n"
  }

  var ab: AnnotationBuilder = null
  var annot: AnnotationMirror = null
  var annotClass: java.lang.Class[_ <: java.lang.annotation.Annotation] = classOf[VarAnnot]

  override def getAnnotation(): AnnotationMirror = {
    if (ab == null) {
      ab = new AnnotationBuilder(InferenceMain.inferenceChecker.getProcessingEnvironment, annotClass)
    }
    if (annot == null) {
      ab.setValue("value", id.asInstanceOf[java.lang.Integer])
      annot = ab.build()
    }
    annot
  }

  //Since AbstractVariable has members that are NOT provided via the constructor (and therefore would be
  //handled by the case classes structural equals) we need to define a equals/hashcode
  override def equals(other : Any) = {

    if(other == null || !this.getClass.equals(other.getClass)) {
      false
    } else {
      //Normally we could use case classes structural equality but those do not capture the variables below
      val that = other.asInstanceOf[AbstractVariable]
      that.id       == this.id       &&
      that.varpos   == this.varpos   &&
      that.stoptree == this.stoptree &&
      that.scurtree == this.scurtree &&
      that.pos      == this.pos
    }
  }


  override def hashCode() = {
    val codes = List(getClass.hashCode,
                     hashcodeOrElse(varpos, 1),
                     id,
                     hashcodeOrElse(stoptree, 2),
                     hashcodeOrElse(scurtree, 3),
                     hashcodeOrElse(pos, 4),
                     annotClass.hashCode)
    codes.fold(0)(_ + 33 * _ )
  }
}

case class Variable(override val varpos: VariablePosition, override val id: Int) extends AbstractVariable(varpos, id) {
}

// TODO: instead of inheriting from Variable, introduce a trait?
// All we need is the nice implementation of getAnnotation.
case class CombVariable(override val id: Int) extends AbstractVariable(null, id) {
  annotClass = classOf[CombVarAnnot]

  override def toString: String = {
    "CombVariable #" + id
  }

  override def toAFUString(sol: AnnotationMirror): String = {
    "GO AWAY! Do not call CombVariable.toAFUString!"
  }
}

case class Constant(val an: AnnotationMirror) extends Slot {
  override def toString(): String = {
    "Constant(" + an.toString + ")"
  }

  override def getAnnotation(): AnnotationMirror = {
    an
  }
}

/**
 * A RefinementVariable is created whenever a declared variable might be refined by flow.  This
 * occurs whenever an assignment occurs in a method.  At the point of the assignment going forward,
 * the declared variable has been refined by the assignment and therefore should be considered
 * a new variable.
 * @param id The id of this refinement variable
 * @param declVar The Variable representing the declaration tree for this variable
 */
case class RefinementVariable(override val id : Int, override val varpos : VariablePosition, declVar : Variable)
  extends AbstractVariable(varpos, id) {

  annotClass = classOf[RefineVarAnnot]

  override def toString: String = {
    "RefinementVariable #" + id + " Declared Variable: " + declVar.toString
  }

  override def toAFUString(sol: AnnotationMirror): String = {
    "GO AWAY! Do not call RefinementVariable.toAFUString!"
  }
}

sealed abstract class AbstractLiteral(val ki: Kind, val lit: Any) extends Slot {
  override def toString(): String = {
    val slit = if (lit.isInstanceOf[String]) "\"" + lit + "\"" else lit
    "Literal(" + ki + ", " + slit + ")"
  }

  override def getAnnotation(): AnnotationMirror = {
    val ab = new AnnotationBuilder(InferenceMain.inferenceChecker.getProcessingEnvironment, classOf[LiteralAnnot])
    ab.setValue("kind", ki)
    ab.setValue("literal", "" + lit)
    ab.build()
  }
}

case class Literal(override val ki: Kind, override val lit: Any) extends AbstractLiteral(ki, lit)

// Keep the specialized classes in sync with SlotManager.extractSlotImpl

case object LiteralThis extends AbstractLiteral(Kind.OTHER, "this")

case object LiteralSuper extends AbstractLiteral(Kind.OTHER, "super")

case object LiteralNull extends AbstractLiteral(Kind.NULL_LITERAL, "null")

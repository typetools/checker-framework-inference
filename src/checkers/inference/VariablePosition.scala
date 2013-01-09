package checkers.inference

import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.AnnotatedTypeTree
import com.sun.source.tree.ParameterizedTypeTree
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeParameterElement
import com.sun.source.tree.ArrayTypeTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.PrimitiveTypeTree
import com.sun.source.tree.VariableTree
import annotator.scanner.AnonymousClassScanner
import javax.lang.model.element.NestingKind
import javax.lang.model.element.TypeElement
import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.tree.TreeInfo
import com.sun.source.tree.CompilationUnitTree
import javax.lang.model.`type`.DeclaredType
import checkers.util.TypesUtils
import checkers.util.TreeUtils
import checkers.util.ElementUtils
import com.sun.source.tree.Tree
import com.sun.tools.javac.code.Type

// TODO: turn on deprecation warnings and fix the case class hierarchy in this file

private object AFUHelper {
  // use this in the places where there also are
  // declaration annotations
  def posToAFUDecl(pos: List[Int]): String = {
    if (pos == null || pos.size == 0)
      "\ntype: "
    else
      "\ntype:\ninner-type " + pos.mkString(", ") + ": "
  }

  // use this in places where only type annotations can occur
  // the previous element must not end with \n
  def posToAFUType(pos: List[Int]): String = {
    if (pos == null || pos.size == 0)
      " "
    else
      "\ninner-type " + pos.mkString(", ") + ": "
  }

  /**
   * Return the binary representation for the given class.
   * This is used for method signatures in the AFU output.
   */
  def getClassJvmName(celem: TypeElement, ctree: ClassTree,
    atf: InferenceAnnotatedTypeFactory): String = {

    val (pn, cn) = getPackageAndClassJvmNames(celem, ctree, atf)

    "L" + (if (pn != "") {
      pn.replace(".", "/") + "/" + cn
    } else {
      cn
    }) + ";"
  }

  /**
   * This returns the package and class name.
   * The package name uses "." as separator.
   * The class name is already in JVM format, that is, "." replaced by "$".
   * The "Lxxx;" is not added by this method.
   * This is needed for the AFU output, where the "package" uses dots,
   * but the "class" is in binary.
   * Use getClassJvmName to directly get a JVM string.
   *
   * TODO: the tree is used for anonymous and local classes.
   * I think the local classes usage will fail, if no source is available.
   */
  def getPackageAndClassJvmNames(celem: TypeElement, ctree: ClassTree,
    atf: InferenceAnnotatedTypeFactory): (String, String) = {
    val pelem = ElementUtils.enclosingPackage(celem)

    val pn = pelem.getQualifiedName().toString()

    var cn: String = null

    if (celem.getNestingKind == NestingKind.ANONYMOUS) {
      // anonymous inner class
      val cpath = atf.getPath(ctree)
      val aci: Int = AnonymousClassScanner.indexOfClassTree(cpath, ctree)
      val encc = TreeUtils.enclosingClass(cpath.getParentPath)

      val enccelem: TypeElement = atf.fromClass(encc).getUnderlyingType().asElement().asInstanceOf[TypeElement];

      cn = enccelem.getQualifiedName().toString() + "$" + aci

      // TODO: the following logic is duplicated below, extract to method
      if (pn != "") {
        cn = cn.drop(pn.size + 1)
      }
      // the separator between outer and inner classes is still a "."
      // make it a "$" for the Annotation-File Utilities
      cn = cn.replace('.', '$')
    } else if (celem.getNestingKind == NestingKind.LOCAL) {
      // local class
      println("TODO: handle local inner classes!")
      cn = celem.getQualifiedName().toString
      val cpath = atf.getPath(ctree)
      val enclc = TreeUtils.enclosingClass(cpath.getParentPath)
      val enclcelem: TypeElement = atf.fromClass(enclc).getUnderlyingType().asElement().asInstanceOf[TypeElement];

      // is the 1 a count of something?
      cn = enclcelem.getQualifiedName().toString + "$1" + cn
      if (pn != "") {
        cn = cn.drop(pn.size + 1)
      }
      // the separator between outer and inner classes is still a "."
      // make it a "$" for the Annotation-File Utilities
      cn = cn.replace('.', '$')
    } else {
      // remainder should need no special treatment
      // the following produces the fully-qualified name, including the package
      cn = celem.getQualifiedName().toString
      // remove the package prefix and the dot between the packages and the first class
      if (pn != "") {
        cn = cn.drop(pn.size + 1)
      }
      // the separator between outer and inner classes is still a "."
      // make it a "$" for the Annotation-File Utilities
      cn = cn.replace('.', '$')
    }
    (pn, cn)
  }

  /**
   * Convert a tree that represents a type to the JVM string representation.
   * This is used for method signatures in the AFU output.
   */
  def toJvmTypeName(typetree: Tree, atf: InferenceAnnotatedTypeFactory): String = {
    //     val pt = atf.getAnnotatedTypeFromTypeTree(typetree).getUnderlyingType

    if (typetree == null) {
      // null is used for constructor return types :-(
      return "V"
    }

    typetree.getKind match {
      case Tree.Kind.PRIMITIVE_TYPE => {
        primitiveTypesJvm(typetree.toString)
      }
      case Tree.Kind.IDENTIFIER => {
        val (celem, ctree) = AFUHelper.getClassElemAndTreeFromTypeTree(typetree, atf)
        AFUHelper.getClassJvmName(celem, ctree, atf)
      }
      case Tree.Kind.ARRAY_TYPE => {
        val at = typetree.asInstanceOf[ArrayTypeTree]
        "[" + toJvmTypeName(at.getType, atf)
      }
      case Tree.Kind.PARAMETERIZED_TYPE => {
        val ptt = typetree.asInstanceOf[ParameterizedTypeTree]
        toJvmTypeName(ptt.getType, atf)
      }
      case Tree.Kind.ANNOTATED_TYPE => {
        val att = typetree.asInstanceOf[AnnotatedTypeTree]
        toJvmTypeName(att.getUnderlyingType, atf)
      }
      case Tree.Kind.MEMBER_SELECT => {
        val mst = typetree.asInstanceOf[MemberSelectTree]
        // TODO: when does this case arise???
        // The Identifier seems to be the class name and the Expression the package with a leading ".".

        val pn = mst.getExpression.toString()
        val rpn = if (pn != "") {
          pn.drop(1).replace(".", "/") + "/"
        } else {
          pn
        }

        // Ensure that inner classes work
        val res = "L" + rpn + mst.getIdentifier().toString().replace(".", "$") + ";"
        // println("Result: " + res)
        res
      }
      case _ => {
        // typetree.toString
        println("AFUHelper::toJvmTypeName: unhandled type tree: " + typetree + " of kind: " + typetree.getKind)
        "AFUHelper::toJvmTypeName::TODO"
      }
    }
  }

  /**
   * Starting from an arbitrary Tree within a class, return the closes enclosing class
   * element and tree.
   */
  def getEnclosingClassElemAndTree(tree: Tree, atf: InferenceAnnotatedTypeFactory): (TypeElement, ClassTree) = {

    tree match {
      case ctree: ClassTree => {
        val ce = TreeUtils.elementFromDeclaration(ctree)
        // TODO: why not directly return ctree?
        val ct = atf.getTrees.getTree(ce)
        (ce, ct)
      }
      case mtree: MethodTree => {
        val exelem = TreeUtils.elementFromDeclaration(mtree)
        val ce = ElementUtils.enclosingClass(exelem)
        val ct = atf.getTrees.getTree(ce)
        (ce, ct)
      }
      case vtree: VariableTree => {
        val varelem = TreeUtils.elementFromDeclaration(vtree)
        val ce = ElementUtils.enclosingClass(varelem)
        val ct = atf.getTrees.getTree(ce)
        (ce, ct)
      }
      case _ => {
        val ct = TreeUtils.enclosingClass(atf.getPath(tree))
        if (ct == null) {
          println("AFUHelper::getEnclosingClassElemAndTree: no class found for tree: " + tree +
            " of kind: " + (if (tree!=null) {tree.getKind} else {""}))
          println("   root: " + atf.getRoot)
          println("   type of this: " + this.getClass)
          (null, null)
        } else {
          val ce = TreeUtils.elementFromDeclaration(ct)
          (ce, ct)
        }
      }
    }
  }

  /**
   * Return the class element and tree corresponding to a type reference tree.
   * Note that the tree might be null, if the source is not available.
   */
  def getClassElemAndTreeFromTypeTree(typetree: Tree, atf: InferenceAnnotatedTypeFactory): (TypeElement, ClassTree) = {
    typetree match {
      case ctree: IdentifierTree => {
        // val undty = atf.getAnnotatedTypeFromTypeTree(typetree).getUnderlyingType

        val el = TreeUtils.elementFromUse(ctree)
        val cel = el.getKind match {
          case ElementKind.CLASS => {
            el.asInstanceOf[TypeElement]
          }
          case ElementKind.INTERFACE => {
            el.asInstanceOf[TypeElement]
          }
          case ElementKind.ENUM => {
            el.asInstanceOf[TypeElement]
          }
          case ElementKind.ANNOTATION_TYPE => {
            el.asInstanceOf[TypeElement]
          }
          case ElementKind.TYPE_PARAMETER => {
            val tpel = el.asInstanceOf[TypeParameterElement]
            val firstbound = tpel.getBounds.get(0)
            // TODO: for f-bounded polymorphism, repeat until we find a DeclaredType

            firstbound match {
              case dt: DeclaredType => {
                // TODO: why is the return type not TypeElement?
                dt.asElement.asInstanceOf[TypeElement];
              }
              case _ => {
                println("AFUHelper::getClassElemAndTreeFromTypeTree: unhandled bound: " + firstbound)
                null
              }
            }
          }
          case _ => {
            println("AFUHelper::getClassElemAndTreeFromTypeTree: unhandled element: " + el +
              " of kind: " + el.getKind)
            null
          }
        }
        val ct = atf.getTrees.getTree(cel)
        (cel, ct)
      }
      case _ => {
        println("AFUHelper::getClassElemAndTreeFromTypeTree: unhandled tree: " + typetree +
          " of kind: " + typetree.getKind)
        null
      }
    }
  }

  // copied and extended from UtilMDE, which didn't allow easy reuse
  val primitiveTypesJvm = Map("void" -> "V",
    "boolean" -> "Z",
    "byte" -> "B",
    "char" -> "C",
    "double" -> "D",
    "float" -> "F",
    "int" -> "I",
    "long" -> "J",
    "short" -> "S")

}

// Rename this from VariablePosition, as it's now also used for constraint positions.
sealed abstract trait VariablePosition {
  def toAFUString(pos: List[Int]): String
  def init(atf: InferenceAnnotatedTypeFactory, tree: Tree): Unit
}

// private
sealed abstract class WithinClassVP extends VariablePosition {
  def getFQClassName: String = {
    (if (pn != "") pn + "." else "") + cn
  }

  override def toString(): String = {
    "class " + getFQClassName
  }

  // generate the Annotation-File-Utilities representation of the solution
  def toAFUString(pos: List[Int]): String = {
    (if (pn != "") "package " + pn + ":\n" else "package:\n") +
      "class " + cn + ":\n"
  }

  // package name
  protected var pn: String = null
  // class name
  protected var cn: String = null

  def init(atf: InferenceAnnotatedTypeFactory, tree: Tree) {
    val (celem, ctree) = AFUHelper.getEnclosingClassElemAndTree(tree, atf)

    // Scala question: is there a way to directly assign to the fields?
    // Using "val (pn, cn)" doesn't work and error without val...
    val (ppn, ccn) = AFUHelper.getPackageAndClassJvmNames(celem, ctree, atf)
    pn = ppn
    cn = ccn
  }
}

// private
sealed abstract class WithinMethodVP extends WithinClassVP {
  // method name
  private var mn: String = null
  // method parameter types, fully qualified JVM names, surrounded by "()"
  private var mpars: String = null
  // method return type, JVM style
  private var mret: String = null

  override def init(atf: InferenceAnnotatedTypeFactory, tree: Tree) {
    super.init(atf, tree)

    val m = if (!tree.isInstanceOf[MethodTree]) {
      val mtree = TreeUtils.enclosingMethod(atf.getPath(tree))
      if (mtree == null) {
        println("Wrong use of WithinMethodVP! Couldn't determine enclosing method. Tree: " + tree)
        println("Type of this: " + this.getClass)
        println("Compilation unit: " + atf.getRoot)
      }
      mtree
    } else {
      tree.asInstanceOf[MethodTree]
    }

    mn = m.getName.toString
    mpars = {
      import scala.collection.JavaConversions._
      // p.getName() would contain the parameter name
      val sig = (for (p <- m.getParameters()) yield {
        AFUHelper.toJvmTypeName(p.getType, atf)
      }) mkString ("")
      "(" + sig + ")"
    }

    mret = AFUHelper.toJvmTypeName(m.getReturnType, atf)
  }

  def getMethodSignature: String = {
    (if (pn != "") pn + "." else "") + cn + "#" + mn + mpars + ":" + mret
  }

  override def toString(): String = "method " + getMethodSignature

  override def toAFUString(pos: List[Int]): String = {
    "" + super.toAFUString(pos) +
      "method " + mn + mpars + mret + ":\n"
    // using \n here prevents us from adding declaration annotation on the method (e.g. purity)
    // see WithinFieldVP for alternative.
  }

}

// private
sealed abstract class WithinStaticInitVP(blockid: Int) extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " static initializer *" + blockid
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "staticinit *" + blockid + ":\n"
  }
}

case class ClassTypeParameterVP(paramIdx: Int, boundIdx: Int) extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " class type parameter bound " + paramIdx + " & " + boundIdx
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) +
      "bound " + paramIdx + " & " + boundIdx + ":" + AFUHelper.posToAFUType(pos)
  }
}

case class ExtendsVP() extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " extends type"
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) +
      "extends:" + AFUHelper.posToAFUType(pos)
  }
}

case class ImplementsVP(index: Int) extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " implements type " + index
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) +
      "implements " + index + ":" + AFUHelper.posToAFUType(pos)
  }
}

// private
sealed abstract class WithinFieldVP(val name: String) extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " field " + name
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "field " + name + ":"
  }
}

case class FieldVP(override val name: String) extends WithinFieldVP(name) {
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + AFUHelper.posToAFUDecl(pos)
  }

  def getFQName: String = {
    (if (pn != "") pn + "." else "") + cn + "#" + name
  }
}

case class ReturnVP() extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + " return type"
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "return:" + AFUHelper.posToAFUType(pos)
  }
}

case class ParameterVP(id: Int) extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + " parameter " + id
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "parameter " + id + ":" + AFUHelper.posToAFUDecl(pos)
  }
}

case class MethodTypeParameterVP(paramIdx: Int, boundIdx: Int) extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + " method type parameter bound " + paramIdx + " & " + boundIdx
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) +
      "bound " + paramIdx + " & " + boundIdx + ":" + AFUHelper.posToAFUType(pos)
  }
}

private object LocalVP {
  def toString(name: String, id: Int): String = {
    // super.toString() +
    " local variable " + name + "(" + id + ")"
  }

  def toAFUString(name: String, id: Int, pos: List[Int]): String = {
    // super.toAFUString(pos) +
    "local " + name +
      (if (id != 0) " *" + id else "") +
      ":" + AFUHelper.posToAFUDecl(pos)
  }
}

case class LocalInMethodVP(name: String, id: Int) extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + LocalVP.toString(name, id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + LocalVP.toAFUString(name, id, pos)
  }
}

case class LocalInStaticInitVP(name: String, id: Int, val blockid: Int) extends WithinStaticInitVP(blockid) {
  override def toString(): String = {
    super.toString() + LocalVP.toString(name, id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + LocalVP.toAFUString(name, id, pos)
  }
}

private object InstanceOfVP {
  def toString(id: Int): String = {
    " instanceof " + id
  }

  def toAFUString(id: Int, pos: List[Int]): String = {
    "instanceof *" + id + ":" + AFUHelper.posToAFUType(pos)
  }
}

case class InstanceOfInMethodVP(id: Int) extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + InstanceOfVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + InstanceOfVP.toAFUString(id, pos)
  }
}

case class InstanceOfInStaticInitVP(id: Int, val blockid: Int) extends WithinStaticInitVP(blockid) {
  override def toString(): String = {
    super.toString() + InstanceOfVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + InstanceOfVP.toAFUString(id, pos)
  }
}

case class InstanceOfInFieldInitVP(id: Int, override val name: String) extends WithinFieldVP(name) {
  override def toString(): String = {
    super.toString() + InstanceOfVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "\n" + InstanceOfVP.toAFUString(id, pos)
  }
}

private object CastVP {
  def toString(id: Int): String = {
    " cast " + id
  }
  def toAFUString(id: Int, pos: List[Int]): String = {
    "typecast *" + id + ":" + AFUHelper.posToAFUType(pos)
  }
}

case class CastInMethodVP(id: Int) extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + CastVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + CastVP.toAFUString(id, pos)
  }
}

case class CastInStaticInitVP(id: Int, val blockid: Int) extends WithinStaticInitVP(blockid) {
  override def toString(): String = {
    super.toString() + CastVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + CastVP.toAFUString(id, pos)
  }
}

case class CastInFieldInitVP(id: Int, override val name: String) extends WithinFieldVP(name) {
  override def toString(): String = {
    super.toString() + CastVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "\n" + CastVP.toAFUString(id, pos)
  }
}

private object NewVP {
  def toString(id: Int): String = {
    " creation " + id
  }
  def toAFUString(id: Int, pos: List[Int]): String = {
    "new *" + id + ":" + AFUHelper.posToAFUType(pos)
  }
}

case class NewInMethodVP(id: Int) extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + NewVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + NewVP.toAFUString(id, pos)
  }
}

case class NewInStaticInitVP(id: Int, val blockid: Int) extends WithinStaticInitVP(blockid) {
  override def toString(): String = {
    super.toString() + NewVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + NewVP.toAFUString(id, pos)
  }
}

case class NewInFieldInitVP(id: Int, override val name: String) extends WithinFieldVP(name) {
  override def toString(): String = {
    super.toString() + NewVP.toString(id)
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "\n" + NewVP.toAFUString(id, pos)
  }
}


private object ConstraintPosition {
  override def toString(): String = {
    " constraint position"
  }
  def toAFUString(pos: List[Int]): String = {
    throw new RuntimeException("ConstraintPosition must never be written to an AFU file!")
  }
}

case class ConstraintInMethodPos() extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + ConstraintPosition.toString()
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + ConstraintPosition.toAFUString(pos)
  }
}

case class ConstraintInStaticInitPos(val blockid: Int) extends WithinStaticInitVP(blockid) {
  override def toString(): String = {
    super.toString() + ConstraintPosition.toString()
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + ConstraintPosition.toAFUString(pos)
  }
}

case class ConstraintInFieldInitPos(override val name: String) extends WithinFieldVP(name) {
  override def toString(): String = {
    super.toString() + ConstraintPosition.toString()
  }
  override def toAFUString(pos: List[Int]): String = {
    super.toAFUString(pos) + "\n" + ConstraintPosition.toAFUString(pos)
  }
}

case class CalledMethodPos() extends WithinMethodVP {}

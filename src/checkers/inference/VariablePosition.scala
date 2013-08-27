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
import javacutils.TypesUtils
import javacutils.{TreeUtils, ElementUtils }
import com.sun.source.tree.Tree
import com.sun.tools.javac.code.Type
import InferenceUtils.hashcodeOrElse
import InferenceUtils.sumWithMultiplier

//TODO JB: Case classes should NOT have state that isn't injected through the constructor
//TODO JB: Case classes must not be composed in an inheritance hierarchy (unless they are bottoms)
//TODO JB: Make the VPs NOT case classes
// TODO: turn on deprecation warnings and fix the case class hierarchy in this file

private object AFUHelper {
  // use this in the places where there also are
  // declaration annotations
  def posToAFUDecl(pos: List[(Int, Int)]): String = {
    if (pos == null || pos.size == 0)
      "\ntype: "
    else
      "\ntype:\ninner-type " + pos.mkString(", ").replaceAll("(\\(|\\))", "") + ": "
  }

  // use this in places where only type annotations can occur
  // the previous element must not end with \n
  def posToAFUType(pos: List[(Int, Int)]): String = {
    if (pos == null || pos.size == 0)
      " "
    else
      "\ninner-type " + pos.mkString(", ").replaceAll("(\\(|\\))", "") + ": "
  }

  /**
   * Return the binary representation for the given class.
   * This is used for method signatures in the AFU output.
   */
  def getClassJvmName(celem: TypeElement, ctree: ClassTree,
    atf: InferenceAnnotatedTypeFactory[_]): String = {

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
    atf: InferenceAnnotatedTypeFactory[_]): (String, String) = {
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
  def toJvmTypeName(typetree: Tree, atf: InferenceAnnotatedTypeFactory[_]): String = {
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
  def getEnclosingClassElemAndTree(tree: Tree, atf: InferenceAnnotatedTypeFactory[_]): (TypeElement, ClassTree) = {

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
  def getClassElemAndTreeFromTypeTree(typetree: Tree, atf: InferenceAnnotatedTypeFactory[_]): (TypeElement, ClassTree) = {
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
  def toAFUString( pos: List[(Int, Int)] ): String
  def init(atf: InferenceAnnotatedTypeFactory[_], tree: Tree): Unit
}

sealed abstract class WithinClassVP extends VariablePosition {
  def getFQClassName: String = {
    (if (pacakgeName != "") pacakgeName + "." else "") + className
  }

  override def toString(): String = {
    "class " + getFQClassName
  }

  // generate the Annotation-File-Utilities representation of the solution
  def toAFUString(pos: List[(Int, Int)]): String = {
    (if (pacakgeName != "") "package " + pacakgeName + ":\n" else "package:\n") +
      "class " + className + ":\n"
  }

  // package name
  protected var pacakgeName: String = null
  // class name
  protected var className: String = null

  //TODO: Should we have TypeParameters saved in a within class?

  def init(atf: InferenceAnnotatedTypeFactory[_], tree: Tree) {
    val (celem, ctree) = AFUHelper.getEnclosingClassElemAndTree(tree, atf)

    // Scala question: is there a way to directly assign to the fields?
    // Using "val (pn, cn)" doesn't work and error without val...
    val (ppn, ccn) = AFUHelper.getPackageAndClassJvmNames(celem, ctree, atf)
    pacakgeName = ppn
    className = ccn
  }

  /**
   * Note: Case classes usually have a sensible equals method, unless you have
   * private/protected mutable state like all VariablePositions
   *
   * In order to avoid having to add this test to each VariablePosition subclass,
   * this method tests the equivalence of the classes of the two VariablePositions.
   * This means, even if the underlying WithinClassVP fields are the same,
   * if the two objects aren't instances of the exact same class than
   * this method will return false.
   *
   * @param any Object to compare this to
   * @return    Whether or not these two objects are equal
   */
  override def equals(any: Any): Boolean = {
    any match {
      case null                     => false
      case that : WithinClassVP     => this.getClass == that.getClass &&
                                       this.pacakgeName     == that.pacakgeName       &&
                                       this.className       == that.className

      case _                        => false
    }
  }

  override def hashCode(): Int = sumWithMultiplier(List[Object](getClass, pacakgeName, className).map(_.hashCode), 33)
}

// private
sealed abstract class WithinMethodVP extends WithinClassVP {
  private var _isMethodStatic = false

  def isMethodStatic : Boolean = _isMethodStatic

  // method name
  private var methodName: String = null
  // method parameter types, fully qualified JVM names, surrounded by "()"
  private var methodParameters: String = null
  // method return type, JVM style
  private var methodReturn: String = null

  //TODO JB: Refactoring suggestions, make the init method generic and take an appropriate tree
  //TODO JB: And fource people to pass the enclosing method
  override def init(atf: InferenceAnnotatedTypeFactory[_], tree: Tree) {
    super.init(atf, tree)

    val methodTree = if (!tree.isInstanceOf[MethodTree]) {
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

    val methodElem = TreeUtils.elementFromDeclaration(methodTree)
    _isMethodStatic = ElementUtils.isStatic( methodElem )

    methodName = methodTree.getName.toString
    methodParameters = {
      import scala.collection.JavaConversions._
      // p.getName() would contain the parameter name
      val sig = (for (p <- methodTree.getParameters()) yield {
        AFUHelper.toJvmTypeName(p.getType, atf)
      }) mkString ("")
      "(" + sig + ")"
    }


    methodReturn = AFUHelper.toJvmTypeName(methodTree.getReturnType, atf)
  }

  def getMethodSignature: String = {
    (if (pacakgeName != "") pacakgeName + "." else "") + className + "#" + methodName + methodParameters + ":" + methodReturn
  }

  override def toString(): String = "method " + getMethodSignature

  override def toAFUString(pos: List[(Int, Int)]): String = {
    "" + super.toAFUString(pos) +
      "method " + methodName + methodParameters + methodReturn + ":\n"
    // using \n here prevents us from adding declaration annotation on the method (e.g. purity)
    // see WithinFieldVP for alternative.
  }

  override def equals(any : Any) = {
    if( super.equals(any) ) {
      val that = any.asInstanceOf[WithinMethodVP]
      this.methodName    == that.methodName    &&
      this.methodParameters == that.methodParameters &&
      this.methodReturn  == that.methodReturn
    } else {
      false
    }
  }

  override def hashCode() =
    sumWithMultiplier(List(super.hashCode) ++ List(methodName, methodParameters, methodReturn).map(_.hashCode), 33)

}

// private
sealed abstract class WithinStaticInitVP(val blockid: Int) extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " static initializer *" + blockid
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "staticinit *" + blockid + ":\n"
  }

  override def equals(any : Any) = {
    super.equals(any) && this.blockid == any.asInstanceOf[WithinStaticInitVP].blockid
  }

  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, blockid), 33)
  }
}

//TODO JB: Find a way to unify the behavior with ClassTypeParameterBoundVP
//TODO JB: Could create a trait but inheritance doesn't work since they are case classes

case class ClassTypeParameterVP(paramIdx : Int)  extends WithinClassVP with HasParamIdx {

  override def toString(): String = {
    super.toString() + " class type parameter " + paramIdx
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) +
      "typeparam " + paramIdx  + AFUHelper.posToAFUType(pos) + ": "
  }

  override def equals(any : Any) = {
    if( super.equals(any) ) {
      val that = any.asInstanceOf[ClassTypeParameterVP]
      this.paramIdx == that.paramIdx
    } else {
      false
    }
  }

  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, paramIdx), 33)
  }
}

case class ClassTypeParameterBoundVP(paramIdx: Int, boundIdx: Int) extends WithinClassVP with HasParamIdx {
  override def toString(): String = {
    super.toString() + " class type parameter bound " + paramIdx + " & " + boundIdx
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) +
      "bound " + paramIdx + " & " + boundIdx + ": "
  }

  override def equals(any : Any) = {
    if( super.equals(any) ) {
      val that = any.asInstanceOf[ClassTypeParameterBoundVP]
      this.paramIdx == that.paramIdx &&
      this.boundIdx == that.boundIdx
    } else {
      false
    }
  }

  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, paramIdx, boundIdx), 33)
  }
}

case class ExtendsVP() extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " extends type"
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) +
      "extends:" + AFUHelper.posToAFUType(pos)
  }
}

case class ImplementsVP(index: Int) extends WithinClassVP {
  override def toString(): String = {
    super.toString() + " implements type " + index
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) +
      "implements " + index + ":" + AFUHelper.posToAFUType(pos)
  }

  override def equals(any : Any) = {
    super.equals(any) && this.index == any.asInstanceOf[ImplementsVP].index
  }

  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, index), 33)
  }
}

// private
sealed abstract class WithinFieldVP(val name: String) extends WithinClassVP {
  private var _isStatic = false
  def isStatic = _isStatic

  override def toString(): String = {
    super.toString() + ( if (isStatic) " static" else "") +  " field " + name
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "field " + name + ":"
  }


  override def equals(any : Any) = {
    super.equals(any) && this.name == any.asInstanceOf[WithinFieldVP].name
  }

  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, name.hashCode), 33)
  }

  override def init(atf: InferenceAnnotatedTypeFactory[_], tree: Tree) {
    super.init(atf, tree )

    val varTree = if( tree.isInstanceOf[VariableTree]) {
      tree.asInstanceOf[VariableTree]
    } else {
      TreeUtils.enclosingVariable( atf.getPath( tree ) )
    }

    val varElem = TreeUtils.elementFromDeclaration( varTree )
    _isStatic   = ElementUtils.isStatic( varElem )
  }
}

case class FieldVP(override val name: String) extends WithinFieldVP(name) {
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + AFUHelper.posToAFUDecl(pos)
  }

  def getFQName: String = {
    (if (pacakgeName != "") pacakgeName + "." else "") + className + "#" + name
  }
}

case class ConstructorResultVP() extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + " return type"
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "return:" + AFUHelper.posToAFUType(pos)
  }
}

case class ReturnVP() extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + " return type"
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "return:" + AFUHelper.posToAFUType(pos)
  }
}

case class ParameterVP(id: Int) extends WithinMethodVP with HasId {
  override def toString(): String = {
    super.toString() + " parameter " + id
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "parameter " + id + ":" + AFUHelper.posToAFUDecl(pos)
  }
}

//TODO JB: Does the AFU have specific handling for Parameters?  Currently the receiver
//TODO JB: should always be the first(0th) parameter
case class ReceiverParameterVP( ) extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + " receiver"
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "receiver: " + AFUHelper.posToAFUType(pos)
  }
}


case class MethodTypeParameterVP(paramIdx : Int)  extends WithinMethodVP with HasParamIdx {

  override def toString(): String = {
    super.toString() + " method type parameter " + paramIdx
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) +
      "typeparam " + paramIdx  + ":" + AFUHelper.posToAFUType(pos)
  }

  override def equals(any : Any) = {
    if( super.equals(any) ) {
      val that = any.asInstanceOf[MethodTypeParameterVP]
      this.paramIdx == that.paramIdx
    } else {
      false
    }
  }

  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, paramIdx), 33)
  }
}

case class MethodTypeParameterBoundVP(paramIdx: Int, boundIdx: Int) extends WithinMethodVP with HasParamIdx {
  override def toString(): String = {
    super.toString() + " method type parameter bound " + paramIdx + " & " + boundIdx
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) +
      "bound " + paramIdx + " & " + boundIdx + ":" + AFUHelper.posToAFUType(pos)
  }

  override def equals(any : Any) = {
    if( super.equals(any) ) {
      val that = any.asInstanceOf[MethodTypeParameterBoundVP]
      this.paramIdx == that.paramIdx &&
      this.boundIdx == that.boundIdx
    } else {
      false
    }
  }

  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, paramIdx, boundIdx), 33)
  }
}

private object LocalVP {
  def toString(name: String, id: Int): String = {
    // super.toString() +
    " local variable " + name + "(" + id + ")"
  }

  def toAFUString(name: String, id: Int, pos: List[(Int, Int)]): String = {
    // super.toAFUString(pos) +
    "local " + name +
      (if (id != 0) " *" + id else "") +
      ":" + AFUHelper.posToAFUDecl(pos)
  }
}

case class LocalInMethodVP(name: String, id: Int) extends WithinMethodVP with HasIdAndName {
  override def toString(): String = {
    super.toString() + LocalVP.toString(name, id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + LocalVP.toAFUString(name, id, pos)
  }
}

case class LocalInStaticInitVP(name: String, id: Int, override val blockid: Int) extends WithinStaticInitVP(blockid) with HasIdAndName {
  override def toString(): String = {
    super.toString() + LocalVP.toString(name, id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + LocalVP.toAFUString(name, id, pos)
  }
}

private object InstanceOfVP {
  def toString(id: Int): String = {
    " instanceof " + id
  }

  def toAFUString(id: Int, pos: List[(Int, Int)]): String = {
    "instanceof *" + id + ":" + AFUHelper.posToAFUType(pos)
  }
}

case class InstanceOfInMethodVP(id: Int) extends WithinMethodVP with HasId{
  override def toString(): String = {
    super.toString() + InstanceOfVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + InstanceOfVP.toAFUString(id, pos)
  }
}

case class InstanceOfInStaticInitVP(id: Int, override val blockid: Int) extends WithinStaticInitVP(blockid) with HasId {
  override def toString(): String = {
    super.toString() + InstanceOfVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + InstanceOfVP.toAFUString(id, pos)
  }
}

case class InstanceOfInFieldInitVP(id: Int, override val name: String) extends WithinFieldVP(name) with HasId {
  override def toString(): String = {
    super.toString() + InstanceOfVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "\n" + InstanceOfVP.toAFUString(id, pos)
  }
}

private object CastVP {
  def toString(id: Int): String = {
    " cast " + id
  }
  def toAFUString(id: Int, pos: List[(Int, Int)]): String = {
    "typecast *" + id + ":" + AFUHelper.posToAFUType(pos)
  }
}

case class CastInMethodVP(id: Int) extends WithinMethodVP with HasId {
  override def toString(): String = {
    super.toString() + CastVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + CastVP.toAFUString(id, pos)
  }
}

case class CastInStaticInitVP(id: Int, override val blockid: Int) extends WithinStaticInitVP(blockid) with HasId {
  override def toString(): String = {
    super.toString() + CastVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + CastVP.toAFUString(id, pos)
  }
}

case class CastInFieldInitVP(id: Int, override val name: String) extends WithinFieldVP(name) with HasId {
  override def toString(): String = {
    super.toString() + CastVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "\n" + CastVP.toAFUString(id, pos)
  }
}

private object NewVP {
  def toString(id: Int): String = {
    " creation " + id
  }
  def toAFUString(id: Int, pos: List[(Int, Int)]): String = {
    "new *" + id + ":" + AFUHelper.posToAFUType(pos)
  }
}

case class NewInMethodVP(id: Int) extends WithinMethodVP with HasId {
  override def toString(): String = {
    super.toString() + NewVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + NewVP.toAFUString(id, pos)
  }
}

case class NewInStaticInitVP(id: Int, override val blockid: Int) extends WithinStaticInitVP(blockid) with HasId {
  override def toString(): String = {
    super.toString() + NewVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + NewVP.toAFUString(id, pos)
  }
}

case class NewInFieldInitVP(id: Int, override val name: String) extends WithinFieldVP(name) with HasId {
  override def toString(): String = {
    super.toString() + NewVP.toString(id)
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "\n" + NewVP.toAFUString(id, pos)
  }
}

case class RefinementInStaticInitVP( override val blockid : Int, val astPathStr : String ) extends WithinStaticInitVP(blockid) {

  override def toString(): String = "RefinementVP in StaticInit #" + blockid

  override def toAFUString(pos: List[(Int, Int)]): String = {
    "RefinementVPs for RefinementVars will need AST paths instead of element indexes"
  }
  def getASTPathStr(): String = {
    astPathStr
  }
}

case class RefinementInMethodVP( val astPathStr : String )  extends WithinMethodVP {
  override def toString(): String = {
    "RefinementVP in " + super.toString()
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    "RefinementVPs for RefinementVars will need AST paths instead of element indexes"
  }
  def getASTPathStr(): String = {
    astPathStr
  }
}

case class MethodTypeArgumentInMethodVP( paramIdx : Int ) extends WithinMethodVP with HasParamIdx {
  override def toString(): String = {
    "MethodTypeArgumentInMethodVP in " + super.toString()
  }

  override def toAFUString(pos: List[(Int, Int)]): String = {
    "We need to implement this!"
  }
}

case class MethodTypeArgumentInFieldInitVP(paramIdx : Int, id: Int, override val name: String) extends WithinFieldVP(name) with HasId with HasParamIdx {
  override def toString(): String = {
    "MethodTypeArgumentInFieldInitVP in " + super.toString()
  }

  override def toAFUString(pos: List[(Int, Int)]): String = {
    "We need to implement this!"
  }
}

case class MethodTypeArgumentInStaticInitVP( paramIdx : Int, override val blockid : Int ) extends WithinStaticInitVP(blockid)  with HasParamIdx {

  override def toString(): String = "RefinementVP in StaticInit #" + blockid

   override def toAFUString(pos: List[(Int, Int)]): String = {
    "We need to implement this!"
  }
}


private object ConstraintPosition {
  override def toString(): String = {
    " constraint position"
  }
  def toAFUString(pos: List[(Int, Int)]): String = {
    throw new RuntimeException("ConstraintPosition must never be written to an AFU file!")
  }
}

case class ConstraintInMethodPos() extends WithinMethodVP {
  override def toString(): String = {
    super.toString() + ConstraintPosition.toString()
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + ConstraintPosition.toAFUString(pos)
  }
}

case class ConstraintInStaticInitPos(override val blockid: Int) extends WithinStaticInitVP(blockid) {
  override def toString(): String = {
    super.toString() + ConstraintPosition.toString()
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + ConstraintPosition.toAFUString(pos)
  }
}

case class ConstraintInFieldInitPos(override val name: String) extends WithinFieldVP(name) {
  override def toString(): String = {
    super.toString() + ConstraintPosition.toString()
  }
  override def toAFUString(pos: List[(Int, Int)]): String = {
    super.toAFUString(pos) + "\n" + ConstraintPosition.toAFUString(pos)
  }
}

case class CalledMethodPos() extends WithinMethodVP {}


/**
 * In order to accurately determine whether or not a Constraint is a a duplicate,
 * we need to be able to compare the positions of the Variables, and VariablePositions
 * that make up that constraint.  Therefore we need an equals/hashcode for all methods.
 *
 * In the above classes, there are many classes with an id and possibly a name which don't
 * share a common base class.  Therefore, I have defined a couple of traits that implement
 * equals/hashcode for these classes.  Each implementation refers to the super classes method
 * first.
 *
 * These classes should be used only on those methods that have an overridden super.hashCode and super.equals
 * because the intent is to provide structural comparison not referential comparison.  In the above
 * examples WithinMethodVP and WithinClassVP (which are supertypes of all of the VPs) have
 * structural equals/hashCodes.
 *
 * TODO JB: Is there an issue with two repeated method calls or field accesses?
 */

/**
 * A trait to provide equals/hashCode to a class with an id field.
 */
trait HasId {
  val id: Int

  /**
   * Returns true only if super.equals returns true and any.id == this.id
   * @param any
   * @return
   */
  override def equals(any : Any) = {
    super.equals(any) && this.id == any.asInstanceOf[HasId].id
  }

  /**
   * @return a combination of super.hashCode and id
   */
  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, id), 33)
  }
}

//Temporary hack until we figure out a better for naming scheme
trait HasParamIdx {
  val paramIdx : Int
}

/**
 * A trait to provide equals/hashCode to a class with id and name fields
 */
trait HasIdAndName {
  val id   : Int
  val name : String

  /**
   * Return true only if super.equals returns true and any.id == this.id and any.name == this.name
   * @param any
   * @return
   */
  override def equals(any : Any) = {
    if( super.equals(any) ) {
      val that = any.asInstanceOf[HasIdAndName]
      this.name == that.name &&
      this.id   == that.id
    } else {
      false
    }
  }

  /**
   * @return a combination of super.hashCode, name.hashCode, and id
   */
  override def hashCode() = {
    sumWithMultiplier(List(super.hashCode, name.hashCode, id), 33)
  }
}
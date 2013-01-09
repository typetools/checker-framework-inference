package checkers.inference

import java.util.LinkedList
import com.sun.source.util.Trees
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import com.sun.source.tree.LiteralTree
import javax.lang.model.element.Element
import checkers.util.{AnnotatedTypes, TreeUtils, AnnotationUtils}
import checkers.types.AnnotatedTypeMirror.AnnotatedNullType
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable
import checkers.types.AnnotatedTypeMirror.AnnotatedWildcardType
import checkers.types.AnnotatedTypeMirror.AnnotatedArrayType
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType
import checkers.types.AnnotatedTypeMirror.AnnotatedNoType
import javax.lang.model.`type`.TypeKind

import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType
import com.sun.source.tree.Tree.Kind
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.InstanceOfTree
import com.sun.source.tree.TypeCastTree
import com.sun.source.tree.NewArrayTree
import com.sun.source.tree.NewClassTree
import javax.lang.model.element.ElementKind
import javax.lang.model.element.VariableElement
import javax.lang.model.element.ExecutableElement
import checkers.types.TypeAnnotator
import com.sun.source.tree.ArrayTypeTree
import com.sun.source.tree.ParameterizedTypeTree
import com.sun.source.tree.WildcardTree
import com.sun.source.tree.TypeParameterTree
import com.sun.source.tree.VariableTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.Tree
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeFactory
import checkers.basetype.BaseTypeChecker
import checkers.types.TreeAnnotator
import com.sun.source.tree.CompilationUnitTree
import checkers.types.BasicAnnotatedTypeFactory

// import scala.collection.JavaConversions._

/*
 * TODOs:
 * - in @Rep C<@Rep Object> the upper bound must also be adapted! A @Peer upper bound is valid!
 */
class InferenceAnnotatedTypeFactory(checker: InferenceChecker, root: CompilationUnitTree, withCombineConstraints: Boolean)
  extends BasicAnnotatedTypeFactory[InferenceChecker](checker, root, false) {

  // TODO: added for debugging, remove
  def getRoot: CompilationUnitTree = { root }

  def getTrees: Trees = { trees }

  override def getSelfType(tree: Tree): AnnotatedDeclaredType = {
    val sty: AnnotatedDeclaredType = super.getSelfType(tree)
    if (sty != null) {
      sty.clearAnnotations()
      sty.addAnnotation(LiteralThis.getAnnotation)
    }
    sty;
  }

  protected override def postAsMemberOf(ty: AnnotatedTypeMirror,
    owner: AnnotatedTypeMirror, element: Element) {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceAnnotatedTypeFactory::postAsMemberOf: ty: " + ty + " owner: " + owner + " element: " + element)
    }

    if (ty.getKind() != TypeKind.DECLARED && ty.getKind() != TypeKind.ARRAY) {
      // nothing to do
      return
    }
    if (element.getKind() == ElementKind.LOCAL_VARIABLE ||
        element.getKind() == ElementKind.PARAMETER) {
      // the type of local variables and parameters also do not need to change
      return
    }

    val decltype = this.getAnnotatedType(element)
    // fromElement would not contain annotations.
    // getAnnotatedType does what we want!
    // val decltype = this.fromElement(element);
    // println("Element type is: " + decltype)

    // is there a difference between using ty and getting the declared
    // type from the element?
    // Yes! One already performed some incorrect substitutions. For comparison,
    // look at the GUT AnnotatedTypeFactory.

    if (withCombineConstraints) {
      if (InferenceMain.DEBUG(this)) {
        println("InferenceAnnotatedTypeFactory::postAsMemberOf: Combine constraint.")
      }
      val combinedType = InferenceMain.constraintMgr.addCombineConstraints(owner, decltype)

      if (combinedType != ty) { // in case something changed
        InferenceUtils.copyAnnotations(combinedType, ty)
      }
    }
    // println("postAsMemberOf: " + ty)
  }

  override def typeVariablesFromUse(ty: AnnotatedDeclaredType, elem: TypeElement): java.util.List[AnnotatedTypeVariable] = {
    val generic = getAnnotatedType(elem)

    val tvars = generic.getTypeArguments()
    var res = new LinkedList[AnnotatedTypeVariable]()

    import scala.collection.JavaConversions._
    for (atm <- tvars) {
      val tvar = atm.asInstanceOf[AnnotatedTypeVariable]
      val tvarUpper = tvar.getEffectiveUpperBound
      if (withCombineConstraints) {
        val combinedUpper = InferenceMain.constraintMgr.addCombineConstraints(ty, tvarUpper)

        if (combinedUpper != tvarUpper) { // in case something changed
          InferenceUtils.copyAnnotations(combinedUpper, tvarUpper)
        }
      }

      res.add(tvar)
    }

    // println("typeVariablesFromUse: " + ty + " and " + res)
    return res
  }

  override def methodFromUse(tree: MethodInvocationTree): checkers.util.Pair[AnnotatedExecutableType, java.util.List[AnnotatedTypeMirror]] = {
    assert(tree != null)

    // Calling super would already substitute type variables and doesn't work!
    // AnnotatedExecutableType method = super.methodFromUse(tree);

    val methodElt = TreeUtils.elementFromUse(tree)
    var method = this.getAnnotatedType(methodElt)

    // System.out.println("Declared method: " + method)

    if (withCombineConstraints) {
      val mappings = new collection.mutable.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      // Set the receiver
      val exprTree = tree.getMethodSelect()
      val receiverType: AnnotatedTypeMirror =
        if (exprTree.getKind() == Kind.MEMBER_SELECT) {
          val memberSelectTree: MemberSelectTree = exprTree.asInstanceOf[MemberSelectTree]
          getAnnotatedType(memberSelectTree.getExpression())
        } else {
          getSelfType(tree)
        }
      assert(receiverType != null)
      // System.out.println("Receiver: " + receiverType);

      // Modify parameters
      import scala.collection.JavaConversions._
      for (parameterType: AnnotatedTypeMirror <- method.getParameterTypes()) {
        if (InferenceMain.DEBUG(this)) {
          println("InferenceAnnotatedTypeFactory::methodFromUse: Combine constraint for parameter.")
        }
        val combinedType: AnnotatedTypeMirror = InferenceMain.constraintMgr.addCombineConstraints(receiverType, parameterType)
        mappings.put(parameterType, combinedType)
      }

      // Modify return type
      val returnType: AnnotatedTypeMirror = getAnnotatedType(method.getElement()).getReturnType()
      if (returnType.getKind() != TypeKind.VOID) {
        if (InferenceMain.DEBUG(this)) {
          println("InferenceAnnotatedTypeFactory::methodFromUse: Combine constraint for return.")
        }
        val combinedType: AnnotatedTypeMirror = InferenceMain.constraintMgr.addCombineConstraints(receiverType, returnType)
        mappings.put(method.getReturnType, combinedType)
      }

      // TODO: upper bounds, throws?!

      method = method.substitute(mappings)
    } // end optional combine constraints

    // determine substitution for method type variables
    val typeVarMapping =  AnnotatedTypes.findTypeArguments(processingEnv, this, tree)
    val typeargs: java.util.List[AnnotatedTypeMirror] = new LinkedList[AnnotatedTypeMirror]()

    if (!typeVarMapping.isEmpty()) {
      import scala.collection.JavaConversions._
      for (tv <- methodElt.getTypeParameters()) {
        // We take the type variables from the method element, not from the annotated method.
        // For some reason, this way works, the other one doesn't.
        val annotv = this.getAnnotatedType(tv).asInstanceOf[AnnotatedTypeVariable]
        if (typeVarMapping.contains(annotv)) {
          typeargs.add(typeVarMapping.get(annotv))
        } else {
          println("InferenceAnnotatedTypeFactory.methodFromUse: did not find a mapping for " + annotv +
            " in inferred type arguments: " + typeVarMapping)
        }
      }
      method = method.substitute(typeVarMapping)
    }

    // System.out.println("adapted method: " + method);

    return checkers.util.Pair.of(method, typeargs)
  }

  override def constructorFromUse(tree: NewClassTree): checkers.util.Pair[AnnotatedExecutableType, java.util.List[AnnotatedTypeMirror]] = {
    assert(tree != null)

    // using super would substitute too much
    // AnnotatedExecutableType constructor = super.constructorFromUse(tree);

    val ctrElt = TreeUtils.elementFromUse(tree)
    var constructor = this.getAnnotatedType(ctrElt)

    if (withCombineConstraints) {
      val mappings = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      // Get the result type
      val resultType = getAnnotatedType(tree);

      // Modify parameters
      import scala.collection.JavaConversions._
      for (parameterType <- constructor.getParameterTypes()) {
        if (InferenceMain.DEBUG(this)) {
          println("InferenceAnnotatedTypeFactory::constructorFromUse: Combine constraint for parameter.")
        }
        val combinedType: AnnotatedTypeMirror = InferenceMain.constraintMgr.addCombineConstraints(resultType, parameterType)
        mappings.put(parameterType, combinedType)
      }

      // TODO: upper bounds, throws?

      constructor = constructor.substitute(mappings)
    } // end optional combine constraints

    // determine substitution for method type variables
    val typeVarMapping =  AnnotatedTypes.findTypeArguments(processingEnv, this, tree)
    val typeargs: java.util.List[AnnotatedTypeMirror] = new LinkedList[AnnotatedTypeMirror]()

    if (!typeVarMapping.isEmpty()) {
      import scala.collection.JavaConversions._
      for (tv <- ctrElt.getTypeParameters()) {
        // We take the type variables from the constructor element, not from the annotated constructor.
        // For some reason, this way works, the other one doesn't.
        val annotv = this.getAnnotatedType(tv).asInstanceOf[AnnotatedTypeVariable]
        if (typeVarMapping.contains(annotv)) {
          typeargs.add(typeVarMapping.get(annotv))
        } else {
          println("InferenceAnnotatedTypeFactory.constructorFromUse: did not find a mapping for " + annotv +
            " in inferred type arguments: " + typeVarMapping)
        }
      }
      constructor = constructor.substitute(typeVarMapping)
    }

    // System.out.println("adapted constructor: " + constructor);

    return checkers.util.Pair.of(constructor, typeargs)
  }

  protected override def annotateImplicit(tree: Tree, ty: AnnotatedTypeMirror) {
    // TODO: set the type of "super"
    treeAnnotator.visit(tree, ty);

    if (false && InferenceMain.DEBUG(this)) {
      println("InferenceAnnotatedTypeFactory::annotateImplicit end: tree: " + tree + " and type: " + ty)
    }
  }

  override def getAnnotatedTypeFromTypeTree(tree: Tree): AnnotatedTypeMirror = {
    if (InferenceMain.inferenceChecker.extImplsTreeCache.contains(tree)) {
      InferenceMain.inferenceChecker.extImplsTreeCache(tree)
    } else {
      super.getAnnotatedTypeFromTypeTree(tree)
    }
  }

  protected override def annotateImplicit(elt: Element, ty: AnnotatedTypeMirror) {
    if (false && InferenceMain.DEBUG(this)) {
      println("InferenceAnnotatedTypeFactory::annotateImplicit: element: " + elt + " and type: " + ty)
    }

    if (elt.getKind == ElementKind.FIELD || elt.getKind == ElementKind.LOCAL_VARIABLE ||
      elt.getKind == ElementKind.PARAMETER || elt.getKind == ElementKind.EXCEPTION_PARAMETER) {
      // TODO: also do for method parameters? But how would I treat the return type?
      val nty = InferenceMain.inferenceChecker.varElemCache.get(elt.asInstanceOf[VariableElement])
      nty match {
        case Some(aty) => {
          InferenceUtils.copyAnnotations(aty, ty)
        }
        case None => {
          val tree = declarationFromElement(elt)
          if (tree!=null) {
            // we have the source -> visit
            treeAnnotator.visit(tree, ty)
            // the treeAnnotator inserts the type into varElemCache,
            // no need to do it again here
          } else {
            val rf = getRealAnnotatedTypeFactory
            val newty = rf.getAnnotatedType(elt)
            InferenceUtils.copyAnnotations(newty, ty)
          }
        }
      }
    } else if (elt.getKind == ElementKind.METHOD ||
      elt.getKind == ElementKind.CONSTRUCTOR) {
      val nty = InferenceMain.inferenceChecker.exeElemCache.get(elt.asInstanceOf[ExecutableElement])
      nty match {
        case Some(ety) => {
          val tyout = ty.asInstanceOf[AnnotatedExecutableType]
          // TODO: type parameters
          InferenceUtils.copyAnnotations(ety.getReturnType, tyout.getReturnType)

          val inparams = ety.getParameterTypes
          val outparams = tyout.getParameterTypes

          assert(inparams.size == outparams.size)

          for (i <- 0 until inparams.size) {
            InferenceUtils.copyAnnotations(inparams.get(i), outparams.get(i))
          }
        }
        case None => {
          val tree = declarationFromElement(elt)
          if (tree!=null) {
            // we have the source code -> visit it
            treeAnnotator.visit(tree, ty)
          } else {
            val rf = getRealAnnotatedTypeFactory
            val newty = rf.getAnnotatedType(elt)
            InferenceUtils.copyAnnotations(newty, ty)
          }
        }
      }
    } else if (elt.getKind == ElementKind.TYPE_PARAMETER) {
      val nty = InferenceMain.inferenceChecker.typeparamElemCache.get(elt.asInstanceOf[TypeParameterElement])
      nty match {
        case Some(tvty) => {
          val tyout = ty.asInstanceOf[AnnotatedTypeVariable]
          InferenceUtils.copyAnnotations(tvty, tyout)
        }
        case None => {
          val tree = declarationFromElement(elt)
          if (tree!=null) {
            treeAnnotator.visit(tree, ty)
          } else {
            // Can we ever run into a type parameter and not have the source??
            val rf = getRealAnnotatedTypeFactory
            val newty = rf.getAnnotatedType(elt)
            InferenceUtils.copyAnnotations(newty, ty)
          }
        }
      }
    } else if (elt.getKind == ElementKind.CLASS ||
      elt.getKind == ElementKind.INTERFACE) {
      val nty = InferenceMain.inferenceChecker.typeElemCache.get(elt.asInstanceOf[TypeElement])
      nty match {
        case Some(atm) => {
          val tyout = ty.asInstanceOf[AnnotatedTypeMirror]
          InferenceUtils.copyAnnotations(atm, tyout)
        }
        case None => {
          val tree = declarationFromElement(elt)
          if (tree != null) {
            // We found the tree -> annotate it
            treeAnnotator.visit(tree, ty)
          } else {
            // We didn't find the tree -> some element from a binary
            // Use the real checker to add implicits
            val rf = getRealAnnotatedTypeFactory
            val newty = rf.getAnnotatedType(elt)
            InferenceUtils.copyAnnotations(newty, ty)
            // caching doesn't seem necessary
            // InferenceMain.inferenceChecker.typeElemCache.put(elt.asInstanceOf[TypeElement], ty)
          }
        }
      }

    } else {
      println("AnnotateImplicit called for elt: " + elt)
      println("AnnotateImplicit called for elt.kind: " + elt.getKind)
    }

    if (false && InferenceMain.DEBUG(this)) {
      println("InferenceAnnotatedTypeFactory::annotateImplicit end: element: " + elt + " and type: " + ty)
    }
  }

  /*
   * Lazily create the AnnotatedTypeFactory from the "real" Checker.
   * We only need it when we run into an element for which we don't have the source code.
   * Be extremely careful with using this factory! As an AnnotatedTypeMirror contains a
   * reference to the factory that created it, we might get mixed up, e.g. with testing for
   * supported type qualifiers.
   */
  var realAnnotatedTypeFactory: AnnotatedTypeFactory = null
  def getRealAnnotatedTypeFactory = {
    if (realAnnotatedTypeFactory == null) {
      realAnnotatedTypeFactory = InferenceMain.getRealChecker.createFactory(root)
    }
    realAnnotatedTypeFactory
  }

  override def createTreeAnnotator(checker: InferenceChecker): TreeAnnotator = {
    new InferenceTreeAnnotator(checker, this)
  }

  /*
  override def createTypeAnnotator(checker: InferenceChecker): TypeAnnotator = {
    new InferenceTypeAnnotator(checker)
  }

  private class InferenceTypeAnnotator(checker: BaseTypeChecker) extends TypeAnnotator(checker) {
    println("In ITypeA!")

    override def scan(atype: AnnotatedTypeMirror, p: ElementKind): Void = {
      //      if (atype != null && !atype.isAnnotated()) { // System.out.println("Scanning unann type: " + atype)
      // System.out.println("   of element: " + p)
      // }
      return super.scan(atype, p)
    }
  }*/
}

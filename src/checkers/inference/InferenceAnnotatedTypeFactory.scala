package checkers.inference

import java.util.LinkedList
import com.sun.source.util.Trees
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import com.sun.source.tree._
import javax.lang.model.element.Element
import javacutils.{AnnotationUtils, TreeUtils}
import checkers.util.AnnotatedTypes
import checkers.types.AnnotatedTypeMirror._
import javax.lang.model.`type`.{TypeKind}

import com.sun.source.tree.Tree.Kind
import javax.lang.model.element.ElementKind
import javax.lang.model.element.VariableElement
import javax.lang.model.element.ExecutableElement
import checkers.types._

import com.sun.source.tree.NewClassTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.Tree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.MemberSelectTree
import scala.Some
import checkers.source.SourceChecker
import dataflow.cfg.{CFGBuilder, ControlFlowGraph}
import dataflow.cfg.node.Node

import InferenceMain.inferenceChecker
import InferenceMain.constraintMgr
import checkers.flow.{CFTransfer, CFAnalysis, CFValue, CFStore}
import java.util.{List => JavaList}
import javacutils.trees.DetachedVarSymbol
import checkers.basetype.BaseTypeChecker

/*
 * TODOs:
 * - in @Rep C<@Rep Object> the upper bound must also be adapted! A @Peer upper bound is valid!
 */
class InferenceAnnotatedTypeFactory[REAL_TYPE_FACTORY <: BasicAnnotatedTypeFactory[_ <: BaseTypeChecker[REAL_TYPE_FACTORY]]](
   checker: InferenceChecker, root: CompilationUnitTree, withCombineConstraints: Boolean)
  extends AbstractBasicAnnotatedTypeFactory[InferenceChecker, CFValue, CFStore, CFTransfer, CFAnalysis](checker, root, true) {
  postInit();

  override protected def createFlowAnalysis(checker : InferenceChecker, fieldValues : JavaList[javacutils.Pair[VariableElement, CFValue]]) =
    new CFAnalysis(this, processingEnv, checker, fieldValues)

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


  /*override def postDirectSuperTypes(ty : AnnotatedTypeMirror, supertypes : JavaList[_ <: AnnotatedTypeMirror]) {
    val originalAnnos = AnnotationUtils.createAnnotationSet();
    originalAnnos.add(ty.getAnnotations());

    for (supertype <- supertypes) {
      supertype.
    } */

    /*
    super.postDirectSuperTypes(ty, supertypes);
    if (ty.getKind() == TypeKind.DECLARED) {
      for (AnnotatedTypeMirror supertype : supertypes) {
        Element elt = ((DeclaredType) supertype.getUnderlyingType()).asElement();
        annotateImplicit(elt, supertype);
      }
    }
  }        */

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

    if (withCombineConstraints && !InferenceMain.isPerformingFlow) {
      if (InferenceMain.DEBUG(this)) {
        println("InferenceAnnotatedTypeFactory::postAsMemberOf: Combine constraint.")
      }
      val combinedType = constraintMgr.addCombineConstraints(owner, decltype)

      if (combinedType != ty) { // in case something changed
        InferenceUtils.copyAnnotations(combinedType, ty)
      }
    }
    // println("postAsMemberOf: " + ty)
  }

  override def typeVariablesFromUse(ty: AnnotatedDeclaredType, elem: TypeElement): JavaList[AnnotatedTypeVariable] = {
    import scala.collection.JavaConversions._

    val generic = getAnnotatedType(elem)
    val tvars      = generic.getTypeArguments().map(_.asInstanceOf[AnnotatedTypeVariable]).toList
    val tvarUppers = tvars.map(_.getEffectiveUpperBound)

    if(withCombineConstraints && !InferenceMain.isPerformingFlow) {
      tvarUppers.foreach(
        tvarUpper => {
          val combinedUpper = constraintMgr.addCombineConstraints(ty, tvarUpper)

          if (combinedUpper != tvarUpper) { // in case something changed
            InferenceUtils.copyAnnotations(combinedUpper, tvarUpper)
          }
      })
    }

    // println("typeVariablesFromUse: " + ty + " and " + res)
    return new LinkedList[AnnotatedTypeVariable](tvars)
  }

  override def methodFromUse(tree: MethodInvocationTree): javacutils.Pair[AnnotatedExecutableType, JavaList[AnnotatedTypeMirror]] = {
    assert(tree != null)

    // Calling super would already substitute type variables and doesn't work!
    // AnnotatedExecutableType method = super.methodFromUse(tree);

    val methodElt = TreeUtils.elementFromUse(tree)
    var method = this.getAnnotatedType(methodElt)

    // System.out.println("Declared method: " + method)

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

    if (withCombineConstraints && !InferenceMain.isPerformingFlow) {
      val mappings = new collection.mutable.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      // System.out.println("Receiver: " + receiverType);

      // Modify parameters
      import scala.collection.JavaConversions._
      createAndMapCombConstraints("InferenceAnnotatedTypeFactory::methodFromUse: Combine constraint for parameter.",
        mappings, receiverType, method.getParameterTypes():_*)


      // Modify return type
      val returnType: AnnotatedTypeMirror = getAnnotatedType(method.getElement()).getReturnType()
      if (returnType.getKind() != TypeKind.VOID) {
        createAndMapCombConstraints("InferenceAnnotatedTypeFactory::methodFromUse: Combine constraint for return.",
          mappings, receiverType, returnType)
      }

      // TODO: upper bounds, throws?!

      method = method.substitute(mappings)
    } // end optional combine constraints


    //TODO JB: Ask Werner!
    //TODO JB: a method public <T> T retT( T t)  when called retT("nn") will return a value of type Literal("nn")
    val methodTypeAsMemberOfReceiver = AnnotatedTypes.asMemberOf(types, this, receiverType, methodElt)
    val (typeArgs, outputMethod) = substituteTypeArgs(tree, methodElt, methodTypeAsMemberOfReceiver)
    // System.out.println("adapted method: " + method);

    return javacutils.Pair.of(outputMethod, typeArgs)
  }

  override def constructorFromUse(tree: NewClassTree): javacutils.Pair[AnnotatedExecutableType, JavaList[AnnotatedTypeMirror]] = {
    assert(tree != null)

    // using super would substitute too much
    // AnnotatedExecutableType constructor = super.constructorFromUse(tree);

    val ctrElt = TreeUtils.elementFromUse(tree)
    var constructor = this.getAnnotatedType(ctrElt)

    if (withCombineConstraints && !InferenceMain.isPerformingFlow) {

      val resultType : AnnotatedDeclaredType = getAnnotatedType(tree);
      val mappings = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      // Modify parameters
      import scala.collection.JavaConversions._
      createAndMapCombConstraints("InferenceAnnotatedTypeFactory::constructorFromUse: Combine constraint for parameter.",
                                  mappings, resultType, constructor.getParameterTypes:_*)

      // TODO: upper bounds, throws?

      constructor = constructor.substitute(mappings)
    }


    val (typeArgs, outputConstructor) = substituteTypeArgs(tree, ctrElt, constructor)
    // System.out.println("adapted constructor: " + constructor);

    return javacutils.Pair.of(outputConstructor, typeArgs)
  }

  /**
   * Get a map of the type arguments for any type variables in tree.  Create a list of type arguments by
   * replacing each type parameter of exeEle by it's corresponding argument in the map.  Substitute the
   * type parameters in exeType with those in the type arg map.
   *
   * @param tree Tree representing the method or constructor we are analyzing
   * @param exeEle The element corresponding with tree
   * @param exeType The type as determined by this class of exeEle
   * @return A list of the actual type arguments for the type parameters of exeEle and exeType with it's type
   *         parameters replaced by the actual type arguments
   */
  private def substituteTypeArgs[ETREE <: ExpressionTree](tree : ETREE, exeEle : ExecutableElement,
                exeType : AnnotatedExecutableType) : (JavaList[AnnotatedTypeMirror], AnnotatedExecutableType) = {
    import scala.collection.JavaConversions._

    // determine substitution for method type variables
    val typeVarMapping  = AnnotatedTypes.findTypeArguments(processingEnv, this, tree)

    //TODO: Do we want to print missing if typeVarMapping is empty?
    if(typeVarMapping.isEmpty()) {
      (List[AnnotatedTypeVariable](), exeType)

    }  else {
      // We take the type variables from the method element, not from the annotated method.
      // For some reason, this way works, the other one doesn't.
      val typeParamAtvs = exeEle.getTypeParameters.iterator.map(tp =>
        this.getAnnotatedType(tp).asInstanceOf[AnnotatedTypeVariable])

      val (found, missing) = typeParamAtvs.partition(atv => typeVarMapping.contains(atv) )

      val actualTypeArgs = found.map(f => typeVarMapping(f))

      missing.foreach(missingAtv =>
        println("InferenceAnnotatedTypeFactory.methodFromUse: did not find a mapping for " + missingAtv +
          " in inferred type arguments: " + typeVarMapping)
      )

      //TODO: This used to be forced to a linked list, do that gain?
      (new LinkedList[AnnotatedTypeMirror](actualTypeArgs.toList), exeType.substitute(typeVarMapping))
    }
  }

  protected override def performFlowAnalysis(classTree : ClassTree) {
    InferenceMain.setPerformingFlow(true)
    super.performFlowAnalysis(classTree)
    InferenceMain.setPerformingFlow(false)
  }

  //TODO: Ask Werner but I guess we just turn flow off with the constructor flag instead
  protected override def annotateImplicitWithFlow(tree: Tree, ty: AnnotatedTypeMirror) {
    // TODO: set the type of "super"

    if ( tree.isInstanceOf[ClassTree] ) {
      val classTree = tree.asInstanceOf[ClassTree]
      if (!scannedClasses.containsKey(classTree)) {
        performFlowAnalysis(classTree)
      }
    }

    treeAnnotator.visit(tree, ty);

    Option( getInferredValueFor(tree) ).map( inf => applyInferredAnnotations(ty, inf) )

    /**
     * The data flow framework refines the all references to a variable after an assignment but (rightly) does not
     * refine the left-hand side of an assignment statement to that of the refined variable.  However, it is
     * more precise for the inference framework to generate the constraints on these assignments using the
     * RefinementVariable resulting from that assignment rather than the original variable itself.  Therefore,
     * we replace the unrefined left hand side of an assignment with the refined variable in order to generate the
     * proper constraints.
     *
     * This should only happen for the tree at which a RefinementVariable is created ( i.e. the assignment )
     */
    InferenceMain.slotMgr.replaceWithRefVar(ty, tree)

    if (false && InferenceMain.DEBUG(this)) {
      println("InferenceAnnotatedTypeFactory::annotateImplicit end: tree: " + tree + " and type: " + ty)
    }
  }

  override def getAnnotatedTypeFromTypeTree(tree: Tree): AnnotatedTypeMirror = {

    if (inferenceChecker.extImplsTreeCache.contains(tree)) {
      inferenceChecker.extImplsTreeCache(tree)

    } else {
      super.getAnnotatedTypeFromTypeTree(tree)

    }
  }



  /**
   * TODO: Expand
   * If we have a cached AnnotatedTypeMirror for the element then copy its annotations to ty
   * else if we can get the source tree for the declaration of that element visit it with the tree annotator
 * else get the AnnotatedTypeMirror from the real AnnotatedTypeFactory and copy its annotations to ty
 * @param elt The element to annotate
 * @param ty The AnnotatedTypeMirror corresponding to elt
 * */
  protected override def annotateImplicit(elt: Element, ty: AnnotatedTypeMirror) {
    import ElementKind._

    if (false && InferenceMain.DEBUG(this)) {
      println("InferenceAnnotatedTypeFactory::annotateImplicit: element: " + elt + " and type: " + ty)
    }

    /**
     * //TODO: Whose copying what to whom
     *
     * If we have a cached AnnotatedTypeMirror for the element then use copyMethod to copy
     *   annotations from the cached AnnotatedTypeMirror to ty
     * else if we can get the source tree for the declaration of that element visit it with the tree annotator
     *   with ty as the AnnotatedTypeMirror
     * else get the AnnotatedType from the real AnnotatedTypeFactory and copy it's annotations using the
     *   InferenceUtils.copyAnnotations method
     * @param element element we whose annotations are being determined
     * @param cache   the cache in which the element might be cached (should be in InferenceMain.inferenceChecker)
     * @param copyMethod When a cached version of the AnnotatedTypeMirror is found, use this copyMethod to copy its annotations
     * @tparam ELE Type of element
     * @tparam ATM Type of AnnotatedTypeMirror that corresponds to element
     */
    def visitOrCopy[ELE <: Element, ATM <: AnnotatedTypeMirror](element : ELE,
                                                                cache   : scala.collection.mutable.HashMap[ELE, ATM],
                                                                copyMethod: (ATM, ATM) => Unit ) {
      cache.get(element) match {
        case Some(atm : AnnotatedTypeMirror) => copyMethod(atm.asInstanceOf[ATM], ty.asInstanceOf[ATM])
        case None =>
          Option(declarationFromElement(element)) match {
            case Some(tree : Tree) =>  //If we have the source code visit the tree
              treeAnnotator.visit(tree, ty)

            case None =>               //If we do not have the source code, use the realATF's type
              val newAty = realAnnotatedTypeFactory.getAnnotatedType(elt)
              InferenceUtils.copyAnnotations(newAty, ty)
          }
      }
    }
    //TODO: Perhaps create a ElementKind => cache method
    elt.getKind match {
      case FIELD | LOCAL_VARIABLE | PARAMETER | EXCEPTION_PARAMETER =>
        visitOrCopy(elt.asInstanceOf[VariableElement], inferenceChecker.varElemCache,
          InferenceUtils.copyAnnotations)

      case METHOD | CONSTRUCTOR => //TODO: I am not sure why we need a different copy method for this case
        visitOrCopy[ExecutableElement, AnnotatedExecutableType] (elt.asInstanceOf[ExecutableElement], inferenceChecker.exeElemCache,
          copyParameterAndReturnTypes)

      case TYPE_PARAMETER =>
        visitOrCopy(elt.asInstanceOf[TypeParameterElement], inferenceChecker.typeparamElemCache,
          InferenceUtils.copyAnnotations)

      case CLASS | INTERFACE =>
        visitOrCopy(elt.asInstanceOf[TypeElement], inferenceChecker.typeElemCache,
          InferenceUtils.copyAnnotations)

      case _ =>
        println("AnnotateImplicit called for elt: "      + elt )
        println("AnnotateImplicit called for elt.kind: " + elt.getKind )
    }

    if (false && InferenceMain.DEBUG(this)) {
      println("InferenceAnnotatedTypeFactory::annotateImplicit end: element: " + elt + " and type: " + ty)
    }
  }

  /*
   * //TODO: Talk to Werner about this
   * Lazily create the AbstractAnnotatedTypeFactory from the "real" Checker.
   * We only need it when we run into an element for which we don't have the source code.  TODO: IS THIS TRUE?
   * Be extremely careful with using this factory! As an AnnotatedTypeMirror contains a
   * reference to the factory that created it, we might get mixed up, e.g. with testing for
   * supported type qualifiers.
   */
  lazy val realAnnotatedTypeFactory = InferenceMain.getRealChecker.createFactory(root)
    .asInstanceOf[BasicAnnotatedTypeFactory[_ <: SourceChecker[REAL_TYPE_FACTORY]]] //TODO: CHANGE PARAM?

  override def createTreeAnnotator(checker: InferenceChecker): TreeAnnotator = {
    new InferenceTreeAnnotator(checker, this)
  }

  /**
   * Use InferenceUtils.copyAnnotations to deep copy the return type and parameter type annotations
   * from one AnnotatedExecutableType to another
   * @param from The executable type with annotations to copy
   * @param to   The executable type to which annotations will be copied
   */
  private def copyParameterAndReturnTypes(from : AnnotatedExecutableType, to : AnnotatedExecutableType) {
    import scala.collection.JavaConversions._
    InferenceUtils.copyAnnotations(from.getReturnType, to.getReturnType)

    val fromParams  = from.getParameterTypes
    val toParams    = to.getParameterTypes

    assert(fromParams.size == toParams.size)

    fromParams.zip( toParams )
      .foreach( fromToTo => InferenceUtils.copyAnnotations(fromToTo._1, fromToTo._2) )
  }

  /**
   * Foreach type in types:
   *     create a combConstraint/combined type
   *     add a mapping from type -> combinedType to mutMap
   *     print out debug msg
   *
   * @param debugMsg Message to print for each created combined constraint when debugging
   * @param mutMap   Map to which the following mappings are added types -> newCombineType
   * @param owner    The combineConstraint owner (see constraintMgr.addCombineConstraints)
   * @param types    types that are combined with owner
   */
  private def createAndMapCombConstraints(debugMsg:String,
                                          mutMap : scala.collection.mutable.Map[AnnotatedTypeMirror, AnnotatedTypeMirror],
                                          owner : AnnotatedTypeMirror, types : AnnotatedTypeMirror*) {
    val typeToCombinedType = types.map(ty => (ty -> constraintMgr.addCombineConstraints(owner, ty)))
    mutMap ++= typeToCombinedType

    //TODO: I have done this in order to match the previous behavior but perhaps we could instead summarize
    //TODO: All comb constraints?
    if (InferenceMain.DEBUG(this)) {
      for(i <- 0 until typeToCombinedType.size) {
        println(debugMsg)
      }
    }

  }

  //TODO: Perhaps remove this, but I don't feel like messing around with reflection at the moment
  override def createFlowTransferFunction(analysis : CFAnalysis) = new InferenceTransfer(analysis)
}

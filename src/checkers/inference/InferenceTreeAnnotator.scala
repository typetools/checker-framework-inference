package checkers.inference

import com.sun.source.tree._
import javax.lang.model.`type`.ArrayType
import javax.lang.model.element._
import javax.lang.model.`type`.DeclaredType
import javax.lang.model.`type`.TypeVariable
import annotator.scanner.CommonScanner
import annotator.scanner.LocalVariableScanner
import annotator.scanner.NewScanner
import annotator.scanner.StaticInitScanner
import annotator.scanner.CastScanner
import annotator.scanner.InstanceOfScanner
import com.sun.source.tree.Tree.Kind
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable
import checkers.types.AnnotatedTypeMirror.AnnotatedWildcardType
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType
import javax.lang.model.`type`.TypeKind
import checkers.types.AnnotatedTypeMirror.AnnotatedArrayType
import checkers.types.AnnotatedTypeMirror
import checkers.types.TreeAnnotator
import javacutils.{TreeUtils, AnnotationUtils}
import com.sun.source.tree.AnnotatedTypeTree
import com.sun.source.tree.ClassTree
import com.sun.source.tree.ParameterizedTypeTree
import com.sun.source.tree.TypeParameterTree
import com.sun.source.tree.LiteralTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.BinaryTree
import com.sun.source.tree.ArrayTypeTree
import com.sun.source.tree.NewClassTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.Tree
import com.sun.source.tree.NewArrayTree
import com.sun.source.tree.WildcardTree
import com.sun.source.tree.InstanceOfTree
import com.sun.source.tree.VariableTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.TypeCastTree
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.ElementKind

import InferenceMain.slotMgr
import InferenceMain.constraintMgr
import InferenceMain.inferenceChecker
import com.sun.source.util.TreePath
import javacutils.trees.DetachedVarSymbol

class InferenceTreeAnnotator(checker: InferenceChecker,
  typeFactory: InferenceAnnotatedTypeFactory) extends TreeAnnotator(checker, typeFactory) {

  // AnnotatedTypeFactory only caches class and method trees, therefore variable and
  // others might get called multiple time.
  val visitedtrees = new collection.mutable.HashSet[Tree]()

  def createVarsAndConstraints(varpos: VariablePosition, toptree: Tree, ty: AnnotatedTypeMirror) {
    ty match {
      case aat: AnnotatedArrayType =>
        createVarsAndConstraints(varpos, toptree, toptree, ty, List(-1))

      case _ =>
        createVarsAndConstraints(varpos, toptree, toptree, ty, null)
    }
  }

  // TODO: return type in method signatures is not fully qualified!

  def createVarsAndConstraints(varpos: VariablePosition, toptree: Tree, curtree: Tree, ty: AnnotatedTypeMirror,
                               pos: List[Int]) {

    // println("Entering createVarsAndConstraints: curtree: " + curtree)
    // println("Entering createVarsAndConstraints: ty: " + ty)
    if (ty == null
      || ty.getKind == TypeKind.VOID
      || ty.getKind == TypeKind.NULL
      || ty.getKind == TypeKind.NONE ) {
      return
    }

    ty match {
      case aat: AnnotatedArrayType => {
        val aty = curtree match {
          case att: ArrayTypeTree => att.getType
          case nat: NewArrayTree => nat.getType
          case antt: AnnotatedTypeTree => {
            antt
          }
          case t => {
            println("InferenceTreeAnnotator: Unexpected tree: " + curtree + " of type: " + (if (curtree != null) curtree.getClass() else "null"))
            println("Unexpected tree within: " + toptree)
            if (curtree == null) return
            null
          }
        }
        if (pos.last == -1) {
          annotateTopLevel(varpos, toptree, curtree, aat, pos.dropRight(1))
        } else {
          annotateTopLevel(varpos, toptree, curtree, aat, pos)
        }

        createVarsAndConstraints(varpos, toptree, aty, aat.getComponentType, pos.dropRight(1) :+ (pos.last + 1))
      }
      case w: AnnotatedWildcardType => {
        val wct = curtree.asInstanceOf[WildcardTree]
        wct.getKind match {
          case Tree.Kind.UNBOUNDED_WILDCARD => {
            // TODO: add implicit Object bound
            // println("InferenceTreeAnnotator: unbound wildcard. tree: " + wct + " type: " + w)
            annotateTopLevel(varpos, toptree, wct, w, pos)
          }
          case Tree.Kind.EXTENDS_WILDCARD => {
            createVarsAndConstraints(varpos, toptree, wct.getBound, w.getExtendsBound, pos)
          }
          case Tree.Kind.SUPER_WILDCARD => {
            createVarsAndConstraints(varpos, toptree, wct.getBound, w.getSuperBound, pos)
          }
        }
      }

      case atv: AnnotatedTypeVariable => {
        // Copy the variable from the upper bound of the declaration of the type variable here
        val typaramel = atv.getUnderlyingType.asElement.asInstanceOf[TypeParameterElement]
        val genelem = typaramel.getGenericElement

        genelem match {
          case te: TypeElement => {
            import scala.collection.JavaConversions._
            for (tp <- te.getTypeParameters) {
              if (tp.getSimpleName.equals(typaramel.getSimpleName)) {
                val annotp = typeFactory.getAnnotatedType(tp).asInstanceOf[AnnotatedTypeVariable]
                InferenceUtils.copyAnnotations(annotp.getUpperBound, atv.getUpperBound)
                // TODO: no lower bounds.
                // TODO: needed?
                // inferenceChecker.typeparamElemCache += (typaramel -> atv)
              }
            }
          }
          case ee: ExecutableElement => {
            import scala.collection.JavaConversions._
            val eeTypeNames = ee.getTypeParameters.map(_.getSimpleName)
            val typaramelSimpleName = typaramel.getSimpleName
            val matchingParam = ee.getTypeParameters.find(_.getSimpleName == typaramel.getSimpleName)

            matchingParam.map( typeParam => {

              //TODO JB: Is this the correct way to annotate a Type Variable VP
              varpos match {
                case paramPos : ParameterVP =>
                  annotateTopLevel(varpos, toptree, curtree, atv, pos)
                case _ =>
              }

              val annotp = typeFactory.getAnnotatedType(typeParam).asInstanceOf[AnnotatedTypeVariable]
              InferenceUtils.copyAnnotations(annotp.getUpperBound, atv.getUpperBound)

              // TODO: needed?
              // inferenceChecker.typeparamElemCache += (typaramel -> atv)
            })
          }
          case _ => {
            println("What should I do for: " + genelem)
            if (genelem != null)
              println("Genelem is of class: " + genelem.getClass)
          }
        }
        // typeFactory.getAnnotatedType(
        // nothing to do for type variables
        // TODO: at least for the Universe type systems, other systems
        // might want annotations on type variables
        // val it = curtree.asInstanceOf[IdentifierTree]
      }
      case adt: AnnotatedDeclaredType => {
        // println("AnnotDeclType with curtree: " + curtree.getClass)
        curtree match {
          case it  : IdentifierTree => annotateTopLevel(varpos, toptree, it, adt, pos)

          case att : AnnotatedTypeTree =>
          // Don't do anything with the type. SlotManager.extractSlot will complain if it doesn't understand the annotation
          // println("Existing annotated type: " + att)

          // Note: bounds are an empty list for implicit Object, but "ty" is correct
          // TODO: what should be done with implicit Object bound?
          case tpt : TypeParameterTree if tpt.getBounds().isEmpty() => annotateTopLevel(varpos, toptree, tpt, adt, pos)
          case tpt : TypeParameterTree => createVarsAndConstraints(varpos, toptree, tpt.getBounds.get(0), adt, pos)

          // TODO: this seems to be a qualified access, like java.util.List
          // It seems these just come for top-level types
          case mst : MemberSelectTree  => annotateTopLevel(varpos, toptree, curtree, adt, pos)

          case ptt : ParameterizedTypeTree =>
            annotateTopLevel(varpos, toptree, ptt.getType, adt, pos)

            val pta = ptt.getTypeArguments
            val tas = adt.getTypeArguments

            if (pta.size == tas.size) {

              // println("Node: " + ptt + " with type: " + ptt.getClass)

              val npos = if (pos == null) List() else pos

              for (i <- 0 until tas.size) {
                val tasi = tas.get(i)
                tasi match {
                  case aat: AnnotatedArrayType => {
                    createVarsAndConstraints(varpos, toptree, pta.get(i), tasi, npos :+ i :+ (-1))
                  }
                  case _ => {
                    createVarsAndConstraints(varpos, toptree, pta.get(i), tasi, npos :+ i)
                  }
                }

                if (tasi.isInstanceOf[AnnotatedDeclaredType]) {
                  val elem = tasi.getUnderlyingType().asInstanceOf[DeclaredType].asElement().asInstanceOf[TypeElement]
                  inferenceChecker.typeElemCache += (elem -> tasi)
                } else if (tasi.isInstanceOf[AnnotatedArrayType]) {
                  // TODO: do we need to cache something for arrays???
                } else if (tasi.isInstanceOf[AnnotatedTypeVariable]) {
                  // println("InferenceTreeAnnotator::declaredtype: is there something to do for type variable: " + tas.get(i))
                  // TODO: note that TVs are in typeparamElemCache, can I unify these two?
                } else if (tasi.isInstanceOf[AnnotatedWildcardType]) {
                  // Wildcards are already handled above
                } else {
                  println("InferenceTreeAnnotator unexpected declaredtype: is there something to do for: " + tasi)
                }
              }
            } else {
              // TODO: what a strange case is this???
              println("InferenceTreeAnnotator: unexpected pta: " + pta + " tas: " + tas + " and toptree: " + toptree)
            }

          case _ =>
            println("InferenceTreeAnnotator::createVarsAndConstraints: unexpected tree found:")
            println("  curtree: " + curtree)
            if (curtree != null) {
              println("  curtree has type: " + curtree.getClass)
            }
        }

      }
      case apt: AnnotatedPrimitiveType =>
        annotateTopLevel(varpos, toptree, curtree, apt, pos)

      case ty => {
        println("TODO! Unhandled tree/type: " + (curtree, ty) +
          " of types " + (if (curtree != null) curtree.getClass else "null",
            if (ty != null) ty.getClass else "null"))
      }
    }
  }

  /**
   * If the given type needs an annotation (see InferenceTypeChecker.needsAnnotation) then either
   * create a variable for the current tree and generate an annotation for this variable or retrieve a previously
   * cached annotation.  If the the given type is already annotated then generate equality constraints between
   * the new/cached annotation and all of the annotations on the given type.
   * Clear the old annotations from the type and replace them with the new annotation.
   * @param varPos The variable position for the given type ty
   * @param topTree The original tree upon which createVarsAndConstraints was called from the visit methods of
   *                InferenceTreeAnnotator
   * @param curTree As createVarsAndAnnotations descends into a particular tree, the latest tree being examined is held
   *
   * @param ty
   * @param pos
   */
  def annotateTopLevel(varPos: VariablePosition, topTree: Tree, curTree: Tree, ty: AnnotatedTypeMirror,
    pos: List[Int]) {
    // println("InferenceTreeAnnotator::annotateTopLevel: curTree: " + curTree)
    // println("InferenceTreeAnnotator::annotateTopLevel: topTree: " + topTree)

    if ( !InferenceMain.getRealChecker.needsAnnotation(ty) ) {
      // println("InferenceTreeAnnotator::annotateTopLevel: no annotation for type: " + ty + " with tree: " + topTree)
    } else {
      // println("InferenceTreeAnnotator::annotateTopLevel: type before: " + ty)

      val annot = slotMgr.getCachedVariableAnnotation(curTree).getOrElse(
        slotMgr.createVariableAnnotation(varPos, typeFactory, topTree, curTree, pos) )

      if (ty.isAnnotated && !InferenceMain.isPerformingFlow) {
        // println("InferenceTreeAnnotator::annotateTopLevel: already annotated type: " + ty + " with tree: " + topTree)

        val newSlot = slotMgr.extractSlot(annot)

        val oldAnnos = InferenceUtils.clearAnnos(ty)

        import scala.collection.JavaConversions._
        val oldSlots = oldAnnos.map( slotMgr.extractSlot _ )
        oldSlots.foreach( oldSlot => constraintMgr.addEqualityConstraint(newSlot, oldSlot) )
      }

      //add the new annotation
      ty.addAnnotation(annot)

      // println("InferenceTreeAnnotator::annotateTopLevel: type after: " + ty)

    }
  }

  def isAnonymousClass(cls: ClassTree): Boolean = {
    cls.getSimpleName == null || cls.getSimpleName().toString().equals("")
  }

  override def visitClass(node: ClassTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      val firstline = node.toString.lines.dropWhile(_.isEmpty).next
      println("InferenceTreeAnnotator::visitClass type: " + p + "\n   tree: " + firstline)
    }

    if (isAnonymousClass(node)) {
    // For anonymous classes, we do not create additional variables, as they
    // were already handled by the visitNewClass. This would otherwise result
    // in new variables for an extends clause, which then cannot be inserted.
    // val firstline = node.toString.lines.dropWhile(_.isEmpty).next
    // println("InferenceTreeAnnotator::visitClass anonymous class type: " + p + "\n   tree: " + firstline)

      return super.visitClass(node, p)
    }

    val tas = p.asInstanceOf[AnnotatedDeclaredType].getTypeArguments
    val ntp = node.getTypeParameters

    assert(tas.size == ntp.size)

    for (i <- 0 until tas.size) {
      tas.get(i) match {
        case tv : AnnotatedTypeVariable =>
          val ntv = ntp.get(i)

          // TODO: when is the bound index not 0?
          val ctpvp = ClassTypeParameterVP(i, 0)
          ctpvp.init(typeFactory, node)
          createVarsAndConstraints(ctpvp, ntv, tv.getUpperBound)

          // TODO: lower bounds impossible?
          // createVarsAndConstraints(xxx, ntv, tv.getLowerBound)

          val elem = tv.getUnderlyingType().asInstanceOf[TypeVariable].asElement().asInstanceOf[TypeParameterElement];
          inferenceChecker.typeparamElemCache += (elem -> tv)

        case typeArg =>  //TODO JB:
          println("Undhandled type args: " + typeArg.toString)
      }
    }

    val ext = node.getExtendsClause()
    if (ext != null) {
      // always a declared type
      val ety = typeFactory.getAnnotatedTypeFromTypeTree(ext).asInstanceOf[AnnotatedDeclaredType]
      val evp = ExtendsVP()
      evp.init(typeFactory, node)

      ety.addAnnotation(InferenceMain.getRealChecker.selfQualifier)
      createVarsAndConstraints(evp, ext, ety)

      inferenceChecker.extImplsTreeCache += (ext -> ety)

      // println("Annoted extends type: " + ety + " tree: " + ext)
    }

    val impls = node.getImplementsClause()
    for (impIdx <- 0 until impls.size) {
      val imp = impls.get(impIdx)
      // always a declared type
      val ity = typeFactory.getAnnotatedTypeFromTypeTree(imp).asInstanceOf[AnnotatedDeclaredType]
      val ivp = ImplementsVP(impIdx)
      ivp.init(typeFactory, node)

      ity.addAnnotation(InferenceMain.getRealChecker.selfQualifier)
      createVarsAndConstraints(ivp, imp, ity)

      inferenceChecker.extImplsTreeCache += (imp -> ity)

      // println("Annoted implements type: " + ity)
    }

    super.visitClass(node, p)
  }

  override def visitMethod(node: MethodTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      val firstline = node.toString.lines.dropWhile(_.isEmpty).next
      println("InferenceTreeAnnotator::visitMethod type: " + p + "\n   tree: " + firstline)
    }
    val mtype = p.asInstanceOf[AnnotatedExecutableType]

    if (node.getReturnType != null) {
      // Return type is null for constructors; for these we don't need a constraint here.
      val vpret = ReturnVP()
      vpret.init(typeFactory, node) // node.getReturnType
      createVarsAndConstraints(vpret, node.getReturnType, mtype.getReturnType)
    }

    val params = mtype.getParameterTypes
    val nparams = node.getParameters
    assert(params.size == nparams.size)

    // parameters automatically visited by a visitVariable
    for (i <- 0 until params.size) {
      val vpparam = ParameterVP(i)
      vpparam.init(typeFactory, node) // nparams.get(i)
      createVarsAndConstraints(vpparam, nparams.get(i).getType, params.get(i))
    }

    val tvars = mtype.getTypeVariables
    val ntvars = node.getTypeParameters
    assert(tvars.size == ntvars.size)

    for (i <- 0 until tvars.size) {
      // TODO: use correct bound index instead of 0
      val mtpvp = MethodTypeParameterVP(i, 0)
      mtpvp.init(typeFactory, node) // ntvars.get(i)
      createVarsAndConstraints(mtpvp, ntvars.get(i), tvars.get(i).getUpperBound)

      val elem = tvars.get(i).getUnderlyingType().asInstanceOf[TypeVariable].asElement().asInstanceOf[TypeParameterElement];
      inferenceChecker.typeparamElemCache += (elem -> tvars.get(i))

      // TODO: when is there a lower bound?
      // createVarsAndConstraints(null, ntvars.get(i), tvars.get(i).getLowerBound)
    }

    super.visitMethod(node, p)

    val elem = TreeUtils.elementFromDeclaration(node)
    inferenceChecker.exeElemCache += (elem -> mtype)

    // println("After visitMethod tree: " + node)
    // println("After visitMethod type: " + p)

    null
  }

  override def visitVariable(node: VariableTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitVariable type: " + p + " tree: " + node)
    }

    val elem = TreeUtils.elementFromDeclaration(node)

    //TODO JB: Another temporary KLUDGE

    val varname = node.getName.toString

    //TODO JB: WHat to actually do with this
    val ignoredDetachedSymbols = List("index#num", "iter#num", "assertionsEnabled#num")

    //Do not create a variable for an index resulting from desugaring arrays in foreach loops
    if( elem.isInstanceOf[DetachedVarSymbol] && ignoredDetachedSymbols.find( varname startsWith _ ).isDefined ) {
      return super.visitVariable(node, p);
    }

    //Do create a variable for the final reference to the array created when desugaring arrays in foreach loops
    val treeToScan = if( elem.isInstanceOf[DetachedVarSymbol] ) {
      node.getInitializer
    } else {
      node
    }
    val varpos = methodStaticOrFieldToVp(treeToScan, LocalVariableScanner.indexOfVarTree(_:TreePath, _:Tree, varname),
                                         LocalInMethodVP     apply (varname, _:Int),
                                         LocalInStaticInitVP apply (varname, _:Int, _:Int),
                                         (Int, String) => FieldVP(varname), true)

    createVarsAndConstraints(varpos, node.getType, p)

    if ( elem.getKind == ElementKind.FIELD     || elem.getKind == ElementKind.LOCAL_VARIABLE      ||
         elem.getKind == ElementKind.PARAMETER || elem.getKind == ElementKind.EXCEPTION_PARAMETER  ) {
      // Add a copy of p to the cache, to prevent further modifications to effect it
      // TODO: is this also needed elsewhere???
      val c = p.getCopy(true)
      inferenceChecker.varElemCache += (elem -> c)
    } else {
      println("InferenceTreeAnnotator::visitVariable on " + elem.getKind)
    }

    super.visitVariable(node, p)
  }

  override def visitNewClass(node: NewClassTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      // anonymous classes would show the whole class body
      val firstline = node.toString.lines.dropWhile(_.isEmpty).next
      println("InferenceTreeAnnotator::visitNewClass type: " + p + " tree: " + firstline)
    }

    val vpnew = methodStaticOrFieldToVp(node, NewScanner.indexOfNewTree _,
                                        NewInMethodVP     apply _,
                                        NewInStaticInitVP apply(_,_),
                                        NewInFieldInitVP  apply(_,_), false)

    if (node.getClassBody() != null && isAnonymousClass(node.getClassBody)) {
      // Anonymous classes are a big pain :-(
      // They implicitly create an extends clause, we first annotate that.

      var thesuper: AnnotatedTypeMirror = null;
      {
        val ext = node.getClassBody.getExtendsClause
        if (ext != null) {
          val extty = typeFactory.getAnnotatedType(ext)
          createVarsAndConstraints(vpnew, ext, extty)
          thesuper = extty
        } else {
          val impls = node.getClassBody.getImplementsClause
          if (impls.size!=1) {
            // I think there has to be exactly one implemented interface now
            println("InferenceTreeAnnotator::visitNewClass: failed to look up implements clause of anonymous class creation! Tree: " + node)
            return super.visitNewClass(node, p)
          }

          val impty = typeFactory.getAnnotatedType(impls.get(0))
          createVarsAndConstraints(vpnew, impls.get(0), impty)
          thesuper = impty
        }
      }

      if (thesuper == null) {
        println("InferenceTreeAnnotator::visitNewClass: failed to look up supertype of anonymous class creation! Tree: " + node)
        return super.visitNewClass(node, p)
      }

      // then, we take the main type "p" again and look for the same type in
      // its supertypes.
      val adt = p.asInstanceOf[AnnotatedDeclaredType]
      import scala.collection.JavaConversions._

      val directSuperTypes = adt.directSuperTypes.iterator  //TODO: Should we only be doing this once?
      val matchingSuperTypes = directSuperTypes.filter(_.getUnderlyingType equals thesuper.getUnderlyingType )

      // We found the matching supertype!
      // Take the annotations from the extends clause and also add them here.
      matchingSuperTypes.foreach(matchSu => InferenceUtils.copyAnnotations(thesuper, matchSu))

    } else {
      createVarsAndConstraints(vpnew, node.getIdentifier, p)
    }

    // this gives the type as tree:
    // println("New class tree: " + node.getIdentifier.getClass)
    // are the TA's to the constructor?
    // println("New ta: " + nct.getTypeArguments)

    super.visitNewClass(node, p)
  }

  override def visitNewArray(node: NewArrayTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitNewArray type: " + p + " tree: " + node)
    }

    val vpnew = methodStaticOrFieldToVp(node, NewScanner.indexOfNewTree _,
                                        NewInMethodVP     apply _,
                                        NewInStaticInitVP apply(_,_),
                                        NewInFieldInitVP  apply(_,_), false)

    createVarsAndConstraints(vpnew, node, node, p, List(-1))
    super.visitNewArray(node, p)
  }

  override def visitTypeCast(node: TypeCastTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitTypeCast type: " + p + " tree: " + node)
    }

    val vpcast = methodStaticOrFieldToVp(node, CastScanner.indexOfCastTree _,
                                         CastInMethodVP     apply _,
                                         CastInStaticInitVP apply(_,_),
                                         CastInFieldInitVP  apply(_,_), false)

    createVarsAndConstraints(vpcast, node.getType, p)
    super.visitTypeCast(node, p)
  }

  override def visitInstanceOf(node: InstanceOfTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitInstanceOf type: " + p + " tree: " + node)
    }

    val vpinstof = methodStaticOrFieldToVp(node, InstanceOfScanner.indexOfInstanceOfTree _,
                                           InstanceOfInMethodVP     apply _,
                                           InstanceOfInStaticInitVP apply(_,_),
                                           InstanceOfInFieldInitVP  apply(_,_), false)

    // note that p is always boolean
    val testtype = typeFactory.getAnnotatedType(node.getType)
    createVarsAndConstraints(vpinstof, node.getType, testtype)
    super.visitInstanceOf(node, p)
  }

  override def visitLiteral(tree: LiteralTree, ty: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitLiteral type: " + ty + " tree: " + tree)
    }
    if (InferenceMain.getRealChecker.needsAnnotation(ty)) {
      val annot = new Literal(tree.getKind, tree.getValue).getAnnotation()
      ty.addAnnotation(annot)
    }

    super.visitLiteral(tree, ty)
  }

  override def visitAssignment(tree: AssignmentTree, ty: AnnotatedTypeMirror) : Void = {


    //TODO: Either here or in the visitor create a new variable
    //val flowNode = typeFactory.realAnnotatedTypeFactory.get

    //TODO: So it does not look like AFU has an "AssignmentScanner" or the notion of the "ith" assignment
    //TODO: Do we need to create variable positions for the various types of Assignments?
    //TODO: So would the variable position
    //TODO: Cast scan to the right side of the assignment tree

    //TODO: Add cast ot the AST?
    /*val vpnew = methodStaticOrFieldToVp(tree.getExpression, CastScanner.indexOfCastTree _,
                                        CastInMethodVP     apply _,
                                        CastInStaticInitVP apply(_,_),
                                        CastInFieldInitVP  apply(_,_))*/
    super.visitAssignment(tree, ty);
  }
  override def visitCompoundAssignment(tree: CompoundAssignmentTree, ty: AnnotatedTypeMirror) : Void = {

    //val flowNode = typeFactory.getNodeForTree(tree)
    super.visitCompoundAssignment(tree, ty);
  }

  override def visitBinary(tree: BinaryTree, ty: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitBinary type: " + ty + " tree: " + tree)
    }

    if (InferenceMain.getRealChecker.needsAnnotation(ty) && !InferenceMain.isPerformingFlow) {
      val combvar = slotMgr.getOrCreateCombVariable(tree)
      ty.addAnnotation(combvar.getAnnotation)
    }

    super.visitBinary(tree, ty)
  }

  /**
   * Convert a field tree node to an id based on it's path name (see AnnotatedTypeFactory.getPath)
   * @param treeNode Node for which an id is needed
   * @return An id string derived from passing the nodes path to typeFactory.getPath and getting its name
   */
  def fieldToId(treeNode : Tree) = {
     val enclosing = TreeUtils.enclosingVariable(typeFactory.getPath(treeNode))
     val name = enclosing.getName
     val str = name.toString
     str
  }

  /**
   * Create and initialize a variable position.  One of the passed VariablePosition factories are
   * called depending on the location of the node tree.  The resulting variable position is then initialized
   * and returned.
   * @param node The node for which we are finding a VariablePosition
   * @param treeToId A method that the code converts the node into an id
   * @param inMethodFactory The factory method called if node is within a method (see InferenceUtils.isWithinMethod)
   * @param inStaticInitFactory The factory method called if node is in a static initialization block (see InferenceUtils.isWithinStaticInit)
   * @param inFieldFactory The default factory method, inFieldFactory is called if the previous two conditions do not apply
   * @return A VariablePosition constructed by one of the three factory methods passed to methodStaticOrFieldToVp
   */
  def methodStaticOrFieldToVp(node : Tree,
               treeToId  : ((TreePath, Tree) => Int),
               inMethodFactory     : ((Int)        => VariablePosition),
               inStaticInitFactory : ((Int, Int)   => VariablePosition),
               inFieldFactory      : (Int, String) => VariablePosition,
               noId : Boolean ) = {
    val treeId = treeToId(typeFactory.getPath(node), node)
    val vp =
      if (InferenceUtils.isWithinMethod(typeFactory, node)) {
        inMethodFactory(treeId)
      } else if (InferenceUtils.isWithinStaticInit(typeFactory, node)) {
        val blockid: Int = StaticInitScanner.indexOfStaticInitTree(typeFactory.getPath(node))
        inStaticInitFactory(treeId, blockid)
      } else {
        inFieldFactory(treeId, if( noId ) null else fieldToId(node) )
      }
    vp.init(typeFactory, node)
    vp
  }
}

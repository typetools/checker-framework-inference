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
import checkers.types.AnnotatedTypeMirror._
import javax.lang.model.`type`.TypeKind
import checkers.types.AnnotatedTypeMirror
import checkers.types.TreeAnnotator
import javacutils.{ElementUtils, TreeUtils, AnnotationUtils}
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

import scala.collection.JavaConversions._
import checkers.inference.util.CollectionUtil._
import checkers.inference.quals.VarAnnot
import checkers.util.AnnotatedTypes
import scala.collection.mutable.ListBuffer

/**
 * The InferenceTreeAnnotators primary job is to generate Slot definitions and annotations
 * (VarAnnot, CombVarAnnot, and LiteralAnnot) for locations in the AST.  Most parts of the AST
 * are annotated by first calling the corresponding visit method on this class which determines
 * the "VariablePosition" of annotations in the visited tree and then calls createVarsAndConstraints
 * on the tree.
 *
 * @param checker
 * @param typeFactory
 */
class InferenceTreeAnnotator(checker: InferenceChecker,
  typeFactory: InferenceAnnotatedTypeFactory[_]) extends TreeAnnotator(checker, typeFactory) {

  // AnnotatedTypeFactory only caches class and method trees, therefore variable and
  // others might get called multiple time.
  val visitedtrees = new collection.mutable.HashSet[Tree]()

  def createVarsAndConstraints(varpos: VariablePosition, toptree: Tree, ty: AnnotatedTypeMirror) {
    ty match {
      case aat: AnnotatedArrayType =>
        createVarsAndConstraints(varpos, toptree, toptree, ty, List((-1, -1)))

      case _ =>
        createVarsAndConstraints(varpos, toptree, toptree, ty, null)
    }
  }

  /**
   * A recursive method for generating slots and their annotations for the given trees/type mirror representing
   * those trees.
   *
   * @param varpos The location in which the toptree was found.  The location a variable should be considered
   *               in.
   * @param toptree The tree visited by this InferenceTreeAnnotator for which we want to generate one or more variables
   * @param curtree This method is called recursively on children of toptree.  This is the current tree for which
   *                we are generating variables.  It should be a child of toptree.
   * @param ty      The type that corresponds with curtree (NOT toptree though at the start curtree == toptree)
   * @param pos     Positional information for locating annotations within the toptree.  As we descend into the tree
   *                the position list usually gains more elements.  pos is primarily used (along with varpos) to
   *                generate an Annotation File Utilities string to insert annotations back into source code at the
   *                location of a generate slot annotation.  It can also be used to uniquely identify variables with
   *                the same varpos.
   *                TODO ITF1: Better explain the positioning system
   */
  def createVarsAndConstraints(varpos: VariablePosition, toptree: Tree, curtree: Tree, ty: AnnotatedTypeMirror,
                               pos: List[(Int, Int)]) {

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
        val npos = if (pos.last.equals((-1, -1))) {
          pos.dropRight(1)
        } else {
          pos
        }
        annotateTopLevel(varpos, toptree, curtree, aat, npos)

        if( aty != null ) { //Array primitives will lead to an nat.getType == null
          createVarsAndConstraints(varpos, toptree, aty, aat.getComponentType, npos :+ (0, 0))
        }
      }
      case w: AnnotatedWildcardType => {
        if( !curtree.isInstanceOf[WildcardTree]) {
          return //TODO ITA2: FIGURE THIS OUT BY RUNNING ON PICARD
        }
        if(!InferenceMain.isPerformingFlow) {
          return
        }

        val wct = curtree.asInstanceOf[WildcardTree]
        annotateTopLevel(varpos, toptree, wct, w, pos)
        wct.getKind match {
          case Tree.Kind.UNBOUNDED_WILDCARD => {
            // TODO ITA3: add implicit Object bound
            annotateTopMissingTree( varpos, toptree, w.getExtendsBound, pos :+ (2, 0))
            // println("InferenceTreeAnnotator: unbound wildcard. tree: " + wct + " type: " + w)
          }
          case Tree.Kind.EXTENDS_WILDCARD => {
            createVarsAndConstraints(varpos, toptree, wct.getBound, w.getExtendsBound, pos :+ (2, 0))
          }
          case Tree.Kind.SUPER_WILDCARD => {
            createVarsAndConstraints(varpos, toptree, wct.getBound, w.getSuperBound, pos :+ (2, 0))
          }
        }
      }

      case atv: AnnotatedTypeVariable => {
        if( varpos.isInstanceOf[ParameterVP] || varpos.isInstanceOf[FieldVP] || varpos.isInstanceOf[ReturnVP] ) {  //Non-defaultable locations
          annotateTopLevel( varpos, toptree, curtree, atv, null)

        } else {
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

                  //See visitClass comment on type parameters
                  InferenceUtils.copyAnnotations(annotp, atv)
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

                val annot = annotateTopLevel(varpos, toptree, curtree, atv, pos)
                //inferenceChecker.typeparamElemCache += (typaramel -> annot )

                val annotp = typeFactory.getAnnotatedType(typeParam).asInstanceOf[AnnotatedTypeVariable]
                InferenceUtils.copyAnnotations(annotp.getUpperBound, atv.getUpperBound)

                // TODO ITA4: needed?
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
          // TODO ITA5: at least for the Universe type systems, other systems
          // might want annotations on type variables
          // val it = curtree.asInstanceOf[IdentifierTree]
        }
      }
      case adt: AnnotatedDeclaredType => {

        // println("AnnotDeclType with curtree: " + curtree.getClass)
        curtree match {
          case it  : IdentifierTree => annotateTopLevel(varpos, toptree, it, adt, pos)

          case att : AnnotatedTypeTree =>
          // Don't do anything with the type. SlotManager.extractSlot will complain if it doesn't understand the annotation
          // println("Existing annotated type: " + att)

          // Note: bounds are an empty list for implicit Object, but "ty" is correct
          // TODO ITA6: what should be done with implicit Object bound?
          case tpt : TypeParameterTree if tpt.getBounds().isEmpty() => annotateTopLevel(varpos, toptree, tpt, adt, pos)
          case tpt : TypeParameterTree => createVarsAndConstraints(varpos, toptree, tpt.getBounds.get(0), adt, pos)

          // TODO ITA7: this seems to be a qualified access, like java.util.List
          // It seems these just come for top-level types
          case mst : MemberSelectTree  => annotateTopLevel(varpos, toptree, curtree, adt, pos)

          case ptt : ParameterizedTypeTree =>
            annotateTopLevel(varpos, toptree, ptt.getType, adt, pos)

            val treeTypeArgs = ptt.getTypeArguments
            val atmTypeArgs  = adt.getTypeArguments

            if (treeTypeArgs.size == atmTypeArgs.size) {

              // println("Node: " + ptt + " with type: " + ptt.getClass)

              val npos = if (pos == null) List() else pos

              for (i <- 0 until atmTypeArgs.size) {
                val tasi = atmTypeArgs.get(i)
                tasi match {
                  case aat: AnnotatedArrayType => {
                    createVarsAndConstraints(varpos, toptree, treeTypeArgs.get(i), tasi, npos :+ (3, i) :+ (-1, -1))
                  }
                  case _ => {
                    createVarsAndConstraints(varpos, toptree, treeTypeArgs.get(i), tasi, npos :+ (3, i))
                  }
                }

                if (tasi.isInstanceOf[AnnotatedDeclaredType]) {
                  val elem = tasi.getUnderlyingType().asInstanceOf[DeclaredType].asElement().asInstanceOf[TypeElement]
                  inferenceChecker.typeElemCache += (elem -> tasi)
                } else if (tasi.isInstanceOf[AnnotatedArrayType]) {
                  // TODO ITA8: do we need to cache something for arrays???
                } else if (tasi.isInstanceOf[AnnotatedTypeVariable]) {
                  // println("InferenceTreeAnnotator::declaredtype: is there something to do for type variable: " + tas.get(i))
                  // TODO IA9: note that TVs are in typeparamElemCache, can I unify these two?
                } else if (tasi.isInstanceOf[AnnotatedWildcardType]) {
                  // Wildcards are already handled above
                } else {
                  println("InferenceTreeAnnotator unexpected declaredtype: is there something to do for: " + tasi)
                }
              }
            } else {
              // TODO ITA10: what a strange case is this???
              println("InferenceTreeAnnotator: unexpected pta: " + treeTypeArgs + " tas: " + atmTypeArgs + " and toptree: " + toptree)
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

  //TODO JB ITA11: Call these annotateCurrentTree since that's the one being annotated
  /**
   * See annotateTopLevelImpl
   * @param varPos
   * @param topTree
   * @param curTree A non-null tree that is top be annotated
   * @param atm
   * @param pos
   */
  def annotateTopLevel(varPos: VariablePosition, topTree: Tree, curTree: Tree, atm: AnnotatedTypeMirror,
    pos: List[(Int, Int)]) {
    if( curTree == null ) {
      throw new RuntimeException( "Attempting to annotate null tree " +
        "position=( " + varPos + " ) topTree=( " + topTree + " ) atm=( " + atm + " )")
    }

    annotateTopLevelImpl(varPos, topTree, Some(curTree), atm, pos)
  }

  /**
   * //TODO ITA12: Synchronize this with annotate missing receiver and other locations that just use annotateTopMissingTree
   * Recursive annotate missing tree.  This is very similar to create vars and constraints but for missing trees
   */
  private def recursiveAnnotateMissingTree(varPos : VariablePosition, topTree : Tree, atm : AnnotatedTypeMirror, pos: List[(Int, Int)] ) {
    import scala.collection.JavaConversions._
    if( atm != null ) { //TODO: This happens on some method invocations with wildcards when there is no super, perhaps create the super variable

      atm.removeAnnotation( inferenceChecker.VAR_ANNOT )

      Option( atm ).map(

        _ match {

          case aat : AnnotatedArrayType =>
            val locPos = pos :+ (-1, -1)
            aat.getComponentType
            annotateTopMissingTree(varPos, topTree, atm, locPos )
            recursiveAnnotateMissingTree(varPos, topTree, aat.getComponentType, locPos :+ (0,0))

          case awt : AnnotatedWildcardType =>
            recursiveAnnotateMissingTree(varPos, topTree, awt.getSuperBound,   pos :+ (2,0) ) //TODO JB: THIS SEEMS WRONG ASK TYLER
            recursiveAnnotateMissingTree(varPos, topTree, awt.getExtendsBound, pos :+ (2,0) )

          case atv : AnnotatedTypeVariable =>
            annotateTopMissingTree(varPos, topTree,  atv, pos :+ (2,0))

          case adt : AnnotatedDeclaredType =>
            annotateTopMissingTree(varPos, topTree, atm, pos )
            adt.getTypeArguments.zipWithIndex.foreach( {
              case (typeArg : AnnotatedTypeMirror, index : Int) =>
                recursiveAnnotateMissingTree(varPos, topTree, typeArg, pos :+ (3, index) )
            })

          case apt: AnnotatedPrimitiveType =>
            annotateTopMissingTree(varPos, topTree, atm, pos )

          case ait : AnnotatedIntersectionType =>
            annotateTopMissingTree(varPos, topTree, ait, pos ) //TODO: Anything else todo?

          case atm : AnnotatedTypeMirror if atm.isInstanceOf[AnnotatedNoType] |
            atm.isInstanceOf[AnnotatedNullType] =>
          //TODO JB: Anything todo here?

          case atm : AnnotatedTypeMirror =>
            throw new RuntimeException("Unhandled annotated type mirror " + atm.getClass.getCanonicalName)
        }
      )
    }
  }

  /**
   * Used for implicit variable locations
   * e.g.
   *  class MyClass  //extends @ImplicitLoc Object
   *  Everything commented out in the previous line is implicit.  These locations aren't usually written and
   *  therefore will have no corresponding tree.
   *  TODO
   *
   * @param varPos
   * @param topTree
   * @param atm
   * @param pos
   * @return
   */
  def annotateTopMissingTree( varPos : VariablePosition, topTree : Tree, atm : AnnotatedTypeMirror, pos: List[(Int, Int)])
    : Option[AnnotationMirror] = {
    val annoOpt = annotateTopLevelImpl( varPos, topTree, None, atm, pos )

    annoOpt
      .map( anno => slotMgr.extractSlot(anno).asInstanceOf[Variable] )
      .map( _.atmDesc = Some(atm).map( _.toString ) )

    annoOpt
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
   * @param curTreeOpt As createVarsAndAnnotations descends into a particular tree, the latest tree being examined is held
   *                   in curTreeOpt.  Some trees might be implied but don't exist (e.g. class C {} really corresponds
   *                   to class C extends Object {}).  We may wish to annotate the implied tree.  We do this by
   *                   creating Variables with no currentTree.
   *                   Note:  Variables with no currentTree must be cached by some mechanism other than the slotManager
   *                   since it then has no way to retrieve the Variable (see createMissingVariable)
   *
   * @param atm
   * @param pos
   */
  private def annotateTopLevelImpl( varPos : VariablePosition, topTree : Tree, curTreeOpt : Option[Tree], atm : AnnotatedTypeMirror,
                            pos : List[(Int, Int)]) : Option[AnnotationMirror] = {

    // println("InferenceTreeAnnotator::annotateTopLevel: curTree: " + curTree)
    // println("InferenceTreeAnnotator::annotateTopLevel: topTree: " + topTree)

      // println("InferenceTreeAnnotator::annotateTopLevel: type before: " + ty)

    val annot =
      curTreeOpt match {
        case Some(curTree : Tree) => slotMgr.getOrCreateVariable(varPos, typeFactory, topTree, curTree, pos)
        case None                 => slotMgr.createMissingTreeVariable(varPos, typeFactory, topTree, pos )
      }

    val equivalentSlots = new ListBuffer[Slot]()
    if( !InferenceMain.getRealChecker.needsAnnotation( atm ) ) {
      if( curTreeOpt.isDefined ) {
        equivalentSlots += slotMgr.extractSlot( typeFactory.realAnnotatedTypeFactory.getAnnotatedType( curTreeOpt.get ) )
      } else {
        equivalentSlots += slotMgr.extractSlot( InferenceMain.getRealChecker.defaultQualifier(atm) )
      }
    }


    if ( InferenceUtils.isAnnotated( atm ) && !InferenceMain.isPerformingFlow ) {
      // println("InferenceTreeAnnotator::annotateTopLevel: already annotated type: " + ty + " with tree: " + topTree)
      val oldAnnos = InferenceUtils.clearAnnos(atm)

      import scala.collection.JavaConversions._
      val oldSlots = oldAnnos.map( slotMgr.extractSlot _ )
      equivalentSlots ++= oldSlots
    }

    val newSlot = slotMgr.extractSlot(annot)
    equivalentSlots.toList.foreach( eqSlot => constraintMgr.addEqualityConstraint(newSlot, eqSlot) )

    //add the new annotation
    atm.addAnnotation(annot)

    // println("InferenceTreeAnnotator::annotateTopLevel: type after: " + ty)
    Some(annot)
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

    val atmTypeArgs    = p.asInstanceOf[AnnotatedDeclaredType].getTypeArguments
    val treeTypeParams = node.getTypeParameters
    val classElem = TreeUtils.elementFromDeclaration( node )

    createTypeParameterVariables( atmTypeArgs.toList, treeTypeParams.toList, node, classElem,
      ClassTypeParameterVP.apply _, ClassTypeParameterBoundVP.apply _ )

    Option( node.getExtendsClause() ) match {
      case Some( extendsTree : Tree ) =>
        // always a declared type
        val ety = typeFactory.getAnnotatedTypeFromTypeTree(extendsTree).asInstanceOf[AnnotatedDeclaredType]
        val extendsVp = ExtendsVP()
        extendsVp.init(typeFactory, node)
        createVarsAndConstraints(extendsVp, extendsTree, ety)

        //TODO JB ITA14:  Write up to Werner and ask what to do here?
        //TODO JB:  A the moment this leads all Unnanotated extends to have an equality constraint
        //TODO JB:  to Constant(NonNull) if the qualifier isn't explicitly present in the real type system
        //TODO JB:  which is obviously incorrect as we would like to have types that can be Null
        //ety.addAnnotation(InferenceMain.getRealChecker.selfQualifier)
        inferenceChecker.extImplsTreeCache += (extendsTree -> ety)

      case None =>
        val classElem = TreeUtils.elementFromDeclaration( node )
        if( !classElem.getKind().isInterface ) {
          if( !inferenceChecker.classToMissingExtCache.containsKey( classElem )) {
            val extendsVp = ExtendsVP()
            extendsVp.init(typeFactory, node)

            val extendsAm = slotMgr.createMissingTreeVariable(extendsVp, typeFactory, node, null )
            inferenceChecker.classToMissingExtCache += ( classElem  -> extendsAm )
            // If there is no extends clause than we assume extends Object and create one
          }
        }
    }

    val impls = node.getImplementsClause()
    for (impIdx <- 0 until impls.size) {
      val imp = impls.get(impIdx)
      // always a declared type
      val ity = typeFactory.getAnnotatedTypeFromTypeTree(imp).asInstanceOf[AnnotatedDeclaredType]
      val ivp = ImplementsVP(impIdx)
      ivp.init(typeFactory, node)

      //ity.addAnnotation(InferenceMain.getRealChecker.selfQualifier)
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

    //TODO ITA15: Use ElementUtils.getSuperTypes to find all super types, create a map between
    val methodElem = TreeUtils.elementFromDeclaration(node)
    val methodType = p.asInstanceOf[AnnotatedExecutableType]

    val atmTypeVars = methodType.getTypeVariables
    val treeTypeVars = node.getTypeParameters

    createTypeParameterVariables( atmTypeVars.toList, treeTypeVars.toList, node, methodElem,
      MethodTypeParameterVP.apply _, MethodTypeParameterBoundVP.apply _ )

    val isConstructor = TreeUtils.isConstructor( node )
    if( isConstructor  ) {
      val returnVp = ReturnVP()
      returnVp.init(typeFactory, node)
      annotateTopLevel( returnVp, node, node, methodType.getReturnType, null )

    } else if ( node.getReturnType != null ) {
      val returnVp = ReturnVP()
      returnVp.init(typeFactory, node)
      createVarsAndConstraints(returnVp, node.getReturnType, methodType.getReturnType)

    }

    //TODO ITA16: Fix up constructor receivers
    if( !isConstructor && !inferenceChecker.exeElemToReceiverCache.keys.contains( methodElem ) && !ElementUtils.isStatic( methodElem ) ) {

      Option( node.getReceiverParameter ) match {
        case Some( receiverTree : VariableTree ) =>
          val receiverVp = ReceiverParameterVP()
          receiverVp.init( typeFactory, receiverTree )
          annotateTopLevel(receiverVp, receiverTree.getType, receiverTree.getType, methodType.getReceiverType, List.empty[(Int,Int)] )
          //createVarsAndConstraints( receiverVp, receiverTree.getType, methodType.getReceiverType )

        case None =>
          val receiverVp = ReceiverParameterVP( )
          receiverVp.init( typeFactory, node )

          annotateTopMissingTree( receiverVp, node, methodType.getReceiverType, List.empty[(Int,Int)])
          //annotateMissingReceiverAtm( node, methodType.getReceiverType )
      }

      inferenceChecker.exeElemToReceiverCache += ( methodElem -> methodType.getReceiverType )
    }

    val paramTypes = methodType.getParameterTypes
    val paramTrees = node.getParameters
    assert ( paramTypes.size == paramTrees.size )

    val paramTypesToTrees = paramTypes.zip( paramTrees )

    // parameters automatically visited by a visitVariable, so we must create the
    // types here or else they are created as LocalIn vps rather than parameters
    zip3WithIndex( paramTypesToTrees.toList ).foreach( pti => {
      val (typ, tree, index) = pti

      val paramVp = ParameterVP( index )
      paramVp.init( typeFactory, node )
      createVarsAndConstraints( paramVp, tree.getType, typ )
    })

    super.visitMethod(node, p)

    inferenceChecker.exeElemCache += (methodElem -> methodType)

    // println("After visitMethod tree: " + node)
    // println("After visitMethod type: " + p)

    null
  }

  override def visitMethodInvocation(methodInvocTree : MethodInvocationTree, atm : AnnotatedTypeMirror) : Void = {

    super.visitMethodInvocation( methodInvocTree, atm )

    def makeTypeArgumentVp( paramIdx : Int ) = {
        if( InferenceUtils.isWithinMethod( typeFactory, methodInvocTree ) ) {
          MethodTypeArgumentInMethodVP( paramIdx )
        } else if( InferenceUtils.isWithinStaticInit( typeFactory, methodInvocTree ) ) {
          MethodTypeArgumentInStaticInitVP(paramIdx, StaticInitScanner.indexOfStaticInitTree( typeFactory.getPath(methodInvocTree) ) )
        } else {
          //TODO ITA17: Need to create a scanner/inserter for Method Type Parameters and use methodStaticOrFieldToVp
          //TODO JB:
          MethodTypeArgumentInFieldInitVP(paramIdx, -1, fieldToId( methodInvocTree ))
        }
    }

    val methodElem   = TreeUtils.elementFromUse( methodInvocTree ).asInstanceOf[ExecutableElement]

    val calledTree = typeFactory.getTrees.getTree( methodElem )
    if (calledTree==null) {
      // TODO ITA18: We currently don't create a constraint for binary only methods(?)
      return null
    }

    if( !methodElem.getTypeParameters.isEmpty && !inferenceChecker.methodInvocationToTypeArgs.contains( methodInvocTree ) ) {
      annotateMethodInvocationTypeArgs( typeFactory, methodElem, methodInvocTree )
    }

    return null
  }

  def annotateMethodInvocationTypeArgs( typeFactory : InferenceAnnotatedTypeFactory[_],
                                        methodElem : ExecutableElement, invocTree : Tree ) {

    def makeTypeArgumentVp( paramIdx : Int ) = {
      if( InferenceUtils.isWithinMethod( typeFactory, invocTree ) ) {
        MethodTypeArgumentInMethodVP( paramIdx )
      } else if( InferenceUtils.isWithinStaticInit( typeFactory, invocTree ) ) {
        MethodTypeArgumentInStaticInitVP(paramIdx, StaticInitScanner.indexOfStaticInitTree( typeFactory.getPath(invocTree) ) )
      } else {
        //TODO ITA17: Need to create a scanner/inserter for Method Type Parameters and use methodStaticOrFieldToVp
        //TODO JB:
        MethodTypeArgumentInFieldInitVP(paramIdx, -1, fieldToId( invocTree ))
      }
    }

    if( !methodElem.getTypeParameters.isEmpty && !inferenceChecker.methodInvocationToTypeArgs.contains( invocTree ) ) {
      val typeArgTrees =  invocTree match {
        case methodInvocation : MethodInvocationTree => methodInvocation.getTypeArguments
        case constructorInvoc : NewClassTree         => constructorInvoc.getTypeArguments
      }

      val typeParamUBs =
        methodElem.getTypeParameters
          .map( inferenceChecker.typeParamElemToUpperBound.apply _ )
          .map( _.getUpperBound )

      //If there are type params but no type-arguments we are going to create synthetic arguments
      //by annotating the upper bound of the type parameter
      if( typeArgTrees.isEmpty ) {
        val typeArgs =
          typeParamUBs.map( AnnotatedTypes.deepCopy _ ).zipWithIndex.map( (atmToIndex : (AnnotatedTypeMirror, Int)) => {
            val (atm, index) = atmToIndex
            atm.removeAnnotation( inferenceChecker.VAR_ANNOT )

            val typeArgVp = makeTypeArgumentVp( index )
            typeArgVp.init( typeFactory, invocTree )

            recursiveAnnotateMissingTree(typeArgVp,  invocTree, atm, List((3,index)))
            atm
          }).toList

        inferenceChecker.methodInvocationToTypeArgs += ( invocTree -> typeArgs )

        //If there are type arguments then we need to annotate them
      } else {

        val mfuPair = invocTree match {
          case methodInvocation : MethodInvocationTree => typeFactory.methodFromUse( methodInvocation )
          case constructorInvoc : NewClassTree         => typeFactory.constructorFromUse( constructorInvoc )
        }

        val typeArgs = typeArgTrees.zipWithIndex.map( typeArgToIndex => {
          val (typeArgTree, index) = typeArgToIndex
          val typeArgVp = makeTypeArgumentVp( index )
          typeArgVp.init( typeFactory, invocTree )

          val typeArgAtm = mfuPair.second(index)
          createVarsAndConstraints(typeArgVp, invocTree, typeArgTree, typeArgAtm, List((3,index)))
          typeArgAtm
        })

        val typeArgsAsUB = typeArgs.zip( typeParamUBs ).map({
          case (typeArg : AnnotatedTypeMirror, upperBound : AnnotatedTypeMirror) =>
            AnnotatedTypes.asSuper( inferenceChecker.getProcessingEnvironment.getTypeUtils, typeFactory, typeArg, upperBound )
        }).toList

        inferenceChecker.methodInvocationToTypeArgs += ( invocTree -> typeArgsAsUB )
      }
    }
  }

  //TODO ITA19: THE POSITION INFORMATION WILL BE OFF, CREATE A TRAVERSE TYPE FROM WHICH WE CAN MANUFACTURE A POSITION
  def annotateMissingReceiverAtm( methodTree : MethodTree,
                                  receiverType : AnnotatedDeclaredType  )  {

    val receiverVp = ReceiverParameterVP()
    receiverVp.init( typeFactory, methodTree )

    //TODO JB: See if there is a way that this can be abstracted into a TraversalUtil.traverseDeclTypes
    def visitReceiverAtm( atm : AnnotatedTypeMirror, pos : List[(Int,Int)] ) {
      import scala.collection.JavaConversions._

      Option( atm ).map(

        _ match {

          case aat : AnnotatedArrayType =>
            annotateTopMissingTree(receiverVp, methodTree, aat, pos :+ (-1, -1) )
            visitReceiverAtm( aat.getComponentType, pos :+ (0,0) )

          case awt : AnnotatedWildcardType =>
            visitReceiverAtm( awt.getSuperBound,   pos )
            visitReceiverAtm( awt.getExtendsBound, pos )

          case atv : AnnotatedTypeVariable =>
            //Since we only want to create one variable per type variable (not one per bound on the type
            //variable) we create the required annotation when we visit the declared type of the lower bound
            //We still want to create one type variable for each type argument found on the declared type
            //of the lower bound.  Basically, if the receiver type returned is:
            // MyClass<T extends List<String>> we want to treat it as MyClass<@0 List<@1 String> >
            visitReceiverAtm( atv.getUpperBound, pos )

          case adt : AnnotatedDeclaredType =>
            annotateTopMissingTree(receiverVp, methodTree, adt, pos )
            adt.getTypeArguments.zipWithIndex.foreach(
              (typeArgToIndex : (AnnotatedTypeMirror, Int)) => {
                val (typeArg, index) = typeArgToIndex
                visitReceiverAtm( typeArg, pos :+ (3, index))
            })

          case ait : AnnotatedIntersectionType =>
            annotateTopMissingTree(receiverVp, methodTree, ait, pos )

          //TODO JB: Anything todo here?
          //case atm : AnnotatedTypeMirror if atm.isInstanceOf[AnnotatedNoType] | atm.isInstanceOf[AnnotatedNullType] =>


          case atm : AnnotatedTypeMirror =>
            throw new RuntimeException("Unhandled annotated type mirror " + atm.getClass.getCanonicalName)
        }

      )
    }

    visitReceiverAtm( receiverType, List.empty )
  }

  override def visitVariable(node: VariableTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitVariable type: " + p + " tree: " + node)
    }

    val elem = TreeUtils.elementFromDeclaration(node)

    //TODO JB: Another temporary KLUDGE

    val varname = node.getName.toString

    //TODO JB: What should we actually do with this?
    val ignoredDetachedSymbols = List("index#num", "iter#num", "assertionsEnabled#num")

    //Do not create a variable for an index resulting from desugaring arrays in foreach loops
    if( elem.isInstanceOf[DetachedVarSymbol] && ignoredDetachedSymbols.find( varname startsWith _ ).isDefined ) {
      return super.visitVariable( node, p );
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

      val directSuperTypes = adt.directSuperTypes.iterator
      val matchingSuperTypes = directSuperTypes.filter(_.getUnderlyingType equals thesuper.getUnderlyingType )

      // We found the matching supertype!
      // Take the annotations from the extends clause and also add them here.
      matchingSuperTypes.foreach(matchSu => InferenceUtils.copyAnnotations(thesuper, matchSu))

    } else {
      createVarsAndConstraints(vpnew, node.getIdentifier, p)
    }
     /**
      * TODO ITA20: Need to handle method type parameters on constructors, for now
      * lets assume they don't exist
      */
    val constructorElem = TreeUtils.elementFromUse( node )
    annotateMethodInvocationTypeArgs( typeFactory, constructorElem, node  )

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

    createVarsAndConstraints(vpnew, node, node, p, List((-1, -1)))
    super.visitNewArray(node, p)
  }

  override def visitTypeCast(node: TypeCastTree, p: AnnotatedTypeMirror): Void = {
    if (InferenceMain.DEBUG(this)) {
      println("InferenceTreeAnnotator::visitTypeCast type: " + p + " tree: " + node)
    }

    //TODO ITA21: Temporary kludge, this happens when the DFF converts a compound assignment
    //TODO JB: into a statement like this.length = this.length CoordMath.getLength(start, end)
    if( typeFactory.getPath(node) != null ) {
      val vpcast = methodStaticOrFieldToVp(node, CastScanner.indexOfCastTree _,
                                         CastInMethodVP     apply _,
                                         CastInStaticInitVP apply(_,_),
                                         CastInFieldInitVP  apply(_,_), false)

      createVarsAndConstraints(vpcast, node.getType, p)
    }
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
     val enclosing = TreeUtils.enclosingVariable( typeFactory.getPath(treeNode) )
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

  def createTypeParameterVariables(atmTypeArgs : List[AnnotatedTypeMirror],
                                   typeParamTrees : List[_ <: TypeParameterTree],
                                   tree : Tree,
                                   element : Element,
                                   typeParamVpFactory      : (Int => WithinClassVP),
                                   typeParamBoundVpFactory : ((Int, Int) => WithinClassVP) ) = {
    assert( atmTypeArgs.size == typeParamTrees.size )

    if( !checker.visited.contains( element ) ) {
      checker.visited += element

      for( index <- 0 until atmTypeArgs.size ) {
        ( atmTypeArgs(index), typeParamTrees(index) ) match {

          case (atmTv : AnnotatedTypeVariable, treeTv : TypeParameterTree ) =>
            //TODO ITA22: The zero here may be incorrect, we may need a more meaningful index
            val upperClassTypeVp = typeParamBoundVpFactory(index, 0)
            upperClassTypeVp.init( typeFactory, tree )

            val elem = atmTv.getUnderlyingType.asElement.asInstanceOf[TypeParameterElement]

            if( treeTv.getBounds.isEmpty ) {
              annotateTopMissingTree( upperClassTypeVp, treeTv, atmTv.getUpperBound, List((3, index)) )
            } else if( atmTv.getUpperBound.isInstanceOf[AnnotatedTypeVariable] ) {
              /*
                For cases like:
                class Foo<T> {
                   class Foo<T2 extends T>      {} //example 1
                   <T3 extends T> void method() {} //example 2
                }
               */
              val upperBound = AnnotatedTypes.deepCopy(atmTv.getUpperBound)
              upperBound.clearAnnotations()
              //TODO JB: Add constraints with the type variable?
              annotateTopLevel( upperClassTypeVp, treeTv, treeTv.getBounds.get(0), upperBound, List() )
            } else {
              createVarsAndConstraints( upperClassTypeVp, treeTv, treeTv.getBounds.get(0), atmTv.getUpperBound, List((3, index)) )
            }


            //TODO JB: Major kludge, since fix
            inferenceChecker.typeParamElemToUpperBound += ( elem -> AnnotatedTypes.deepCopy( atmTv ) )
            //Note, consider the following class definition:
            // class MyClass<@LOWER T extends @UPPER Object> {...}
            //If @UPPER is not annotated then @LOWER is actually an EXACT bound not a lower
            //at the moment we are ensuring that @UPPER has an annotation and therefore you can
            //can consider @LOWER an actual lower bound but if @LOWER == @UPPER after solving then
            //we could leave off @UPPER
            val lowerClassTypeVp = typeParamVpFactory( index )
            lowerClassTypeVp.init(typeFactory, tree)
            annotateTopLevel( lowerClassTypeVp, treeTv, treeTv, atmTv, List() )

            inferenceChecker.typeParamElemCache += ( elem -> atmTv )

        case typeArg =>
          //TODO JB: What type args are these and handle them if necessary, seems to be none at the moment
          println( "Undhandled type args: " + typeArg.toString )
        }
      }
    }
  }
}

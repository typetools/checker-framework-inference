package checkers.inference

import com.sun.source.tree._
import com.sun.source.util.TreePath

import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType
import checkers.types.AnnotatedTypeMirror.AnnotatedWildcardType
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType
import checkers.types.AnnotatedTypeMirror.AnnotatedNullType
import javax.lang.model.`type`.TypeKind
import checkers.types.AnnotatedTypeMirror.AnnotatedArrayType
import checkers.types.AnnotatedTypeMirror.AnnotatedNoType
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable
import checkers.types.AnnotatedTypeMirror
import com.sun.source.tree.Tree
import javacutils.{AnnotationUtils, TreeUtils}
import annotations.io.ASTPath


import scala.collection.JavaConversions._
import scala.collection.immutable.List


object InferenceUtils {

  def isWithinMethod(typeFactory: InferenceAnnotatedTypeFactory[_], node: Tree): Boolean = {
    TreeUtils.enclosingMethod(typeFactory.getPath(node)) != null
  }

  def isWithinStaticInit(typeFactory: InferenceAnnotatedTypeFactory[_], node: Tree): Boolean = {
    var path = typeFactory.getPath(node)
    if (path == null) {
      // TODO: can we ignore this? Should we copy the behavior from WithinMethodVP/ClassVP?
      // println("InferenceUtils::isWithinStaticInit: empty path for Tree: " + node)
      return false;
    }

    //Traverse up the TreePath until we either reach the root or find the path
    //immediately enclosed by the class
    var parpath = path.getParentPath

    while (parpath!=null && parpath.getLeaf().getKind() != Tree.Kind.CLASS) {
      path = parpath
      parpath = parpath.getParentPath()
    }

    path.getLeaf.getKind == Tree.Kind.BLOCK
  }

  /**
   * Copy annotations from in to mod, descending into any nested types in
   * the two AnnotatedTypeMirrors.  Any existing annotations will be cleared first
   * @param in The AnnotatedTypeMirror that should be copied
   * @param mod The AnnotatedTypeMirror to which annotations will be copied
   */
  def copyAnnotations(in: AnnotatedTypeMirror, mod: AnnotatedTypeMirror) {
    copyAnnotationsImpl(in, mod, clearAndCopy, new java.util.LinkedList[AnnotatedTypeMirror])
  }

  def clearAnnos( mod: AnnotatedTypeMirror ) = {
    val oldAnnos = AnnotationUtils.createAnnotationSet()
    oldAnnos.addAll( mod.getAnnotations )

    mod.clearAnnotations
    oldAnnos
  }

  /** Clear mod of any annotations and copy those from in to mod
    * Does not descend into nested types if any
    * @param in  AnnotatedTypeMirror to copy
    * @param mod AnnotatedTypeMirror to clear then add to
    * @return The original set of annotations on Mod
    */
  private def clearAndCopy( in: AnnotatedTypeMirror, mod: AnnotatedTypeMirror ) {
    mod.clearAnnotations()
    mod.addAnnotations(in.getAnnotations)
  }

  //TODO: Annotations are only cleared if there is an annotation to replace
  /**
   * copyAnnotationsImpl contains the logic for copying all annotations on a type mirror for
   * a given type kind.  It will descend into the type and copy the nested annotations.  It can
   * be parameterized by a copy method in order to avoid duplicating this descent logic in case
   * you want some other copying logic then clearing an adding annotations if they exist (e.g. perhaps
   * you want to only replace annotation within a certain hierarchy)
   * @param in Annotated type mirror to copy
   * @param mod Annotated type mirror to which annotations will be added
   * @param copyMethod Method that actually copies the outer most annotations on one type mirror to another
   * @param visited A map of type mirrors contained by in and that have already been visited
   */
  private def copyAnnotationsImpl(in: AnnotatedTypeMirror, mod: AnnotatedTypeMirror,
                                  copyMethod : ((AnnotatedTypeMirror, AnnotatedTypeMirror) => Unit),
                                  visited: java.util.List[AnnotatedTypeMirror]) {
    // careful! We have to use reference equality here, because == doesn't seem to take annotations into account
    if (in eq mod) return

    if (visited.contains(in)) return
    visited.add(in)

    if (in.isAnnotated) {
      copyMethod(in, mod)

    } else if (in.isInstanceOf[AnnotatedNoType] ||
               in.isInstanceOf[AnnotatedNullType]) {
      // no annotations on "void" or "null"
    } else {
      // Some elements are not annotated. Maybe debug some more sometime.
      // println("copyAnnotations TODO: is there something to do for: " + in)
      // println("copyAnnotations TODO: is there something to do for with class: " + in.getClass)
    }

    def copyAnnotationsByIndex(inSeq : Seq[AnnotatedTypeMirror], modSeq : Seq[AnnotatedTypeMirror]) = {
      for ((in, mod) <- inSeq zip modSeq) {
        copyAnnotationsImpl(in, mod, copyMethod, visited)
      }
    }

    import TypeKind._
    (mod.getKind, in.getKind) match {
      case (DECLARED, DECLARED) =>
        val declaredType   = mod.asInstanceOf[AnnotatedDeclaredType]
        val declaredInType = in.asInstanceOf[AnnotatedDeclaredType]
        copyAnnotationsByIndex( declaredInType.getTypeArguments(), declaredType.getTypeArguments() )

      // Do NOT call
      // declaredType.setTypeArguments()
      // as this would take the other arguments, which might have been created by a different factory
      case (EXECUTABLE, EXECUTABLE) =>
        val exeType   = mod.asInstanceOf[AnnotatedExecutableType]
        val exeInType = in.asInstanceOf[AnnotatedExecutableType]
        copyAnnotationsImpl(exeInType.getReturnType, exeType.getReturnType, copyMethod, visited)
        copyAnnotationsByIndex( exeInType.getParameterTypes(), exeType.getParameterTypes() )
        copyAnnotationsByIndex( exeInType.getTypeVariables(),  exeType.getTypeVariables()  )

      case (ARRAY, ARRAY) =>
        val arrayType   = mod.asInstanceOf[AnnotatedArrayType]
        val arrayInType = in.asInstanceOf[AnnotatedArrayType]
        copyAnnotationsImpl( arrayInType.getComponentType, arrayType.getComponentType, copyMethod, visited )

      case (TYPEVAR, TYPEVAR) =>
        val tvin  = in.asInstanceOf[AnnotatedTypeVariable]
        val tvmod = mod.asInstanceOf[AnnotatedTypeVariable]
        copyAnnotationsImpl( tvin.getUpperBound, tvmod.getUpperBound, copyMethod, visited )
        copyAnnotationsImpl( tvin.getLowerBound, tvmod.getLowerBound, copyMethod, visited )

      case (TYPEVAR, _) =>
      // Why is sometimes the mod a type variable, but in is Declared or Wildcard?
      // For declared, the annotations match. For wildcards, in is unannotated?
      // TODO. Look at tests/Interfaces.java

      case (WILDCARD, WILDCARD) =>
        val tvin = in.asInstanceOf[AnnotatedWildcardType]
        val tvmod = mod.asInstanceOf[AnnotatedWildcardType]
        copyAnnotationsImpl( tvin.getExtendsBound, tvmod.getExtendsBound, copyMethod, visited )
        copyAnnotationsImpl( tvin.getSuperBound,   tvmod.getSuperBound,   copyMethod, visited )


      case (_,_) if mod.getKind().isPrimitive || in.getKind().isPrimitive =>
      // Primitives only take one annotation, which was already copied

      case (_,_) if mod.isInstanceOf[AnnotatedNoType] || mod.isInstanceOf[AnnotatedNullType] ||
                     in.isInstanceOf[AnnotatedNoType] || in.isInstanceOf[AnnotatedNullType]  =>
      // No annotations

      case _ =>
        println("InferenceUtils.copyAnnotationsImpl: unhandled getKind results: " + in +
                " and " + mod + "\n    of kinds: " + in.getKind + " and " + mod.getKind)
    }

  }

  /**
   * Gets an ASTPath to the given node.
   * @param typeFactory The typeFactory to use to get paths
   * @param node The node to get the ASTPath to
   * @throws RuntimeException if there is an unrecognized tree in the path
   * @return The ASTPath from the enclosing method or class to the node
   */
  def getASTPathToNode(typeFactory : InferenceAnnotatedTypeFactory[_], node : Tree) : ASTPath = {
    var path = typeFactory.getPath(node)
    if (path == null) {
      // println("InferenceUtils::getASTPathToNode: empty path for Tree: " + node)
      return null;
    }

    return getASTPathToNode(node, path)
  }

  /**
   * Helper method to get an ASTPath to the given node.
   * @param node The node to get the ASTPath to
   * @param path The TreePath to the node
   * @throws RuntimeException if there is an unrecognized tree in the path
   * @return The ASTPath from the enclosing method or class to the node
   */
  private def getASTPathToNode(node : Tree, path : TreePath) : ASTPath = {
    val parpath = path.getParentPath
    val parNode = parpath.getLeaf
    val parKind = parNode.getKind
    if (parKind == Tree.Kind.METHOD || parKind == Tree.Kind.CLASS) {
      new ASTPath
    } else {
      val astPath = getASTPathToNode(parNode, parpath)
      
      val (selector, arg) = parNode match {
        case att: AnnotatedTypeTree => {
          node match {
            case a: AnnotationTree => (ASTPath.ANNOTATION, att.getAnnotations.indexOf(node))
            case e: ExpressionTree => (ASTPath.UNDERLYING_TYPE, -1)
          }
        }
        case ait: ArrayAccessTree => {
          node match {
            case _ if node.equals(ait.getExpression) => (ASTPath.EXPRESSION, -1)
            case _ if node.equals(ait.getIndex) => (ASTPath.INDEX, -1)
          }
        }
        case att: ArrayTypeTree => (ASTPath.TYPE, -1)
        case at: AssertTree => {
          node match {
            case _ if node.equals(at.getCondition) => (ASTPath.CONDITION, -1)
            case _ if node.equals(at.getDetail) => (ASTPath.DETAIL, -1)
          }
        }
        case at: AssignmentTree => {
          node match {
            case _ if node.equals(at.getVariable) => (ASTPath.VARIABLE, -1)
            case _ if node.equals(at.getExpression) => (ASTPath.EXPRESSION, -1)
          }
        }
        case bt: BinaryTree => {
          node match {
            case _ if node.equals(bt.getLeftOperand) => (ASTPath.LEFT_OPERAND, -1)
            case _ if node.equals(bt.getRightOperand) => (ASTPath.RIGHT_OPERAND, -1)
          }
        }
        case bt: BlockTree => (ASTPath.STATEMENT, bt.getStatements.indexOf(node))
        case ct: CaseTree => {
          node match {
            case et: ExpressionTree => (ASTPath.EXPRESSION, -1)
            case st: StatementTree => (ASTPath.STATEMENT, ct.getStatements.indexOf(st))
          }
        }
        case ct: CatchTree => {
          node match {
            case vt: VariableTree => (ASTPath.PARAMETER, -1)
            case bt: BlockTree => (ASTPath.BLOCK, -1)
          }
        }
        case cat: CompoundAssignmentTree => {
          node match {
            case _ if node.equals(cat.getVariable) => (ASTPath.VARIABLE, -1)
            case _ if node.equals(cat.getExpression) => (ASTPath.EXPRESSION, -1)
          }
        }
        case cet: ConditionalExpressionTree => {
          node match {
            case _ if node.equals(cet.getCondition) => (ASTPath.CONDITION, -1)
            case _ if node.equals(cet.getTrueExpression) => (ASTPath.TRUE_EXPRESSION, -1)
            case _ if node.equals(cet.getFalseExpression) => (ASTPath.FALSE_EXPRESSION, -1)
          }
        }
        case dwl: DoWhileLoopTree => {
          node match {
            case _ if node.equals(dwl.getCondition) => (ASTPath.CONDITION, -1)
            case _ if node.equals(dwl.getStatement) => (ASTPath.STATEMENT, -1)
          }
        }
        case efl: EnhancedForLoopTree => {
          node match {
            case _ if node.equals(efl.getVariable) => (ASTPath.VARIABLE, -1)
            case _ if node.equals(efl.getExpression) => (ASTPath.EXPRESSION, -1)
            case _ if node.equals(efl.getStatement) => (ASTPath.STATEMENT, -1)
          }
        }
        case est: ExpressionStatementTree => (ASTPath.EXPRESSION, -1)
        case flt: ForLoopTree => {
          node match {
            case _ if node.equals(flt.getStatement) => (ASTPath.STATEMENT, -1)
            case _ if node.equals(flt.getCondition) => (ASTPath.CONDITION, -1)
            case _ if flt.getInitializer.contains(node) => (ASTPath.INITIALIZER, flt.getInitializer.indexOf(node))
            case _ if flt.getUpdate.contains(node) => (ASTPath.UPDATE, flt.getUpdate.indexOf(node))
          }
        }
        case it: IfTree => {
          node match {
            case _ if node.equals(it.getCondition) => (ASTPath.CONDITION, -1)
            case _ if node.equals(it.getThenStatement) => (ASTPath.THEN_STATEMENT, -1)
            case _ if node.equals(it.getElseStatement) => (ASTPath.ELSE_STATEMENT, -1)
          }
        }
        case iot: InstanceOfTree => {
          node match {
            case _ if node.equals(iot.getExpression) => (ASTPath.EXPRESSION, -1)
            case _ if node.equals(iot.getType) => (ASTPath.TYPE, -1)
          }
        }
        case ls: LabeledStatementTree => (ASTPath.STATEMENT, -1)
        case let: LambdaExpressionTree => {
          node match {
            case vt: VariableTree => (ASTPath.PARAMETER, let.getParameters.indexOf(vt))
            case _ if node.equals(let.getBody) => (ASTPath.BODY, -1)
          }
        }
        case mrt: MemberReferenceTree => {
          node match {
            case _ if node.equals(mrt.getQualifierExpression) => (ASTPath.QUALIFIER_EXPRESSION, -1)
            case et: ExpressionTree => (ASTPath.TYPE_ARGUMENT, mrt.getTypeArguments.indexOf(et))
          }
        }
        case mst: MemberSelectTree => (ASTPath.EXPRESSION, -1)
        case mit: MethodInvocationTree => {
          node match {
            case _ if node.equals(mit.getMethodSelect) => (ASTPath.METHOD_SELECT, -1)
            case et: ExpressionTree => (ASTPath.ARGUMENT, mit.getArguments.indexOf(et))
            case _ => (ASTPath.TYPE_ARGUMENT, mit.getTypeArguments.indexOf(node))
          }
        }
        case nat: NewArrayTree => {
          if (nat.getDimensions.contains(node)) (ASTPath.DIMENSION, nat.getDimensions.indexOf(node))
          else if (nat.getInitializers.contains(node)) (ASTPath.INITIALIZER, nat.getInitializers.indexOf(node))
          else (ASTPath.TYPE, -1)
        }
        case nct: NewClassTree => {
          if (nct.getEnclosingExpression.equals(node)) (ASTPath.ENCLOSING_EXPRESSION, -1)
          else if (nct.getIdentifier.equals(node)) (ASTPath.IDENTIFIER, -1)
          else if (nct.getArguments.contains(node)) (ASTPath.ARGUMENT, nct.getArguments.indexOf(node))
          else if (nct.getTypeArguments.contains(node)) (ASTPath.TYPE_ARGUMENT, nct.getTypeArguments.indexOf(node))
          else (ASTPath.CLASS_BODY, -1)
        }
        case ptt: ParameterizedTypeTree => {
          node match {
            case _ if node.equals(ptt.getType) => (ASTPath.TYPE, -1)
            case _ => (ASTPath.TYPE_ARGUMENT, ptt.getTypeArguments.indexOf(node))
          }
        }
        case pt: ParenthesizedTree => (ASTPath.EXPRESSION, -1)
        case rt: ReturnTree => (ASTPath.EXPRESSION, -1)
        case st: SwitchTree => {
          node match {
            case et: ExpressionTree => (ASTPath.EXPRESSION, -1)
            case ct: CaseTree => (ASTPath.CASE, st.getCases.indexOf(ct))
          }
        }
        case st: SynchronizedTree => {
          node match {
            case et: ExpressionTree => (ASTPath.EXPRESSION, -1)
            case bt: BlockTree => (ASTPath.BLOCK, -1)
          }
        }
        case tt: ThrowTree => (ASTPath.EXPRESSION, -1)
        case tt: TryTree => {
          node match {
            case _ if node.equals(tt.getBlock) => (ASTPath.BLOCK, -1)
            case _ if node.equals(tt.getFinallyBlock) => (ASTPath.FINALLY_BLOCK, -1)
            case ct: CatchTree => (ASTPath.CATCH, tt.getCatches.indexOf(ct))
          }
        }
        case tct: TypeCastTree => {
          node match {
            case _ if node.equals(tct.getExpression) => (ASTPath.EXPRESSION, -1)
            case _ if node.equals(tct.getType) => (ASTPath.TYPE, -1)
          }
        }
        case ut: UnaryTree => (ASTPath.EXPRESSION, -1)
        case utt: UnionTypeTree => (ASTPath.TYPE_ALTERNATIVE, utt.getTypeAlternatives.indexOf(node))
        case vt: VariableTree => {
          node match {
            case _ if node.equals(vt.getType) => (ASTPath.TYPE, -1)
            case _ if node.equals(vt.getInitializer) => (ASTPath.INITIALIZER, -1)
          }
        }
        case wlt: WhileLoopTree => {
          node match {
            case _ if node.equals(wlt.getCondition) => (ASTPath.CONDITION, -1)
            case _ if node.equals(wlt.getStatement) => (ASTPath.STATEMENT, -1)
          }
        }
        case wt: WildcardTree => (ASTPath.BOUND, -1)
        case _ => throw new RuntimeException("No match for tree: " + parNode + " with class " + parNode.getClass)
      }
      astPath.add(new ASTPath.ASTEntry(parKind, selector, arg))
      return astPath
    }
  }

  /**
   * Gets an ASTPath and converts it to a string in the format that can be read by the AFU.
   * @param path The ASTPath to convert
   * @return A String containing all of the ASTEntries of the ASTPath formatted as the AFU will parse it.
   */
  def convertASTPathToAFUFormat(path : ASTPath) : String = {
    var list = List[String]()
    var i = 0
    for (i <- 0 to path.size - 1) {
      val entry = path.get(i)
      val entryStr = entry.toString
      val index = entryStr.indexOf('.')
      list = list :+ capsToProperCase(entryStr.substring(0, index)) + entryStr.substring(index)
    }
    list.mkString(", ")
  }

  /**
   * Takes a string in ALL_CAPS and converts it to ProperCase. Necessary because the AFU requires
   * kinds in ASTPaths to be in proper case, but by default we get them in all caps.
   * @param str String in all caps
   * @return The same string with the initial letters of each word (at the beginning or following
   * an underscore) copitalized and the other letters lowercased
   */
  def capsToProperCase(str : String) : String = {
    return str.split("_").map(s => s.toLowerCase).map(s => s.capitalize).foldLeft("")(_+_)
  }

  /**
   * If obj is defined return it's hashcode
   * otherwise return the integer iElse
   * @param obj Object whose hashcode we want
   * @param iElse Alternative default hashcode
   * @return Either obj.hashCode or iElse
   */
  def hashcodeOrElse(obj : Object, iElse : Int) =
    Option(obj).map(_.hashCode).getOrElse(iElse)

  /**
   * @param intList A list of integers to add
   * @param multiplier A multiplier to multiply each integer by
   * @return an integer which is the sum of each member of intList which is first multiplied by multiplier
   */
  def sumWithMultiplier(intList : List[Int], multiplier : Int) =
    intList.fold(0)(_ + multiplier * _ )

}
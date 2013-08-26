package checkers.inference

import com.sun.source.tree.{MethodInvocationTree, Tree, CompilationUnitTree, ClassTree}
import checkers.source.{SourceChecker, SourceVisitor}
import checkers.basetype.BaseTypeVisitor
import javax.lang.model.element.TypeParameterElement
import checkers.quals.SubtypeOf
import checkers.util.MultiGraphQualifierHierarchy
import checkers.types.TypeHierarchy
import javax.lang.model.element.Name
import checkers.types.QualifierHierarchy
import java.util.HashMap
import javax.lang.model.element.VariableElement
import javax.lang.model.element.ExecutableElement
import javax.lang.model.`type`.TypeKind
import com.sun.source.tree.Tree.Kind
import com.sun.source.util.TreePath
import javax.lang.model.element.TypeElement
import javax.lang.model.element.AnnotationMirror
import javacutils.{TreeUtils, AnnotationUtils}
import checkers.types.AnnotatedTypeMirror.AnnotatedNullType
import java.util.Collections
import java.util.HashSet
import javax.lang.model.element.AnnotationValue
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror.AnnotatedArrayType
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType
import checkers.types.AnnotatedTypeMirror.AnnotatedPrimitiveType
import checkers.types.AnnotatedTypeMirror.AnnotatedWildcardType
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable
import checkers.quals.TypeQualifiers
import checkers.quals.Unqualified
import checkers.basetype.BaseTypeChecker
import javax.annotation.processing.ProcessingEnvironment
import quals.{RefineVarAnnot, VarAnnot, CombVarAnnot, LiteralAnnot}
import checkers.types.AnnotatedTypeFactory
import checkers.util.MultiGraphQualifierHierarchy.MultiGraphFactory
import scala.collection.mutable.{HashMap => MutHashMap}

class InferenceChecker extends BaseTypeChecker[InferenceAnnotatedTypeFactory[_]] {

  // map from fully qualified annotation name to the corresponding AnnotationMirror
  val REAL_QUALIFIERS: java.util.Map[String, AnnotationMirror] = new HashMap[String, AnnotationMirror]()
  var VAR_ANNOT: AnnotationMirror = null
  var COMBVAR_ANNOT: AnnotationMirror = null
  var REFVAR_ANNOT: AnnotationMirror = null
  var LITERAL_ANNOT: AnnotationMirror = null

  /*
  override def typeProcess(e: TypeElement, p: TreePath) {
	  super.typeProcess(e, p)
	  println("typeprocessed: " + currentRoot)
  }
  */
  //TODO IC1: Shouldn't we use AnnotationUtils (this is how it was done when Werner wrote CFInference originally)
  private def compareAnnoByString( anno : AnnotationMirror, string : String) = anno.getAnnotationType.toString == string
  def isVarAnnot(     anno : AnnotationMirror ) = compareAnnoByString( anno, VAR_ANNOT.getAnnotationType.toString     )
  def isCombVarAnnot( anno : AnnotationMirror ) = compareAnnoByString( anno, COMBVAR_ANNOT.getAnnotationType.toString )
  def isRefVarAnnot(  anno : AnnotationMirror ) = compareAnnoByString( anno, REFVAR_ANNOT.getAnnotationType.toString  )
  def isLiteralAnnot( anno : AnnotationMirror ) = compareAnnoByString( anno, LITERAL_ANNOT.getAnnotationType.toString )

  override def initChecker(): Unit = {
    InferenceMain.init(this)
    super.initChecker()

    val tquals = InferenceMain.getRealChecker.getSupportedTypeQualifiers()
    val elements = processingEnv.getElementUtils();

    import scala.collection.JavaConversions._
    for (tq <- tquals) {
      REAL_QUALIFIERS.put(tq.getName, AnnotationUtils.fromClass(elements, tq))
    }

    VAR_ANNOT     = AnnotationUtils.fromClass(elements, classOf[VarAnnot])
    REFVAR_ANNOT  = AnnotationUtils.fromClass(elements, classOf[RefineVarAnnot])
    COMBVAR_ANNOT = AnnotationUtils.fromClass(elements, classOf[CombVarAnnot])
    LITERAL_ANNOT = AnnotationUtils.fromClass(elements, classOf[LiteralAnnot])
    InferenceMain.getRealChecker.asInstanceOf[SourceChecker[_]].initChecker()
  }

  def cleanUp() {
    varElemCache.clear()
    exeElemCache.clear()
    typeParamElemCache.clear()
    typeElemCache.clear()
    typeParamElemToUpperBound.clear()
  }

  override protected def createSourceVisitor(root: CompilationUnitTree): InferenceVisitor = {
    InferenceMain.createVisitors(root)
  }

  /* Maybe we want to (also) use this to add the qualifiers from the "main" checker. */
  override protected def createSupportedTypeQualifiers(): java.util.Set[Class[_ <: java.lang.annotation.Annotation]] = {
    val typeQualifiers = new HashSet[Class[_ <: java.lang.annotation.Annotation]]()

    typeQualifiers.add(classOf[Unqualified])
    typeQualifiers.add(classOf[VarAnnot])
    typeQualifiers.add(classOf[RefineVarAnnot])
    typeQualifiers.add(classOf[CombVarAnnot])
    typeQualifiers.add(classOf[LiteralAnnot])

    typeQualifiers.addAll(InferenceMain.getRealChecker.getSupportedTypeQualifiers())

    // println("modifiers: " + Collections.unmodifiableSet(typeQualifiers))
    Collections.unmodifiableSet(typeQualifiers)
  }

  override def createFactory(root: CompilationUnitTree): InferenceAnnotatedTypeFactory[_] = {
    new InferenceAnnotatedTypeFactory(this, root, InferenceMain.getRealChecker.withCombineConstraints)
  }

  // Make the processing environment available to other parts
  def getProcessingEnv = processingEnv

  override protected def createTypeHierarchy(): TypeHierarchy = {
    new InferenceTypeHierarchy(this, getQualifierHierarchy());
  }

  private class InferenceTypeHierarchy(checker: BaseTypeChecker[_], qh: QualifierHierarchy)
      extends TypeHierarchy(checker, qh) {
    // copied from super, also allow type arguments with different qualifiers and create equality constraints
    override protected def isSubtypeAsTypeArgument(rhs: AnnotatedTypeMirror, lhs: AnnotatedTypeMirror): Boolean = {
      if (lhs.getKind() == TypeKind.WILDCARD && rhs.getKind() != TypeKind.WILDCARD) {
        if (visited.contains(lhs))
          return true

        visited.add(lhs)
        val wclhs = lhs.asInstanceOf[AnnotatedWildcardType].getExtendsBound()
        if (wclhs == null)
          return true
        return isSubtypeImpl(rhs, wclhs)
      }

      if (lhs.getKind() == TypeKind.WILDCARD && rhs.getKind() == TypeKind.WILDCARD) {
        return isSubtype(rhs.asInstanceOf[AnnotatedWildcardType].getExtendsBound(),
          lhs.asInstanceOf[AnnotatedWildcardType].getExtendsBound())
      }

      if (lhs.getKind() == TypeKind.TYPEVAR && rhs.getKind() != TypeKind.TYPEVAR) {
        if (visited.contains(lhs))
          return true
        visited.add(lhs)
        return isSubtype(rhs, lhs.asInstanceOf[AnnotatedTypeVariable].getUpperBound())
      }

      val lannoset = lhs.getAnnotations()
      val rannoset = rhs.getAnnotations()

      // TODO IC2: improve handling of raw types
      // println("lhs: " + lhs)
      // println("rhs: " + rhs)

      // if (!AnnotationUtils.areSame(lannoset, rannoset))
      //  return false
      if (lannoset.size == rannoset.size) {

        if (lannoset.size != 1) {
          // TODO IC3: maybe different for other type systems...
          return true
        }

        if(!InferenceMain.isPerformingFlow) {
          if (InferenceMain.DEBUG(this)) {
            println("InferenceTypeHierarchy::isSubtypeAsTypeArgument: Equality constraint for type argument.")
          }
          InferenceMain.constraintMgr.addEqualityConstraint(lannoset.iterator().next(), rannoset.iterator().next())
        }

        if (lhs.getKind() == TypeKind.DECLARED && rhs.getKind() == TypeKind.DECLARED)
          return isSubtypeTypeArguments(rhs.asInstanceOf[AnnotatedDeclaredType], lhs.asInstanceOf[AnnotatedDeclaredType])
        else if (lhs.getKind() == TypeKind.ARRAY && rhs.getKind() == TypeKind.ARRAY) {
          // arrays components within type arguments are invariants too
          // List<String[]> is not a subtype of List<Object[]>
          val rhsComponent = rhs.asInstanceOf[AnnotatedArrayType].getComponentType()
          val lhsComponent = lhs.asInstanceOf[AnnotatedArrayType].getComponentType()
          return isSubtypeAsTypeArgument(rhsComponent, lhsComponent)
        }
      } else {
        // TODO IC4: this case happens with raw types, were no annotations were added to the non-existent arguments.
        // Think about this.
      }
      return true
    }
  }

  override protected def createQualifierHierarchyFactory(): MultiGraphQualifierHierarchy.MultiGraphFactory = {
          new MultiGraphQualifierHierarchy.MultiGraphFactory(this);
  }

  override def createQualifierHierarchy(factory : MultiGraphFactory) : QualifierHierarchy = new InferenceQualifierHierarchy(factory)

  private class InferenceQualifierHierarchy(f: MultiGraphFactory) extends MultiGraphQualifierHierarchy(f) {
    override def isSubtype(rhs: java.util.Collection[_ <: AnnotationMirror], lhs: java.util.Collection[_ <: AnnotationMirror]): Boolean = {
      if (rhs.isEmpty || lhs.isEmpty || (lhs.size()!=rhs.size())) {
        // TODO IC5: make behavior in superclass easier to adapt.
        true
      } else {
        super.isSubtype(rhs, lhs)
      }
    }

    override def isSubtype(sub: AnnotationMirror, sup: AnnotationMirror): Boolean = {

      if( !InferenceMain.isPerformingFlow ) {
        if (InferenceMain.DEBUG(this)) {
          println("InferenceQualifierHierarchy::isSubtype: Subtype constraint for qualifiers sub: " + sub + " sup: " + sup)
        }
        InferenceMain.constraintMgr.addSubtypeConstraint(sub, sup)
      }

      true
    }

    override def leastUpperBound(a1: AnnotationMirror, a2: AnnotationMirror): AnnotationMirror = {
      if (InferenceMain.DEBUG(this)) {
        println("InferenceQualifierHierarchy::leastUpperBound(" + a1 + ", " + a2 + ")")
      }

      if (a1 == null) { return a2 }
      if (a2 == null) { return a1 }


      if( !InferenceMain.isPerformingFlow ) {
        val res = InferenceMain.slotMgr.createCombVariable

        if (InferenceMain.DEBUG(this)) {
          println("InferenceQualifierHierarchy::leastUpperBound: Two subtype constraints for qualifiers.")
        }

        val c1 = InferenceMain.constraintMgr.addSubtypeConstraint(a1, res.getAnnotation)
        val c2 = InferenceMain.constraintMgr.addSubtypeConstraint(a2, res.getAnnotation)

        res.getAnnotation
      } else {
        super.leastUpperBound(a1, a2)
      }
    }
  }

  /*
  // just for debugging
  override def isSubtype(sub: AnnotatedTypeMirror,
    sup: AnnotatedTypeMirror): Boolean = {
    println("InferenceChecker::isSubtype: sub: " + sub + " and " + sup)
    super.isSubtype(sub, sup)
  }
  */

  val varElemCache       = new MutHashMap[VariableElement, AnnotatedTypeMirror]()
  val exeElemCache       = new MutHashMap[ExecutableElement, AnnotatedExecutableType]()
  val typeParamElemCache = new MutHashMap[TypeParameterElement, AnnotatedTypeVariable]()
  val typeElemCache      = new MutHashMap[TypeElement, AnnotatedTypeMirror]()

  /**
   * Maps individual extends/implements trees to AnnotatedTypeMirrors for those trees
   * See: AnnotatedTypeFactory#getAnnotatedTypeFromTypeTree
   */
  val extImplsTreeCache = new MutHashMap[Tree, AnnotatedTypeMirror]()

  //For classes that don't have extends trees, we still want to be able to write an annotation
  //on it (since all classes have an implicit extends Object).  Cache the typeElement to the
  //AnnotationMirror that represents the variable
  val classToMissingExtCache = new MutHashMap[TypeElement, AnnotationMirror]()

  //TODO JB IC6: Currently the upperbound always gets overwritten by the type annotation in front of a Type Parameter
  //TODO JB: when both exist (as is always the case in Verigames).  This means we have no way of getting the original
  //TODO JB: upper bound variable.  Keep a cache of them.  This is majorly kludgey.  Either refactor the Checker
  //TODO JB: Framework or remove this comment
  val typeParamElemToUpperBound = new MutHashMap[TypeParameterElement, AnnotatedTypeVariable]()

  val exeElemToReceiverCache = new MutHashMap[ExecutableElement, AnnotatedDeclaredType]()

  val methodInvocationToTypeArgs = new MutHashMap[Tree, List[AnnotatedTypeMirror]]()

  def hasBounds( typeParamElem : TypeParameterElement ) = typeParamElemToUpperBound.contains( typeParamElem)

  def getTypeParamBounds( typeParamElem : TypeParameterElement ) =
    ( typeParamElemToUpperBound(typeParamElem) -> typeParamElemCache(typeParamElem) )

}

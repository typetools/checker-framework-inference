package checkers.inference

import com.sun.source.tree._
import checkers.source.{SourceChecker, SourceVisitor}
import javax.lang.model.element._
import java.util.HashMap
import javax.lang.model.`type`.TypeKind
import com.sun.source.tree.Tree.Kind
import com.sun.source.util.{Trees}
import javacutils.{TreeUtils, AnnotationUtils}
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType
import checkers.types.AnnotatedTypeMirror.AnnotatedTypeVariable
import checkers.basetype.BaseTypeChecker
import quals.{RefineVarAnnot, VarAnnot, CombVarAnnot, LiteralAnnot}
import scala.collection.mutable.{HashMap => MutHashMap, HashSet => MutHashSet, LinkedHashMap, MutableList}
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.VariableElement
import checkers.inference.util.WildcardBounds

//import checkers.inference.util.{WildcardBounds, WildcardIndex}

class InferenceChecker extends BaseTypeChecker {

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
    
    val elements = processingEnv.getElementUtils();
    VAR_ANNOT     = AnnotationUtils.fromClass(elements, classOf[VarAnnot])
    REFVAR_ANNOT  = AnnotationUtils.fromClass(elements, classOf[RefineVarAnnot])
    COMBVAR_ANNOT = AnnotationUtils.fromClass(elements, classOf[CombVarAnnot])
    LITERAL_ANNOT = AnnotationUtils.fromClass(elements, classOf[LiteralAnnot]);
    
    //In between these brackets, is code copied directly from SourceChecker
    //except for the last line assigning the visitor
    {
      val trees = Trees.instance(processingEnv)
      assert( trees != null ) /*nninvariant*/
      this.trees = trees;

      this.messager = processingEnv.getMessager();
      this.messages = getMessages();

      this.visitor = createInferenceVisitor();
    }

    val tquals = visitor.asInstanceOf[InferenceVisitor[_,_]].getInferenceTypeFactory.realAnnotatedTypeFactory.getSupportedTypeQualifiers()


    import scala.collection.JavaConversions._
    for (tq <- tquals) {
      REAL_QUALIFIERS.put(tq.getName, AnnotationUtils.fromClass(elements, tq))
    }


    InferenceMain.getRealChecker.asInstanceOf[SourceChecker].initChecker()
  }

  def cleanUp() {
    varElemCache.clear()
    exeElemCache.clear()
    typeParamElemCache.clear()
    typeElemCache.clear()
    typeParamElemToUpperBound.clear()
    methodInvocationToTypeArgs.clear()
  }

  override protected def createSourceVisitor() : SourceVisitor[_,_] = return null

  protected def createInferenceVisitor() : InferenceVisitor[_,_] = {
    InferenceMain.createVisitors()
  }

  def getInferenceTypeFactory = visitor.asInstanceOf[InferenceVisitor[_,_]].getInferenceTypeFactory

  // Make the processing environment available to other parts
  def getProcessingEnv = processingEnv

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
  //For type parameters in method/class declarations, ensures we don't annotate the same thing twice
  val visited = new MutHashSet[Element]

  def hasBounds( typeParamElem : TypeParameterElement ) = typeParamElemToUpperBound.contains( typeParamElem)

  def getTypeParamBounds( typeParamElem : TypeParameterElement ) =
    ( typeParamElemToUpperBound(typeParamElem) -> typeParamElemCache(typeParamElem) )

  // Cache to hold the BallSizeTestConstraint until it is added by GameVisitor.visitBinary
  // Needed for constraint ordering
  val ballSizeTestCache = new MutHashMap[Tree, BallSizeTestConstraint]

  // Subtype constraints created for merge refinement variables. These are generated during flow, but we want them added to
  // the board as the first connection to a merge variable, but the last connection to the variables being merged.
  val mergeRefinementConstraintCache = new LinkedHashMap[RefinementVariable, List[SubtypeConstraint]]


  // Map of WildcardTree to its bounds.  Note that AnnotatedWildcardTypes DO NOT suffer from the bug that causes the
  // lower bound to be overwritten in AnnotatedTypeVariable.  HOWEVER, we need to be able to support missing/implied
  // bounds for a wildcard.  That is, all wildcards have two bounds ( one is always explicit ) and the other is the
  // primary annotation on the WildcardTree, so we always hold on to a pair of type mirrors for the bounds
  //val wildcardBounds = new MutHashMap[WildcardIndex, WildcardBounds]
  val wildcardBounds = new MutHashMap[WildcardTree, WildcardBounds]
}

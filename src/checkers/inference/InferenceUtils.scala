package checkers.inference

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

import scala.collection.JavaConversions._


object InferenceUtils {

  def isWithinMethod(typeFactory: InferenceAnnotatedTypeFactory, node: Tree): Boolean = {
    TreeUtils.enclosingMethod(typeFactory.getPath(node)) != null
  }

  def isWithinStaticInit(typeFactory: InferenceAnnotatedTypeFactory, node: Tree): Boolean = {
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
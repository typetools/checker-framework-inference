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
import checkers.util.TreeUtils

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

    var parpath = path.getParentPath

    while (parpath!=null && parpath.getLeaf().getKind() != Tree.Kind.CLASS) {
      path = parpath
      parpath = parpath.getParentPath()
    }
    path.getLeaf.getKind == Tree.Kind.BLOCK
  }

  /*
   * TODO
   * Should the copyAnnotations methods be part of the Framework?
   * Maybe they are somewhere already???
   */
  def copyAnnotations(in: AnnotatedTypeMirror, mod: AnnotatedTypeMirror) {
    copyAnnotationsImpl(in, mod, new java.util.LinkedList[AnnotatedTypeMirror])
  }

  private def copyAnnotationsImpl(in: AnnotatedTypeMirror, mod: AnnotatedTypeMirror,
    visited: java.util.List[AnnotatedTypeMirror]) {
    // careful! We have to use reference equality here, because == doesn't seem to take annotations into account
    if (in eq mod) return

    if (visited.contains(in)) return
    visited.add(in)

    if (in.isAnnotated) {
      mod.clearAnnotations
      mod.addAnnotations(in.getAnnotations)
    } else if (in.isInstanceOf[AnnotatedNoType] ||
      in.isInstanceOf[AnnotatedNullType]) {
      // no annotations on "void" or "null"
    } else {
      // Some elements are not annotated. Maybe debug some more sometime.
      // println("copyAnnotations TODO: is there something to do for: " + in)
      // println("copyAnnotations TODO: is there something to do for with class: " + in.getClass)
    }

    if (mod.getKind() == TypeKind.DECLARED && in.getKind() == TypeKind.DECLARED) {
      val declaredType = mod.asInstanceOf[AnnotatedDeclaredType]
      val declaredInType = in.asInstanceOf[AnnotatedDeclaredType]

      import scala.collection.JavaConversions._
      for ((in, mod) <- declaredInType.getTypeArguments() zip declaredType.getTypeArguments()) {
        copyAnnotationsImpl(in, mod, visited)
      }

      // Do NOT call
      // declaredType.setTypeArguments()
      // as this would take the other arguments, which might have been created by a different factory
    } else if (mod.getKind() == TypeKind.EXECUTABLE && in.getKind() == TypeKind.EXECUTABLE) {
      val exeType = mod.asInstanceOf[AnnotatedExecutableType]
      val exeInType = in.asInstanceOf[AnnotatedExecutableType]
      copyAnnotationsImpl(exeInType.getReturnType, exeType.getReturnType, visited)
      
      for (i <- 0 until exeInType.getParameterTypes().size()) {
          copyAnnotationsImpl(exeInType.getParameterTypes().get(i), exeType.getParameterTypes().get(i), visited)
      }
      for (i <- 0 until exeInType.getTypeVariables().size()) {
          copyAnnotationsImpl(exeInType.getTypeVariables().get(i), exeType.getTypeVariables().get(i), visited)
      }
      
    } else if (mod.getKind() == TypeKind.ARRAY && in.getKind() == TypeKind.ARRAY) {
      val arrayType = mod.asInstanceOf[AnnotatedArrayType]
      val arrayInType = in.asInstanceOf[AnnotatedArrayType]
      copyAnnotationsImpl(arrayInType.getComponentType, arrayType.getComponentType, visited)
    } else if (mod.getKind() == TypeKind.TYPEVAR && in.getKind() == TypeKind.TYPEVAR) {
      val tvin = in.asInstanceOf[AnnotatedTypeVariable]
      val tvmod = mod.asInstanceOf[AnnotatedTypeVariable]

      copyAnnotationsImpl(tvin.getUpperBound, tvmod.getUpperBound, visited)
      copyAnnotationsImpl(tvin.getLowerBound, tvmod.getLowerBound, visited)
    } else if (mod.getKind() == TypeKind.TYPEVAR) {
      // Why is sometimes the mod a type variable, but in is Declared or Wildcard?
      // For declared, the annotations match. For wildcards, in is unannotated?
      // TODO. Look at tests/Interfaces.java
    } else if (mod.getKind() == TypeKind.WILDCARD && in.getKind() == TypeKind.WILDCARD) {
      val tvin = in.asInstanceOf[AnnotatedWildcardType]
      val tvmod = mod.asInstanceOf[AnnotatedWildcardType]

      copyAnnotationsImpl(tvin.getExtendsBound, tvmod.getExtendsBound, visited)
      copyAnnotationsImpl(tvin.getSuperBound, tvmod.getSuperBound, visited)
    } else if (mod.getKind().isPrimitive || in.getKind().isPrimitive) {
      // Primitives only take one annotation, which was already copied
    } else if (mod.isInstanceOf[AnnotatedNoType] || mod.isInstanceOf[AnnotatedNullType] ||
      in.isInstanceOf[AnnotatedNoType] || in.isInstanceOf[AnnotatedNullType]) {
      // No annotations
    } else {
      println("InferenceUtils.copyAnnotationsImpl: unhandled getKind results: " +
        in + " and " + mod + "\n    of kinds: " + in.getKind + " and " + mod.getKind)
    }
  }

}
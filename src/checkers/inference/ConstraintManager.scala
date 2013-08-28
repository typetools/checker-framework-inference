package checkers.inference

import javax.lang.model.element.{ExecutableElement, TypeParameterElement, TypeElement, AnnotationMirror}
import javax.lang.model.`type`.TypeVariable
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror._
import javax.lang.model.`type`.TypeKind
import com.sun.source.tree._
import javacutils.{InternalUtils, ElementUtils, TreeUtils}
import annotator.scanner.StaticInitScanner

import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.Tree
import checkers.util.AnnotatedTypes
import checkers.inference.util.CollectionUtil._
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import checkers.inference.util.SlotUtil
import checkers.inference.quals.LiteralAnnot

/**
 * ConstraintManager maintains the list of constraints created during inference.  It also contains
 * methods/utilities used to create constraints.
 */
class ConstraintManager {
  //TODO CM1: NEED TO FIX EQUALS OF SET TYPE POSITION
  val constraints = new scala.collection.mutable.LinkedHashSet[Constraint]()

  // addSubtypeConstraints not needed, as the framework calls this for
  // each type argument.
  // TODO CM2: check

  def cleanUp() {
    constraints.clear
  }

  def addSubtypeConstraint(sub: AnnotationMirror, sup: AnnotationMirror) {
    val subel = InferenceMain.slotMgr.extractSlot( sub )
    val supel = InferenceMain.slotMgr.extractSlot( sup )
    
    if (subel == null || supel == null) {
      if (InferenceMain.DEBUG(this)) {
        // TODO CM3: in the UTS should only happen for primitive types.
        // however, we don't know the underlying type
        println("ConstraintManager::addSubtypeConstraint: no annotation in subtype: " + sub + " or supertype: " + sup)
      }
    } else if (subel.isInstanceOf[Constant] && supel.isInstanceOf[Constant]) {
      // Ignore trivial subtype relationships
      if (InferenceMain.DEBUG(this)) {
        println("ConstraintManager::addSubtypeConstraint: ignoring trivial subtype: " + sub + " or supertype: " + sup)
      }
    } else {
      addSubtypeConstraint(subel, supel)
    }
  }

  def addSubtypeConstraint(sub: Slot, sup: Slot) {
    if (sub == null || sup == null) {
      println("ConstraintManager::addSubtypeConstraint: one of the arguments is null!")
      return
    }

    if (sub == sup) {
      // don't create a constraint with yourself
    } else {
      val c = SubtypeConstraint(sub, sup)
      if (InferenceMain.DEBUG(this)) {
        println("New " + c)
      }
      constraints += c
    }
  }

  def addCombineConstraints(owner: AnnotatedTypeMirror,
    ty: AnnotatedTypeMirror): AnnotatedTypeMirror = {
    /* non-generic case:
     val recv = InferenceMain.slotMgr.extractSlot(owner)
     val decl = InferenceMain.slotMgr.extractSlot(ty)
     val res = addCombineConstraint(recv, decl)
    */

    // println("owner: " + owner)
    // println("ty: " + ty)

    val declowner = if (owner.getKind() == TypeKind.TYPEVAR) {
      // TODO CM4: need to go to the cache?
      owner.asInstanceOf[AnnotatedTypeVariable].getUpperBound()
    } else {
      owner
    }

    val recv = InferenceMain.slotMgr.extractSlot(declowner)

    if (recv == LiteralThis || recv == LiteralSuper) {
      // No combination for accesses through the current object
      // TODO CM5: is this good? should we copy ty?
      // TODO: is this GUT specific or general enough?
      if (InferenceMain.DEBUG(this)) {
        println("ConstraintManager::addCombineConstraints: No constraint necessary for combination with this.")
      }
      return ty
    }

    var combinedType: AnnotatedTypeMirror = combineSlotWithType(recv, ty)

    combinedType = substituteTVars(declowner, combinedType)

    // println("combTT comb: " + combinedType)
    combinedType
  }

  // Used to limit the recursion in upper bounds
  // private var isTypeVarExtends: Boolean = false

  private def combineSlotWithType(recv: Slot,
    ty: AnnotatedTypeMirror): AnnotatedTypeMirror = {

    // println("combineSlotWithType: recv: " + recv)
    // println("combineSlotWithType: ty: " + ty)

    if (ty.getKind().isPrimitive()) {
      // TODO CM5: we should also create a combine constraint
      // Might be needed for other type systems
      return ty
    } else if (ty.getKind() == TypeKind.TYPEVAR) {
      /* TODO CM6: remove this left-over code:
      if (!isTypeVarExtends) {
        isTypeVarExtends = true

        val atv = ty.getCopy(true).asInstanceOf[AnnotatedTypeVariable]
        val typevarBound = atv.getUpperBound()
        val resUpper = combineSlotWithType(recv, typevarBound)

        val mapping = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()
        mapping.put(typevarBound, resUpper)

        val result = atv.substitute(mapping)
        isTypeVarExtends = false
        return result
      }*/
      return ty
    } else if (ty.getKind() == TypeKind.DECLARED) {
      // Create a copy
      val declaredType = ty.getCopy(true).asInstanceOf[AnnotatedDeclaredType]
      val mapping = new collection.mutable.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      val declSlot = InferenceMain.slotMgr.extractSlot(declaredType)
      val combinedSlot = addCombineConstraint(recv, declSlot)

      if (combinedSlot == null) {
        // addCombineConstraint already output a message
        return ty
      }

      // Get the combined type arguments
      for (typeArgument: AnnotatedTypeMirror <- declaredType.getTypeArguments()) {
        val combinedTypeArgument = combineSlotWithType(recv, typeArgument)
        mapping.put(typeArgument, combinedTypeArgument)
      }

      // Construct result type
      val result: AnnotatedTypeMirror = declaredType.substitute(mapping)
      result.clearAnnotations()
      result.addAnnotation(combinedSlot.getAnnotation)

      return result
    } else if (ty.getKind() == TypeKind.ARRAY) {
      // Create a copy
      val arrayType = ty.getCopy(true).asInstanceOf[AnnotatedArrayType]
      // Get the combined main modifier
      val arraySlot = InferenceMain.slotMgr.extractSlot(arrayType)
      val combinedSlot = addCombineConstraint(recv, arraySlot)

      if (combinedSlot == null) {
        // addCombineConstraint already output a message
        return ty
      }

      // Construct result type            
      arrayType.clearAnnotations()
      arrayType.addAnnotation(combinedSlot.getAnnotation)

      // Combine element type
      val elemType = arrayType.getComponentType()
      val combinedElemType = combineSlotWithType(recv, elemType)

      arrayType.setComponentType(combinedElemType)
      return arrayType
    } else if (ty.getKind() == TypeKind.WILDCARD) {
      // Create a copy
      val wildType = ty.getCopy(true).asInstanceOf[AnnotatedWildcardType]
      val mapping = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      // There is no main modifier for a wildcard

      if (wildType.getExtendsBound() != null) {
        val combined = combineSlotWithType(recv, wildType.getExtendsBound())
        mapping.put(wildType.getExtendsBound(), combined)
      }
      if (wildType.getSuperBound() != null) {
        val combined = combineSlotWithType(recv, wildType.getSuperBound())
        mapping.put(wildType.getSuperBound(), combined)
      }

      // Construct result type
      val result: AnnotatedTypeMirror = wildType.substitute(mapping)
      return result
    } else {
      println("ConstraintManager.combineSlotWithType: unknown result.getKind(): " + ty.getKind())
      assert(false)
      return null
    }
  }

  private def substituteTVars(lhs: AnnotatedTypeMirror, rhs: AnnotatedTypeMirror): AnnotatedTypeMirror = {

    if (rhs.getKind() == TypeKind.TYPEVAR) {
      // method type variables will never be found and no special case needed; maybe as optimization.
      val rhsTypeVar = rhs.getCopy(true).asInstanceOf[AnnotatedTypeVariable]

      if (lhs.getKind() == TypeKind.DECLARED) {
        getTypeVariableSubstitution(lhs.asInstanceOf[AnnotatedDeclaredType], rhsTypeVar)
      } else {
        rhs
      }
      // else TODO CM7: the receiver might be another type variable... should we do something?
    } else if (rhs.getKind() == TypeKind.DECLARED) {
      // Create a copy
      val declaredType = rhs.getCopy(true).asInstanceOf[AnnotatedDeclaredType]
      val mapping = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      for (typeArgument <- declaredType.getTypeArguments()) {
        val substTypeArgument = substituteTVars(lhs, typeArgument)
        mapping.put(typeArgument, substTypeArgument)
      }

      // Construct result type
      declaredType.substitute(mapping)
    } else if (rhs.getKind() == TypeKind.WILDCARD) {
      val wildType = rhs.getCopy(true).asInstanceOf[AnnotatedWildcardType]
      val mapping = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      if (wildType.getExtendsBound() != null) {
        val substBound = substituteTVars(lhs, wildType.getExtendsBound())
        mapping.put(wildType.getExtendsBound(), substBound)
      }
      if (wildType.getSuperBound() != null) {
        val substBound = substituteTVars(lhs, wildType.getSuperBound())
        mapping.put(wildType.getSuperBound(), substBound)
      }

      wildType.substitute(mapping)
    } else if (rhs.getKind() == TypeKind.ARRAY) {
      // Create a copy
      val arrayType = rhs.getCopy(true).asInstanceOf[AnnotatedArrayType]

      val mapping = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      val compType = arrayType.getComponentType()
      val substTypeArgument = substituteTVars(lhs, compType)
      mapping.put(compType, substTypeArgument)

      // Construct result type
      arrayType.substitute(mapping)
    } else if (rhs.getKind().isPrimitive()) {
      // nothing to do for primitive types
      rhs
    } else {
      println("ConstraintManager::substituteTVars: What should be done with: " + rhs + " of kind: " + rhs.getKind())
      rhs
    }
  }

  private def getTypeVariableSubstitution(ltype: AnnotatedDeclaredType, rvar: AnnotatedTypeVariable): AnnotatedTypeMirror = {
    // println("Looking for " + var + " in type " + type)

    val (decltype, foundindex) = findDeclType(ltype, rvar)

    if (decltype == null) {
      return rvar
    }

    if ( !decltype.wasRaw() ) {
      val tas = decltype.getTypeArguments()
      // assert foundindex < tas.size()) {
      // CAREFUL: return a copy, as we want to modify the type later.
      return tas.get(foundindex).getCopy(true)
    } else {
      // TOD CM8O: use upper bound instead of var
      // type.getTypeArguments()
      // System.out.println("Raw Type: " + decltype)
      // TODO CM9: do we still need this:
      if ( !InferenceUtils.isAnnotated( rvar.getUpperBound ) ) {
        // TODO CM10: hmm, seems to be needed :-(
        rvar.getUpperBound().addAnnotation( InferenceMain.getRealChecker.defaultQualifier )
      }

      return rvar.getUpperBound()
    }
  }

  private def findDeclType(ltype: AnnotatedDeclaredType, rvar: AnnotatedTypeVariable): (AnnotatedDeclaredType, Int) = {
    // println("Finding " + var + " in type " + type)

    val varelem = rvar.getUnderlyingType().asElement()

    val dtype = ltype.getUnderlyingType()
    val el = dtype.asElement().asInstanceOf[TypeElement]
    val tparams = el.getTypeParameters()
    var foundindex: Int = 0

    for (tparam <- tparams) {
      if (tparam.equals(varelem) ||
        //TODO CM11: comparing by name!!!???
        // Sometimes "E" and "E extends Object" are compared, which do not match by "equals".
        tparam.getSimpleName().equals(varelem.getSimpleName())) {
        // we found the right index!
        // TODO CM12: optimize this loop for Scala, a break here doesn't work.
      } else {
        foundindex += 1
      }
    }

    if (foundindex >= tparams.size()) {
      // didn't find the desired type :-(
      for (sup <- ltype.directSuperTypes()) {
        val res = findDeclType(sup, rvar)

        if (res != null) {
          return res
        }
      }
      // we reach this point if the variable wasn't found in any recursive call.
      return (null, 0)
    }

    (ltype, foundindex)
  }

  def addCombineConstraint(recv: Slot, decl: Slot): Slot = {
    if (recv == null || decl == null) {
      println("ConstraintManager::addCombineConstraint: one of the arguments is null!")
      return null
    }
    val res = InferenceMain.slotMgr.createCombVariable()
    val c = CombineConstraint(recv, decl, res)
    if (InferenceMain.DEBUG(this)) {
      println("New " + c)
    }
    constraints += c
    res
  }

  def addEqualityConstraint(left: AnnotationMirror, right: AnnotationMirror) {

    val leftel = InferenceMain.slotMgr.extractSlot(left)
    val rightel = InferenceMain.slotMgr.extractSlot(right)

    if (leftel == null || rightel == null) {
      println("ConstraintManager::addEqualityConstraint: no annotation in : " + left + " or: " + right)
    } else {
      addEqualityConstraint(leftel, rightel)
    }
  }

  def addEqualityConstraint(ell: Slot, elr: Slot) {
    if (ell == null || elr == null) {
      println("ConstraintManager::addEqualityConstraint: one of the arguments is null!")
      return
    }

    if (ell == elr) {
      // don't create a constraint if it's the same element already
    } else {
      val c = EqualityConstraint(ell, elr)
      if (InferenceMain.DEBUG(this)) {
        println("New " + c)
      }
      constraints += c
    }
  }

  def addInequalityConstraint(context: VariablePosition, ell: Slot, elr: Slot) {
    if (ell == null || elr == null) {
      println("ConstraintManager::addInequalityConstraint: one of the arguments is null!")
      return
    }

    if (ell == elr) {
      println("ConstraintManager::addInequalityConstraint: Inequality with self, ignored! Slot: " + ell)
    } else {
      val c = InequalityConstraint(context, ell, elr)
      if (InferenceMain.DEBUG(this)) {
        println("New " + c)
      }
      constraints += c
    }
  }

  def addComparableConstraint(ell: Slot, elr: Slot) {
    if (ell == null || elr == null) {
      println("ConstraintManager::addComparableConstraint: one of the arguments is null!")
      return
    }

    if (ell == elr) {
      // nothing to do if it's the same slot already
    } else {
      val c = ComparableConstraint(ell, elr)
      if (InferenceMain.DEBUG(this)) {
        println("New " + c)
      }
      constraints += c
    }
  }

  def getCommonMethodCallInformation( infFactory: InferenceAnnotatedTypeFactory[_],
                                      trees: com.sun.source.util.Trees,
                                      node : Tree,
                                      args : List[_ <: ExpressionTree],
                                      classTypeArgsOpt : Option[List[AnnotatedTypeMirror]],
                                      resolvedResultType : AnnotatedTypeMirror,
                                      methodElem : ExecutableElement,
                                      annotateVoidResult : Boolean ) :
  (WithinClassVP, CalledMethodPos, List[Slot], List[Slot], List[List[Slot]], List[List[Slot]], List[Slot], List[Slot]) = {
    val infChecker = InferenceMain.inferenceChecker
    val slotMgr = InferenceMain.slotMgr

    val calledTree = trees.getTree( methodElem )
    val callerVp = ConstraintManager.constructConstraintPosition( infFactory, node )
    val calledMethodVp = new CalledMethodPos()
    calledMethodVp.init(infFactory, calledTree)

    val classElem = methodElem.getEnclosingElement.asInstanceOf[TypeElement]

    val typeParameters = classElem.getTypeParameters.toList
    val classTypeParamBounds = typeParameters.map( infChecker.getTypeParamBounds _ ).toList


    val classTypeArgsToBounds =
      classTypeArgsOpt.map( classTypeArgs => {
        assert ( classTypeArgs.size() == classTypeParamBounds.size() )
        replaceAtvs( classTypeArgs, infChecker ).zip( classTypeParamBounds )

    }).getOrElse( Map.empty[AnnotatedTypeMirror, (AnnotatedTypeVariable, AnnotatedTypeVariable)] )

    val methodTypeParamBounds = methodElem.getTypeParameters.map( infChecker.getTypeParamBounds _ )
    val invocationTypeArgsOpt = infChecker.methodInvocationToTypeArgs.get( node )

    val methodTypeArgToBounds  =
      invocationTypeArgsOpt match {
        case None => Map.empty[AnnotatedTypeMirror, (AnnotatedTypeVariable, AnnotatedTypeVariable)]

        case Some( invocationTypeArgs ) =>
          replaceAtvs( invocationTypeArgs, infChecker ).zip( methodTypeParamBounds ).toMap
      }

    val methodType = infFactory.getAnnotatedType( methodElem )

    val classParamTypesToBounds =
      if( classTypeArgsOpt.isDefined )
        classElem.getTypeParameters.map(  _.asType ).zip( classTypeParamBounds )
      else
        List.empty

    val methodParamTypesToBounds = methodElem.getTypeParameters.map( _.asType ).zip( methodTypeParamBounds )

    //A map of class/method type parameter's types to their associated annotated type bounds
    val typesToBounds = ( classParamTypesToBounds ++ methodParamTypesToBounds ).toMap
    //The types of the method arguments as they appear in the invocation
    val originalArgs = args.map( infFactory.getAnnotatedType _ )

    // Create a map with a key for each method argument as the type of the formal parameter
    val argTypeParamBounds =
      methodType.getParameterTypes
        .map( _.getUnderlyingType )
        .map( typesToBounds.get _ )

    //A map of the arguments as instances of the formal parameters mapped to the bounds of the formal parameter
    //if that parameter was a use of a type parameter declaration
    val argBuffer = new ListBuffer[Slot]

    //TODO CM13: We basically need to create a method to create the equivalents/bounds maps
    //TODO: We also have to apply it to the result type
    zip3( originalArgs.zip( methodType.getParameterTypes ), argTypeParamBounds ).map( argParamBounds => {
      val (original, param, typeParamBounds ) = argParamBounds

      typeParamBounds match {
        case Some( (upperBound : AnnotatedTypeVariable, lowerBound : AnnotatedTypeVariable) ) =>
          //TODO CM14: Add equivalent slots and bounding
          //upperBound.getUpperBound
          argBuffer += slotMgr.extractSlot( original )

        case None =>
          val asLit = getLiteral( original, slotMgr )
          if( asLit.isDefined) {
            argBuffer += asLit.get
          } else {
            argBuffer ++= SlotUtil.listDeclVariables( asSuper( infFactory, original, param ) )
          }
      }
    })

    val methodTypeParamLBs = methodTypeArgToBounds.map( entry => slotMgr.extractSlot( entry._2._2 ) )
    val classTypeParamLBs  = classTypeArgsToBounds.map( entry => slotMgr.extractSlot( entry._2._2 ) )

    val methodTypeArgAsUBs = methodTypeArgToBounds.map( arg => argAsUpperBound( infFactory, arg ) )
    val classTypeArgAsUBs  = classTypeArgsToBounds.map( arg => argAsUpperBound( infFactory, arg )  )

    val argsAsUBs = argBuffer.toList
    val resultSlots =
      if( !resolvedResultType.isInstanceOf[AnnotatedNoType] || annotateVoidResult ) {
        SlotUtil.listDeclVariables( resolvedResultType )
      } else {
        List.empty[Slot]
      }

    ( callerVp, calledMethodVp, methodTypeParamLBs.toList, classTypeParamLBs.toList,
      methodTypeArgAsUBs.toList, classTypeArgAsUBs.toList, argsAsUBs.toList, resultSlots )
  }

  def addConstructorInvocationConstraint(infFactory: InferenceAnnotatedTypeFactory[_], trees: com.sun.source.util.Trees,
                                         newClassTree : NewClassTree) {

    val constructorElem = InternalUtils.constructor( newClassTree )

    val calledTree = trees.getTree( constructorElem )
    if (calledTree==null) {
      // TODO CM15: We currently don't create a constraint for binary only methods(?)
      return
    }

    val constructorType = infFactory.getAnnotatedType( newClassTree )
    val constructorFromUse = infFactory.constructorFromUse( newClassTree )
    val typeArgs = constructorType.getTypeArguments.toList
    val receiverSlot = null

    val typeElems = constructorElem.getEnclosingElement.asInstanceOf[TypeElement].getTypeParameters
    if( typeElems.find( te => !InferenceMain.inferenceChecker.hasBounds(te) ).isDefined ) {
      return //TODO CM16: Something to do with visiting order of inner classes with generics
    }

    val (callerVp, calledMethodVp, methodTypeParamLBs, classTypeParamLBs,
    methodTypeArgAsUBs, classTypeArgAsUBs, argsAsUBs, resultSlots) =
      getCommonMethodCallInformation( infFactory, trees, newClassTree, newClassTree.getArguments.toList,
        Some( typeArgs ), constructorFromUse.first.getReturnType, constructorElem, true )

    //TODO CM17: CURRENTLY THE RECEIVERS FOR CONSTRUCTORS ARE NOT HANDLED (i.e. in the case where there IS actually
    //TODO JB: a constructor receiver, we do nothing with it)
    addInstanceMethodCallConstraint( true, callerVp, calledMethodVp, receiverSlot,
                                     methodTypeParamLBs, classTypeParamLBs,
                                     methodTypeArgAsUBs, classTypeArgAsUBs,
                                     argsAsUBs, resultSlots,
                                     Map.empty[Slot, Option[(Slot, Slot)]],
                                     Set.empty[(Slot, Slot)] )
  }

  //For calls to constructors this() or super()
  def addDeferredConstructorInvocationConstraint(infFactory: InferenceAnnotatedTypeFactory[_],
                                                 trees: com.sun.source.util.Trees,
                                                 otherConstructor : MethodInvocationTree) {
    val infChecker = InferenceMain.inferenceChecker
    val methodElem = TreeUtils.elementFromUse( otherConstructor )
    val classElem  = methodElem.getEnclosingElement.asInstanceOf[TypeElement]
    val calledTree = trees.getTree( methodElem )
    if (calledTree==null) {
      // TODO CM18: We currently don't create a constraint for binary only methods(?)
      return
    }

    val typeElems = methodElem.getEnclosingElement.asInstanceOf[TypeElement].getTypeParameters
    if( typeElems.find( te => !InferenceMain.inferenceChecker.hasBounds(te) ).isDefined && !InferenceMain.STRICT_MODE ) {
      return //TODO CM18: Something to do with visiting order of inner classes with generics
    }

    val methodFromUse = infFactory.methodFromUse( otherConstructor )
    val typeArgs = Some(
      typeElems.map( infChecker.typeParamElemToUpperBound.apply _ )
               .map( _.getUpperBound )
               .toList
    )

    val receiverSlot = null

    val (callerVp, calledMethodVp, methodTypeParamLBs, classTypeParamLBs,
    methodTypeArgAsUBs, classTypeArgAsUBs, argsAsUBs, resultSlots) =
      getCommonMethodCallInformation( infFactory, trees, otherConstructor, otherConstructor.getArguments.toList,
        typeArgs, methodFromUse.first.getReturnType, methodElem, true )

    addInstanceMethodCallConstraint( true, callerVp, calledMethodVp, receiverSlot,
      methodTypeParamLBs, classTypeParamLBs,
      methodTypeArgAsUBs, classTypeArgAsUBs,
      argsAsUBs, resultSlots,
      Map.empty[Slot, Option[(Slot, Slot)]],
      Set.empty[(Slot, Slot)] )
  }

  def addInstanceMethodCallConstraint(infFactory: InferenceAnnotatedTypeFactory[_], trees: com.sun.source.util.Trees,
                                      node: MethodInvocationTree) {

    val methodElem = TreeUtils.elementFromUse(node)

    val calledTree = trees.getTree( methodElem )
    if (calledTree==null) {
      // TODO CM19: We currently don't create a constraint for binary only methods(?)
      return
    }

    val isStatic = ElementUtils.isStatic( methodElem )

    val methodFromUse = infFactory.methodFromUse( node )


    val typeElems = methodElem.getEnclosingElement.asInstanceOf[TypeElement].getTypeParameters
    if( typeElems.find( te => !InferenceMain.inferenceChecker.hasBounds(te) ).isDefined ) {
      return //TODO CM20: Something to do with visiting order of inner classes with generics
    }

    val (receiver, classTypeArgs) =
      if( !isStatic ) {
        val recvType = infFactory.getReceiverType( node )

        val declRecvType = InferenceMain.inferenceChecker.exeElemToReceiverCache( methodElem )
        val receiver = asSuper(  infFactory, recvType, declRecvType )

        ( receiver, Some( receiver.getTypeArguments.toList ) )

      } else {
        ( null, None )
      }

    val receiverSlot = Option( receiver ).map( InferenceMain.slotMgr.extractSlot _ ).getOrElse( null )

    val (callerVp, calledMethodVp, methodTypeParamLBs, classTypeParamLBs,
         methodTypeArgAsUBs, classTypeArgAsUBs, argsAsUBs, resultSlots) =
     getCommonMethodCallInformation( infFactory, trees, node, node.getArguments.toList, classTypeArgs,
                                     methodFromUse.first.getReturnType, methodElem, false )
    if( isStatic ) {
      addStaticMethodCallConstraint( callerVp, calledMethodVp, methodTypeParamLBs, methodTypeArgAsUBs,
                                     argsAsUBs, resultSlots,
                                     Map.empty[Slot, Option[(Slot, Slot)]],
                                     Set.empty[(Slot, Slot)])
    } else {
      addInstanceMethodCallConstraint( false, callerVp, calledMethodVp, receiverSlot,
                                       methodTypeParamLBs, classTypeParamLBs,
                                       methodTypeArgAsUBs, classTypeArgAsUBs,
                                       argsAsUBs, resultSlots,
                                       Map.empty[Slot, Option[(Slot, Slot)]],
                                       Set.empty[(Slot, Slot)] )
    }
  }

  def asSuper[T <: AnnotatedTypeMirror]( infFactory : InferenceAnnotatedTypeFactory[_],
                                         typ : AnnotatedTypeMirror, superType : T ) : T = {
    val typeUtils = InferenceMain.inferenceChecker.getProcessingEnvironment.getTypeUtils
    val sup = AnnotatedTypes.asSuper( typeUtils, infFactory, typ, superType )
    sup.asInstanceOf[T]
  }

  def argAsUpperBound( infFactory : InferenceAnnotatedTypeFactory[_],
                       argToBound : ( AnnotatedTypeMirror, (AnnotatedTypeVariable, AnnotatedTypeVariable) ) ) = {
    val (arg, (upperBound, lowerBound ) ) = argToBound
    val asUpper = asSuper( infFactory, arg, upperBound.getUpperBound )
    InferenceAnnotationUtils.traverseLinkAndBound(asUpper, upperBound, null, null )
    SlotUtil.listDeclVariables( asUpper )
  }


  private def addInstanceMethodCallConstraint( isConstructor : Boolean,
                                               contextVp : VariablePosition,
                                               calledVp  : CalledMethodPos,
                                               receiver  : Slot,
                                               methodTypeParamLBs : List[Slot],
                                               classTypeParamLBs  : List[Slot],
                                               methodTypeArgs     : List[List[Slot]],
                                               classTypeArgs      : List[List[Slot]],
                                               args               : List[Slot],
                                               result             : List[Slot],
                                               slotToBounds    : Map[Slot, Option[(Slot, Slot)]],
                                               equivalentSlots : Set[(Slot, Slot)] ) {
    val c = new InstanceMethodCallConstraint(isConstructor, contextVp, calledVp, receiver, methodTypeParamLBs,
                   classTypeParamLBs,methodTypeArgs, classTypeArgs, args, result, slotToBounds, equivalentSlots)
    if (InferenceMain.DEBUG(this)) {
        println("New " + c)
    }
    constraints += c
  }

  private def addStaticMethodCallConstraint( contextVp : VariablePosition,
                                             calledVp  : CalledMethodPos,
                                             methodTypeParamLBs : List[Slot],
                                             methodTypeArgs     : List[List[Slot]],
                                             args               : List[Slot],
                                             result             : List[Slot],
                                             slotToBounds    : Map[Slot, Option[(Slot, Slot)]],
                                             equivalentSlots : Set[(Slot, Slot)] ) {
    val c = new StaticMethodCallConstraint( contextVp, calledVp, methodTypeParamLBs, methodTypeArgs,
                                            args, result, slotToBounds, equivalentSlots )
    if (InferenceMain.DEBUG(this)) {
      println("New " + c)
    }
    constraints += c
  }

  /**
   * Given a list of AnnotatedTypeMirrors, replace each AnnotatedTypeVariable with the
   * AnnotatedTypeVariable cached for it's element
   * @param atv
   * @param infChecker
   * @tparam ATMS
   * @return
   */
  def replaceAtvs[ATMS <: Seq[AnnotatedTypeMirror]]( atv : ATMS,  infChecker : InferenceChecker )  = {
    atv.map( _ match {
      case atv : AnnotatedTypeVariable =>
        val typeParamElem = atv.getUnderlyingType.asElement.asInstanceOf[TypeParameterElement]
        infChecker.typeParamElemToUpperBound( typeParamElem ).getUpperBound

      case atm : AnnotatedTypeMirror =>
        atm
    })
  }

  def getCommonFieldData(infFactory: InferenceAnnotatedTypeFactory[_], trees: com.sun.source.util.Trees,
                         node: ExpressionTree ) :
    Option[(VariablePosition, FieldVP, Slot, List[Slot], List[List[Slot]], List[Slot])] = {
    import scala.collection.JavaConversions._
    val infChecker = InferenceMain.inferenceChecker
    val slotMgr = InferenceMain.slotMgr

    val fieldType = infFactory.getAnnotatedType( node )
    if (!InferenceMain.getRealChecker.needsAnnotation(fieldType)) {
      // No constraint if the type doesn't need an annotation.
      return None
    }

    val declFieldElem = TreeUtils.elementFromUse(node)
    val declFieldTree = trees.getTree( declFieldElem )
    if (declFieldTree==null) {
      // Don't create constraints for fields for which we don't have the source code.
      return None
    }

    val declFieldVp = new FieldVP(declFieldElem.getSimpleName().toString())
    declFieldVp.init(infFactory, declFieldTree)

    if ( node.getKind() == Tree.Kind.ARRAY_ACCESS )
      return None; //TODO CM22: Talk to Mike and Werner about this

    val isSelfAccess = TreeUtils.isSelfAccess( node )

    val recvTypeOpt =
      if( ElementUtils.isStatic( declFieldElem ) ) {
        None
      } else {
        Option( infFactory.getReceiverType( node ) )
      }

    val classElem = declFieldElem.getEnclosingElement.asInstanceOf[TypeElement]
    val typeParamElems = classElem.getTypeParameters
    val classTypeParamBounds = typeParamElems.map( infChecker.getTypeParamBounds _ ).toList

    val field = SlotUtil.listDeclVariables( fieldType )
    val recvAsUB = recvTypeOpt.map( rt => asSuper( infFactory, rt, infFactory.getAnnotatedType(classElem) ) )
                              .getOrElse( null )
    val receiverSlot = recvTypeOpt.map( slotMgr.extractSlot _ ).getOrElse(null)

    val classTypeArgsToBounds =
      if (recvAsUB == null) {
        Map.empty[AnnotatedTypeMirror, ( AnnotatedTypeVariable, AnnotatedTypeVariable )]
      } else {
        assert ( recvAsUB.getTypeArguments.size() == classTypeParamBounds.size() )
        replaceAtvs( recvAsUB.getTypeArguments.toList, infChecker ).zip( classTypeParamBounds ).toMap
      }

    val accessContext = ConstraintManager.constructConstraintPosition(infFactory, node)

    val classTypeParamLBs  = classTypeArgsToBounds.map( entry => slotMgr.extractSlot( entry._2._2 ) ).toList
    val classTypeArgAsUBs  = classTypeArgsToBounds.map( arg   => argAsUpperBound( infFactory, arg ) ).toList

    Some( ( accessContext, declFieldVp, receiverSlot, classTypeParamLBs, classTypeArgAsUBs, field ) )
  }


  /**
   * Create a field access constraint and add it to the list of constraints.
   * @param infFactory Used to get the type of node and (potentially) the receiver
   * @param trees Required trees util to determine the type of the declaration for the field
   *              that is being accessed
   * @param node  The tree of the identifier or the member.select that we are generating a constraint for
   */
  def addFieldAccessConstraint(infFactory: InferenceAnnotatedTypeFactory[_], trees: com.sun.source.util.Trees,
                               node: ExpressionTree) {
    val commonInfo = getCommonFieldData(infFactory, trees, node)
    if( commonInfo.isDefined ) {
      val (accessContext, declFieldVp, receiverSlot, classTypeParamsLBs, classTypeArgAsUBs, field) = commonInfo.get
        addFieldAccessConstraint(accessContext, declFieldVp, receiverSlot, classTypeParamsLBs, classTypeArgAsUBs,
          field, Map.empty[Slot, Option[(Slot, Slot)]], Set.empty[(Slot, Slot)] )
    }

  }

  /**
   * @param contextVp
   * @param calledVp
   * @param receiver
   * @param classTypeParamLBs
   * @param classTypeArgs
   * @param field
   * @param slotToBounds
   * @param equivalentSlots
   */
  def addFieldAccessConstraint(contextVp : VariablePosition,
                               calledVp  : FieldVP,
                               receiver  : Slot,
                               classTypeParamLBs  : List[Slot],
                               classTypeArgs      : List[List[Slot]],
                               field              : List[Slot],
                               slotToBounds       : Map[Slot, Option[(Slot, Slot)]],
                               equivalentSlots    : Set[(Slot, Slot)]) {
    val c = new FieldAccessConstraint(contextVp, calledVp, receiver, classTypeParamLBs, classTypeArgs,
                                      field, slotToBounds, equivalentSlots)
    if (InferenceMain.DEBUG(this)) {
      println("New " + c)
    }
    constraints += c
  }

  def addFieldAssignmentConstraint(infFactory: InferenceAnnotatedTypeFactory[_], trees: com.sun.source.util.Trees,
      node: AssignmentTree) {

    //TODO CM23: Might have to do DECL Field Type
    val fieldType = infFactory.getAnnotatedType( node )

    val rightType = infFactory.getAnnotatedType(node.getExpression())

    //TODO CM24: Need to handle type parameters and setting up bounds/identity
    val rhsAsLeft = asSuper(infFactory, rightType, fieldType)
    val rhsSlots  = SlotUtil.listDeclVariables( rhsAsLeft )

    val commonInfo = getCommonFieldData( infFactory, trees, node.getVariable )
    if( commonInfo.isDefined ) {
      val (accessContext, declFieldVp, receiverSlot, classTypeParamsLBs, classTypeArgAsUBs, field) = commonInfo.get

      addFieldAssignmentConstraint(accessContext, declFieldVp, receiverSlot, classTypeParamsLBs, classTypeArgAsUBs,
          field, rhsSlots, Map.empty[Slot, Option[(Slot, Slot)]], Set.empty[(Slot, Slot)] )
    }
  }

  private def addFieldAssignmentConstraint( contextVp : VariablePosition,
                                            calledVp  : FieldVP,
                                            receiver  : Slot,
                                            classTypeParams  : List[Slot],
                                            classTypeArgs    : List[List[Slot]],
                                            field            : List[Slot],
                                            rhs              : List[Slot],
                                            slotToBounds     : Map[Slot, Option[(Slot, Slot)]],
                                            equivalentSlots  : Set[(Slot, Slot)] ) {
    val c = new FieldAssignmentConstraint( contextVp, calledVp, receiver, classTypeParams, classTypeArgs,
                                           field, rhs, slotToBounds, equivalentSlots )
    if (InferenceMain.DEBUG(this)) {
        println("New " + c)
    }
    constraints += c
  }

  private def addAssignmentConstraint(context: VariablePosition, left: Slot, right: Slot) {
    val c = AssignmentConstraint(context, left, right)
    if (InferenceMain.DEBUG(this)) {
        println("New " + c)
    }
    constraints += c
  }



  def getLiteral( atm : AnnotatedTypeMirror, slotMgr : SlotManager ) : Option[Slot] = {
    Option( atm.getAnnotation( classOf[LiteralAnnot] ) ).map( slotMgr.extractSlot _ )
  }

}

object ConstraintManager {
  private def extractSlots(infFactory: InferenceAnnotatedTypeFactory[_], trees: java.util.List[_ <: Tree]): List[Slot] = {
    val res = new collection.mutable.ListBuffer[Slot]

    for (tree <- trees) {
      val ty = infFactory.getAnnotatedType(tree)
      val slot = InferenceMain.slotMgr.extractSlot(ty)
      if (slot!=null)
        res += slot
    }
    res.toList
  }

  //TODO JB:  This is duplicate code from InferenceTreeAnnotator
  def constructConstraintPosition(infFactory: InferenceAnnotatedTypeFactory[_], node: Tree): WithinClassVP = {
    val res = if (InferenceUtils.isWithinMethod(infFactory, node)) {
        new ConstraintInMethodPos()
    } else if (InferenceUtils.isWithinStaticInit(infFactory, node)) {
        val blockid = StaticInitScanner.indexOfStaticInitTree(infFactory.getPath(node))
        new ConstraintInStaticInitPos(blockid)
    } else {
        val fname = TreeUtils.enclosingVariable(infFactory.getPath(node)).getName().toString()
        new ConstraintInFieldInitPos(fname)
    }
    res.init(infFactory, node)
    res
  }
}
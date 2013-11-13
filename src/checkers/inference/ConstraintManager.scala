package checkers.inference

import javax.lang.model.element._
import javax.lang.model.`type`.TypeVariable
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror._
import javax.lang.model.`type`.TypeKind
import com.sun.source.tree._
import javacutils.{InternalUtils, ElementUtils, TreeUtils}
import annotator.scanner.StaticInitScanner
import scala.collection.mutable.{HashMap => MutHashMap}

import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.Tree
import checkers.util.AnnotatedTypes
import checkers.inference.util.CollectionUtil._
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import checkers.inference.util._
import checkers.inference.quals.LiteralAnnot
import checkers.inference.util.CommonSubboardCallInfo

/**
 * ConstraintManager maintains the list of constraints created during inference.  It also contains
 * methods/utilities used to create constraints.
 */
class ConstraintManager {
  //TODO CM1: NEED TO FIX EQUALS OF SET TYPE POSITION
  val constraints = new scala.collection.mutable.LinkedHashSet[Constraint]()
  val methodElemToStubBoardConstraints      = new MutHashMap[ExecutableElement, StubBoardUseConstraint]
  val fieldElemToAccessStubBoardConstraints = new MutHashMap[Element, StubBoardUseConstraint]
  val fieldElemToAssignmentStubBoardConstraints = new MutHashMap[Element, StubBoardUseConstraint]

  // addSubtypeConstraints not needed, as the framework calls this for
  // each type argument.
  // TODO CM2: check

  def cleanUp() {
    constraints.clear()
    methodElemToStubBoardConstraints.clear()
    fieldElemToAccessStubBoardConstraints.clear()
    fieldElemToAssignmentStubBoardConstraints.clear()
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

  def isAnonymousNewClass( nct: NewClassTree ): Boolean = {
    val classBody = nct.getClassBody
    classBody != null && ( classBody.getSimpleName == null || classBody.getSimpleName().toString().equals("") )
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

  def addBallSizeTestConstraint(bs: BallSizeTestConstraint) {
    constraints += bs
  }

  def classElemToBounds( classElem : TypeElement, isStatic : Boolean ) = {
    if( isStatic ) {
      List.empty[(List[Constant], Constant)]
    } else {
      typeParamsToBounds( classElem.getTypeParameters.toList )
    }
  }

  def typeParamsToBounds( typeParams : List[TypeParameterElement] ) = {
    val infChecker = InferenceMain.inferenceChecker
    val slotMgr    = InferenceMain.slotMgr
    typeParams
      .map( infChecker.getTypeParamBounds _ )
      .map( bounds => ( SlotUtil.listDeclVariables( bounds._1 ).map( _.asInstanceOf[Constant]).toList,
      slotMgr.extractConstant( bounds._2 ) ) )
      .toList
  }

  /**
   * Create a StubBoardUseConstraint which represents the DECLARATION (not use) of a library field.  SubboardCalls
   * with StubBoardUseConstraints represent calls to library methods
   */
  def getOrCreateFieldSubboardUseConstraint( fieldElem : Element, levelVp : WithinClassVP, isAccess : Boolean,
                                             infFactory : InferenceAnnotatedTypeFactory ) :StubBoardUseConstraint = {
    val slotMgr = InferenceMain.slotMgr
    val cache = if( isAccess ) fieldElemToAccessStubBoardConstraints else fieldElemToAssignmentStubBoardConstraints

    cache.get( fieldElem ).getOrElse({
      val fieldName = fieldElem.getSimpleName.toString
      val classElem = fieldElem.getEnclosingElement.asInstanceOf[TypeElement]

      val (packageName, className ) = AFUHelper.getPackageAndClassJvmNames( classElem )
      val fqClassName    = VariablePosition.fqClassName( packageName, className )
      val fqFieldName    = VariablePosition.fqFieldName( packageName, className, fieldName )
      val fieldSignature =
        if( isAccess ) {
          SolverUtil.getFieldAccessorName( fqFieldName )
        } else {
          SolverUtil.getFieldSetterName( fqFieldName )
        }


      val isStatic = ElementUtils.isStatic( fieldElem )

      val recvTypeOpt =
        if( isStatic ) {
          None
        } else {
          Option( infFactory.getRealAnnotatedType( classElem ) )
        }

      val receiver = recvTypeOpt match{
        case Some( rcvType : AnnotatedTypeMirror ) => slotMgr.extractConstant( rcvType )
        case None => null
      }

      val classTypeParamBounds = classElemToBounds( classElem, isStatic )
      val classTypeParamUppers  = classTypeParamBounds.map( _._1 )
      val classTypeParamLowers  = classTypeParamBounds.map( _._2 )

      val fieldTypeConstants =
        SlotUtil.listDeclVariables( infFactory.getRealAnnotatedType( fieldElem ) )
          .map( _.asInstanceOf[Constant] )

      val ( args, result ) =
        if( isAccess )
          ( List.empty[Constant], fieldTypeConstants )
        else {
          ( fieldTypeConstants, List.empty[Constant] )
        }

      val stubConstraint = new StubBoardUseConstraint( fqClassName, fieldSignature, levelVp, receiver,
                                                       List.empty[Constant],       classTypeParamLowers,
                                                       List.empty[List[Constant]], classTypeParamUppers,
                                                       args, result )
      constraints += stubConstraint
      cache( fieldElem ) = stubConstraint
      stubConstraint
    })
  }

  /**
   * Create a StubBoardUseConstraint which represents the DECLARATION (not use) of a library method.  SubboardCalls
   * with StubBoardUseConstraints represent calls to library methods
   */
  def getOrCreateMethodStubboardUseConstraint( methodElem : ExecutableElement, ignoreReceiver : Boolean, levelVp : WithinClassVP,
                                               annotateVoidResult : Boolean,
                                               infFactory : InferenceAnnotatedTypeFactory )
    : StubBoardUseConstraint = {
    methodElemToStubBoardConstraints.get( methodElem ).getOrElse({
      val isStatic   = ElementUtils.isStatic( methodElem )
      val slotMgr    = InferenceMain.slotMgr
      val infChecker = InferenceMain.inferenceChecker

      val classElem  = methodElem.getEnclosingElement.asInstanceOf[TypeElement]
      val methodType = infFactory.getRealAnnotatedType( methodElem ).asInstanceOf[AnnotatedExecutableType]

      //TODO: Handle actual constructor receivers
      val receiver = if ( ignoreReceiver ) null else slotMgr.extractConstant( methodType.getReceiverType )

      val classTypeParamBounds  = classElemToBounds( classElem, isStatic )
      val methodTypeParamBounds = typeParamsToBounds( methodElem.getTypeParameters.toList )


      val args = methodType.getParameterTypes
                  .map( SlotUtil.listDeclVariables _ )
                  .flatten
                  .map( _.asInstanceOf[Constant] )
                  .toList

      val methodTypeParamUppers = methodTypeParamBounds.map( _._1 )
      val methodTypeParamLowers = methodTypeParamBounds.map( _._2 )
      val classTypeParamUppers  = classTypeParamBounds.map( _._1 )
      val classTypeParamLowers  = classTypeParamBounds.map( _._2 )

      val resultType = methodType.getReturnType
      val result =
        if( !resultType.isInstanceOf[AnnotatedNoType] || annotateVoidResult ) {
          SlotUtil.listDeclVariables( resultType ).map(_.asInstanceOf[Constant])
        } else {
          List.empty[Constant]
        }

      val (packageName, className ) = AFUHelper.getPackageAndClassJvmNames( classElem )
      val methodName     = methodElem.getSimpleName.toString
      val paramSignature = "(" + methodElem.getParameters.map( AFUHelper.toJvmTypeName _ ).mkString("") + ")"

      val fqClassName     = VariablePosition.fqClassName( packageName, className )
      val methodSignature = VariablePosition.methodSignature( packageName, className, methodName, paramSignature,
                                                              methodElem.getReturnType.toString )

      val stubConstraint = new StubBoardUseConstraint( fqClassName, methodSignature, levelVp, receiver,
                                                       methodTypeParamLowers, classTypeParamLowers,
                                                       methodTypeParamUppers, classTypeParamUppers,
                                                       args, result )
      constraints += stubConstraint
      methodElemToStubBoardConstraints( methodElem ) = stubConstraint
      stubConstraint

    })
  }



  /**
   * Extract information common to MethodInvocationTree and NewClassTree trees needed
   * to generate a subboard call constraint.
   * @param infFactory The InferenceAnnotatedTypeFactory
   * @param trees The tree utility for asSuper calls
   * @param node The tree representing the method call
   * @param args The arguments to the method call
   * @param classTypeArgsOpt If there are class type arguments and the are relevant (i.e. it's a non-static call)
   *                         then they will be passed as Some(List(AnnotatedTypeMirrors))
   * @param resolvedResultType The return type of this method call (or the type of the constructed object
   *                           for constructors)
   * @param methodElem The element corresponding to the called method
   * @param annotateVoidResult Whether or not a void result type should have annotations (i.e whether or
   *                           not this is a constructor call and we should honor annotations on the void type)
   * @return A tuple (callerVp, calledMethodVp, methodTypeParamLBs, classTypeParamLBs, methodTypeArgAsUBs,
   *                  classTypeArgAsUBs, argsAsUBs, resultSlots) used to create SubboardCalls
   *///TODO: REPLACE THE TUPLE WITH A CASE CLASS
  def getCommonMethodCallInformation( infFactory: InferenceAnnotatedTypeFactory,
                                      trees: com.sun.source.util.Trees,
                                      ignoreReceiver : Boolean,
                                      node : Tree,
                                      args : List[_ <: ExpressionTree],
                                      classTypeArgsOpt : Option[List[AnnotatedTypeMirror]],
                                      resolvedResultType : AnnotatedTypeMirror,
                                      methodElem : ExecutableElement,
                                      libraryCall : Boolean,
                                      annotateVoidResult : Boolean ) : CommonSubboardCallInfo = {
    val infChecker = InferenceMain.inferenceChecker
    val slotMgr = InferenceMain.slotMgr

    val subtypingVisitor = new SubtypingVisitor(slotMgr, infChecker, infFactory )

    val calledTree = trees.getTree( methodElem )
    val callerVp = ConstraintManager.constructConstraintPosition( infFactory, node )

    val ( calledMethodVp, stubUse ) =
      if( libraryCall ) {
        ( None, Some( getOrCreateMethodStubboardUseConstraint( methodElem, ignoreReceiver, callerVp, annotateVoidResult, infFactory ) ) )
      } else {
        val vp = new CalledMethodPos()
        vp.init(infFactory, calledTree)
        ( Some( vp ), None )
      }

    val classElem = methodElem.getEnclosingElement.asInstanceOf[TypeElement]

    val typeParameters = classElem.getTypeParameters.toList
    if( typeParameters.find( tp => !infChecker.hasBounds( tp) ).isDefined ) {
      if( typeParameters.find( tp => !infChecker.hasBounds( tp) ).isDefined ) {
        return null   //TODO: Limited cases in Picard in which this happens, test on hadoop
      }
    }

    val classTypeParamBounds = typeParameters.map( infChecker.getTypeParamBounds _ ).toList

    val classTypeArgsToBounds =
      classTypeArgsOpt.map( classTypeArgs => {
        if ( classTypeArgs.size() == 0 && classTypeParamBounds.size() > 0 ) {
          println("TODO: Receiver has no class arguments, but declared class has arguments. (Happens when receiver has raw type.) Node: " + node);
          replaceGenerics( classTypeParamBounds.map(_._1) ).zip( classTypeParamBounds )
        } else if( classTypeArgs.size() > 0 && classTypeParamBounds.size() == 0 ) {
          println("TODO: Suppressed warnings can lead to right hand side rawness!  Skipping this method call constraint: " + node )
          return null

        } else if( classTypeArgs.size() != classTypeParamBounds.size() ) {
          throw new RuntimeException("classTypeArgs(" + classTypeArgs.mkString + " )" + " != " +
                                     "classTypeParamBounds(" + classTypeParamBounds.mkString + ")")
        } else {
          classTypeParamBounds.zip( classTypeArgs ).foreach({
            case (superType, subtype ) => subtypingVisitor.visitTopLevel( superType._1, subtype )
          })
          replaceGenerics( classTypeArgs ).zip( classTypeParamBounds )
        }


    }).getOrElse( Map.empty[AnnotatedTypeMirror, (AnnotatedTypeVariable, AnnotatedTypeVariable)] )

    val methodTypeParamBounds = methodElem.getTypeParameters.map( infChecker.getTypeParamBounds _ )
    val invocationTypeArgsOpt = infChecker.methodInvocationToTypeArgs.get( node )

    val methodTypeArgToBounds  =
      invocationTypeArgsOpt match {
        case None => Map.empty[AnnotatedTypeMirror, (AnnotatedTypeVariable, AnnotatedTypeVariable)]

        case Some( invocationTypeArgs ) =>

          methodTypeParamBounds.zip( invocationTypeArgs ).foreach({
            case (superType, subtype ) => subtypingVisitor.visitTopLevel( superType._1, subtype )
          })
          replaceGenerics( invocationTypeArgs ).zip( methodTypeParamBounds ).toMap
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

    zip3( originalArgs.zip( methodType.getParameterTypes ), argTypeParamBounds ).map( argParamBounds => {
      val (original, param, typeParamBounds ) = argParamBounds

      typeParamBounds match {
        case Some( (upperBound : AnnotatedTypeVariable, lowerBound : AnnotatedTypeVariable) ) =>
          //upperBound.getUpperBound
          argBuffer += slotMgr.extractSlot( original )
          val paramAtv = SlotUtil.typeUseToUpperBound( param.asInstanceOf[AnnotatedTypeVariable] )
          paramAtv match {
            case Right( atd : AnnotatedDeclaredType )     => subtypingVisitor.visitTopLevel( atd, original )
            case Left(  ati : AnnotatedIntersectionType ) =>
              println("TODO: AnnotatedIntersectionTypes in commond method call!")
          }

          //USE THE SUBTYPING VISITOR TO MATCH THE UPPER BOUND OF THE PARAM WITH THE ARG AND ADD EQUALITY CONSTRAINTS

        case None =>
          val asLit = getLiteral( original, slotMgr )
          if( asLit.isDefined) {
            argBuffer += asLit.get
          } else {
            subtypingVisitor.visitTopLevel( param, original )
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

    val subtypingResult = subtypingVisitor.getResult

    CommonSubboardCallInfo( callerVp, calledMethodVp, methodTypeParamLBs.toList, classTypeParamLBs.toList,
      methodTypeArgAsUBs.toList, classTypeArgAsUBs.toList, argsAsUBs.toList, resultSlots, stubUse,
      subtypingResult.equality, subtypingResult.lowerBounds )
  }

  def addConstructorInvocationConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
                                         newClassTree : NewClassTree) {

    val constructorElem = InternalUtils.constructor( newClassTree )

    val calledTree  = trees.getTree( constructorElem )
    val libraryCall = calledTree == null

    val constructorType = infFactory.getAnnotatedType( newClassTree )
    val constructorFromUse = infFactory.constructorFromUse( newClassTree )
    val typeArgs = constructorType.getTypeArguments.toList
    val receiverSlot = null

    if( isAnonymousNewClass( newClassTree ) ) {
      //TODO: At the moment we don't correctly handle type vars
      return
    }

    if( libraryCall ) {
      val classElem  = constructorElem.getEnclosingElement.asInstanceOf[TypeElement]
      val typeParams = classElem.getTypeParameters.toList
      addMissingClassBounds( typeParams, infFactory )
    }

    if( !testMissingMethodBounds( constructorElem, libraryCall, infFactory ) ) {
      return
    }

    val methodInfo =
      getCommonMethodCallInformation( infFactory, trees, true, newClassTree, newClassTree.getArguments.toList,
        Some( typeArgs ), constructorFromUse.first.getReturnType, constructorElem, libraryCall, true )

    if( methodInfo == null ) {
      return
    }

    //TODO CM17: CURRENTLY THE RECEIVERS FOR CONSTRUCTORS ARE NOT HANDLED (i.e. in the case where there IS actually
    //TODO JB: a constructor receiver, we do nothing with it)
    addInstanceMethodCallConstraint( true, receiverSlot, methodInfo )
  }

  //For calls to constructors this() or super()
  def addDeferredConstructorInvocationConstraint(infFactory: InferenceAnnotatedTypeFactory,
                                                 trees: com.sun.source.util.Trees,
                                                 otherConstructor : MethodInvocationTree) {
    val infChecker = InferenceMain.inferenceChecker
    val methodElem = TreeUtils.elementFromUse( otherConstructor )
    val classElem  = methodElem.getEnclosingElement.asInstanceOf[TypeElement]

    val calledTree  = trees.getTree( methodElem )
    val libraryCall = calledTree == null

    if( libraryCall ) {
      val classElem  = methodElem.getEnclosingElement.asInstanceOf[TypeElement]
      val typeParams = classElem.getTypeParameters.toList
      addMissingClassBounds( typeParams, infFactory )
    }

    if( !testMissingMethodBounds( methodElem, libraryCall, infFactory ) ) {
      return
    }

    val typeElems = methodElem.getEnclosingElement.asInstanceOf[TypeElement].getTypeParameters
    val methodFromUse = infFactory.methodFromUse( otherConstructor )
    val classTypeArgs = Some(
      typeElems.map( infChecker.typeParamElemToUpperBound.apply _ )
        .map( _.getUpperBound )
        .toList
    )

    val receiverSlot = null

    val methodInfo =
      getCommonMethodCallInformation( infFactory, trees, true, otherConstructor, otherConstructor.getArguments.toList,
        classTypeArgs, methodFromUse.first.getReturnType, methodElem, libraryCall, true )

    if( methodInfo == null ) {
      return
    }

    addInstanceMethodCallConstraint( true, receiverSlot, methodInfo )
  }

  def getReceiverInfo( methodElem : ExecutableElement, node : MethodInvocationTree,
                       libraryCall : Boolean, infFactory : InferenceAnnotatedTypeFactory )
    : ( AnnotatedTypeMirror, Option[List[AnnotatedTypeMirror]] ) = {

    val recvType = infFactory.getReceiverType( node )

    val declRecvType =
      if ( libraryCall ) {
        infFactory.getRealAnnotatedType( methodElem ).asInstanceOf[AnnotatedExecutableType].getReceiverType
      }
      else {
        if( !InferenceMain.inferenceChecker.exeElemToReceiverCache.contains( methodElem )  ) {
          return null //TODO: Figure these out
        }
        InferenceMain.inferenceChecker.exeElemToReceiverCache( methodElem )
      }

    val receiver = asSuper(  infFactory, replaceGeneric(recvType), replaceGeneric(declRecvType) )
                     .asInstanceOf[AnnotatedDeclaredType]

    //TODO: ANOTHER TEMPORARY KLUDGE TO DEAL WITH THE FACT THAT OUTER CLASS TYPE PARAMS ARE NOT
    //TODO: SUBSTITUTED IN asMemberOf AND THEREFORE methodFromUse
    if( receiver == null && node.toString().equals("bin.getId().getHistogramString()") ) {
      ( declRecvType, Some( declRecvType.getTypeArguments.toList ) )

    } else if( receiver == null ) {
      //TODO JB: Temporary kludge for Picard when we get a receiverType node back that is a super type of the declRecvType
      //TODO JB: because infFactory doesn't have knowledge of exeElemeToReceiverCache (which is there to annotate missing receivers)
      //TODO JB: So sometimes we get back a recvType that is less specific then declRecvType
      if( InferenceMain.STRICT ) {
        throw new RuntimeException("Null receiver type on instance method call. (" + declRecvType + ", " + recvType + ")")
      } else {
        null
      }
    } else {
      ( receiver, Some( receiver.getTypeArguments.toList ) )
    }
  }

  //For binary-only methods, if the bounds are missing we add them here
  def testMissingMethodBounds( methodElem : ExecutableElement, libraryCall : Boolean,
                               infFactory : InferenceAnnotatedTypeFactory ) : Boolean = {
    val typeElems = methodElem.getTypeParameters.toList
    if( typeElems.find( te => !InferenceMain.inferenceChecker.hasBounds(te) ).isDefined ) {
      if( libraryCall ) { //Only LibraryCall bounds may be missing here
        typeElems.foreach( te => {
          val atv = infFactory.getRealAnnotatedType( te ).asInstanceOf[AnnotatedTypeVariable]
          InferenceMain.inferenceChecker.typeParamElemToUpperBound(te) = atv
          InferenceMain.inferenceChecker.typeParamElemCache(te)        = atv
          //TODO: Make sure the lower doesn't clobber the upper (which I think it will)
        })
        return true
        //Get bounds from real annotated type factory
      } else {
        return false //TODO CM20: Something to do with visiting order of inner classes with generics
      }
    }  
    
    true
  }

  //For binary-only methods, if the bounds are missing we add them here
  def addMissingClassBounds( classTypeElements : List[TypeParameterElement],
                             infFactory : InferenceAnnotatedTypeFactory ) {

    if( classTypeElements.find( te => !InferenceMain.inferenceChecker.hasBounds(te) ).isDefined ) {
      classTypeElements.foreach( te => {
          val atv = infFactory.getRealAnnotatedType( te ).asInstanceOf[AnnotatedTypeVariable]
          InferenceMain.inferenceChecker.typeParamElemToUpperBound(te) = atv
          InferenceMain.inferenceChecker.typeParamElemCache(te)        = atv
          //TODO: Make sure the lower doesn't clobber the upper (which I think it will)
        })
    }
  }
  
  
  def addInstanceMethodCallConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
                                      node: MethodInvocationTree) {

    val methodElem = TreeUtils.elementFromUse(node)
    val isStatic   = ElementUtils.isStatic( methodElem )

    val calledTree  = trees.getTree( methodElem )
    val libraryCall = calledTree == null

    val methodFromUse = infFactory.methodFromUse( node )

    if( libraryCall ) {
      val classElem = methodElem.getEnclosingElement.asInstanceOf[TypeElement]
      addMissingClassBounds( classElem.getTypeParameters.toList, infFactory )
    }
    if( !testMissingMethodBounds( methodElem, libraryCall, infFactory ) ) {
      return
    }

    val (receiver, classTypeArgs) =
      if( !isStatic ) {
         val info = getReceiverInfo( methodElem, node, libraryCall, infFactory )
         if( info == null ) { //Only happens in relaxed mode
            return
         } else {
           info
         }
      } else {
        ( null, None )
      }

    val receiverSlot = Option( receiver ).map( InferenceMain.slotMgr.extractSlot _ ).getOrElse( null )

    val methodInfo =
      getCommonMethodCallInformation( infFactory, trees, isStatic, node, node.getArguments.toList, classTypeArgs,
        methodFromUse.first.getReturnType, methodElem, libraryCall, false )

    if( methodInfo == null ) {
      return
    }

    if( isStatic ) {
      addStaticMethodCallConstraint( methodInfo )
    } else {
      addInstanceMethodCallConstraint( false, receiverSlot, methodInfo )
    }
  }

  def asSuper[T <: AnnotatedTypeMirror]( infFactory : InferenceAnnotatedTypeFactory,
                                         typ : AnnotatedTypeMirror, superType : T ) : T = {
    val typeUtils = InferenceMain.inferenceChecker.getProcessingEnvironment.getTypeUtils
    val sup = AnnotatedTypes.asSuper( typeUtils, infFactory, typ, superType )
    sup.asInstanceOf[T]
  }

  def argAsUpperBound( infFactory : InferenceAnnotatedTypeFactory,
                       argToBound : ( AnnotatedTypeMirror, (AnnotatedTypeVariable, AnnotatedTypeVariable) ) ) = {
    val (arg, (upperBound, lowerBound ) ) = argToBound
    val asUpper = asSuper( infFactory, arg, replaceGeneric( upperBound ) )
    //InferenceAnnotationUtils.traverseLinkAndBound(asUpper, upperBound, null, null )
    SlotUtil.listDeclVariables( asUpper )
  }

  private def addInstanceMethodCallConstraint( isConstructor : Boolean,
                                               receiver  : Slot,
                                               subboardInfo : CommonSubboardCallInfo ) {
    val c = new InstanceMethodCallConstraint(
      isConstructor, subboardInfo.contextVp, subboardInfo.calledVp.map(_.asInstanceOf[CalledMethodPos]),
      receiver, subboardInfo.methodTypeParamLBs, subboardInfo.classTypeParamLBs,
      subboardInfo.methodTypeArgAsUBs, subboardInfo.classTypeArgAsUBs,
      subboardInfo.argsAsUBs, subboardInfo.resultSlots,
      subboardInfo.slotToLowerBound, subboardInfo.equivalentSlots,
      subboardInfo.stubUseConstraint )

    if (InferenceMain.DEBUG(this)) {
        println("New " + c)
    }

    constraints += c
  }

  private def addStaticMethodCallConstraint( subboardInfo : CommonSubboardCallInfo ) {
    val c = new StaticMethodCallConstraint(
      subboardInfo.contextVp, subboardInfo.calledVp.map(_.asInstanceOf[CalledMethodPos]),
      subboardInfo.methodTypeParamLBs, subboardInfo.methodTypeArgAsUBs,
      subboardInfo.argsAsUBs, subboardInfo.resultSlots,
      subboardInfo.slotToLowerBound, subboardInfo.equivalentSlots,
      subboardInfo.stubUseConstraint )

    if (InferenceMain.DEBUG(this)) {
      println("New " + c)
    }

    constraints += c
  }

  def replaceGeneric( atm : AnnotatedTypeMirror ) : AnnotatedTypeMirror = {
    val infChecker = InferenceMain.inferenceChecker
    atm match {
      case atv : AnnotatedTypeVariable =>
        val typeParamElem = atv.getUnderlyingType.asElement.asInstanceOf[TypeParameterElement]
        replaceGeneric( infChecker.typeParamElemToUpperBound( typeParamElem ).getUpperBound )

      case atw : AnnotatedWildcardType =>
        replaceGeneric( atw.getEffectiveExtendsBound )

      case atm : AnnotatedTypeMirror => atm
    }
  }

  /**
   * Given a list of AnnotatedTypeMirrors, replace each AnnotatedTypeVariable with the
   * AnnotatedTypeVariable cached for it's element
   * @param atms
   * @tparam ATMS
   * @return
   */
  def replaceGenerics[ATMS <: Seq[AnnotatedTypeMirror]]( atms : ATMS ) = {
    atms.map( atm => replaceGeneric( atm  ) )
  }

  def  getCommonFieldData(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees, isAccess : Boolean,
                         node: ExpressionTree, fieldType : AnnotatedTypeMirror ) : Option[(Slot,CommonSubboardCallInfo)] = {
    import scala.collection.JavaConversions._
    val infChecker = InferenceMain.inferenceChecker
    val slotMgr = InferenceMain.slotMgr

    if (!InferenceMain.getRealChecker.needsAnnotation(fieldType)) {
      // No constraint if the type doesn't need an annotation.
      return None
    }

    val accessContext = ConstraintManager.constructConstraintPosition(infFactory, node)

    val declFieldElem = TreeUtils.elementFromUse( node )
    val declFieldTree = trees.getTree( declFieldElem )
    val libraryCall = declFieldTree == null

    val ( declFieldVp, stubUse ) =
      if( libraryCall ) {
        ( None, Some( getOrCreateFieldSubboardUseConstraint( declFieldElem, accessContext, isAccess, infFactory ) ) )
      } else {
        val vp = new FieldVP(declFieldElem.getSimpleName().toString())
        vp.init(infFactory, declFieldTree)
        ( Some( vp ), None )
      }

    if ( node.getKind() == Tree.Kind.ARRAY_ACCESS )
      return None; //TODO CM22: Talk to Mike and Werner about this

    val isSelfAccess = TreeUtils.isSelfAccess( node )

    val recvTypeOpt =
      if( ElementUtils.isStatic( declFieldElem ) ) {
        None
      } else {
        Option( infFactory.getReceiverType( node ) )
      }

    val subtypingVisitor = new SubtypingVisitor(slotMgr, infChecker, infFactory )

    val classElem = declFieldElem.getEnclosingElement.asInstanceOf[TypeElement]
    val typeParamElems = classElem.getTypeParameters
    val classTypeParamBounds = typeParamElems.map( infChecker.getTypeParamBounds _ ).toList

    val field = SlotUtil.listDeclVariables( fieldType )

    val recvAsUB = recvTypeOpt.map( rt =>
      asSuper( infFactory, replaceGeneric( rt ), replaceGeneric( infFactory.getAnnotatedType(classElem) ) ) )
        .map( _.asInstanceOf[AnnotatedDeclaredType] )
        .getOrElse( null )
    val receiverSlot = recvTypeOpt.map( slotMgr.extractSlot _ ).getOrElse(null)

    val classTypeArgsToBounds =
      if (recvAsUB == null) {
        Map.empty[AnnotatedTypeMirror, ( AnnotatedTypeVariable, AnnotatedTypeVariable )]
      } else {
        assert ( recvAsUB.getTypeArguments.size() == classTypeParamBounds.size() )

        val classTypeArgs = recvAsUB.getTypeArguments.toList
        classTypeParamBounds.zip( classTypeArgs ).foreach({
          case (superType, subtype ) => subtypingVisitor.visitTopLevel( superType._1, subtype )
        })

        replaceGenerics( classTypeArgs ).zip( classTypeParamBounds ).toMap
      }

    val classTypeParamLBs  = classTypeArgsToBounds.map( entry => slotMgr.extractSlot( entry._2._2 ) ).toList
    val classTypeArgAsUBs  = classTypeArgsToBounds.map( arg   => argAsUpperBound( infFactory, arg ) ).toList

    val subtypingResult = subtypingVisitor.getResult

    Some ( ( receiverSlot,
      CommonSubboardCallInfo(
        accessContext, declFieldVp, List.empty[Slot], classTypeParamLBs,
        List.empty[List[Slot]], classTypeArgAsUBs, field, List.empty[Slot],
        stubUse, subtypingResult.lowerBounds, subtypingResult.equality
      )
    ) )
  }


  /**
   * Create a field access constraint and add it to the list of constraints.
   * @param infFactory Used to get the type of node and (potentially) the receiver
   * @param trees Required trees util to determine the type of the declaration for the field
   *              that is being accessed
   * @param node  The tree of the identifier or the member.select that we are generating a constraint for
   */
  def addFieldAccessConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
                               node: ExpressionTree) {

    val fieldType = infFactory.getAnnotatedType( node )
    val commonInfo = getCommonFieldData(infFactory, trees, true, node, fieldType)
    commonInfo.map({
      case (receiverSlot, fieldInfo ) => addFieldAccessConstraint( receiverSlot, fieldInfo )
    })
  }

  /**
   */
  def addFieldAccessConstraint( receiver  : Slot, fieldCallInfo : CommonSubboardCallInfo ) {
    val c = new FieldAccessConstraint(
      fieldCallInfo.contextVp, fieldCallInfo.calledVp.map(_.asInstanceOf[FieldVP]), receiver,
      fieldCallInfo.classTypeParamLBs, fieldCallInfo.classTypeArgAsUBs,
      fieldCallInfo.argsAsUBs, fieldCallInfo.slotToLowerBound, fieldCallInfo.equivalentSlots,
      fieldCallInfo.stubUseConstraint )

    if (InferenceMain.DEBUG(this)) {
      println("New " + c)
    }
    constraints += c
  }

  def addFieldAssignmentConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
      node: AssignmentTree) {

    //TODO CM23: Might have to do DECL Field Type
    val fieldType = infFactory.getAnnotatedType( node )

    val rightType = infFactory.getAnnotatedType( node.getExpression() )

    val subtypingResult =
      SubtypingVisitor.subtype(
        fieldType, rightType,
        InferenceMain.slotMgr, InferenceMain.inferenceChecker,
        infFactory
      )

    //TODO CM24: Need to handle type parameters and setting up bounds/identity
    val rhsAsLeft = asSuper(infFactory, rightType, fieldType)
    val rhsSlots  = SlotUtil.listDeclVariables( asSuper(infFactory, rhsAsLeft, replaceGeneric( fieldType ) ) )

    val commonInfo = getCommonFieldData( infFactory, trees, false, node.getVariable, fieldType )

    commonInfo.map({
      case (receiverSlot, fieldInfo ) =>
        addFieldAssignmentConstraint( receiverSlot, fieldInfo.mergeSubtypingResult( subtypingResult ), rhsSlots )
    })
  }

  private def addFieldAssignmentConstraint( receiver  : Slot, fieldInfo : CommonSubboardCallInfo, rhsSlots : List[Slot] ) {
    val c = new FieldAssignmentConstraint(
      fieldInfo.contextVp, fieldInfo.calledVp.map(_.asInstanceOf[FieldVP]), receiver,
      fieldInfo.classTypeParamLBs, fieldInfo.classTypeArgAsUBs, fieldInfo.argsAsUBs, rhsSlots,
      fieldInfo.slotToLowerBound, fieldInfo.equivalentSlots, fieldInfo.stubUseConstraint )
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
  private def extractSlots(infFactory: InferenceAnnotatedTypeFactory, trees: java.util.List[_ <: Tree]): List[Slot] = {
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
  def constructConstraintPosition(infFactory: InferenceAnnotatedTypeFactory, node: Tree): WithinClassVP = {
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
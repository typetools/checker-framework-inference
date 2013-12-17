package checkers.inference

import javax.lang.model.element._
import javax.lang.model.`type`.TypeVariable
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror._
import javax.lang.model.`type`.TypeKind
import com.sun.source.tree._
import javacutils.{InternalUtils, ElementUtils, TreeUtils}
import annotator.scanner.InitBlockScanner
import scala.collection.mutable.{HashMap => MutHashMap}
import scala.collection.mutable.ListBuffer

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
      .map( bounds => ( SubtypingVisitor.listSlots( bounds._1 ).map( _.asInstanceOf[Constant]).toList,
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
        SubtypingVisitor.listSlots( infFactory.getRealAnnotatedType( fieldElem ) )
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
                  .map( tp => SubtypingVisitor.listSlots( tp ) )
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
          SubtypingVisitor.listSlots( resultType ).map(_.asInstanceOf[Constant])
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

  //TODO: A LOT OF DUPLICATION BETWEEN THE DIFFERENT ADD METHOD CALLS, FIGURE OUT A BETTER SCHEME
  //For calls to constructors this() or super()
  def addDeferredConstructorCallConstraint(infFactory: InferenceAnnotatedTypeFactory,
                                           trees: com.sun.source.util.Trees,
                                           otherConstructor : MethodInvocationTree) {
    val methodElem = TreeUtils.elementFromUse( otherConstructor )
    val classElem = methodElem.getEnclosingElement.asInstanceOf[TypeElement]

    if( classElem.getQualifiedName.toString().equals("java.lang.Enum") ) {
      //TODO: HANDLE ENUM CONSTRUCTORS
      return
    }

    val calledTree  = trees.getTree( methodElem )
    val libraryCall = calledTree == null

    addMissingClassBounds( classElem, libraryCall, infFactory )

    if( !testMissingMethodBounds( methodElem, libraryCall, infFactory ) ) {
      throw new RuntimeException("MISSING BOUNDS" + otherConstructor)
    }

    val callerVp = ConstraintManager.constructConstraintPosition( infFactory, otherConstructor )

    val ( calledMethodVp, stubUse ) =
      if( libraryCall ) {
        ( None, Some( getOrCreateMethodStubboardUseConstraint( methodElem, true, callerVp, true, infFactory ) ) )
      } else {
        val vp = new CalledMethodPos()
        vp.init(infFactory, calledTree)
        ( Some( vp ), None )
      }

    //Force the inferenceTreeAnnotator to visit the class in which methodElem is defined (in case it hasn't been already)
    infFactory.getAnnotatedType( otherConstructor )   //TODO: ARE THESE NEEDED

    val declaredMethod = getDeclaredMethodInfo( methodElem, None, true, infFactory )
    val calledMethod   = getCalledDeferredConstructorInfo( otherConstructor, declaredMethod, infFactory )

    val methodInfo = CommonSubboardTypeInfo.makeCall( callerVp, calledMethodVp, stubUse, declaredMethod, calledMethod )

    addInstanceMethodCallConstraint( true, null, methodInfo )
  }

  def getDeclaredReceiver( methodElem : ExecutableElement, node : MethodInvocationTree,
                   libraryCall : Boolean, infFactory : InferenceAnnotatedTypeFactory ) : AnnotatedDeclaredType = {
    if ( libraryCall ) {
      infFactory.getRealAnnotatedType( methodElem ).asInstanceOf[AnnotatedExecutableType].getReceiverType
    } else {
      InferenceMain.inferenceChecker.exeElemToReceiverCache( methodElem )
    }
  }

  def getCalledReceiver(node : ExpressionTree, declaredRecv : AnnotatedTypeMirror,
                        infFactory : InferenceAnnotatedTypeFactory ) : AnnotatedDeclaredType = {
    val called = infFactory.getReceiverType( node )
    asSuper( infFactory, replaceGeneric( called ), replaceGeneric( declaredRecv ) ).asInstanceOf[AnnotatedDeclaredType]
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

  def addMissingClassBounds( classElem : TypeElement, isLibraryCall : Boolean,
                             infFactory : InferenceAnnotatedTypeFactory ) {
    val classTypeElements = classElem.getTypeParameters.toList
    if( classTypeElements.find( te => !InferenceMain.inferenceChecker.hasBounds(te) ).isDefined ) {

      if( isLibraryCall ) {
        classTypeElements.foreach( te => {
            val atv = infFactory.getRealAnnotatedType( te ).asInstanceOf[AnnotatedTypeVariable]
            InferenceMain.inferenceChecker.typeParamElemToUpperBound(te) = atv
            InferenceMain.inferenceChecker.typeParamElemCache(te)        = atv
            //TODO: Make sure the lower doesn't clobber the upper (which I think it will)
          })
      } else {
        //Force the InferenceTreeAnnotator to visit the class of a given type element if it hasn't already
        classTypeElements.foreach( te => {
          val classEle  = ElementUtils.enclosingClass( te )
          infFactory.getAnnotatedType( classEle )
        })
      }
    }
  }

  def addMethodCallConstraint( infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
                               node: MethodInvocationTree ) {   val methodElem = TreeUtils.elementFromUse(node)
    val isStatic   = ElementUtils.isStatic( methodElem )

    val calledTree  = trees.getTree( methodElem )
    val libraryCall = calledTree == null

    val classElem = methodElem.getEnclosingElement.asInstanceOf[TypeElement]
    addMissingClassBounds( classElem, libraryCall, infFactory )

    if( !testMissingMethodBounds( methodElem, libraryCall, infFactory ) ) {
      throw new RuntimeException("MISSING BOUNDS" + node)
    }

    val callerVp = ConstraintManager.constructConstraintPosition( infFactory, node )

    val ( calledMethodVp, stubUse ) =
      if( libraryCall ) {
        ( None, Some( getOrCreateMethodStubboardUseConstraint( methodElem, isStatic, callerVp, false, infFactory ) ) )
      } else {
        val vp = new CalledMethodPos()
        vp.init(infFactory, calledTree)
        ( Some( vp ), None )
      }

    val (declReceiver, receiver ) =
      if( isStatic ) {
        ( None, None )
      } else {
        val decl = getDeclaredReceiver( methodElem, node, libraryCall, infFactory )
        val call = getCalledReceiver( node, decl, infFactory )
        ( Some( decl ), Some( call ) )
      }

    //Force the inferenceTreeAnnotator to visit the class in which methodElem is defined (in case it hasn't been already)
    infFactory.getAnnotatedType( node )

    val declaredMethod = getDeclaredMethodInfo( methodElem, declReceiver, false, infFactory )
    val calledMethod   = getCalledMethodInfo( node, receiver, declaredMethod, infFactory )

    val methodInfo = CommonSubboardTypeInfo.makeCall( callerVp, calledMethodVp, stubUse, declaredMethod, calledMethod )

    if( isStatic ) {
      addStaticMethodCallConstraint( methodInfo )
    } else {
      addInstanceMethodCallConstraint( false, methodInfo.receiver.getOrElse(null), methodInfo )
    }
  }

  def addConstructorCallConstraint( infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
                                    newClassTree : NewClassTree ) {

    if( isAnonymousNewClass( newClassTree ) ) {
      //TODO: At the moment we don't correctly handle type vars
      return
    }

    val constructorElem = InternalUtils.constructor( newClassTree )

    val calledTree  = trees.getTree( constructorElem )
    val libraryCall = calledTree == null

    val classElem  = constructorElem.getEnclosingElement.asInstanceOf[TypeElement]
    addMissingClassBounds( classElem, libraryCall, infFactory )

    if( !testMissingMethodBounds( constructorElem, libraryCall, infFactory ) ) {
      return
    }

    val callerVp = ConstraintManager.constructConstraintPosition( infFactory, newClassTree )

    val ( calledMethodVp, stubUse ) =
      if( libraryCall ) {
        ( None, Some( getOrCreateMethodStubboardUseConstraint( constructorElem, true, callerVp, true, infFactory ) ) )
      } else {
        val vp = new CalledMethodPos()
        vp.init(infFactory, calledTree)
        ( Some( vp ), None )
      }

    //Force the inferenceTreeAnnotator to visit the class in which methodElem is defined (in case it hasn't been already)
    infFactory.getAnnotatedType( constructorElem )

    val declaredMethod = getDeclaredMethodInfo( constructorElem, None, true, infFactory )
    val calledMethod   = getCalledConstructorInfo( newClassTree, declaredMethod, infFactory )

    val methodInfo = CommonSubboardTypeInfo.makeCall( callerVp, calledMethodVp, stubUse, declaredMethod, calledMethod )

    addInstanceMethodCallConstraint( true, null, methodInfo )
  }

  def asAtms( atms : Seq[_ <: AnnotatedTypeMirror] ) : List[AnnotatedTypeMirror] = {
    atms.map(_.asInstanceOf[AnnotatedTypeMirror]).toList
  }

  def getDeclaredMethodInfo( methodElem : ExecutableElement, receiver : Option[AnnotatedTypeMirror],
                             isConstructor : Boolean, infFactory : InferenceAnnotatedTypeFactory ) : CommonSubboardTypeInfo = {
    val infChecker = InferenceMain.inferenceChecker

    val classElem = methodElem.getEnclosingElement.asInstanceOf[TypeElement]
    val typeParamElems = classElem.getTypeParameters

    val (classTypeParamUBs, classTypeParamLBs)   = typeParamElems.map( infChecker.getTypeParamBounds _ ).unzip
    val (methodTypeParamUBs, methodTypeParamLBs) = methodElem.getTypeParameters.map( infChecker.getTypeParamBounds _ ).unzip

    val methodType = infFactory.getAnnotatedType( methodElem )
    val params = ListBuffer[AnnotatedTypeMirror]()
    params ++= methodType.getParameterTypes.toList
    if ( methodType.isVarArgs() ) {
      params(params.length -1) = params(params.length -1).asInstanceOf[AnnotatedArrayType].getComponentType()
    }

    val returnType = methodType.getReturnType
    val resultType =
      if( isConstructor || !returnType.isInstanceOf[AnnotatedNoType]  )
        Some( returnType )
      else {
        None
      }

    CommonSubboardTypeInfo( receiver,
                           asAtms( methodTypeParamLBs ), asAtms( classTypeParamLBs ),
                            asAtms( methodTypeParamUBs ), asAtms( classTypeParamUBs ),
                            params.toList, resultType )
  }

  def getCalledMethodInfo( node : MethodInvocationTree, receiver : Option[AnnotatedDeclaredType],
                           declared : CommonSubboardTypeInfo, infFactory : InferenceAnnotatedTypeFactory ) = {
    val infChecker = InferenceMain.inferenceChecker

    val methodFromUse = infFactory.methodFromUse( node )
    val methodType = methodFromUse.first
    val returnType = methodType.getReturnType

    var args = node.getArguments.map( infFactory.getAnnotatedType _ ).toList
    args = processArgsForVarArgs(methodType, args)

    val classTypeArgs  =
      receiver.map( _.getTypeArguments )
        .flatten
        .toList

    val methodTypeArgs =
      infChecker.methodInvocationToTypeArgs.get( node )
        .getOrElse( List.empty[AnnotatedTypeMirror] )

    val resultType =
      if( !returnType.isInstanceOf[AnnotatedNoType]  )
        Some( returnType )
      else {
        None
      }

    CommonSubboardTypeInfo( receiver,
                            declared.methodTypeParamLBs, declared.classTypeParamLBs,
                            methodTypeArgs, classTypeArgs,
                            args, resultType )
  }

  def getCalledConstructorInfo( newClassTree : NewClassTree, declared : CommonSubboardTypeInfo,
                                infFactory : InferenceAnnotatedTypeFactory ) = {
    val infChecker = InferenceMain.inferenceChecker

    val constructorType    = infFactory.getAnnotatedType( newClassTree )
    val constructorFromUse = infFactory.constructorFromUse( newClassTree )
    val classTypeArgs = //TODO: HANDLE RAWNESS
      if( newClassTree.getTypeArguments.size == 0 && declared.classTypeParamUBs.size != 0 ) {
        declared.classTypeParamUBs
      } else {
        constructorType.getTypeArguments.toList
      }

    var args = newClassTree.getArguments.map( infFactory.getAnnotatedType _ ).toList
    val constructorMethodType = constructorFromUse.first
    args = processArgsForVarArgs(constructorMethodType, args)
    
    val methodTypeArgs =
      infChecker.methodInvocationToTypeArgs.get( newClassTree )
        .getOrElse( List.empty[AnnotatedTypeMirror] )

    val resultType = Some( constructorFromUse.first.getReturnType )

    CommonSubboardTypeInfo( None,
                            declared.methodTypeParamLBs, declared.classTypeParamLBs,
                            methodTypeArgs, classTypeArgs,
                            args, resultType )
  }


  def getCalledDeferredConstructorInfo( deferredConstructor : MethodInvocationTree, declared : CommonSubboardTypeInfo,
                                        infFactory : InferenceAnnotatedTypeFactory ) = {
    val infChecker = InferenceMain.inferenceChecker
    val classElem = TreeUtils.elementFromUse( deferredConstructor ).getEnclosingElement.asInstanceOf[TypeElement]

    val methodFromUse = infFactory.methodFromUse( deferredConstructor )
    val methodType = methodFromUse.first
    
    val classTypeArgs =
      classElem.getTypeParameters
        .map( infChecker.typeParamElemToUpperBound.apply _ )
        .toList

    //ENUMS AREN'T HANDLED RIGHT HERE
    var args = deferredConstructor.getArguments
          .map( infFactory.getAnnotatedType _ )
          .toList
    args = processArgsForVarArgs(methodType, args)
    
    val methodTypeArgs =
      infChecker.methodInvocationToTypeArgs.get( deferredConstructor )
        .getOrElse( List.empty[AnnotatedTypeMirror] )
        .toList

    val resultType = Some( methodFromUse.first.getReturnType )

    CommonSubboardTypeInfo( None,
                            declared.methodTypeParamLBs, declared.classTypeParamLBs,
                            methodTypeArgs, classTypeArgs,
                            args, resultType )
  }
  
  def processArgsForVarArgs( methodType: AnnotatedExecutableType, args: List[AnnotatedTypeMirror]): List[AnnotatedTypeMirror] = {
    if( methodType.isVarArgs() ) {
      val declaredParams = ListBuffer[AnnotatedTypeMirror]()
      declaredParams ++= methodType.getParameterTypes.toList
      declaredParams(declaredParams.length -1) = declaredParams(declaredParams.length -1).asInstanceOf[AnnotatedArrayType].getComponentType()
      val componentType = declaredParams(declaredParams.size - 1)
      if( args.size == declaredParams.size ) {
        // TODO: deal with varargs of arrays
        // Need to check dimensionality of both
        
        // Do nothing, they match
        args
      } else if( args.size == declaredParams.size - 1 ) {
        // No parameters, pass in self
        args :+ declaredParams(declaredParams.size - 1)
      } else {
        // Only take first parameter type.
        args.slice(0, declaredParams.size)
      }
    } else {
      args
    }
  }

  def asSuper[T <: AnnotatedTypeMirror]( infFactory : InferenceAnnotatedTypeFactory,
                                         typ : AnnotatedTypeMirror, superType : T ) : T = {
    if( superType.isInstanceOf[AnnotatedDeclaredType] ) {
      val simpleName =
        superType.asInstanceOf[AnnotatedDeclaredType]
          .getUnderlyingType
          .asElement
          .getSimpleName
      if( simpleName.toString.equals("Array") ) {
        val superCopy = AnnotatedTypes.deepCopy( superType )
        superCopy.clearAnnotations()
        superCopy.addAnnotations( typ.getAnnotations )
        return superCopy
      }
    }

    val typeUtils = InferenceMain.inferenceChecker.getProcessingEnvironment.getTypeUtils
    val sup = AnnotatedTypes.asSuper( typeUtils, infFactory, typ, superType )
    sup.asInstanceOf[T]
  }

  def argAsUpperBound( infFactory : InferenceAnnotatedTypeFactory,
                       argToBound : ( AnnotatedTypeMirror, (AnnotatedTypeVariable, AnnotatedTypeVariable) ) ) = {
    val (arg, (upperBound, lowerBound ) ) = argToBound
    val asUpper = asSuper( infFactory, arg, replaceGeneric( upperBound ) )
    //InferenceAnnotationUtils.traverseLinkAndBound(asUpper, upperBound, null, null )
    SubtypingVisitor.listSlots( asUpper )
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

  def getDeclaredFieldInfo( node : ExpressionTree, isAccess : Boolean, infFactory : InferenceAnnotatedTypeFactory ) = {
    val infChecker = InferenceMain.inferenceChecker
    val declNode = if( isAccess ) node else node.asInstanceOf[AssignmentTree].getVariable
    val declFieldElem = TreeUtils.elementFromUse( declNode )
    val isStatic = ElementUtils.isStatic( declFieldElem )

    val classElem = declFieldElem.getEnclosingElement.asInstanceOf[TypeElement]

    val isArrayAccess = declNode.getKind == Tree.Kind.ARRAY_ACCESS

    val receiver =
      if( isStatic ) {
        None
      } else if( isArrayAccess ) {
        Some( infFactory.getAnnotatedType( declFieldElem ) )
      } else {
        Some( infFactory.getAnnotatedType( classElem ) )
      }

    val emptyTvs = List.empty[AnnotatedTypeVariable]

    val ( classTypeParamUBs,  classTypeParamLBs  ) =
      if( isStatic || isArrayAccess ) {
        ( emptyTvs, emptyTvs )
      } else {
        val typeParamElems = classElem.getTypeParameters
        typeParamElems.map( infChecker.getTypeParamBounds _ ).unzip
      }


    val fieldType = infFactory.getAnnotatedType( declFieldElem )

    val args    = if( isAccess ) List.empty[AnnotatedTypeMirror] else List( fieldType )
    val results = if( isAccess ) Some( fieldType ) else None

    CommonSubboardTypeInfo( receiver,
                            emptyTvs, asAtms( classTypeParamLBs ),
                            emptyTvs, asAtms( classTypeParamUBs ),
                            args, results )
  }

  def getCalledFieldData(  node : ExpressionTree, selectNode : ExpressionTree, isAccess : Boolean, declaredField : CommonSubboardTypeInfo,
                           infFactory : InferenceAnnotatedTypeFactory ) = {

    val declFieldElem = TreeUtils.elementFromUse( selectNode )
    val declFieldTree = infFactory.getTrees.getTree( declFieldElem )
    val isStatic = ElementUtils.isStatic( declFieldElem )

    val receiver =
        if( isStatic ) {
          None
        } else if( selectNode.getKind == Tree.Kind.ARRAY_ACCESS ) {
          //TODO: I don't think this will correctly handle T[] because we will use T rather than the correct type-parameter
          Some( infFactory.getAnnotatedType( declFieldTree ) )
        } else {
          declaredField.receiver.map( declReceiver => getCalledReceiver( selectNode, declReceiver, infFactory ) )
        }

    val emptyTvs = List.empty[AnnotatedTypeVariable]

    val ( classTypeArgs,  classTypeParamLBs  ) =
      receiver match {
        case Some( rec : AnnotatedDeclaredType ) => ( rec.getTypeArguments.toList, declaredField.classTypeParamLBs )
        case Some( rec : AnnotatedTypeMirror   ) => ( emptyTvs, emptyTvs )
        case None                                => ( emptyTvs, emptyTvs )
      }

    val (args, results) =
      if( isAccess ) {
        val fieldType = infFactory.getAnnotatedType( node )
        ( List.empty[AnnotatedTypeMirror], Some( fieldType ) )
      } else {
        val rhsType = infFactory.getAnnotatedType( node.asInstanceOf[AssignmentTree].getExpression )
        ( List( rhsType ), None )
      }

    CommonSubboardTypeInfo( receiver,
                            emptyTvs, classTypeParamLBs,
                            emptyTvs, classTypeArgs,
                            args, results )

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

  /**
   * Create a field access constraint and add it to the list of constraints.
   * @param infFactory Used to get the type of node and (potentially) the receiver
   * @param trees Required trees util to determine the type of the declaration for the field
   *              that is being accessed
   * @param node  The tree of the identifier or the member.select that we are generating a constraint for
   */
  def addFieldAccess( infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
                      node: ExpressionTree ) {
    addFieldConstraint( infFactory, trees, node, true)
  }

  private def addFieldConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
                                 node: ExpressionTree, isAccess : Boolean ) {

    if ( node.getKind() == Tree.Kind.ARRAY_ACCESS ) {
      return //TODO CM22: Talk to Mike and Werner about this
    }

    val selectNode    = if( isAccess ) node else node.asInstanceOf[AssignmentTree].getVariable
    val declaredField = getDeclaredFieldInfo( node, isAccess, infFactory )
    val calledField   = getCalledFieldData( node, selectNode, isAccess, declaredField, infFactory )

    val accessContext = ConstraintManager.constructConstraintPosition(infFactory, node)

    val declFieldElem = TreeUtils.elementFromUse( selectNode )
    val classElem     = declFieldElem.getEnclosingElement.asInstanceOf[TypeElement]
    val declFieldTree = trees.getTree( declFieldElem )
    val libraryCall = declFieldTree == null

    addMissingClassBounds( classElem, libraryCall, infFactory )

    val ( declFieldVp, stubUse ) =
      if( libraryCall ) {
        ( None, Some( getOrCreateFieldSubboardUseConstraint( declFieldElem, accessContext, isAccess, infFactory ) ) )
      } else {
        val vp = new FieldVP(declFieldElem.getSimpleName().toString())
        vp.init(infFactory, declFieldTree)
        ( Some( vp ), None )
      }

    val fieldInfo = CommonSubboardTypeInfo.makeCall( accessContext, declFieldVp, stubUse, declaredField, calledField )
    if( isAccess ) {
      addFieldAcessConstraint( fieldInfo.receiver.getOrElse(null), fieldInfo )
    } else {
      addFieldAssignmentConstraint( fieldInfo.receiver.getOrElse(null), fieldInfo, fieldInfo.argsAsUBs )
    }
  }

  def addFieldAcessConstraint( receiver  : Slot, fieldCallInfo : CommonSubboardCallInfo ) {
    val c = new FieldAccessConstraint(
      fieldCallInfo.contextVp, fieldCallInfo.calledVp.map(_.asInstanceOf[FieldVP]), receiver,
      fieldCallInfo.classTypeParamLBs, fieldCallInfo.classTypeArgAsUBs,
      fieldCallInfo.resultSlots, fieldCallInfo.slotToLowerBound, fieldCallInfo.equivalentSlots,
      fieldCallInfo.stubUseConstraint )

    if (InferenceMain.DEBUG(this)) {
      println("New " + c)
    }
    constraints += c
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


  /**
   * Create a field access constraint and add it to the list of constraints.
   * @param infFactory Used to get the type of node and (potentially) the receiver
   * @param trees Required trees util to determine the type of the declaration for the field
   *              that is being accessed
   * @param node  The tree of the identifier or the member.select that we are generating a constraint for
   */
  def addFieldAssignment(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees, node: ExpressionTree) {
    addFieldConstraint( infFactory, trees, node, false )
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
    // TODO: Instance init
    } else if (InferenceUtils.isWithinStaticInit(infFactory, node)) {
        val blockid = InitBlockScanner.indexOfInitTree(infFactory.getPath(node), true)
        new ConstraintInStaticInitPos(blockid)
    } else {
        val fname = TreeUtils.enclosingVariable(infFactory.getPath(node)).getName().toString()
        new ConstraintInFieldInitPos(fname)
    }
    res.init(infFactory, node)
    res
  }
}

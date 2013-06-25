package checkers.inference

import javax.lang.model.element.TypeParameterElement
import javax.lang.model.`type`.TypeVariable
import checkers.types.AnnotatedTypeMirror
import checkers.types.AnnotatedTypeMirror._
import javax.lang.model.element.TypeElement
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.`type`.TypeKind
import com.sun.source.tree.Tree
import com.sun.source.tree.MethodInvocationTree
import javacutils.TreeUtils
import annotator.scanner.StaticInitScanner
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.AssignmentTree
import collection.mutable.ListBuffer

import checkers.inference.InferenceMain.inferenceChecker
import checkers.inference.InferenceMain.slotMgr

class ConstraintManager {
  //TODO: NEED TO FIX EQUALS OF SET TYPE POSITION
  val constraints = new scala.collection.mutable.LinkedHashSet[Constraint]()

  // addSubtypeConstraints not needed, as the framework calls this for
  // each type argument.
  // TODO: check

  def cleanUp() {
    constraints.clear
  }

  def addSubtypeConstraint(sub: AnnotationMirror, sup: AnnotationMirror) {
    val subel = InferenceMain.slotMgr.extractSlot(sub)
    val supel = InferenceMain.slotMgr.extractSlot(sup)
    
    if (subel == null || supel == null) {
      if (InferenceMain.DEBUG(this)) {
        // TODO: in the UTS should only happen for primitive types.
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
      // TODO: need to go to the cache?
      owner.asInstanceOf[AnnotatedTypeVariable].getUpperBound()
    } else {
      owner
    }

    val recv = InferenceMain.slotMgr.extractSlot(declowner)

    if (recv == LiteralThis || recv == LiteralSuper) {
      // No combination for accesses through the current object
      // TODO: is this good? should we copy ty?
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
      // TODO: we should also create a combine constraint
      // Might be needed for other type systems
      return ty
    } else if (ty.getKind() == TypeKind.TYPEVAR) {
      /* TODO: remove this left-over code:
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
      import scala.collection.JavaConversions._
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
      // else TODO: the receiver might be another type variable... should we do something?
    } else if (rhs.getKind() == TypeKind.DECLARED) {
      // Create a copy
      val declaredType = rhs.getCopy(true).asInstanceOf[AnnotatedDeclaredType]
      val mapping = new java.util.HashMap[AnnotatedTypeMirror, AnnotatedTypeMirror]()

      import scala.collection.JavaConversions._
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

    if (decltype.isParameterized()) {
      val tas = decltype.getTypeArguments()
      // assert foundindex < tas.size()) {
      // CAREFUL: return a copy, as we want to modify the type later.
      return tas.get(foundindex).getCopy(true)
    } else {
      // TODO: use upper bound instead of var
      // type.getTypeArguments()
      // System.out.println("Raw Type: " + decltype)
      // TODO: do we still need this:
      if (!rvar.getUpperBound().isAnnotated()) {
        // TODO: hmm, seems to be needed :-(
        rvar.getUpperBound().addAnnotation(InferenceMain.getRealChecker.defaultQualifier)
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

    import scala.collection.JavaConversions._
    for (tparam <- tparams) {
      if (tparam.equals(varelem) ||
        //TODO: comparing by name!!!???
        // Sometimes "E" and "E extends Object" are compared, which do not match by "equals".
        tparam.getSimpleName().equals(varelem.getSimpleName())) {
        // we found the right index!
        // TODO: optimize this loop for Scala, a break here doesn't work.
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

  def addCallInstanceMethodConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
      node: MethodInvocationTree) {
    import scala.collection.JavaConversions._

    val select = node.getMethodSelect()
    val calledmeth = new CalledMethodPos()
    val exeelem = TreeUtils.elementFromUse(node)
    val calledTree = trees.getTree(exeelem)
    if (calledTree==null) {
      // TODO JB: We currently don't create a constraint for binary only methods(?)
      return
    }

    calledmeth.init(infFactory, calledTree)

    val explicitTypeArgs = node.getTypeArguments()
    val explicitTypeArgSlots = ConstraintManager.extractSlots(infFactory, explicitTypeArgs)


    val args = node.getArguments()
    val argSlots = ConstraintManager.extractSlots(infFactory, args)


    val methodTypeParamSlots = exeelem.getTypeParameters.map( infFactory.getAnnotatedType _ )
    val typeParamsToTypeArgSlots = argSlots zip methodTypeParamSlots //TODO: The subtyping between this is already handled

    val returnType = infFactory.getAnnotatedType( node )
    val result = listFieldVariables( returnType )

    val recvTree = TreeUtils.getReceiverTree(select)
    val recvType = if (recvTree != null) {
        infFactory.getAnnotatedType(recvTree)
    } else {
        infFactory.getSelfType(node)
    }
    val recvslot = InferenceMain.slotMgr.extractSlot(recvType)

    val callervp = ConstraintManager.constructConstraintPosition(infFactory, node)

    addCallInstanceMethodConstraint(callervp, recvslot, calledmeth, explicitTypeArgSlots, argSlots, result)
  }

  private def addCallInstanceMethodConstraint(callervp: VariablePosition, receiver: Slot, calledmeth: CalledMethodPos,
    typeargs: List[Slot], args: List[Slot], result: List[Slot]) {
    // todo validation?
    val c = CallInstanceMethodConstraint(callervp, receiver, calledmeth, typeargs, args, result)
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
  def addFieldAccessConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
      node: ExpressionTree) {
    val fieldType = infFactory.getAnnotatedType(node)
    if (!InferenceMain.getRealChecker.needsAnnotation(fieldType)) {
      // No constraint if the type doesn't need an annotation.
      return
    }

    val declFieldElem = TreeUtils.elementFromUse(node)
    val declFieldTree = trees.getTree(declFieldElem)
    if (declFieldTree==null) {
      // Don't create constraints for fields for which we don't have the source code.
      return
    }

    //TODO JB: Enormous Kludge, fix the output where a ? generics wildcard gets converted to
    //TODO JB: @VarAnnot(1) ? extends @VarAnnot(1) Object
    //TODO JB: REmove the .toSet.toList
    val fieldVars = listFieldVariables(fieldType).toSet.toList



    val secondaryVariables = if(fieldVars.isEmpty) fieldVars else fieldVars.reverse.tail

    val declFieldVp = new FieldVP(declFieldElem.getSimpleName().toString())
    declFieldVp.init(infFactory, declFieldTree)

    val accessFieldSlot = InferenceMain.slotMgr.extractSlot(fieldType)

    val recvTree = TreeUtils.getReceiverTree(node)
    val recvType = if (recvTree != null) {
        infFactory.getAnnotatedType(recvTree)
    } else {
        infFactory.getSelfType(node)
    }

    val receiverSlot = InferenceMain.slotMgr.extractSlot(recvType)

    val accessContext = ConstraintManager.constructConstraintPosition(infFactory, node)

    addFieldAccessConstraint(accessContext, receiverSlot, accessFieldSlot, declFieldVp, secondaryVariables)
  }

  def listFieldVariables(atm : AnnotatedTypeMirror) : List[Slot] = {
    val variables = new ListBuffer[Slot]
    listFieldVariables( atm, variables )
    variables.toList.reverse
  }

  /**
   * //TODO: tailrec it by visit node/opposite subtree first and then reverse the order?  Or just create a map of (TopLevel, Position) -> Variable
   * Do a depth first search of all Variables introduced by the given field ExpressionTree
   * //TODO: At the moment this is the reverse of the normal depth first search we would do (then it's reversed in
   * //TODO: listVariables.  I don't think the ordering matters because each variable already has a POS but
   * //TODO: it might prove important to have the right ordering in the game solver in order to match
   * //TODO: FieldAccesses and method call wiring correctly, however, I think we should be able to order
   * //TODO: by pos in worst case
   */
  private def listFieldVariables(atm : AnnotatedTypeMirror, types : ListBuffer[Slot]) {
    import scala.collection.JavaConversions._

    def primaryToSlot(atm : AnnotatedTypeMirror) = {
      val anno = atm.getAnnotationInHierarchy(inferenceChecker.VAR_ANNOT) //TODO: Why is @NonNull and @Nullable returned in the same hierarchy?
      try {
      Option( slotMgr.extractSlot(anno).asInstanceOf[AbstractVariable] ) //TODO: I believe in a field declaration we should only ever have abstract variables
      } catch {
        case exc => throw new RuntimeException(exc)
      }
    }

    Option(atm).map(

      _ match {

        case aat : AnnotatedArrayType =>
          types ++= primaryToSlot(aat)
          listFieldVariables(aat.getComponentType, types)

        case awt : AnnotatedWildcardType =>
          types ++= primaryToSlot(awt)
          listFieldVariables(awt.getSuperBound,   types)
          listFieldVariables(awt.getExtendsBound, types)

        case atv : AnnotatedTypeVariable =>
          /* TODO JB: Talk to Werner about AnnotatedTypeVariables
          types ++= primaryToSlot(atv)
          listFieldVariables(atv.getLowerBound, types)
          listFieldVariables(atv.getUpperBound, types)   */

        case adt : AnnotatedDeclaredType =>
          types ++= primaryToSlot(adt)
          adt.getTypeArguments.foreach( (typeArg : AnnotatedTypeMirror) => listFieldVariables(typeArg, types) )

        case apt: AnnotatedPrimitiveType =>
          types ++= primaryToSlot(apt)

        case ait : AnnotatedIntersectionType =>
          types ++= primaryToSlot(ait) //TODO: Anything else todo?

        case atm : AnnotatedTypeMirror if atm.isInstanceOf[AnnotatedNoType] |
                                          atm.isInstanceOf[AnnotatedNullType] =>
          //TODO JB: Anything todo here?

        case atm : AnnotatedTypeMirror =>
          throw new RuntimeException("Unhandled annotated type mirror " + atm.getClass.getCanonicalName)
      }

    )
   }

  /**
   * Create a field access constraint and add it to the list of constraints.
   * @param accessContext Where was the field accessed
   * @param receiver The receiver that contains the field
   * @param fieldslot The slot corresponding to the type of the tree in which the field was accessed //TODO: Shouldn't this have the declFieldVP?
   * @param declFieldVp The slot corresponding to the declaration of the field
   */
  private def addFieldAccessConstraint(accessContext: VariablePosition, receiver: Slot, fieldslot: Slot, declFieldVp: FieldVP, secondaryVariables : List[Slot]) {
    // todo validation?
    val c = FieldAccessConstraint(accessContext, receiver, fieldslot, declFieldVp, secondaryVariables)
    if (InferenceMain.DEBUG(this)) {
        println("New " + c)
    }
    constraints += c
  }

  def addAssignmentConstraint(infFactory: InferenceAnnotatedTypeFactory, trees: com.sun.source.util.Trees,
      node: AssignmentTree) {
    val lefttype = infFactory.getAnnotatedType(node.getVariable())
    if (!InferenceMain.getRealChecker.needsAnnotation(lefttype)) {
      // No constraint if the type doesn't need an annotation.
      return
    }
    val leftslot = InferenceMain.slotMgr.extractSlot(lefttype)

    val righttype = infFactory.getAnnotatedType(node.getExpression())
    val rightslot = InferenceMain.slotMgr.extractSlot(righttype)

    val context = ConstraintManager.constructConstraintPosition(infFactory, node)

    val leftelem = lefttype.getElement()
    if (leftelem!=null && leftelem.getKind().isField()) {
      val recvTree = TreeUtils.getReceiverTree(node.getVariable())
      val recvType = if (recvTree != null) {
          infFactory.getAnnotatedType(recvTree)
        } else {
          infFactory.getSelfType(node)
        }
      val recvslot = InferenceMain.slotMgr.extractSlot(recvType)

      addFieldAssignmentConstraint(context, recvslot, leftslot, rightslot)
    } else {
      println("TODO ASSIGNMENT CONSTRAINT")
    }
  }

  private def addFieldAssignmentConstraint(context: VariablePosition, recv: Slot, field: Slot, right: Slot) {
    // todo validation?
    val c = FieldAssignmentConstraint(context, recv, field, right)
    if (InferenceMain.DEBUG(this)) {
        println("New " + c)
    }
    constraints += c
  }

  private def addAssignmentConstraint(context: VariablePosition, left: Slot, right: Slot) {
    // todo validation?
    val c = AssignmentConstraint(context, left, right)
    if (InferenceMain.DEBUG(this)) {
        println("New " + c)
    }
    constraints += c
  }

}

object ConstraintManager {
  private def extractSlots(infFactory: InferenceAnnotatedTypeFactory, trees: java.util.List[_ <: Tree]): List[Slot] = {
    val res = new collection.mutable.ListBuffer[Slot]
    import scala.collection.JavaConversions._
    for (tree <- trees) {
      val ty = infFactory.getAnnotatedType(tree)
      val slot = InferenceMain.slotMgr.extractSlot(ty)
      if (slot!=null)
        res += slot
    }
    res.toList
  }

  //TODO JB:  This is duplicate code from InferenceTreeAnnotator
  // TODO: more specific return type? Introduce ConstraintPosition interface?
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
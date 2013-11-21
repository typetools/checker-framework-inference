package checkers.inference.util

import javax.lang.model.element.{TypeElement, Element}
import com.sun.source.tree._
import checkers.inference.{InferenceMain, InferenceAnnotatedTypeFactory, Slot}
import checkers.types.AnnotatedTypeMirror
import javacutils.TreeUtils

object WcBoundKind extends Enumeration {
  type WcBoundKind = Value
  val NoBound, Extends, Super = Value
}

import WcBoundKind._

object WildcardBounds {
  def superWcToUpperBound( wct : WildcardTree, iAtf : InferenceAnnotatedTypeFactory ) : AnnotatedTypeMirror = {
    val treePath      = iAtf.getPath( wct )
    val enclosingTree = treePath.getParentPath.getLeaf.asInstanceOf[ParameterizedTypeTree]  //TODO Does not work on Methods?

    val index = enclosingTree.getTypeArguments.indexOf( wct )
    val enclosingClassElem = TreeUtils.elementFromUse( enclosingTree.getType.asInstanceOf[ExpressionTree] ).asInstanceOf[TypeElement]
    val typeParamElement = enclosingClassElem.getTypeParameters.get( index )
    val upperBound = InferenceMain.inferenceChecker.typeParamElemToUpperBound( typeParamElement )
    upperBound.getUpperBound
  }
}

/**
*
* @param upperBound
*/
abstract class WildcardBounds( val upperBound : AnnotatedTypeMirror ) {

  /**
   * If true the tree looked like  ? extends UpperBound
   * else the tree looked like     ? super   LowerBound
   */
  val kind : WcBoundKind
}

abstract class UpperWcBounds(  override val upperBound : AnnotatedTypeMirror, val lowerBound : Slot )
  extends WildcardBounds( upperBound )

case class NoBoundWcBounds( override val upperBound : AnnotatedTypeMirror, override val lowerBound : Slot )
  extends UpperWcBounds( upperBound, lowerBound ) {
  override val kind = NoBound
}

case class ExtendsWcBounds( override val upperBound : AnnotatedTypeMirror, override val lowerBound : Slot )
  extends UpperWcBounds( upperBound, lowerBound ) {
  override val kind = Extends
}

case class SuperWcBounds( override val upperBound : AnnotatedTypeMirror,
                          val lowerBound : AnnotatedTypeMirror )
  extends WildcardBounds( upperBound ) {
  override val kind = Super
}


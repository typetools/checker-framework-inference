package checkers.inference.util

import checkers.inference._
import checkers.inference.CalledMethodPos
import checkers.inference.StubBoardUseConstraint

case class CommonSubboardCallInfo(
    contextVp : WithinClassVP,
    calledVp : Option[VariablePosition],
    methodTypeParamLBs : List[Slot], classTypeParamLBs : List[Slot],
    methodTypeArgAsUBs : List[List[Slot]], classTypeArgAsUBs : List[List[Slot]],
    argsAsUBs : List[Slot], resultSlots : List[Slot],
    stubUseConstraint : Option[StubBoardUseConstraint],
    equivalentSlots : Set[(Slot, Slot)],
    slotToLowerBound : Set[(Slot, Slot)]
) {

  def mergeSubtypingResult( subtypingResult : SubtypingResult ) = {
    CommonSubboardCallInfo(
      contextVp, calledVp, methodTypeParamLBs, classTypeParamLBs,
      methodTypeArgAsUBs, classTypeArgAsUBs, argsAsUBs, resultSlots, stubUseConstraint,
      equivalentSlots ++ subtypingResult.equality,
      slotToLowerBound ++ subtypingResult.lowerBounds
    )
  }
}
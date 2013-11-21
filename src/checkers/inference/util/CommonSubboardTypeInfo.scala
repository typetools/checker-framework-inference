package checkers.inference.util

import checkers.inference._
import checkers.types.AnnotatedTypeMirror
import checkers.inference.StubBoardUseConstraint


case class CommonSubboardTypeInfo(
  receiver : Option[AnnotatedTypeMirror],
  methodTypeParamLBs  : List[AnnotatedTypeMirror],
  classTypeParamLBs   : List[AnnotatedTypeMirror],
  methodTypeParamUBs  : List[AnnotatedTypeMirror],
  classTypeParamUBs   : List[AnnotatedTypeMirror],
  args : List[AnnotatedTypeMirror],
  results : Option[AnnotatedTypeMirror]
)


object CommonSubboardTypeInfo {
  private lazy val infChecker = InferenceMain.inferenceChecker
  private lazy val slotMgr    = InferenceMain.slotMgr


  def makeCall[CONTEXT_VP <: WithinClassVP](
                            contextVp : CONTEXT_VP, calledVp : Option[VariablePosition],
                            stubUse : Option[StubBoardUseConstraint], infFactory : InferenceAnnotatedTypeFactory,
                            declared : CommonSubboardTypeInfo, called : CommonSubboardTypeInfo ) = {

    try {
      //TODO: Need to do more for wildcards
      val methodTypeParamLBs = called.methodTypeParamLBs.map( slotMgr.extractSlot _ )
      val classTypeParamLBs  = called.classTypeParamLBs.map(  slotMgr.extractSlot _ )

      val subtypingVisitor = new SubtypingVisitor( slotMgr, infChecker, infFactory )

      //At the moment we only care about the 1st (primary) annotation on receivers
      val ( recvResult, recvSlots ) = getInputSlots( declared.receiver, called.receiver, subtypingVisitor )

      val ( mtUBResult, methodTypeParamUBs ) = getInputSlots( declared.methodTypeParamUBs, called.methodTypeParamUBs, subtypingVisitor )
      val ( clUBResult, classTypeParamUBs  ) = getInputSlots( declared.classTypeParamUBs, called.classTypeParamUBs, subtypingVisitor )

      subtypingVisitor.typeUse = true
      val ( argsResult, argsAsUb ) = getInputSlots( declared.args, called.args, subtypingVisitor )
      val results =
        called.results
         .map( SlotUtil.listDeclVariables _ )
         .getOrElse( List.empty[Slot] )

      val subtypingResult =
        mtUBResult
          .merge( clUBResult )
          .merge( argsResult )

      CommonSubboardCallInfo(contextVp, calledVp, recvSlots.headOption.map( _.apply(0) ),
                             methodTypeParamLBs, classTypeParamLBs,
                             methodTypeParamUBs, classTypeParamUBs,
                             argsAsUb.flatten, results, stubUse,
                             subtypingResult.equality, subtypingResult.lowerBounds )
    } catch {
      case throwable : Throwable =>
        val fieldSummaries =
          List( "Context Pos"  -> contextVp, "Called Pos" -> calledVp, "StubUse" -> stubUse,
                "Declared" -> declared, "Called" -> called )
            .map({case (title, obj) => title +":\n" + obj })
            .mkString("\n")

        throw new RuntimeException( "Throwable caught while making call to: \n" + fieldSummaries, throwable )
    }
  }

  private def getInputSlots( declared : Option[AnnotatedTypeMirror], subtype : Option[AnnotatedTypeMirror], subtypingVisitor : SubtypingVisitor ) : (SubtypingResult, List[List[Slot]]) = {
    if( subtype.isDefined ) {
      getInputSlots( declared.toList, subtype.toList, subtypingVisitor )
    } else {
      ( SubtypingResult.empty, List.empty[List[Slot]] )
    }
  }

  private def getInputSlots( declareds : List[AnnotatedTypeMirror], subtypes : List[AnnotatedTypeMirror], subtypingVisitor : SubtypingVisitor ) : (SubtypingResult, List[List[Slot]]) = {

    assert( declareds.length == subtypes.length )
    if( declareds.size == 0 ) {
      return ( SubtypingResult.empty, List.empty[List[Slot]] )
    }

    var subtypingResult : SubtypingResult = null

    val resultsToSlots : List[( SubtypingResult, List[Slot] )] =
      declareds.zip( subtypes )
        .map({
          case ( declared : AnnotatedTypeMirror, subtype : AnnotatedTypeMirror ) =>
            subtypingVisitor.clearResult()
            subtypingVisitor.visitTopLevel( declared, subtype )
            val result = subtypingVisitor.getResult
            if( subtypingResult == null ) {
              subtypingResult = result
            } else {
              subtypingResult = subtypingResult.merge( result )
            }

            ( result, result.methodInputs.map( _._2 ) )
      })

    val subtypingResults = resultsToSlots.map( _._1 ).toList
    val subtypesAsUb     = resultsToSlots.map( _._2 ).toList

    val accumulatedResult = subtypingResults.tail.foldLeft( subtypingResults.head ) ( _ merge _ )

    ( accumulatedResult , subtypesAsUb )
  }
}
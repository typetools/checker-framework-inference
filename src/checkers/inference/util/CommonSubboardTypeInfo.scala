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
) {
  val summary =
    List( "receiver"           -> receiver,
          "methodTypeParamLBs" -> methodTypeParamLBs,
          "classTypeParamLBs"  -> classTypeParamLBs,
          "methodTypeParamUBs" -> methodTypeParamUBs,
          "classTypeParamUBs"  -> classTypeParamUBs,
          "args"    -> args,
          "results" -> results )
      .map( {case (label, value) => label + ": " + value} )
      .mkString("\n")

  "CommonSubboardTypeInfo(\n" + summary + "\n)\n"
}


object CommonSubboardTypeInfo {
  private lazy val slotMgr = InferenceMain.slotMgr
  private lazy val log     = new Log( classOf[CommonSubboardTypeInfo], InferenceMain.LogSettings )

  def makeCall[CONTEXT_VP <: WithinClassVP](
                            contextVp : CONTEXT_VP, calledVp : Option[VariablePosition],
                            stubUse : Option[StubBoardUseConstraint],
                            declared : CommonSubboardTypeInfo, called : CommonSubboardTypeInfo ) = {

    try {

      //TODO: Need to do more for wildcards
      val methodTypeParamLBs = called.methodTypeParamLBs.map( slotMgr.extractSlot _ )
      val classTypeParamLBs  = called.classTypeParamLBs.map(  slotMgr.extractSlot _ )

      val subtypingVisitor = new SubtypingVisitor(  )

      //At the moment we only care about the 1st (primary) annotation on receivers
      val ( recvResult, recvSlots ) = getInputSlots( declared.receiver, called.receiver, subtypingVisitor )

      val ( declaredClassTypeUBs,  calledClassTypeUBs  ) =
        fixRawness( contextVp, calledVp, stubUse, declared.classTypeParamUBs,  called.classTypeParamUBs )

      val ( mtUBResult, methodTypeParamUBs ) = getInputSlots( declared.methodTypeParamUBs, called.methodTypeParamUBs, subtypingVisitor )
      val ( clUBResult, classTypeParamUBs  ) = getInputSlots( declaredClassTypeUBs, calledClassTypeUBs, subtypingVisitor )

      subtypingVisitor.typeUse = true
      val ( argsResult, argsAsUb ) = getInputSlots( declared.args, called.args, subtypingVisitor )
      val ( retResult , retAsUb )  = getInputSlots( declared.results, called.results, subtypingVisitor )

      val subtypingResult =
        mtUBResult
          .merge( clUBResult )
          .merge( argsResult )
          .merge( retResult )

      CommonSubboardCallInfo(contextVp, calledVp, recvSlots.headOption.map( _.apply(0) ),
                             methodTypeParamLBs, classTypeParamLBs,
                             methodTypeParamUBs, classTypeParamUBs,
                             argsAsUb.flatten, retAsUb.flatten, stubUse,
                             subtypingResult.equality, subtypingResult.lowerBounds )
    } catch {
      case throwable : Throwable =>
        val fieldSummaries = summarizeFields( contextVp, calledVp, stubUse, declared, called )

        throw new RuntimeException( "Throwable caught while making call to: \n" + fieldSummaries + "\n", throwable )
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

    assert( declareds.length == subtypes.length, "Number of parameters and number of arguments don't match." )
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

  //NOTE: Everything besides declareds and subtypes is passed in solely for reporting reasons
  private def fixRawness( contextVp : WithinClassVP, calledVp : Option[VariablePosition],
                          stubUse : Option[StubBoardUseConstraint],
                          declareds : List[AnnotatedTypeMirror], subtypes : List[AnnotatedTypeMirror] ) :
    (List[AnnotatedTypeMirror], List[AnnotatedTypeMirror]) = {

    ( declareds.isEmpty, subtypes.isEmpty ) match {
      case (false, false ) | (true, true ) => ( declareds, subtypes )
      case (false, true  ) =>
        log.error("Raw type encountered: " +  summarizeFields( contextVp, calledVp, stubUse, declareds, subtypes ) )
        ( declareds, declareds )
      case (true,  false ) =>
        log.error("Raw type encountered: " +  summarizeFields( contextVp, calledVp, stubUse, declareds, subtypes ) )
        ( subtypes,  subtypes  )
    }

  }

  private def summarizeFields( contextVp : WithinClassVP, calledVp : Option[VariablePosition],
                               stubUse : Option[StubBoardUseConstraint],  declared : Object,
                               called : Object ) = {
    List( "Context Pos"  -> contextVp,
          "Called Pos"   -> calledVp,
          "StubUse"     -> stubUse,
          "Declared"    -> declared,
          "Called"      -> called )
          .map({case (title, obj) => title +":\n" + obj })
          .mkString("\n")
  }
}
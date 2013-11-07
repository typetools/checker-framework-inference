package checkers.inference.util

import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Calendar

object LogLevel extends Enumeration {
  type LogLevel = Value

  //Alert is the lowest level of logging and is designed to be used when you are working on a specific
  //bug and wish to know only whether or not that bug is encountered.  You should NEVER check in a message
  //using the Alert level
  val Alert, Debug, Info, Error = Value

  lazy val LevelFromEnv = PropertiesUtil.enumPropOrEnv( "LOG_LEVEL", LogLevel, Debug )

  val DateFormatLock = new Object
  val DateFormatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
}

import LogLevel._

case class LogSettings( level : LogLevel, outs : Map[LogLevel, List[PrintStream]] )

object Log {
  def apply( clazz : Class[_], logSettings : LogSettings ) = new Log( clazz, logSettings )
}


class Log( val clazz : Class[_], val logSettings : LogSettings ) {
  val settings = logSettings

  def log(  level : LogLevel, msg : String ) = {
    if( settings.level.id <= level.id ) {
      val outsForLevel = settings.outs.get( level )
      outsForLevel.map( outs => {
        val stampedMsg = "[ " + clazz.getName + " ] " +
                         DateFormatLock.synchronized { DateFormatter.format( Calendar.getInstance.getTime ) } + " : " +
                         msg
        outs.foreach( _.println( stampedMsg ) )
      } )
    }
  }

  def debug( msg : String ) = log( Debug, msg )
  def info ( msg : String ) = log( Info,  msg )
  def error( msg : String ) = log( Error, msg )
  def alert( msg : String ) = log( Alert, msg )
}

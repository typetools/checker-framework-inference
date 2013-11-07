package checkers.inference.util

object PropertiesUtil {

  def booleanPropOrEnv( propName : String, default : Boolean=true ) = {
    propOrEnvValue(propName).map(_ == "true").getOrElse(default)
  }

  def propOrEnvValue( propName : String, default : Option[String]=None ) : Option[String] = {
    ( Option( System.getProperty(propName) ) ) match {
      case Some( value : String ) => Some( value )
      case None =>
        Option( System.getenv(propName) ) match {
          case Some( value : String ) => Some( value )
          case None => default
        }
    }
  }

  def propOrEnvValues( propNames : String*) : Option[String] = {
    propNames
      .map( propName => propOrEnvValue( propName ) )
      .find( _.isDefined )
      .getOrElse( None )
  }

  def enumPropOrEnv[E]( propName : String, enum : Enumeration, alt : E ) : E = {
    propOrEnvValue( propName ) match {
      case Some( value : String ) => enum.withName( value ).asInstanceOf[E]
      case None => alt
    }
  }

}

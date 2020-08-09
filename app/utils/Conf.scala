package utils

import com.typesafe.config.ConfigFactory
import play.api.{Configuration, Logger}

object Conf {
  val config: Configuration = Configuration(ConfigFactory.load())
  private val logger: Logger = Logger(this.getClass)

  lazy val serverUrl: String = readKey("server.url")
  lazy val pk: String = readKey("pk")

  def readKey(key: String, default: String = null): String = {
    try {
      if(config.has(key)) config.getOptional[String](key).getOrElse(default)
      else throw config.reportError(key,s"${key} not found!")
    } catch {
        case ex: Throwable =>
          logger.error(ex.getMessage)
          null
      }
  }
}

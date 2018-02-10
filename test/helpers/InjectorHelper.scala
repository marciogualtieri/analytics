package helpers

import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder

import scala.reflect.ClassTag

object InjectorHelper {
  lazy val injector: Injector = (new GuiceApplicationBuilder).injector()

  def inject[T: ClassTag]: T = injector.instanceOf[T]
}
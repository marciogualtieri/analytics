import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneServerPerTest

class IntegrationSpec extends PlaySpec with GuiceOneServerPerTest with OneBrowserPerTest with HtmlUnitFactory {

  "Application" should {

    "work from within a browser" in {

      go to ("http://localhost:" + port)
      pageSource must include("It's Alive")
    }
  }
}

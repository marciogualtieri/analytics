package entities

case class Counts(users: Int,
                  clicks: Int,
                  impressions: Int) {

  override def toString: String =
    s"""
      |unique_users,$users
      |clicks,$clicks
      |impressions,$impressions
    """.stripMargin
}
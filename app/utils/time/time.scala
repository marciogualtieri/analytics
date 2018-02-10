package utils

import java.sql.Timestamp
import javax.inject.Singleton

package object time {

  val MillisecondsInOneHour: Long = 1000 * 60 * 60

  def hoursSinceEpoch(millisecondsSinceEpoch: Timestamp): Long = {
    millisecondsSinceEpoch.getTime / MillisecondsInOneHour
  }

  @Singleton
  class Clock {
    def now(): Timestamp = new Timestamp(System.currentTimeMillis())

    def currentHoursSinceEpoch(): Long = hoursSinceEpoch(now())
  }
}
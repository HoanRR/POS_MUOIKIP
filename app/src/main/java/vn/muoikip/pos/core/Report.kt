package vn.muoikip.pos.core

import java.util.Calendar
import java.util.TimeZone

/** A quán nhậu sells past midnight: a business day runs from 06:00 to 06:00 the next morning. */
object BusinessDay {
    const val CUTOFF_HOUR = 6

    /** [from, until) of the business day containing [at]. */
    fun range(at: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = at }
        if (c.get(Calendar.HOUR_OF_DAY) < CUTOFF_HOUR) c.add(Calendar.DAY_OF_MONTH, -1)
        c.set(Calendar.HOUR_OF_DAY, CUTOFF_HOUR)
        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 1)
        return from to c.timeInMillis
    }
}

data class ReportLine(val name: String, val quantity: Int, val amount: Vnd)

data class DailyReport(
    val billCount: Int,
    val revenue: Vnd,
    val roomFees: Vnd,
    /** Best sellers first (by amount). */
    val items: List<ReportLine>,
)

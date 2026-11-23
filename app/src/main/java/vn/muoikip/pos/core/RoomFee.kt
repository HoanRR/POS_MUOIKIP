package vn.muoikip.pos.core

/** VIP room billing: round up to 15-minute blocks, minimum one hour. */
object RoomFee {
    private const val BLOCK_MS = 15 * 60 * 1000L
    private const val MIN_BLOCKS = 4

    fun billedBlocks(elapsedMs: Long): Int {
        val blocks = ((elapsedMs.coerceAtLeast(0) + BLOCK_MS - 1) / BLOCK_MS).toInt()
        return maxOf(blocks, MIN_BLOCKS)
    }

    fun fee(hourlyRate: Vnd, elapsedMs: Long): Vnd = hourlyRate * billedBlocks(elapsedMs) / 4

    /** "2 giờ 15 phút" for the billed (rounded) duration. */
    fun billedDurationText(elapsedMs: Long): String = durationText(billedBlocks(elapsedMs) * 15L)

    fun durationText(totalMinutes: Long): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "$m phút"
            m == 0L -> "$h giờ"
            else -> "$h giờ $m phút"
        }
    }
}

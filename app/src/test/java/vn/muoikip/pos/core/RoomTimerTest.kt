package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTimerTest {
    private val store = InMemoryStore()
    private val clock = FakeClock()
    private val engine = PosEngine(store, clock, FakePrinter(), DefaultData.SHOP)

    private fun table(zone: String, n: Int) =
        engine.tables(engine.zones().first { it.name == zone }.id).first { it.table.number == n }.table.id

    @Test fun startTimeCanBeMovedEarlierAndFeeFollows() {
        val vip = table("VIP", 1)
        engine.openRoom(vip)                       // opened "now" but guests came 90 minutes ago
        val start = clock.now() - 90 * 60_000L

        assertTrue(engine.setRoomStart(vip, start))

        assertEquals(start, engine.order(vip)!!.order.openedAt)
        assertEquals(90 * 60_000L, engine.table(vip).elapsedMs)
        assertEquals(150_000L, engine.order(vip)!!.roomFee)
    }

    @Test fun startTimeSurvivesRestart() {
        val vip = table("VIP", 2)
        engine.openRoom(vip)
        val start = clock.now() - 30 * 60_000L
        engine.setRoomStart(vip, start)
        assertEquals(start, PosEngine(store, clock, FakePrinter(), DefaultData.SHOP).order(vip)!!.order.openedAt)
    }

    @Test fun startTimeCannotBeInTheFuture() {
        val vip = table("VIP", 3)
        engine.openRoom(vip)
        val opened = engine.order(vip)!!.order.openedAt
        assertFalse(engine.setRoomStart(vip, clock.now() + 60_000L))
        assertEquals(opened, engine.order(vip)!!.order.openedAt)
    }

    @Test fun onlyRoomsHaveAnAdjustableStart() {
        val t = table("Sân trước", 1)
        engine.addItem(t, engine.categories().flatMap { engine.menu(it.id) }.first { it.name == "Rau xào" }.id)
        assertFalse(engine.setRoomStart(t, clock.now() - 60_000L))
    }

    @Test fun rateChangeAppliesToRunningRoom() {
        val vip = table("VIP", 1)
        engine.openRoom(vip)
        clock.advanceMinutes(60)
        engine.setRoomRate(vip, 150_000)
        assertEquals(150_000L, engine.order(vip)!!.roomFee)
    }
}

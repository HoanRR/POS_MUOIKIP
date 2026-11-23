package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveMergeTest {
    private val clock = FakeClock()
    private val engine = PosEngine(InMemoryStore(), clock, FakePrinter(), DefaultData.SHOP)

    private fun table(zone: String, n: Int) =
        engine.tables(engine.zones().first { it.name == zone }.id).first { it.table.number == n }.table.id
    private fun item(name: String) = engine.categories().flatMap { engine.menu(it.id) }.first { it.name == name }.id

    @Test fun moveCarriesOrderToEmptyTable() {
        val from = table("Sân trước", 1)
        val to = table("Nhà gỗ", 5)
        engine.addItem(from, item("Hến xào"))
        assertTrue(to in engine.moveTargets(from).map { it.table.id })

        engine.moveTable(from, to)

        assertFalse(engine.table(from).occupied)
        assertEquals(50_000L, engine.order(to)!!.total)
    }

    @Test fun cannotMoveToOccupiedTableOrAcrossRoomAndTable() {
        val from = table("Sân trước", 1)
        val busy = table("Sân trước", 2)
        engine.addItem(from, item("Hến xào"))
        engine.addItem(busy, item("Rau xào"))
        val targets = engine.moveTargets(from).map { it.table.id }
        assertFalse(busy in targets)
        assertFalse(table("VIP", 1) in targets)
        assertFalse(from in targets)
    }

    @Test fun movingRoomKeepsItsClockAndUsesNewRate() {
        val vip1 = table("VIP", 1)
        engine.openRoom(vip1)
        clock.advanceMinutes(120)
        val vip2 = table("VIP", 2)
        assertEquals(listOf(table("VIP", 2), table("VIP", 3)), engine.moveTargets(vip1).map { it.table.id })
        engine.moveTable(vip1, vip2)
        assertEquals(200_000L, engine.order(vip2)!!.roomFee)
        assertEquals(120 * 60_000L, engine.table(vip2).elapsedMs)
    }

    @Test fun mergeAddsLinesIntoTargetAndFreesSource() {
        val a = table("Sân trước", 1)
        val b = table("Sân trước", 2)
        engine.addItem(a, item("Bia Tiger"))
        engine.addItem(a, item("Bia Tiger"))
        engine.addItem(a, item("Hến xào"))
        engine.addItem(b, item("Bia Tiger"))
        engine.addItem(b, item("Tôm sông"))
        engine.setLinePrice(b, engine.order(b)!!.order.lines.first { it.name == "Tôm sông" }.id, 150_000)

        assertTrue(b in engine.mergeTargets(a).map { it.table.id })
        engine.mergeInto(a, b)

        assertFalse(engine.table(a).occupied)
        val lines = engine.order(b)!!.order.lines.associate { it.name to (it.quantity to it.unitPrice) }
        assertEquals(mapOf("Bia Tiger" to (3 to 20_000L), "Tôm sông" to (1 to 150_000L), "Hến xào" to (1 to 50_000L)), lines)
        assertEquals(260_000L, engine.order(b)!!.total)
    }

    @Test fun sameItemAtDifferentPriceStaysSeparateWhenMerging() {
        val a = table("Sân trước", 1)
        val b = table("Sân trước", 2)
        engine.addItem(a, item("Tôm sông"))
        engine.addItem(b, item("Tôm sông"))
        engine.setLinePrice(b, engine.order(b)!!.order.lines.single().id, 150_000)
        engine.mergeInto(a, b)
        assertEquals(listOf(150_000L, 100_000L), engine.order(b)!!.order.lines.sortedBy { it.seq }.map { it.unitPrice })
    }

    @Test fun mergeTargetsAreOtherOccupiedTablesAndNeverFromRoom() {
        val a = table("Sân trước", 1)
        val vip = table("VIP", 1)
        engine.addItem(a, item("Rau xào"))
        engine.addItem(vip, item("Rau xào"))
        assertEquals(listOf(vip), engine.mergeTargets(a).map { it.table.id })
        assertEquals(emptyList<String>(), engine.mergeTargets(vip).map { it.table.id })
    }
}

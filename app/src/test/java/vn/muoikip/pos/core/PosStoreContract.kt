package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Seam 2: behaviour every PosStore must share. Runs against the in-memory fake and the real
 * Room/SQLite store so the fake used by PosEngineTest cannot drift from production.
 */
abstract class PosStoreContract {
    /** A store over fresh, empty storage. */
    abstract fun newStore(): PosStore

    /** A new store instance over the same storage, as after an app restart. */
    abstract fun reopen(): PosStore

    @Test fun zoneSurvivesRestart() {
        newStore().saveZone(Zone("z1", "Sân trước", timed = false, sort = 1))
        assertEquals(listOf(Zone("z1", "Sân trước", false, 1)), reopen().load().zones)
    }

    @Test fun tablesKeepOptionalHourlyRate() {
        val s = newStore()
        s.saveTable(Table("t1", "z1", 1, null))
        s.saveTable(Table("r1", "vip", 1, 100_000))
        assertEquals(
            setOf(Table("t1", "z1", 1, null), Table("r1", "vip", 1, 100_000)),
            reopen().load().tables.toSet(),
        )
    }

    @Test fun menuKeepsNullPriceAndHiddenItems() {
        val s = newStore()
        s.saveCategory(Category("c1", "Gà", 1))
        s.saveMenuItem(MenuItem("m1", "c1", "Gà", price = null, sort = 1))
        s.saveMenuItem(MenuItem("m2", "c1", "Gà nướng", price = 250_000, sort = 2, active = false))
        val snap = reopen().load()
        assertEquals(listOf(Category("c1", "Gà", 1)), snap.categories)
        assertEquals(
            setOf(MenuItem("m1", "c1", "Gà", null, 1), MenuItem("m2", "c1", "Gà nướng", 250_000, 2, active = false)),
            snap.menuItems.toSet(),
        )
    }

    @Test fun openOrderRoundTripsWithLines() {
        val order = Order(
            id = "o1", tableId = "t1", openedAt = 1_000, roomFeeOverride = 90_000,
            lines = listOf(
                OrderLine("l1", "m1", "Gà", 1, null, 1),
                OrderLine("l2", "m2", "Bia Tiger", 3, 20_000, 2),
            ),
        )
        newStore().saveOrder(order)
        val loaded = reopen().load().openOrders.single()
        assertEquals(order.copy(lines = emptyList()), loaded.copy(lines = emptyList()))
        assertEquals(order.lines, loaded.lines.sortedBy { it.seq })
    }

    @Test fun savingOrderReplacesItsLines() {
        val s = newStore()
        val line1 = OrderLine("l1", "m1", "Rau xào", 1, 30_000, 1)
        val line2 = OrderLine("l2", "m2", "Hến xào", 1, 50_000, 2)
        s.saveOrder(Order("o1", "t1", 1_000, lines = listOf(line1, line2)))
        s.saveOrder(Order("o1", "t1", 1_000, lines = listOf(line2.copy(quantity = 4))))
        assertEquals(listOf(line2.copy(quantity = 4)), reopen().load().openOrders.single().lines)
    }

    @Test fun settingsSurviveRestartAndCanBeDeleted() {
        val s = newStore()
        s.saveSetting("bank.bin", "970436")
        s.saveSetting("bank.account", "0123")
        s.saveSetting("bank.account", "0456")
        s.saveSetting("bank.bin", null)
        assertEquals(mapOf("bank.account" to "0456"), reopen().loadSettings())
    }

    @Test fun paidOrdersAreQueriedByCloseTimeWithLabelAndLines() {
        val s = newStore()
        // Line ids are globally unique (UUIDs in production).
        fun line(id: String) = OrderLine(id, "m1", "Bia Tiger", 2, 20_000, 1)
        val line = line("l-inside")
        s.saveOrder(Order("early", "t1", 100, OrderStatus.PAID, listOf(line("l-early")), closedAt = 500, total = 40_000, cashGiven = 50_000, placeLabel = "Sân trước · Bàn 1"))
        s.saveOrder(Order("inside", "t2", 900, OrderStatus.PAID, listOf(line), closedAt = 1_500, roomFeeCharged = 100_000, total = 140_000, cashGiven = 200_000, placeLabel = "Phòng VIP 1"))
        s.saveOrder(Order("cancelled", "t3", 900, OrderStatus.CANCELLED, closedAt = 1_200))
        s.saveOrder(Order("late", "t4", 900, OrderStatus.PAID, listOf(line("l-late")), closedAt = 2_000, total = 40_000, cashGiven = 40_000))
        val got = reopen().paidOrders(from = 1_000, until = 2_000)
        assertEquals(listOf("inside"), got.map { it.id })
        assertEquals("Phòng VIP 1", got.single().placeLabel)
        assertEquals(listOf(line), got.single().lines)
    }

    @Test fun deletedZoneAndTableAreGone() {
        val s = newStore()
        s.saveZone(Zone("z1", "Sân trước", false, 1))
        s.saveZone(Zone("z2", "Nhà gỗ", false, 2))
        s.saveTable(Table("t1", "z1", 1, null))
        s.saveTable(Table("t2", "z2", 1, null))
        s.saveTable(Table("t3", "z2", 2, null))
        s.deleteZone("z1")
        s.deleteTable("t3")
        val snap = reopen().load()
        assertEquals(listOf("z2"), snap.zones.map { it.id })
        assertEquals(listOf("t2"), snap.tables.map { it.id })
    }

    @Test fun closedOrdersAreNotLoadedAsOpen() {
        val s = newStore()
        s.saveOrder(Order("o1", "t1", 1_000, status = OrderStatus.PAID, closedAt = 2_000, total = 50_000, cashGiven = 50_000))
        s.saveOrder(Order("o2", "t2", 1_000, status = OrderStatus.CANCELLED, closedAt = 2_000))
        s.saveOrder(Order("o3", "t3", 1_000))
        assertEquals(listOf("o3"), reopen().load().openOrders.map { it.id })
    }
}

class InMemoryStoreContractTest : PosStoreContract() {
    private var store = InMemoryStore()
    override fun newStore(): PosStore = InMemoryStore().also { store = it }
    override fun reopen(): PosStore = store
}

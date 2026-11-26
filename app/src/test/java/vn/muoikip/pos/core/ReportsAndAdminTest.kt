package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReportsAndAdminTest {
    private val vn = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    private val store = InMemoryStore()
    private val clock = FakeClock(at(2026, 11, 23, 18, 0))
    private val printer = FakePrinter()
    private val engine = PosEngine(store, clock, printer, DefaultData.SHOP)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance(vn).apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis
    private fun table(zone: String, n: Int) =
        engine.tables(engine.zones().first { it.name == zone }.id).first { it.table.number == n }.table.id
    private fun item(name: String) = engine.categories().flatMap { engine.menu(it.id) }.first { it.name == name }.id

    private fun sell(tableId: String, vararg items: String) {
        items.forEach { engine.addItem(tableId, item(it)) }
        engine.checkout(tableId, 10_000_000)
    }

    // ---------- Business day ----------

    @Test fun businessDayRunsFromSixAmToSixAm() {
        val (from, until) = BusinessDay.range(at(2026, 11, 24, 2, 30), vn)
        assertEquals(at(2026, 11, 23, 6, 0), from)
        assertEquals(at(2026, 11, 24, 6, 0), until)
        val (from2, _) = BusinessDay.range(at(2026, 11, 24, 6, 0), vn)
        assertEquals(at(2026, 11, 24, 6, 0), from2)
    }

    // ---------- Daily report ----------

    @Test fun reportSumsPaidBillsOfTheDayOnly() {
        sell(table("Sân trước", 1), "Bia Tiger lớn", "Bia Tiger lớn", "Rau xào")       // 70.000
        clock.ms = at(2026, 11, 24, 1, 0)                                       // after midnight, same business day
        sell(table("Sân trước", 2), "Bia Tiger lớn")                               // 20.000
        val vip = table("VIP", 1)
        engine.openRoom(vip)
        clock.ms += 90 * 60_000L
        sell(vip, "Heineken lớn")                                               // 25.000 + 150.000 room
        engine.addItem(table("Sân trước", 3), item("Hến xào"))                 // still open: not in report
        clock.ms = at(2026, 11, 24, 7, 0)
        sell(table("Sân trước", 4), "Rau xào")                                 // next business day

        val r = engine.report(at(2026, 11, 23, 6, 0), at(2026, 11, 24, 6, 0))
        assertEquals(3, r.billCount)
        assertEquals(265_000L, r.revenue)
        assertEquals(150_000L, r.roomFees)
        assertEquals(
            listOf(ReportLine("Bia Tiger lớn", 3, 60_000), ReportLine("Rau xào", 1, 30_000), ReportLine("Heineken lớn", 1, 25_000)),
            r.items,
        )
    }

    // ---------- Old bills ----------

    @Test fun paidBillsKeepPlaceLabelAndCanBeReprinted() {
        sell(table("Nhà gỗ", 2), "Ốc bươu")
        val bill = engine.paidBills(0, Long.MAX_VALUE).single()
        assertEquals("Nhà gỗ · Bàn 2", bill.placeLabel)
        assertEquals(150_000L, bill.total)

        printer.printed.clear()
        assertNull(engine.reprintPaid(bill.id))
        val again = printer.printed.single()
        assertEquals(ReceiptKind.BILL, again.kind)
        assertEquals("Nhà gỗ · Bàn 2", again.placeLabel)
        assertEquals(150_000L, again.total)
        assertEquals(listOf(ReceiptItem("Ốc bươu", 1, 150_000, 150_000)), again.items)
    }

    @Test fun billsPaidBeforeLabelsExistedFallBackToCurrentTableName() {
        val t = table("Sân trước", 5)
        store.saveOrder(Order("old", t, 1_000, OrderStatus.PAID, closedAt = 2_000, total = 30_000, cashGiven = 30_000))
        assertEquals("Sân trước · Bàn 5", engine.paidBills(0, 10_000).single().placeLabel)
    }

    @Test fun reprintedRoomBillUsesChargedRoomFeeNotTodaysClock() {
        val vip = table("VIP", 2)
        engine.openRoom(vip)
        clock.ms += 60 * 60_000L
        engine.checkout(vip, 100_000)
        clock.ms += 10 * 60 * 60_000L
        engine.reprintPaid(engine.paidBills(0, Long.MAX_VALUE).single().id)
        assertEquals(100_000L, printer.printed.last().roomFee)
        assertEquals(100_000L, printer.printed.last().total)
    }

    // ---------- PIN ----------

    @Test fun pinGuardsSettingsOnceSet() {
        assertFalse(engine.hasPin())
        assertTrue(engine.checkPin("anything"))
        engine.setPin("2468")
        assertTrue(engine.hasPin())
        assertFalse(engine.checkPin("1234"))
        assertTrue(engine.checkPin("2468"))
        assertTrue(PosEngine(store, clock, printer, DefaultData.SHOP).checkPin("2468"))
        assertFalse(store.settings.values.any { it.contains("2468") })
        engine.setPin(null)
        assertFalse(engine.hasPin())
    }

    // ---------- Zones & tables ----------

    @Test fun renameZone() {
        val z = engine.zones().first { it.name == "Sân sau" }
        engine.renameZone(z.id, "Vườn")
        assertEquals("Vườn", engine.zones().first { it.id == z.id }.name)
    }

    @Test fun onlyEmptyTablesAndZonesCanBeDeleted() {
        val busy = table("Sân trước", 1)
        engine.addItem(busy, item("Rau xào"))
        assertFalse(engine.canDeleteTable(busy))
        assertFalse(engine.canDeleteZone(engine.zones().first { it.name == "Sân trước" }.id))

        val free = table("Sân trước", 8)
        assertTrue(engine.canDeleteTable(free))
        engine.deleteTable(free)
        assertEquals((1..7).toList(), engine.tables(engine.zones().first { it.name == "Sân trước" }.id).map { it.table.number })

        val nhaGo = engine.zones().first { it.name == "Nhà gỗ" }.id
        engine.deleteZone(nhaGo)
        assertEquals(listOf("Sân trước", "Sân sau", "VIP"), engine.zones().map { it.name })
        assertEquals(listOf("Sân trước", "Sân sau", "VIP"), PosEngine(store, clock, printer, DefaultData.SHOP).zones().map { it.name })
    }

    @Test fun roomRateCanBeChanged() {
        val vip = table("VIP", 1)
        engine.setRoomRate(vip, 120_000)
        engine.openRoom(vip)
        assertEquals(120_000L, engine.order(vip)!!.roomFee)
    }
}

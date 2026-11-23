package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class PosEngineTest {
    private lateinit var store: InMemoryStore
    private lateinit var clock: FakeClock
    private lateinit var printer: FakePrinter
    private lateinit var engine: PosEngine

    @Before fun setUp() {
        store = InMemoryStore()
        clock = FakeClock()
        printer = FakePrinter()
        engine = newEngine()
    }

    private fun newEngine() = PosEngine(store, clock, printer, DefaultData.SHOP)

    private fun zone(name: String) = engine.zones().first { it.name == name }
    private fun table(zone: String, n: Int) = engine.tables(zone(zone).id).first { it.table.number == n }.table.id
    private fun item(name: String) =
        engine.categories().flatMap { engine.menu(it.id) }.first { it.name == name }.id
    private fun line(tableId: String, name: String) = engine.order(tableId)!!.order.lines.first { it.name == name }

    // ---------- Seed ----------

    @Test fun firstRunSeedsZonesTablesAndMenu() {
        assertEquals(listOf("Sân trước", "Nhà gỗ", "Sân sau", "VIP"), engine.zones().map { it.name })
        assertEquals(8, engine.tables(zone("Nhà gỗ").id).size)
        assertEquals(3, engine.tables(zone("VIP").id).size)
        assertTrue(zone("VIP").timed)
        assertEquals(25_000L, engine.categories().flatMap { engine.menu(it.id) }.first { it.name == "Bia Heineken" }.price)
    }

    @Test fun seedRunsOnlyOnce() {
        newEngine()
        assertEquals(4, newEngine().zones().size)
    }

    @Test fun openOrdersSurviveRestart() {
        val t = table("Sân trước", 1)
        engine.addItem(t, item("Bia Tiger"))
        val reloaded = newEngine()
        assertEquals(20_000L, reloaded.order(t)!!.total)
        assertTrue(reloaded.table(t).occupied)
    }

    // ---------- Ordering ----------

    @Test fun addingSameItemIncreasesQuantity() {
        val t = table("Sân trước", 1)
        engine.addItem(t, item("Bia Tiger"))
        engine.addItem(t, item("Bia Tiger"))
        val lines = engine.order(t)!!.order.lines
        assertEquals(1, lines.size)
        assertEquals(2, lines[0].quantity)
        assertEquals(40_000L, engine.order(t)!!.total)
    }

    @Test fun reducingLastItemToZeroFreesNormalTable() {
        val t = table("Sân trước", 2)
        engine.addItem(t, item("Rau xào"))
        engine.setQuantity(t, line(t, "Rau xào").id, 0)
        assertNull(engine.order(t))
        assertFalse(engine.table(t).occupied)
    }

    @Test fun removeLineKeepsOtherLines() {
        val t = table("Sân trước", 2)
        engine.addItem(t, item("Rau xào"))
        engine.addItem(t, item("Hến xào"))
        engine.removeLine(t, line(t, "Rau xào").id)
        assertEquals(listOf("Hến xào"), engine.order(t)!!.order.lines.map { it.name })
    }

    @Test fun changingLinePriceDoesNotChangeMenu() {
        val t = table("Nhà gỗ", 1)
        engine.addItem(t, item("Tôm sông"))
        engine.setLinePrice(t, line(t, "Tôm sông").id, 150_000)
        assertEquals(150_000L, engine.order(t)!!.total)
        assertEquals(100_000L, engine.categories().flatMap { engine.menu(it.id) }.first { it.name == "Tôm sông" }.price)
        val other = table("Nhà gỗ", 2)
        engine.addItem(other, item("Tôm sông"))
        assertEquals(100_000L, engine.order(other)!!.total)
    }

    @Test fun orderKeepsNameAndPriceSnapshot() {
        val t = table("Nhà gỗ", 1)
        engine.addItem(t, item("Rau xào"))
        assertEquals("Rau xào", line(t, "Rau xào").name)
        assertEquals(30_000L, line(t, "Rau xào").unitPrice)
    }

    // ---------- Price required ----------

    @Test fun itemWithoutPriceCanBeAddedButBlocksCheckout() {
        val t = table("Sân sau", 1)
        engine.addItem(t, item("Gà"))
        engine.addItem(t, item("Bia Tiger"))
        val gaId = line(t, "Gà").id
        assertTrue(engine.table(t).hasMissingPrices)
        assertEquals(listOf(gaId), engine.order(t)!!.missingPriceLineIds)

        assertEquals(CheckoutResult.MissingPrices(listOf(gaId)), engine.checkout(t, 1_000_000))
        assertTrue(engine.table(t).occupied)

        engine.setLinePrice(t, gaId, 300_000)
        val r = engine.checkout(t, 400_000) as CheckoutResult.Paid
        assertEquals(320_000L, r.receipt.total)
        assertEquals(80_000L, r.receipt.change)
    }

    // ---------- Room fee ----------

    @Test fun roomFeeRules() {
        val rate = 100_000L
        assertEquals(100_000L, RoomFee.fee(rate, 0))                       // minimum 1 hour
        assertEquals(100_000L, RoomFee.fee(rate, 59 * 60_000L))
        assertEquals(100_000L, RoomFee.fee(rate, 60 * 60_000L))            // exactly 1h: no extra block
        assertEquals(125_000L, RoomFee.fee(rate, 61 * 60_000L))            // 1h01 -> 1h15
        assertEquals(125_000L, RoomFee.fee(rate, 75 * 60_000L))
        assertEquals(150_000L, RoomFee.fee(rate, 75 * 60_000L + 1000))     // 1h15m01s -> 1h30
        assertEquals("2 giờ 15 phút", RoomFee.billedDurationText((2 * 60 + 10) * 60_000L))
        assertEquals("1 giờ", RoomFee.billedDurationText(10 * 60_000L))
    }

    @Test fun openingRoomStartsClockAndBillsTime() {
        val vip2 = table("VIP", 2)
        engine.openRoom(vip2)
        assertTrue(engine.table(vip2).occupied)
        clock.advanceMinutes(130)
        engine.addItem(vip2, item("Bia Heineken"))
        val ov = engine.order(vip2)!!
        assertEquals(225_000L, ov.roomFee)
        assertEquals("Tiền phòng VIP 2 – 2 giờ 15 phút", ov.roomFeeLabel)
        assertEquals(250_000L, ov.total)
        assertEquals(130 * 60_000L, engine.table(vip2).elapsedMs)
    }

    @Test fun addingItemToClosedRoomOpensIt() {
        val vip1 = table("VIP", 1)
        engine.addItem(vip1, item("Bia Tiger"))
        assertEquals(120_000L, engine.order(vip1)!!.total)
    }

    @Test fun roomFeeOverrideWins() {
        val vip1 = table("VIP", 1)
        engine.openRoom(vip1)
        clock.advanceMinutes(200)
        engine.setRoomFeeOverride(vip1, 150_000)
        assertEquals(150_000L, engine.order(vip1)!!.total)
        engine.setRoomFeeOverride(vip1, null)
        assertEquals(350_000L, engine.order(vip1)!!.total)
    }

    @Test fun emptyRoomStaysOpenUntilCancelled() {
        val vip3 = table("VIP", 3)
        engine.addItem(vip3, item("Bia Tiger"))
        engine.removeLine(vip3, line(vip3, "Bia Tiger").id)
        assertTrue(engine.table(vip3).occupied)
        engine.cancelEmpty(vip3)
        assertFalse(engine.table(vip3).occupied)
    }

    @Test fun roomCanBePaidWithNoItems() {
        val vip3 = table("VIP", 3)
        engine.openRoom(vip3)
        clock.advanceMinutes(30)
        val r = engine.checkout(vip3, 100_000) as CheckoutResult.Paid
        assertEquals(100_000L, r.receipt.total)
    }

    // ---------- Pre-bill & checkout ----------

    @Test fun preBillPrintsAndKeepsTableOpen() {
        val t = table("Sân trước", 3)
        engine.addItem(t, item("Hến xào"))
        assertNull(engine.printPreBill(t))
        assertEquals(ReceiptKind.PRE_BILL, printer.printed.single().kind)
        assertTrue(engine.table(t).occupied)
    }

    @Test fun checkoutPrintsBillAndFreesTable() {
        val t = table("Sân trước", 4)
        engine.addItem(t, item("Cá chình nướng"))
        engine.addItem(t, item("Bia Heineken"))
        engine.addItem(t, item("Bia Heineken"))
        val r = engine.checkout(t, 600_000) as CheckoutResult.Paid
        assertNull(r.printError)
        assertEquals(550_000L, r.receipt.total)
        assertEquals(50_000L, r.receipt.change)
        assertEquals(ReceiptKind.BILL, printer.printed.single().kind)
        assertFalse(engine.table(t).occupied)
        val saved = store.orders.values.single { it.status == OrderStatus.PAID }
        assertEquals(550_000L, saved.total)
        assertEquals(600_000L, saved.cashGiven)
    }

    @Test fun checkoutWithoutCashPrintsBillWithoutChangeLines() {
        val t = table("Sân trước", 6)
        engine.addItem(t, item("Hến xào"))
        engine.addItem(t, item("Bia Tiger"))
        val r = engine.checkout(t) as CheckoutResult.Paid
        assertEquals(70_000L, r.receipt.total)
        assertNull(r.receipt.cashGiven)
        assertFalse(engine.table(t).occupied)
        val rows = ReceiptLayout.rows(printer.printed.single())
        assertFalse(rows.any { it is PrintRow.Amount && (it.label == "Khách đưa" || it.label == "Tiền thối") })
        assertTrue(PrintRow.Amount("TỔNG CỘNG", "70.000đ", emphasized = true) in rows)
    }

    @Test fun insufficientCashIsRejected() {
        val t = table("Sân trước", 5)
        engine.addItem(t, item("Ốc bươu"))
        assertEquals(CheckoutResult.InsufficientCash(150_000), engine.checkout(t, 100_000))
        assertTrue(engine.table(t).occupied)
    }

    @Test fun emptyNormalTableHasNothingToPay() {
        assertEquals(CheckoutResult.NothingToPay, engine.checkout(table("Sân trước", 6), 0))
    }

    @Test fun printerFailureStillClosesTableAndAllowsReprint() {
        val t = table("Sân trước", 7)
        engine.addItem(t, item("Dế"))
        printer.failWith = "Máy in hết giấy"
        val r = engine.checkout(t, 100_000) as CheckoutResult.Paid
        assertEquals("Máy in hết giấy", r.printError)
        assertFalse(engine.table(t).occupied)
        printer.failWith = null
        assertNull(engine.reprint(r.receipt))
        assertEquals(1, printer.printed.size)
    }

    // ---------- Zones & tables ----------

    @Test fun addTableNumbersSequentially() {
        val z = zone("Sân sau")
        assertEquals(9, engine.addTable(z.id).number)
        assertEquals(10, engine.addTable(z.id).number)
    }

    @Test fun addTimedZoneWithRoom() {
        val z = engine.addZone("Phòng lạnh", timed = true)
        val room = engine.addTable(z.id, hourlyRate = 80_000)
        assertEquals(1, room.number)
        engine.openRoom(room.id)
        assertEquals(80_000L, engine.order(room.id)!!.total)
        assertEquals("Phòng lạnh", engine.zones().last().name)
    }

    @Test fun revisionChangesOnEveryCommand() {
        val before = engine.revision.value
        engine.addItem(table("Sân trước", 1), item("Rau xào"))
        assertTrue(engine.revision.value > before)
    }

    // ---------- Receipt layout ----------

    @Test fun moneyFormat() {
        assertEquals("150.000đ", formatVnd(150_000))
        assertEquals("1.250.000đ", formatVnd(1_250_000))
        assertEquals("0đ", formatVnd(0))
        assertEquals("500đ", formatVnd(500))
    }

    @Test fun billLayoutHasHeaderItemsRoomFeeAndTotals() {
        val vip1 = table("VIP", 1)
        engine.openRoom(vip1)
        clock.advanceMinutes(90)
        engine.addItem(vip1, item("Bia Tiger"))
        engine.addItem(vip1, item("Bia Tiger"))
        val r = (engine.checkout(vip1, 200_000) as CheckoutResult.Paid).receipt
        val rows = ReceiptLayout.rows(r, TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))

        assertEquals(PrintRow.Title("MƯỜI KIP"), rows[0])
        assertTrue(PrintRow.Center("ĐT: 0989824694") in rows)
        assertTrue(PrintRow.Title("HÓA ĐƠN") in rows)
        assertTrue(PrintRow.Text("Phòng VIP 1") in rows)
        assertTrue(PrintRow.Item("Bia Tiger", "2", "40.000đ") in rows)
        assertTrue(PrintRow.Item("Tiền phòng VIP 1 – 1 giờ 30 phút", "", "150.000đ") in rows)
        assertTrue(PrintRow.Amount("TỔNG CỘNG", "190.000đ", emphasized = true) in rows)
        assertTrue(PrintRow.Amount("Khách đưa", "200.000đ") in rows)
        assertTrue(PrintRow.Amount("Tiền thối", "10.000đ") in rows)
    }

    @Test fun preBillLayoutSaysTamTinhAndHasNoChange() {
        val t = table("Nhà gỗ", 3)
        engine.addItem(t, item("Rau xào"))
        engine.printPreBill(t)
        val rows = ReceiptLayout.rows(printer.printed.single())
        assertTrue(PrintRow.Title("TẠM TÍNH") in rows)
        assertTrue(PrintRow.Text("Nhà gỗ · Bàn 3") in rows)
        assertFalse(rows.any { it is PrintRow.Amount && it.label == "Tiền thối" })
    }
}

package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ba ba is sold by the kilo: the cashier types a decimal weight; other items take whole quantities. */
class WeighedItemTest {
    private val store = InMemoryStore()
    private val clock = FakeClock()
    private val printer = FakePrinter()
    private val engine = PosEngine(store, clock, printer, DefaultData.SHOP)

    private fun table(n: Int) = engine.tables(engine.zones().first { it.name == "Nhà gỗ" }.id).first { it.table.number == n }.table.id
    private fun item(name: String) = engine.categories().flatMap { engine.menu(it.id) }.first { it.name == name }
    private fun line(t: String, name: String) = engine.order(t)!!.order.lines.first { it.name == name }

    @Test fun baBaIsSoldByWeightInTheDefaultMenu() {
        assertTrue(item("Ba ba").soldByWeight)
        assertEquals(700_000L, item("Ba ba").price)
    }

    @Test fun weighedItemStartsAtOneKiloAndChargesByWeight() {
        val t = table(1)
        engine.addItem(t, item("Ba ba").id)
        assertEquals(1.0, line(t, "Ba ba").weightKg!!, 0.0)
        assertEquals(700_000L, engine.order(t)!!.total)

        engine.setLineWeight(t, line(t, "Ba ba").id, 1.25)

        assertEquals(875_000L, engine.order(t)!!.total)
    }

    @Test fun weightedAmountRoundsToWholeDong() {
        val t = table(2)
        engine.addItem(t, item("Ba ba").id)
        engine.setLineWeight(t, line(t, "Ba ba").id, 1.333)
        assertEquals(933_100L, engine.order(t)!!.total) // 700.000 × 1,333
    }

    @Test fun tappingWeighedItemAgainDoesNotAddAnotherKilo() {
        val t = table(3)
        engine.addItem(t, item("Ba ba").id)
        engine.addItem(t, item("Ba ba").id)
        assertEquals(1, engine.order(t)!!.order.lines.size)
        assertEquals(1.0, line(t, "Ba ba").weightKg!!, 0.0)
    }

    @Test fun typedQuantityForNormalItem() {
        val t = table(4)
        engine.addItem(t, item("Bia Tiger lớn").id)
        engine.setQuantity(t, line(t, "Bia Tiger lớn").id, 12)
        assertEquals(240_000L, engine.order(t)!!.total)
    }

    @Test fun receiptShowsWeightInsteadOfCount() {
        val t = table(5)
        engine.addItem(t, item("Ba ba").id)
        engine.setLineWeight(t, line(t, "Ba ba").id, 1.25)
        val receipt = (engine.checkout(t) as CheckoutResult.Paid).receipt
        assertTrue(PrintRow.Item("Ba ba", "1,25kg", "875.000đ") in ReceiptLayout.rows(receipt))
    }

    @Test fun weightSurvivesRestart() {
        val t = table(6)
        engine.addItem(t, item("Ba ba").id)
        engine.setLineWeight(t, line(t, "Ba ba").id, 0.8)
        assertEquals(560_000L, PosEngine(store, clock, printer, DefaultData.SHOP).order(t)!!.total)
    }

    @Test fun menuItemCanBeMarkedSoldByWeight() {
        val lau = engine.categories().first { it.name == "Cá" }
        val ca = engine.addMenuItem(lau.id, "Cá lăng", 400_000, soldByWeight = true)
        assertTrue(engine.menu(lau.id).first { it.id == ca.id }.soldByWeight)
        engine.updateMenuItem(ca.id, "Cá lăng", 400_000, lau.id, soldByWeight = false)
        assertEquals(false, engine.menu(lau.id).first { it.id == ca.id }.soldByWeight)
    }

    @Test fun mergeKeepsWeighedLinesSeparate() {
        val a = table(7)
        val b = table(8)
        engine.addItem(a, item("Ba ba").id)
        engine.setLineWeight(a, line(a, "Ba ba").id, 0.5)
        engine.addItem(b, item("Ba ba").id)
        engine.mergeInto(a, b)
        assertEquals(listOf(1.0, 0.5), engine.order(b)!!.order.lines.sortedBy { it.seq }.map { it.weightKg })
        assertEquals(1_050_000L, engine.order(b)!!.total)
    }
}

package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuEditingTest {
    private val store = InMemoryStore()
    private val clock = FakeClock()
    private val engine = PosEngine(store, clock, FakePrinter(), DefaultData.SHOP)

    private fun category(name: String) = engine.categories().first { it.name == name }

    @Test fun addedItemAppearsAtEndOfItsCategory() {
        val drinks = category("Đồ uống")
        val before = engine.menu(drinks.id).size
        engine.addMenuItem(drinks.id, "Nước ngọt", 15_000)
        assertEquals(before + 1, engine.menu(drinks.id).size)
        assertEquals("Nước ngọt" to 15_000L, engine.menu(drinks.id).last().let { it.name to it.price })
    }

    @Test fun hiddenItemLeavesOrderMenuButStaysInEditor() {
        val drinks = category("Đồ uống")
        val tiger = engine.menu(drinks.id).first { it.name == "Bia Tiger lớn" }
        engine.setMenuItemActive(tiger.id, false)
        assertFalse("Bia Tiger lớn" in engine.menu(drinks.id).map { it.name })
        assertEquals(false, engine.menuForEditing(drinks.id).first { it.name == "Bia Tiger lớn" }.active)
        engine.setMenuItemActive(tiger.id, true)
        assertTrue("Bia Tiger lớn" in engine.menu(drinks.id).map { it.name })
    }

    @Test fun editingItemAffectsNewOrdersOnly() {
        val drinks = category("Đồ uống")
        val tiger = engine.menu(drinks.id).first { it.name == "Bia Tiger lớn" }
        val busy = engine.tables(engine.zones().first().id).first().table.id
        engine.addItem(busy, tiger.id)

        engine.updateMenuItem(tiger.id, name = "Bia Tiger bạc", price = 22_000, categoryId = drinks.id)

        assertEquals("Bia Tiger lớn" to 20_000L, engine.order(busy)!!.order.lines.single().let { it.name to it.unitPrice })
        val fresh = engine.tables(engine.zones().first().id)[1].table.id
        engine.addItem(fresh, tiger.id)
        assertEquals("Bia Tiger bạc" to 22_000L, engine.order(fresh)!!.order.lines.single().let { it.name to it.unitPrice })
    }

    @Test fun movingItemToAnotherCategoryPutsItLast() {
        val drinks = category("Đồ uống")
        val lau = category("Cá")
        val tiger = engine.menu(drinks.id).first { it.name == "Bia Tiger lớn" }
        engine.updateMenuItem(tiger.id, name = tiger.name, price = tiger.price, categoryId = lau.id)
        assertFalse("Bia Tiger lớn" in engine.menu(drinks.id).map { it.name })
        assertEquals("Bia Tiger lớn", engine.menu(lau.id).last().name)
    }

    @Test fun addedCategoryComesLast() {
        val c = engine.addCategory("Cơm – Mì")
        assertEquals("Cơm – Mì", engine.categories().last().name)
        engine.addMenuItem(c.id, "Cơm chiên", 40_000)
        assertEquals(listOf("Cơm chiên"), engine.menu(c.id).map { it.name })
    }

    @Test fun menuEditsSurviveRestart() {
        val drinks = category("Đồ uống")
        engine.addMenuItem(drinks.id, "Nước suối", 10_000)
        val again = PosEngine(store, clock, FakePrinter(), DefaultData.SHOP)
        assertEquals("Nước suối", again.menu(drinks.id).last().name)
    }
}

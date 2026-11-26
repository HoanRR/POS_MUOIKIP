package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The menu is regrouped into four groups and extended with the owner's list of 2026-11-23. */
class MenuUpgradeTest {
    private val clock = FakeClock()

    private fun engineOn(store: InMemoryStore) = PosEngine(store, clock, FakePrinter(), DefaultData.SHOP)
    private fun PosEngine.group(name: String) = menu(categories().first { it.name == name }.id).map { it.name }
    private fun PosEngine.item(name: String) =
        categories().flatMap { menuForEditing(it.id) }.first { it.name == name }

    /** A store as the T1 has it: the first winter menu, one price edited by the owner. */
    private fun oldStore(): InMemoryStore {
        val s = InMemoryStore()
        s.saveZone(Zone("z1", "Sân trước", false, 1))
        s.saveTable(Table("t1", "z1", 1, null))
        listOf("c1" to "Xào – Chiên", "c2" to "Cá – Nướng", "c3" to "Lẩu", "c4" to "Đồ uống")
            .forEachIndexed { i, (id, n) -> s.saveCategory(Category(id, n, i + 1)) }
        var n = 0
        fun add(cat: String, name: String, price: Long?, byWeight: Boolean = false) =
            s.saveMenuItem(MenuItem("m${++n}", cat, name, price, n, soldByWeight = byWeight))
        add("c1", "Rau xào", 35_000) // owner raised it from 30.000
        add("c1", "Cá rầm suối chiên giòn", 80_000)
        add("c1", "Ốc bươu", 150_000)
        add("c2", "Cá niên", 250_000)
        add("c2", "Gà", null)
        add("c3", "Lẩu cá leo um chuối", 250_000)
        add("c3", "Lẩu gà lá dang", null)
        add("c3", "Ba ba", 700_000, byWeight = true)
        add("c4", "Bia Heineken", 25_000)
        add("c4", "Bia Tiger", 20_000)
        return s
    }

    @Test fun regroupsIntoFourGroupsInOrder() {
        val e = engineOn(oldStore())
        assertEquals(listOf("Đồ ăn", "Cá", "Linh tinh", "Đồ uống"), e.categories().map { it.name })
    }

    @Test fun fishDishesGoToCa() {
        val e = engineOn(oldStore())
        assertTrue(e.group("Cá").containsAll(listOf("Cá rầm suối chiên giòn", "Cá niên", "Lẩu cá leo um chuối")))
        assertTrue(e.group("Đồ ăn").containsAll(listOf("Rau xào", "Ốc bươu", "Gà", "Lẩu gà lá dang", "Ba ba", "Ốc đá", "Cơm chiên")))
    }

    @Test fun keepsOwnerPricesOfExistingItems() {
        assertEquals(35_000L, engineOn(oldStore()).item("Rau xào").price)
    }

    @Test fun addsNewItemsWithListedPrices() {
        val e = engineOn(oldStore())
        assertEquals(listOf("Bánh tráng", "Trái cây", "Cơm trắng", "Đậu khuôn", "Đậu phụng"), e.group("Linh tinh"))
        assertEquals(5_000L, e.item("Bánh tráng").price)
        assertNull(e.item("Cơm trắng").price)     // "cho nhập giá"
        assertNull(e.item("Thịt xào").price)      // "thời giá"
        assertEquals(1_000_000L, e.item("Thịt hông").price)
        assertTrue(e.item("Thịt hông").soldByWeight)
        assertEquals(144_000L, e.item("Bia Larue").price)
    }

    @Test fun drinksIncludeColdTowelAndReplaceOldBeers() {
        val e = engineOn(oldStore())
        assertEquals(
            listOf(
                "Bia Tiger nhỏ", "Bia Tiger lớn", "Bia Tiger chai", "Heineken nhỏ", "Heineken lớn", "Heineken chai",
                "Bia Larue", "Bia Huda", "Bia Đức", "Nước suối", "Nước khoáng", "Coca", "Khăn lạnh",
            ),
            e.group("Đồ uống"),
        )
        assertFalse(e.item("Bia Tiger").active)
        assertFalse(e.item("Bia Heineken").active)
    }

    @Test fun upgradeRunsOnceAndKeepsLaterOwnerEdits() {
        val store = oldStore()
        val e = engineOn(store)
        e.setMenuItemActive(e.item("Bia Tiger").id, true)   // owner brings the old one back
        val again = engineOn(store)
        assertTrue(again.item("Bia Tiger").active)
        assertEquals(1, again.categories().count { it.name == "Cá" })
    }

    @Test fun freshInstallGetsTheNewMenuDirectly() {
        val e = engineOn(InMemoryStore())
        assertEquals(listOf("Đồ ăn", "Cá", "Linh tinh", "Đồ uống"), e.categories().map { it.name })
        assertEquals(30_000L, e.item("Rau xào").price)
        assertTrue(e.item("Ba ba").soldByWeight)
        assertTrue("Khăn lạnh" in e.group("Đồ uống"))
    }
}

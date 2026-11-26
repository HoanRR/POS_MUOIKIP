package vn.muoikip.pos.core

/** First-run data and the built-in menu catalog. */
object DefaultData {
    val SHOP = ShopInfo(
        name = "MƯỜI KIP",
        address = "Lam Phụng, Đại Đồng, Đại Lộc, Quảng Nam",
        phone = "0989824694",
    )

    /** Placeholder room rate; the owner corrects it later. */
    const val DEFAULT_ROOM_RATE: Vnd = 100_000

    /** One catalog entry. [price] null = "nhập giá khi bán" / thời giá. */
    private data class Dish(val name: String, val price: Vnd?, val byWeight: Boolean = false)

    private fun d(name: String, price: Long?, byWeight: Boolean = false) = Dish(name, price, byWeight)

    /**
     * Menu revision 2 (owner's list of 2026-11-23): four groups. Sources: Canva "Menu Mùa Đông"
     * plus the owner's additions.
     */
    private val CATALOG: List<Pair<String, List<Dish>>> = listOf(
        "Đồ ăn" to listOf(
            d("Rau xào", 30_000), d("Hến xào", 50_000), d("Tôm sông", 100_000), d("Dế", 100_000),
            d("Sùng", 100_000), d("Ve ve xào", 100_000), d("Nhái xào sả ớt", 100_000), d("Búp chuối trộn", 100_000),
            d("Ếch", 100_000), d("Ếch đồng", 150_000), d("Ốc bươu", 150_000), d("Ốc đá", 100_000),
            d("Gà", null), d("Lẩu gà lá dang", null), d("Ba ba", 700_000, byWeight = true),
            d("Thịt hông", 1_000_000, byWeight = true), d("Thịt xào", null), d("Trứng hấp", 50_000),
            d("Mì xào trứng", 70_000), d("Cơm chiên", 70_000), d("Khoai tây chiên", 40_000), d("Bún xì dầu", 15_000),
        ),
        "Cá" to listOf(
            d("Cá rầm suối chiên giòn", 80_000), d("Cá niên", 250_000), d("Cá leo nướng", 250_000),
            d("Cá chình nướng", 500_000), d("Lẩu cá leo um chuối", 250_000), d("Lẩu cá chuỗi ngọc", 250_000),
            d("Lẩu cá ngạnh nấu chua", 250_000), d("Lẩu cá chình um chuối", 500_000),
        ),
        "Linh tinh" to listOf(
            d("Bánh tráng", 5_000), d("Trái cây", 30_000), d("Cơm trắng", null),
            d("Đậu khuôn", 30_000), d("Đậu phụng", 10_000),
        ),
        "Đồ uống" to listOf(
            d("Bia Tiger nhỏ", 16_000), d("Bia Tiger lớn", 20_000), d("Bia Tiger chai", 20_000),
            d("Heineken nhỏ", 20_000), d("Heineken lớn", 25_000), d("Heineken chai", 25_000),
            d("Bia Larue", 144_000), d("Bia Huda", 14_000), d("Bia Đức", 60_000),
            d("Nước suối", 7_000), d("Nước khoáng", 10_000), d("Coca", 10_000), d("Khăn lạnh", 5_000),
        ),
    )

    /** Revision 1 group names that become revision 2 groups. */
    private val CATEGORY_ALIASES = mapOf("Xào – Chiên" to "Đồ ăn", "Cá – Nướng" to "Cá", "Lẩu" to "Linh tinh")

    /** Replaced by sized variants; hidden (not deleted) so old bills and reports stay intact. */
    private val RETIRED = setOf("Bia Tiger", "Bia Heineken")

    private const val MENU_REV = 2
    private const val KEY_MENU_REV = "menu.rev"

    fun seed(store: PosStore, newId: () -> String) {
        listOf("Sân trước", "Nhà gỗ", "Sân sau").forEachIndexed { i, name ->
            val z = Zone(newId(), name, timed = false, sort = i + 1)
            store.saveZone(z)
            (1..8).forEach { n -> store.saveTable(Table(newId(), z.id, n, null)) }
        }
        val vip = Zone(newId(), "VIP", timed = true, sort = 4)
        store.saveZone(vip)
        (1..3).forEach { n -> store.saveTable(Table(newId(), vip.id, n, DEFAULT_ROOM_RATE)) }

        CATALOG.forEachIndexed { ci, (catName, dishes) ->
            val c = Category(newId(), catName, ci + 1)
            store.saveCategory(c)
            dishes.forEachIndexed { ii, dish ->
                store.saveMenuItem(MenuItem(newId(), c.id, dish.name, dish.price, ii + 1, soldByWeight = dish.byWeight))
            }
        }
        store.saveSetting(KEY_MENU_REV, MENU_REV.toString())
    }

    /**
     * Brings an older menu to the current catalog once: renames/reorders groups, moves dishes,
     * adds missing ones, hides retired ones. Existing prices and owner-added dishes are kept.
     */
    fun upgradeMenu(store: PosStore, newId: () -> String) {
        if ((store.loadSettings()[KEY_MENU_REV]?.toIntOrNull() ?: 1) >= MENU_REV) return
        val snap = store.load()

        val categoryIds = CATALOG.mapIndexed { i, (name, _) ->
            val existing = snap.categories.firstOrNull { it.name == name }
                ?: snap.categories.firstOrNull { CATEGORY_ALIASES[it.name] == name }
            val c = existing?.copy(name = name, sort = i + 1) ?: Category(newId(), name, i + 1)
            store.saveCategory(c)
            name to c.id
        }.toMap()
        // Groups the owner created keep existing, after the four standard ones.
        snap.categories.filter { it.id !in categoryIds.values }.forEachIndexed { i, c ->
            store.saveCategory(c.copy(sort = CATALOG.size + 1 + i))
        }

        val byName = snap.menuItems.associateBy { it.name }
        val placed = mutableSetOf<String>()
        CATALOG.forEach { (catName, dishes) ->
            dishes.forEachIndexed { j, dish ->
                val old = byName[dish.name]
                val item = old?.copy(categoryId = categoryIds.getValue(catName), sort = j + 1,
                    soldByWeight = old.soldByWeight || dish.byWeight)
                    ?: MenuItem(newId(), categoryIds.getValue(catName), dish.name, dish.price, j + 1, soldByWeight = dish.byWeight)
                store.saveMenuItem(item)
                placed += item.id
            }
        }
        snap.menuItems.filter { it.id !in placed }.forEach { item ->
            store.saveMenuItem(
                if (item.name in RETIRED) item.copy(active = false)
                else item.copy(sort = 100 + item.sort) // owner's own dishes: after the catalog ones
            )
        }
        store.saveSetting(KEY_MENU_REV, MENU_REV.toString())
    }
}

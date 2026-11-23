package vn.muoikip.pos.core

/** First-run data: zones, tables and the winter menu (source: Canva "menu – Menu Mùa Đông"). */
object DefaultData {
    val SHOP = ShopInfo(
        name = "MƯỜI KIP",
        address = "Lam Phụng, Đại Đồng, Đại Lộc, Quảng Nam",
        phone = "0989824694",
    )

    /** Placeholder room rate; the owner corrects it later. */
    const val DEFAULT_ROOM_RATE: Vnd = 100_000

    private val MENU: List<Pair<String, List<Pair<String, Vnd?>>>> = listOf(
        "Xào – Chiên" to listOf(
            "Rau xào" to 30_000L,
            "Hến xào" to 50_000L,
            "Cá rầm suối chiên giòn" to 80_000L,
            "Tôm sông" to 100_000L,
            "Dế" to 100_000L,
            "Sùng" to 100_000L,
            "Ve ve xào" to 100_000L,
            "Nhái xào sả ớt" to 100_000L,
            "Búp chuối trộn" to 100_000L,
            "Ếch" to 100_000L,
            "Ếch đồng" to 150_000L,
            "Ốc bươu" to 150_000L,
        ),
        "Cá – Nướng" to listOf(
            "Cá niên" to 250_000L,
            "Cá leo nướng" to 250_000L,
            "Cá chình nướng" to 500_000L,
            "Gà" to null,
        ),
        "Lẩu" to listOf(
            "Lẩu cá leo um chuối" to 250_000L,
            "Lẩu cá chuỗi ngọc" to 250_000L,
            "Lẩu cá ngạnh nấu chua" to 250_000L,
            "Lẩu cá chình um chuối" to 500_000L,
            "Lẩu gà lá dang" to null,
            "Ba ba (1kg)" to 700_000L,
        ),
        "Đồ uống" to listOf(
            "Bia Heineken" to 25_000L,
            "Bia Tiger" to 20_000L,
        ),
    )

    fun seed(store: PosStore, newId: () -> String) {
        listOf("Sân trước", "Nhà gỗ", "Sân sau").forEachIndexed { i, name ->
            val z = Zone(newId(), name, timed = false, sort = i + 1)
            store.saveZone(z)
            (1..8).forEach { n -> store.saveTable(Table(newId(), z.id, n, null)) }
        }
        val vip = Zone(newId(), "VIP", timed = true, sort = 4)
        store.saveZone(vip)
        (1..3).forEach { n -> store.saveTable(Table(newId(), vip.id, n, DEFAULT_ROOM_RATE)) }

        MENU.forEachIndexed { ci, (catName, items) ->
            val c = Category(newId(), catName, ci + 1)
            store.saveCategory(c)
            items.forEachIndexed { ii, (name, price) ->
                store.saveMenuItem(MenuItem(newId(), c.id, name, price, ii + 1))
            }
        }
    }
}

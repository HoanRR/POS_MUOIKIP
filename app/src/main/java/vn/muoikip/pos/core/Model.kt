package vn.muoikip.pos.core

/** Money is always an integer number of đồng. */
typealias Vnd = Long

data class ShopInfo(val name: String, val address: String, val phone: String)

/** A zone (Khu). [timed] zones bill each table (room) by the hour. */
data class Zone(val id: String, val name: String, val timed: Boolean, val sort: Int)

/** A table or, in a timed zone, a room. [hourlyRate] is set only for rooms in timed zones. */
data class Table(val id: String, val zoneId: String, val number: Int, val hourlyRate: Vnd?)

data class Category(val id: String, val name: String, val sort: Int)

/** [price] null means "giá nhập khi bán": the price must be entered before payment. */
data class MenuItem(
    val id: String,
    val categoryId: String,
    val name: String,
    val price: Vnd?,
    val sort: Int,
    val active: Boolean = true,
    /** Priced per kilo (e.g. Ba ba): the line carries a decimal weight instead of a count. */
    val soldByWeight: Boolean = false,
)

enum class OrderStatus { OPEN, PAID, CANCELLED }

/** Name and price are copied from the menu when ordered so later menu edits never change an order. */
data class OrderLine(
    val id: String,
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Vnd?,
    val seq: Int,
    /** Kilos for items sold by weight; null for counted items. */
    val weightKg: Double? = null,
) {
    /** Weighed lines round to the nearest whole đồng. */
    val amount: Vnd? get() = unitPrice?.let { p -> weightKg?.let { Math.round(p * it) } ?: (p * quantity) }
}

data class Order(
    val id: String,
    val tableId: String,
    val openedAt: Long,
    val status: OrderStatus = OrderStatus.OPEN,
    val lines: List<OrderLine> = emptyList(),
    /** Room fee typed in by the cashier; null = computed from time. */
    val roomFeeOverride: Vnd? = null,
    val closedAt: Long? = null,
    /** Room fee actually charged, fixed at payment. */
    val roomFeeCharged: Vnd? = null,
    val total: Vnd? = null,
    val cashGiven: Vnd? = null,
    /** Table name at payment time, so renaming or deleting a table never changes old bills. */
    val placeLabel: String? = null,
)

/** Everything the engine needs at start-up. Only OPEN orders are loaded. */
data class Snapshot(
    val zones: List<Zone>,
    val tables: List<Table>,
    val categories: List<Category>,
    val menuItems: List<MenuItem>,
    val openOrders: List<Order>,
)

interface PosStore {
    fun load(): Snapshot
    fun saveZone(zone: Zone)
    fun saveTable(table: Table)
    fun saveCategory(category: Category)
    fun saveMenuItem(item: MenuItem)
    /** Upsert the order and replace its lines. */
    fun saveOrder(order: Order)
    /** PAID orders with closedAt in [from, until), oldest first, lines included. */
    fun paidOrders(from: Long, until: Long): List<Order>
    /** Deletes the zone and all its tables. */
    fun deleteZone(zoneId: String)
    fun deleteTable(tableId: String)
    fun loadSettings(): Map<String, String>
    /** null deletes the key. */
    fun saveSetting(key: String, value: String?)
}

fun interface Clock {
    fun now(): Long
}

interface ReceiptPrinter {
    /** Returns null on success, or a Vietnamese error message. */
    fun print(receipt: Receipt): String?
}

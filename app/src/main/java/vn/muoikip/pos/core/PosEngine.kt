package vn.muoikip.pos.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class TableView(
    val table: Table,
    val zone: Zone,
    val label: String,
    val occupied: Boolean,
    val total: Vnd,
    /** Elapsed ms since opening; only for open rooms in timed zones. */
    val elapsedMs: Long?,
    val hasMissingPrices: Boolean,
)

data class OrderView(
    val order: Order,
    val table: TableView,
    /** null for normal tables. */
    val roomFee: Vnd?,
    val roomFeeLabel: String?,
    val total: Vnd,
    val missingPriceLineIds: List<String>,
)

sealed interface CheckoutResult {
    data class MissingPrices(val lineIds: List<String>) : CheckoutResult
    data class InsufficientCash(val total: Vnd) : CheckoutResult
    data object NothingToPay : CheckoutResult
    /** The table is closed. [printError] non-null means printing failed; use [PosEngine.reprint]. */
    data class Paid(val receipt: Receipt, val printError: String?) : CheckoutResult
}

/**
 * All POS business rules. Pure Kotlin: no Android types, so it is tested on the JVM with an
 * in-memory store, a fake clock and a fake printer.
 */
class PosEngine(
    private val store: PosStore,
    private val clock: Clock,
    private val printer: ReceiptPrinter,
    val shop: ShopInfo,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val zones = LinkedHashMap<String, Zone>()
    private val tables = LinkedHashMap<String, Table>()
    private val categories = LinkedHashMap<String, Category>()
    private val menuItems = LinkedHashMap<String, MenuItem>()
    /** Open order per table id. */
    private val openOrders = HashMap<String, Order>()

    private val _revision = MutableStateFlow(0)
    /** Increments after every change so the UI knows to re-read. */
    val revision: StateFlow<Int> = _revision

    init {
        var snap = store.load()
        if (snap.zones.isEmpty() && snap.menuItems.isEmpty()) {
            DefaultData.seed(store, newId)
            snap = store.load()
        }
        snap.zones.forEach { zones[it.id] = it }
        snap.tables.forEach { tables[it.id] = it }
        snap.categories.forEach { categories[it.id] = it }
        snap.menuItems.forEach { menuItems[it.id] = it }
        snap.openOrders.filter { it.status == OrderStatus.OPEN }.forEach { openOrders[it.tableId] = it }
    }

    // ---------- Queries ----------

    fun zones(): List<Zone> = zones.values.sortedBy { it.sort }

    fun tables(zoneId: String): List<TableView> =
        tables.values.filter { it.zoneId == zoneId }.sortedBy { it.number }.map { view(it) }

    fun table(tableId: String): TableView = view(tableOf(tableId))

    fun categories(): List<Category> = categories.values.sortedBy { it.sort }

    fun menu(categoryId: String): List<MenuItem> =
        menuItems.values.filter { it.categoryId == categoryId && it.active }.sortedBy { it.sort }

    fun order(tableId: String): OrderView? {
        val order = openOrders[tableId] ?: return null
        val tv = view(tableOf(tableId))
        val fee = roomFee(order)
        return OrderView(
            order = order,
            table = tv,
            roomFee = fee,
            roomFeeLabel = fee?.let { roomFeeLabel(order) },
            total = total(order),
            missingPriceLineIds = order.lines.filter { it.unitPrice == null }.map { it.id },
        )
    }

    // ---------- Zones & tables ----------

    fun addZone(name: String, timed: Boolean): Zone {
        val zone = Zone(newId(), name.trim(), timed, (zones.values.maxOfOrNull { it.sort } ?: 0) + 1)
        zones[zone.id] = zone
        store.saveZone(zone)
        changed()
        return zone
    }

    /** Adds the next-numbered table. [hourlyRate] is required for timed zones and ignored otherwise. */
    fun addTable(zoneId: String, hourlyRate: Vnd? = null): Table {
        val zone = zones.getValue(zoneId)
        require(!zone.timed || hourlyRate != null) { "Phòng tính giờ cần đơn giá" }
        val next = (tables.values.filter { it.zoneId == zoneId }.maxOfOrNull { it.number } ?: 0) + 1
        val table = Table(newId(), zoneId, next, if (zone.timed) hourlyRate else null)
        tables[table.id] = table
        store.saveTable(table)
        changed()
        return table
    }

    // ---------- Ordering ----------

    /** Opens a timed room: the clock starts now. No-op if already open. */
    fun openRoom(tableId: String) {
        if (openOrders.containsKey(tableId)) return
        save(newOrder(tableId))
    }

    /** Adds one portion; tapping an item already on the table increases its quantity. */
    fun addItem(tableId: String, menuItemId: String) {
        val item = menuItems.getValue(menuItemId)
        val order = openOrders[tableId] ?: newOrder(tableId)
        val existing = order.lines.firstOrNull { it.menuItemId == menuItemId }
        val lines = if (existing != null) {
            order.lines.map { if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            order.lines + OrderLine(
                id = newId(), menuItemId = item.id, name = item.name, quantity = 1,
                unitPrice = item.price, seq = (order.lines.maxOfOrNull { it.seq } ?: 0) + 1,
            )
        }
        save(order.copy(lines = lines))
    }

    /** Quantity 0 removes the line. */
    fun setQuantity(tableId: String, lineId: String, quantity: Int) {
        require(quantity >= 0)
        val order = openOrders.getValue(tableId)
        val lines = if (quantity == 0) order.lines.filterNot { it.id == lineId }
        else order.lines.map { if (it.id == lineId) it.copy(quantity = quantity) else it }
        saveOrDropEmpty(order.copy(lines = lines))
    }

    fun removeLine(tableId: String, lineId: String) = setQuantity(tableId, lineId, 0)

    /** Changes the price for this table only; the menu price is untouched. */
    fun setLinePrice(tableId: String, lineId: String, price: Vnd) {
        require(price >= 0)
        val order = openOrders.getValue(tableId)
        save(order.copy(lines = order.lines.map { if (it.id == lineId) it.copy(unitPrice = price) else it }))
    }

    /** null restores the time-based fee. */
    fun setRoomFeeOverride(tableId: String, fee: Vnd?) {
        val order = openOrders.getValue(tableId)
        save(order.copy(roomFeeOverride = fee))
    }

    /** Closes a table that has nothing ordered (e.g. a room opened by mistake). */
    fun cancelEmpty(tableId: String) {
        val order = openOrders[tableId] ?: return
        require(order.lines.isEmpty()) { "Bàn còn món, không hủy được" }
        openOrders.remove(tableId)
        store.saveOrder(order.copy(status = OrderStatus.CANCELLED, closedAt = clock.now()))
        changed()
    }

    // ---------- Printing & payment ----------

    /** Prints a pre-bill; the table stays open. Returns a printer error or null. */
    fun printPreBill(tableId: String): String? {
        val order = openOrders.getValue(tableId)
        return printer.print(receipt(order, ReceiptKind.PRE_BILL, cashGiven = null, at = clock.now()))
    }

    fun checkout(tableId: String, cashGiven: Vnd): CheckoutResult {
        val order = openOrders[tableId] ?: return CheckoutResult.NothingToPay
        val missing = order.lines.filter { it.unitPrice == null }.map { it.id }
        if (missing.isNotEmpty()) return CheckoutResult.MissingPrices(missing)
        val isTimed = zoneOf(tableOf(tableId)).timed
        if (order.lines.isEmpty() && !isTimed) return CheckoutResult.NothingToPay
        val now = clock.now()
        val total = total(order, now)
        if (cashGiven < total) return CheckoutResult.InsufficientCash(total)

        val receipt = receipt(order, ReceiptKind.BILL, cashGiven, now)
        // Close first: a printer failure must never reopen a paid table.
        val paid = order.copy(
            status = OrderStatus.PAID, closedAt = now, roomFeeCharged = roomFee(order, now),
            total = total, cashGiven = cashGiven,
        )
        openOrders.remove(tableId)
        store.saveOrder(paid)
        changed()
        return CheckoutResult.Paid(receipt, printer.print(receipt))
    }

    fun reprint(receipt: Receipt): String? = printer.print(receipt)

    // ---------- Internals ----------

    private fun tableOf(id: String) = tables.getValue(id)
    private fun zoneOf(t: Table) = zones.getValue(t.zoneId)

    private fun label(t: Table): String {
        val z = zoneOf(t)
        return if (z.timed) "Phòng ${z.name} ${t.number}" else "${z.name} · Bàn ${t.number}"
    }

    private fun view(t: Table): TableView {
        val order = openOrders[t.id]
        val z = zoneOf(t)
        return TableView(
            table = t,
            zone = z,
            label = label(t),
            occupied = order != null,
            total = order?.let { total(it) } ?: 0,
            elapsedMs = if (order != null && z.timed) clock.now() - order.openedAt else null,
            hasMissingPrices = order?.lines?.any { it.unitPrice == null } == true,
        )
    }

    private fun roomFee(order: Order, now: Long = clock.now()): Vnd? {
        val t = tableOf(order.tableId)
        if (!zoneOf(t).timed) return null
        return order.roomFeeOverride ?: RoomFee.fee(t.hourlyRate ?: 0, now - order.openedAt)
    }

    private fun roomFeeLabel(order: Order, now: Long = clock.now()): String {
        val t = tableOf(order.tableId)
        return "Tiền phòng ${zoneOf(t).name} ${t.number} – ${RoomFee.billedDurationText(now - order.openedAt)}"
    }

    private fun total(order: Order, now: Long = clock.now()): Vnd =
        order.lines.sumOf { it.amount ?: 0 } + (roomFee(order, now) ?: 0)

    private fun receipt(order: Order, kind: ReceiptKind, cashGiven: Vnd?, at: Long): Receipt {
        val total = total(order, at)
        val fee = roomFee(order, at)
        return Receipt(
            shop = shop,
            kind = kind,
            placeLabel = label(tableOf(order.tableId)),
            openedAt = order.openedAt,
            printedAt = at,
            items = order.lines.sortedBy { it.seq }.map {
                ReceiptItem(it.name, it.quantity, it.unitPrice ?: 0, it.amount ?: 0)
            },
            roomFeeLabel = fee?.let { roomFeeLabel(order, at) },
            roomFee = fee,
            total = total,
            cashGiven = cashGiven,
            change = cashGiven?.let { it - total },
        )
    }

    private fun newOrder(tableId: String): Order {
        tableOf(tableId) // validates
        return Order(id = newId(), tableId = tableId, openedAt = clock.now())
    }

    private fun save(order: Order) {
        openOrders[order.tableId] = order
        store.saveOrder(order)
        changed()
    }

    /** A normal table with no items left goes back to empty; a timed room stays open. */
    private fun saveOrDropEmpty(order: Order) {
        if (order.lines.isEmpty() && !zoneOf(tableOf(order.tableId)).timed) {
            openOrders.remove(order.tableId)
            store.saveOrder(order.copy(status = OrderStatus.CANCELLED, closedAt = clock.now()))
            changed()
        } else {
            save(order)
        }
    }

    private fun changed() {
        _revision.value = _revision.value + 1
    }
}

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
    private var bank: BankAccount? = null
    private var pinHash: String? = null

    private val _revision = MutableStateFlow(0)
    /** Increments after every change so the UI knows to re-read. */
    val revision: StateFlow<Int> = _revision

    init {
        var snap = store.load()
        if (snap.zones.isEmpty() && snap.menuItems.isEmpty()) {
            DefaultData.seed(store, newId)
            snap = store.load()
        }
        DefaultData.upgradeMenu(store, newId)
        snap = store.load()
        snap.zones.forEach { zones[it.id] = it }
        snap.tables.forEach { tables[it.id] = it }
        snap.categories.forEach { categories[it.id] = it }
        snap.menuItems.forEach { menuItems[it.id] = it }
        snap.openOrders.filter { it.status == OrderStatus.OPEN }.forEach { openOrders[it.tableId] = it }
        val settings = store.loadSettings()
        bank = loadBank(settings)
        pinHash = settings[KEY_PIN]
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

    // ---------- Bank account (VietQR) ----------

    fun bankAccount(): BankAccount? = bank

    /** null removes the QR from receipts. */
    fun setBankAccount(account: BankAccount?) {
        bank = account
        store.saveSetting(KEY_BIN, account?.bin)
        store.saveSetting(KEY_ACCOUNT, account?.account)
        store.saveSetting(KEY_HOLDER, account?.holder)
        changed()
    }

    private fun loadBank(settings: Map<String, String>): BankAccount? {
        val bin = settings[KEY_BIN] ?: return null
        val acc = settings[KEY_ACCOUNT] ?: return null
        return BankAccount(bin, acc, settings[KEY_HOLDER].orEmpty())
    }

    private companion object {
        const val KEY_BIN = "bank.bin"
        const val KEY_ACCOUNT = "bank.account"
        const val KEY_HOLDER = "bank.holder"
        const val KEY_PIN = "pin.hash"
    }

    // ---------- Menu editing ----------

    fun addMenuItem(categoryId: String, name: String, price: Vnd?, soldByWeight: Boolean = false): MenuItem {
        val sort = (menuItems.values.filter { it.categoryId == categoryId }.maxOfOrNull { it.sort } ?: 0) + 1
        val item = MenuItem(newId(), categoryId, name.trim(), price, sort, soldByWeight = soldByWeight)
        menuItems[item.id] = item
        store.saveMenuItem(item)
        changed()
        return item
    }

    /** All items of a category, hidden ones included. */
    fun menuForEditing(categoryId: String): List<MenuItem> =
        menuItems.values.filter { it.categoryId == categoryId }.sortedBy { it.sort }

    fun setMenuItemActive(itemId: String, active: Boolean) = saveMenuItem(menuItems.getValue(itemId).copy(active = active))

    /** Open orders keep the name and price copied when they were ordered. Moving category puts the item last. */
    fun updateMenuItem(itemId: String, name: String, price: Vnd?, categoryId: String, soldByWeight: Boolean? = null) {
        val old = menuItems.getValue(itemId)
        val sort = if (old.categoryId == categoryId) old.sort
        else (menuItems.values.filter { it.categoryId == categoryId }.maxOfOrNull { it.sort } ?: 0) + 1
        saveMenuItem(old.copy(name = name.trim(), price = price, categoryId = categoryId, sort = sort,
            soldByWeight = soldByWeight ?: old.soldByWeight))
    }

    fun addCategory(name: String): Category {
        val c = Category(newId(), name.trim(), (categories.values.maxOfOrNull { it.sort } ?: 0) + 1)
        categories[c.id] = c
        store.saveCategory(c)
        changed()
        return c
    }

    private fun saveMenuItem(item: MenuItem) {
        menuItems[item.id] = item
        store.saveMenuItem(item)
        changed()
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
        // A weighed item already on the table is corrected by typing its weight, not by tapping again.
        if (existing != null && item.soldByWeight) return
        val lines = if (existing != null) {
            order.lines.map { if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            order.lines + OrderLine(
                id = newId(), menuItemId = item.id, name = item.name, quantity = 1,
                unitPrice = item.price, seq = (order.lines.maxOfOrNull { it.seq } ?: 0) + 1,
                weightKg = if (item.soldByWeight) 1.0 else null,
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

    /** Kilos for a weighed line (e.g. 1.25). */
    fun setLineWeight(tableId: String, lineId: String, kg: Double) {
        require(kg > 0) { "Số ký phải lớn hơn 0" }
        val order = openOrders.getValue(tableId)
        save(order.copy(lines = order.lines.map { if (it.id == lineId) it.copy(weightKg = kg) else it }))
    }

    /** Changes the price for this table only; the menu price is untouched. */
    fun setLinePrice(tableId: String, lineId: String, price: Vnd) {
        require(price >= 0)
        val order = openOrders.getValue(tableId)
        save(order.copy(lines = order.lines.map { if (it.id == lineId) it.copy(unitPrice = price) else it }))
    }

    /**
     * Corrects when guests entered a room (e.g. opened late in the app). Rooms only; never in
     * the future. Returns false when refused.
     */
    fun setRoomStart(tableId: String, openedAt: Long): Boolean {
        val order = openOrders[tableId] ?: return false
        if (!zoneOf(tableOf(tableId)).timed || openedAt > clock.now()) return false
        save(order.copy(openedAt = openedAt))
        return true
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

    // ---------- Move & merge ----------

    private fun allTablesInOrder(): List<Table> =
        tables.values.sortedWith(compareBy({ zoneOf(it).sort }, { it.number }))

    /**
     * Empty tables a group can move to. A normal table may go to any empty table or room; a room
     * only to another room (moving out would drop the time already billed).
     */
    fun moveTargets(fromTableId: String): List<TableView> {
        val fromRoom = zoneOf(tableOf(fromTableId)).timed
        return allTablesInOrder()
            .filter { it.id != fromTableId && it.id !in openOrders && (!fromRoom || zoneOf(it).timed) }
            .map { view(it) }
    }

    /**
     * Room → room keeps its clock and takes the new room's rate. Table → room starts the room
     * clock at the moment of the move.
     */
    fun moveTable(fromTableId: String, toTableId: String) {
        require(moveTargets(fromTableId).any { it.table.id == toTableId }) { "Không chuyển được sang bàn này" }
        val order = openOrders.remove(fromTableId) ?: return
        val intoRoomFromTable = zoneOf(tableOf(toTableId)).timed && !zoneOf(tableOf(fromTableId)).timed
        save(order.copy(tableId = toTableId, openedAt = if (intoRoomFromTable) clock.now() else order.openedAt))
    }

    /** Other occupied tables. A room cannot be merged away, or its room fee would be lost. */
    fun mergeTargets(fromTableId: String): List<TableView> {
        if (zoneOf(tableOf(fromTableId)).timed) return emptyList()
        return allTablesInOrder().filter { it.id != fromTableId && it.id in openOrders }.map { view(it) }
    }

    /** Moves every line of [fromTableId] into [intoTableId]; same item at same price adds up. */
    fun mergeInto(fromTableId: String, intoTableId: String) {
        require(mergeTargets(fromTableId).any { it.table.id == intoTableId }) { "Không gộp được vào bàn này" }
        val from = openOrders.getValue(fromTableId)
        var lines = openOrders.getValue(intoTableId).lines
        for (l in from.lines.sortedBy { it.seq }) {
            val same = if (l.weightKg != null) null
            else lines.firstOrNull { it.menuItemId == l.menuItemId && it.unitPrice == l.unitPrice && it.weightKg == null }
            lines = if (same != null) {
                lines.map { if (it.id == same.id) it.copy(quantity = it.quantity + l.quantity) else it }
            } else {
                lines + l.copy(id = newId(), seq = (lines.maxOfOrNull { it.seq } ?: 0) + 1)
            }
        }
        openOrders.remove(fromTableId)
        store.saveOrder(from.copy(status = OrderStatus.CANCELLED, closedAt = clock.now(), lines = emptyList()))
        save(openOrders.getValue(intoTableId).copy(lines = lines))
    }

    // ---------- Printing & payment ----------

    /** Prints a pre-bill; the table stays open. Returns a printer error or null. */
    fun printPreBill(tableId: String): String? {
        val order = openOrders.getValue(tableId)
        return printer.print(receipt(order, ReceiptKind.PRE_BILL, cashGiven = null, at = clock.now()))
    }

    /** [cashGiven] null = no cash/change step: the bill is just printed and the table closed. */
    fun checkout(tableId: String, cashGiven: Vnd? = null): CheckoutResult {
        val order = openOrders[tableId] ?: return CheckoutResult.NothingToPay
        val missing = order.lines.filter { it.unitPrice == null }.map { it.id }
        if (missing.isNotEmpty()) return CheckoutResult.MissingPrices(missing)
        val isTimed = zoneOf(tableOf(tableId)).timed
        if (order.lines.isEmpty() && !isTimed) return CheckoutResult.NothingToPay
        val now = clock.now()
        val total = total(order, now)
        if (cashGiven != null && cashGiven < total) return CheckoutResult.InsufficientCash(total)

        val receipt = receipt(order, ReceiptKind.BILL, cashGiven, now)
        // Close first: a printer failure must never reopen a paid table.
        val paid = order.copy(
            status = OrderStatus.PAID, closedAt = now, roomFeeCharged = roomFee(order, now),
            total = total, cashGiven = cashGiven, placeLabel = label(tableOf(tableId)),
        )
        openOrders.remove(tableId)
        store.saveOrder(paid)
        changed()
        return CheckoutResult.Paid(receipt, printer.print(receipt))
    }

    fun reprint(receipt: Receipt): String? = printer.print(receipt)

    // ---------- Reports & old bills ----------

    /** Bills paid before labels were stored (schema v1–v2) get the table's current name, if it still exists. */
    fun paidBills(from: Long, until: Long): List<Order> = store.paidOrders(from, until).map { o ->
        if (o.placeLabel != null) o else o.copy(placeLabel = tables[o.tableId]?.let { label(it) })
    }

    fun report(from: Long, until: Long): DailyReport {
        val bills = store.paidOrders(from, until)
        val items = bills.flatMap { it.lines }
            .groupBy { it.name }
            .map { (name, ls) -> ReportLine(name, ls.sumOf { it.quantity }, ls.sumOf { it.amount ?: 0 }) }
            .sortedByDescending { it.amount }
        return DailyReport(
            billCount = bills.size,
            revenue = bills.sumOf { it.total ?: 0 },
            roomFees = bills.sumOf { it.roomFeeCharged ?: 0 },
            items = items,
        )
    }

    /** Rebuilds a paid bill exactly as charged (room fee is the stored one, not re-timed). */
    fun reprintPaid(orderId: String): String? {
        val o = paidBills(0, Long.MAX_VALUE).first { it.id == orderId }
        val fee = o.roomFeeCharged
        val place = o.placeLabel ?: ""
        val receipt = Receipt(
            shop = shop,
            kind = ReceiptKind.BILL,
            placeLabel = place,
            openedAt = o.openedAt,
            printedAt = clock.now(),
            items = o.lines.sortedBy { it.seq }.map { ReceiptItem(it.name, it.quantity, it.unitPrice ?: 0, it.amount ?: 0, it.weightKg) },
            roomFeeLabel = fee?.let {
                "Tiền phòng ${place.removePrefix("Phòng ")} – ${RoomFee.billedDurationText((o.closedAt ?: o.openedAt) - o.openedAt)}"
            },
            roomFee = fee,
            total = o.total ?: 0,
            cashGiven = o.cashGiven,
            change = o.cashGiven?.let { it - (o.total ?: 0) },
            qrPayload = bank?.let { VietQr.payload(it.bin, it.account, o.total ?: 0, "${shop.name} $place") },
            bankLine = bank?.let { "${bankName(it.bin)} · ${it.account} · ${it.holder}" },
        )
        return printer.print(receipt)
    }

    // ---------- PIN ----------

    fun hasPin(): Boolean = pinHash != null

    /** Always true when no PIN is set. */
    fun checkPin(pin: String): Boolean = pinHash == null || pinHash == hash(pin)

    fun setPin(pin: String?) {
        pinHash = pin?.let { hash(it) }
        store.saveSetting(KEY_PIN, pinHash)
    }

    private fun hash(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("muoikip:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ---------- Zone & table admin ----------

    fun renameZone(zoneId: String, name: String) {
        val z = zones.getValue(zoneId).copy(name = name.trim())
        zones[z.id] = z
        store.saveZone(z)
        changed()
    }

    fun setRoomRate(tableId: String, rate: Vnd) {
        val t = tableOf(tableId).copy(hourlyRate = rate)
        tables[t.id] = t
        store.saveTable(t)
        changed()
    }

    fun canDeleteTable(tableId: String): Boolean = tableId !in openOrders

    fun canDeleteZone(zoneId: String): Boolean =
        tables.values.none { it.zoneId == zoneId && it.id in openOrders } && zones.size > 1

    fun deleteTable(tableId: String) {
        require(canDeleteTable(tableId)) { "Bàn đang có khách" }
        tables.remove(tableId)
        store.deleteTable(tableId)
        changed()
    }

    fun deleteZone(zoneId: String) {
        require(canDeleteZone(zoneId)) { "Khu còn bàn đang có khách" }
        zones.remove(zoneId)
        tables.values.removeAll { it.zoneId == zoneId }
        store.deleteZone(zoneId)
        changed()
    }

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
                ReceiptItem(it.name, it.quantity, it.unitPrice ?: 0, it.amount ?: 0, it.weightKg)
            },
            roomFeeLabel = fee?.let { roomFeeLabel(order, at) },
            roomFee = fee,
            total = total,
            cashGiven = cashGiven,
            change = cashGiven?.let { it - total },
            qrPayload = bank?.let { VietQr.payload(it.bin, it.account, total, "${shop.name} ${label(tableOf(order.tableId))}") },
            bankLine = bank?.let { "${bankName(it.bin)} · ${it.account} · ${it.holder}" },
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

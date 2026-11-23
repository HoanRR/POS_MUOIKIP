package vn.muoikip.pos.core

class InMemoryStore : PosStore {
    val zones = LinkedHashMap<String, Zone>()
    val tables = LinkedHashMap<String, Table>()
    val categories = LinkedHashMap<String, Category>()
    val items = LinkedHashMap<String, MenuItem>()
    val orders = LinkedHashMap<String, Order>()
    override fun load() = Snapshot(
        zones.values.toList(), tables.values.toList(), categories.values.toList(), items.values.toList(),
        orders.values.filter { it.status == OrderStatus.OPEN },
    )
    override fun saveZone(zone: Zone) { zones[zone.id] = zone }
    override fun saveTable(table: Table) { tables[table.id] = table }
    override fun saveCategory(category: Category) { categories[category.id] = category }
    override fun saveMenuItem(item: MenuItem) { items[item.id] = item }
    override fun saveOrder(order: Order) { orders[order.id] = order }
    override fun paidOrders(from: Long, until: Long) = orders.values
        .filter { it.status == OrderStatus.PAID && it.closedAt != null && it.closedAt >= from && it.closedAt < until }
        .sortedBy { it.closedAt }
    override fun deleteZone(zoneId: String) {
        zones.remove(zoneId)
        tables.values.removeAll { it.zoneId == zoneId }
    }
    override fun deleteTable(tableId: String) { tables.remove(tableId) }
    val settings = LinkedHashMap<String, String>()
    override fun loadSettings(): Map<String, String> = settings.toMap()
    override fun saveSetting(key: String, value: String?) { if (value == null) settings.remove(key) else settings[key] = value }
}

class FakeClock(var ms: Long = 1_700_000_000_000L) : Clock {
    override fun now() = ms
    fun advanceMinutes(m: Int, s: Int = 0) { ms += m * 60_000L + s * 1000L }
}

class FakePrinter : ReceiptPrinter {
    val printed = mutableListOf<Receipt>()
    var failWith: String? = null
    override fun print(receipt: Receipt): String? {
        failWith?.let { return it }
        printed += receipt
        return null
    }
}

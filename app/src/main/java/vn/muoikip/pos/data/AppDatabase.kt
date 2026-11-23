package vn.muoikip.pos.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import vn.muoikip.pos.core.Category
import vn.muoikip.pos.core.MenuItem
import vn.muoikip.pos.core.Order
import vn.muoikip.pos.core.OrderLine
import vn.muoikip.pos.core.OrderStatus
import vn.muoikip.pos.core.PosStore
import vn.muoikip.pos.core.Snapshot
import vn.muoikip.pos.core.Table
import vn.muoikip.pos.core.Zone

@Entity(tableName = "zone")
data class ZoneEntity(@PrimaryKey val id: String, val name: String, val timed: Boolean, val sort: Int)

@Entity(tableName = "dining_table")
data class TableEntity(@PrimaryKey val id: String, val zoneId: String, val number: Int, val hourlyRate: Long?)

@Entity(tableName = "category")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val sort: Int)

@Entity(tableName = "menu_item")
data class MenuItemEntity(
    @PrimaryKey val id: String, val categoryId: String, val name: String,
    val price: Long?, val sort: Int, val active: Boolean,
)

@Entity(tableName = "orders", indices = [Index("status")])
data class OrderEntity(
    @PrimaryKey val id: String, val tableId: String, val openedAt: Long, val status: String,
    val roomFeeOverride: Long?, val closedAt: Long?, val roomFeeCharged: Long?,
    val total: Long?, val cashGiven: Long?,
)

@Entity(tableName = "order_line", indices = [Index("orderId")])
data class OrderLineEntity(
    @PrimaryKey val id: String, val orderId: String, val menuItemId: String, val name: String,
    val quantity: Int, val unitPrice: Long?, val seq: Int,
)

@Dao
interface PosDao {
    @Query("SELECT * FROM zone") fun zones(): List<ZoneEntity>
    @Query("SELECT * FROM dining_table") fun tables(): List<TableEntity>
    @Query("SELECT * FROM category") fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM menu_item") fun menuItems(): List<MenuItemEntity>
    @Query("SELECT * FROM orders WHERE status = 'OPEN'") fun openOrders(): List<OrderEntity>
    @Query("SELECT * FROM order_line WHERE orderId = :orderId") fun lines(orderId: String): List<OrderLineEntity>

    @Upsert fun upsert(z: ZoneEntity)
    @Upsert fun upsert(t: TableEntity)
    @Upsert fun upsert(c: CategoryEntity)
    @Upsert fun upsert(m: MenuItemEntity)
    @Upsert fun upsert(o: OrderEntity)
    @Upsert fun upsertLines(lines: List<OrderLineEntity>)
    @Query("DELETE FROM order_line WHERE orderId = :orderId") fun deleteLines(orderId: String)

    @Transaction
    fun saveOrder(o: OrderEntity, lines: List<OrderLineEntity>) {
        upsert(o)
        deleteLines(o.id)
        upsertLines(lines)
    }
}

@Database(
    entities = [ZoneEntity::class, TableEntity::class, CategoryEntity::class, MenuItemEntity::class,
        OrderEntity::class, OrderLineEntity::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PosDao

    companion object {
        /** The data set is tiny (tens of rows), so synchronous main-thread access is acceptable here. */
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pos.db")
                .allowMainThreadQueries()
                .build()
    }
}

class RoomPosStore(private val dao: PosDao) : PosStore {
    override fun load(): Snapshot = Snapshot(
        zones = dao.zones().map { Zone(it.id, it.name, it.timed, it.sort) },
        tables = dao.tables().map { Table(it.id, it.zoneId, it.number, it.hourlyRate) },
        categories = dao.categories().map { Category(it.id, it.name, it.sort) },
        menuItems = dao.menuItems().map { MenuItem(it.id, it.categoryId, it.name, it.price, it.sort, it.active) },
        openOrders = dao.openOrders().map { o ->
            Order(
                id = o.id, tableId = o.tableId, openedAt = o.openedAt, status = OrderStatus.valueOf(o.status),
                lines = dao.lines(o.id).map { OrderLine(it.id, it.menuItemId, it.name, it.quantity, it.unitPrice, it.seq) },
                roomFeeOverride = o.roomFeeOverride, closedAt = o.closedAt, roomFeeCharged = o.roomFeeCharged,
                total = o.total, cashGiven = o.cashGiven,
            )
        },
    )

    override fun saveZone(zone: Zone) = dao.upsert(ZoneEntity(zone.id, zone.name, zone.timed, zone.sort))
    override fun saveTable(table: Table) = dao.upsert(TableEntity(table.id, table.zoneId, table.number, table.hourlyRate))
    override fun saveCategory(category: Category) = dao.upsert(CategoryEntity(category.id, category.name, category.sort))
    override fun saveMenuItem(item: MenuItem) =
        dao.upsert(MenuItemEntity(item.id, item.categoryId, item.name, item.price, item.sort, item.active))

    override fun saveOrder(order: Order) = dao.saveOrder(
        OrderEntity(
            order.id, order.tableId, order.openedAt, order.status.name, order.roomFeeOverride,
            order.closedAt, order.roomFeeCharged, order.total, order.cashGiven,
        ),
        order.lines.map { OrderLineEntity(it.id, order.id, it.menuItemId, it.name, it.quantity, it.unitPrice, it.seq) },
    )
}

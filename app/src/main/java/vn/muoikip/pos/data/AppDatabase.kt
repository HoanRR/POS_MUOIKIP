package vn.muoikip.pos.data

import android.content.Context
import androidx.room.ColumnInfo
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Added in schema v4. */
    @ColumnInfo(defaultValue = "0") val soldByWeight: Boolean = false,
)

@Entity(tableName = "orders", indices = [Index("status")])
data class OrderEntity(
    @PrimaryKey val id: String, val tableId: String, val openedAt: Long, val status: String,
    val roomFeeOverride: Long?, val closedAt: Long?, val roomFeeCharged: Long?,
    val total: Long?, val cashGiven: Long?,
    /** Added in schema v3. */
    val placeLabel: String? = null,
)

@Entity(tableName = "order_line", indices = [Index("orderId")])
data class OrderLineEntity(
    @PrimaryKey val id: String, val orderId: String, val menuItemId: String, val name: String,
    val quantity: Int, val unitPrice: Long?, val seq: Int,
    /** Added in schema v4. */
    val weightKg: Double? = null,
)

/** Key/value app settings (bank account, …). Added in schema v2. */
@Entity(tableName = "setting")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface PosDao {
    @Query("SELECT * FROM setting") fun settings(): List<SettingEntity>
    @Upsert fun upsert(s: SettingEntity)
    @Query("DELETE FROM setting WHERE `key` = :key") fun deleteSetting(key: String)

    @Query("SELECT * FROM zone") fun zones(): List<ZoneEntity>
    @Query("SELECT * FROM dining_table") fun tables(): List<TableEntity>
    @Query("SELECT * FROM category") fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM menu_item") fun menuItems(): List<MenuItemEntity>
    @Query("SELECT * FROM orders WHERE status = 'OPEN'") fun openOrders(): List<OrderEntity>
    @Query("SELECT * FROM order_line WHERE orderId = :orderId") fun lines(orderId: String): List<OrderLineEntity>
    @Query("SELECT * FROM orders WHERE status = 'PAID' AND closedAt >= :from AND closedAt < :until ORDER BY closedAt")
    fun paidOrders(from: Long, until: Long): List<OrderEntity>
    @Query("DELETE FROM dining_table WHERE zoneId = :zoneId") fun deleteTablesOfZone(zoneId: String)
    @Query("DELETE FROM zone WHERE id = :zoneId") fun deleteZoneRow(zoneId: String)
    @Query("DELETE FROM dining_table WHERE id = :tableId") fun deleteTable(tableId: String)

    @Transaction
    fun deleteZone(zoneId: String) {
        deleteTablesOfZone(zoneId)
        deleteZoneRow(zoneId)
    }

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
        OrderEntity::class, OrderLineEntity::class, SettingEntity::class],
    version = 4,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PosDao

    companion object {
        const val NAME = "pos.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `setting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        /** Every schema change gets a migration here and a case in MigrationTest. Never destructive. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `orders` ADD COLUMN `placeLabel` TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `menu_item` ADD COLUMN `soldByWeight` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `order_line` ADD COLUMN `weightKg` REAL")
                // Ba ba was seeded as a fixed "1kg" portion; it is now priced per kilo.
                db.execSQL("UPDATE `menu_item` SET `name` = 'Ba ba', `soldByWeight` = 1 WHERE `name` = 'Ba ba (1kg)'")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        /** The data set is tiny (tens of rows), so synchronous main-thread access is acceptable here. */
        fun open(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .allowMainThreadQueries()
                .addMigrations(*MIGRATIONS)
                // Single file on disk so DatabaseBackup can copy it as-is.
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
    }
}

class RoomPosStore(private val dao: PosDao) : PosStore {
    override fun load(): Snapshot = Snapshot(
        zones = dao.zones().map { Zone(it.id, it.name, it.timed, it.sort) },
        tables = dao.tables().map { Table(it.id, it.zoneId, it.number, it.hourlyRate) },
        categories = dao.categories().map { Category(it.id, it.name, it.sort) },
        menuItems = dao.menuItems().map { MenuItem(it.id, it.categoryId, it.name, it.price, it.sort, it.active, it.soldByWeight) },
        openOrders = dao.openOrders().map(::toOrder),
    )

    private fun toOrder(o: OrderEntity) = Order(
        id = o.id, tableId = o.tableId, openedAt = o.openedAt, status = OrderStatus.valueOf(o.status),
        lines = dao.lines(o.id).sortedBy { it.seq }.map { OrderLine(it.id, it.menuItemId, it.name, it.quantity, it.unitPrice, it.seq, it.weightKg) },
        roomFeeOverride = o.roomFeeOverride, closedAt = o.closedAt, roomFeeCharged = o.roomFeeCharged,
        total = o.total, cashGiven = o.cashGiven, placeLabel = o.placeLabel,
    )

    override fun paidOrders(from: Long, until: Long): List<Order> = dao.paidOrders(from, until).map(::toOrder)
    override fun deleteZone(zoneId: String) = dao.deleteZone(zoneId)
    override fun deleteTable(tableId: String) = dao.deleteTable(tableId)

    override fun saveZone(zone: Zone) = dao.upsert(ZoneEntity(zone.id, zone.name, zone.timed, zone.sort))
    override fun saveTable(table: Table) = dao.upsert(TableEntity(table.id, table.zoneId, table.number, table.hourlyRate))
    override fun saveCategory(category: Category) = dao.upsert(CategoryEntity(category.id, category.name, category.sort))
    override fun saveMenuItem(item: MenuItem) =
        dao.upsert(MenuItemEntity(item.id, item.categoryId, item.name, item.price, item.sort, item.active, item.soldByWeight))

    override fun loadSettings(): Map<String, String> = dao.settings().associate { it.key to it.value }

    override fun saveSetting(key: String, value: String?) {
        if (value == null) dao.deleteSetting(key) else dao.upsert(SettingEntity(key, value))
    }

    override fun saveOrder(order: Order) = dao.saveOrder(
        OrderEntity(
            order.id, order.tableId, order.openedAt, order.status.name, order.roomFeeOverride,
            order.closedAt, order.roomFeeCharged, order.total, order.cashGiven, order.placeLabel,
        ),
        order.lines.map { OrderLineEntity(it.id, order.id, it.menuItemId, it.name, it.quantity, it.unitPrice, it.seq, it.weightKg) },
    )
}

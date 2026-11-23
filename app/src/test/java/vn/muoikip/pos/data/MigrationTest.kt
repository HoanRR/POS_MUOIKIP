package vn.muoikip.pos.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Updating the app on the T1 must never lose orders: every schema version has a migration test.
 *
 * Room's MigrationTestHelper (2.8) fails on Windows paths, so old databases are built straight from
 * the exported schema JSON in app/schemas. Opening through AppDatabase then runs the real migrations,
 * and Room validates the resulting tables against the current entities.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun tearDown() { context.deleteDatabase(dbName) }

    /** Creates an empty database exactly as schema [version] defined it. */
    private fun createAtVersion(version: Int): SQLiteDatabase {
        val schema = JSONObject(File("schemas/vn.muoikip.pos.data.AppDatabase/$version.json").readText())
            .getJSONObject("database")
        context.deleteDatabase(dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices")
            if (indices != null) for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    @Test fun v1ToV2KeepsDataAndAddsSettings() {
        createAtVersion(1).apply {
            execSQL("INSERT INTO zone (id, name, timed, sort) VALUES ('z1', 'Sân trước', 0, 1)")
            execSQL("INSERT INTO orders (id, tableId, openedAt, status) VALUES ('o1', 't1', 1000, 'OPEN')")
            execSQL("INSERT INTO order_line (id, orderId, menuItemId, name, quantity, unitPrice, seq) VALUES ('l1', 'o1', 'm1', 'Bia Tiger', 2, 20000, 1)")
            close()
        }

        val db = AppDatabase.open(context, dbName)
        val store = RoomPosStore(db.dao())
        val snap = store.load()
        assertEquals(listOf("Sân trước"), snap.zones.map { it.name })
        assertEquals(2, snap.openOrders.single().lines.single().quantity)
        store.saveSetting("bank.bin", "970436")
        assertEquals(mapOf("bank.bin" to "970436"), store.loadSettings())
        db.close()
    }

    @Test fun v2ToV3KeepsPaidOrdersAndAddsPlaceLabel() {
        createAtVersion(2).apply {
            execSQL("INSERT INTO setting (`key`, value) VALUES ('bank.bin', '970436')")
            execSQL("INSERT INTO orders (id, tableId, openedAt, status, closedAt, total, cashGiven) VALUES ('o1', 't1', 1000, 'PAID', 2000, 40000, 50000)")
            close()
        }
        val db = AppDatabase.open(context, dbName)
        val store = RoomPosStore(db.dao())
        assertEquals(mapOf("bank.bin" to "970436"), store.loadSettings())
        val paid = store.paidOrders(0, 10_000).single()
        assertEquals(40_000L, paid.total)
        assertEquals(null, paid.placeLabel)
        db.close()
    }
}

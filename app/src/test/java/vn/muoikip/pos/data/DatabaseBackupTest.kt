package vn.muoikip.pos.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.muoikip.pos.core.Zone

@RunWith(RobolectricTestRunner::class)
class DatabaseBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "backup-test.db"
    private var db: AppDatabase? = null
    private val backup = DatabaseBackup(context, dbName, keep = 3)

    private fun store(): RoomPosStore {
        db?.close()
        return RoomPosStore(AppDatabase.open(context, dbName).also { db = it }.dao())
    }

    @After fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
        backup.dir.deleteRecursively()
    }

    @Test fun restoreBringsBackDataFromBackup() {
        store().saveZone(Zone("z1", "Sân trước", false, 1))
        db!!.close(); db = null
        val file = backup.backup(stamp = "20261123-2000")

        store().saveZone(Zone("z2", "Nhà gỗ", false, 2))
        db!!.close(); db = null
        backup.restore(file)

        assertEquals(listOf("Sân trước"), store().load().zones.map { it.name })
    }

    /** Found on the T1: v1 of the app ran SQLite in WAL mode and the first backup missed the -wal file. */
    @Test fun backupIncludesChangesStillInWriteAheadLog() {
        val path = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        val live = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
        live.enableWriteAheadLogging()
        live.execSQL("CREATE TABLE t (v TEXT)")
        live.execSQL("INSERT INTO t VALUES ('from-wal')")
        // Connection left open, as when the old app was killed by the update: rows sit in pos.db-wal.

        val file = backup.backup(stamp = "20261123-2024")

        val copy = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        val rows = copy.rawQuery("SELECT v FROM t", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        copy.close()
        live.close()
        assertEquals(listOf("from-wal"), rows)
    }

    @Test fun keepsOnlyNewestBackups() {
        store().saveZone(Zone("z1", "Sân trước", false, 1))
        db!!.close(); db = null
        listOf("20261120-0800", "20261121-0800", "20261122-0800", "20261123-0800").forEach { backup.backup(it) }
        assertEquals(
            listOf("pos-20261123-0800.db", "pos-20261122-0800.db", "pos-20261121-0800.db"),
            backup.list().map { it.name },
        )
    }
}

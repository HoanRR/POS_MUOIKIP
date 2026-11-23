package vn.muoikip.pos.data

import android.content.Context
import java.io.File

/**
 * Copies the SQLite file to / from `Android/data/vn.muoikip.pos/files/backups`, reachable over
 * USB without extra permissions. The database runs in TRUNCATE journal mode, so the main file
 * is complete whenever no transaction is running.
 */
class DatabaseBackup(
    private val context: Context,
    private val dbName: String = AppDatabase.NAME,
    private val keep: Int = 30,
) {
    val dir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }

    /** Newest first. */
    fun list(): List<File> =
        dir.listFiles { f -> f.name.endsWith(".db") }.orEmpty().sortedByDescending { it.name }

    /** [stamp] like "20261123-2000"; one file per stamp, so a repeat on the same stamp overwrites. */
    fun backup(stamp: String): File {
        val target = File(dir, "pos-$stamp.db")
        val source = context.getDatabasePath(dbName)
        source.copyTo(target, overwrite = true)
        val wal = File(source.path + "-wal")
        if (wal.exists() && wal.length() > 0) {
            // Older installs used WAL: fold its pending pages into the copy, never touching the live file.
            val targetWal = File(target.path + "-wal")
            wal.copyTo(targetWal, overwrite = true)
            android.database.sqlite.SQLiteDatabase.openDatabase(target.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
                it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() }
                it.rawQuery("PRAGMA journal_mode=DELETE", null).use { c -> c.moveToFirst() }
            }
            listOf("-wal", "-shm").forEach { File(target.path + it).delete() }
        }
        list().drop(keep).forEach { it.delete() }
        return target
    }

    /** The database must be closed; the app restarts afterwards. */
    fun restore(file: File) {
        val dbFile = context.getDatabasePath(dbName)
        listOf("-journal", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        file.copyTo(dbFile, overwrite = true)
    }
}

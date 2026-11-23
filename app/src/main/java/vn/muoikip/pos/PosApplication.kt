package vn.muoikip.pos

import android.app.Application
import vn.muoikip.pos.core.DefaultData
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.data.AppDatabase
import vn.muoikip.pos.data.DatabaseBackup
import vn.muoikip.pos.data.RoomPosStore
import vn.muoikip.pos.printer.SunmiPrinter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owns the single PosEngine for the whole app, so it survives activity recreation. */
class PosApplication : Application() {
    val printer by lazy { SunmiPrinter(this).also { it.connect() } }
    val backup by lazy { DatabaseBackup(this) }

    private val db by lazy { AppDatabase.open(this) }

    val engine: PosEngine by lazy {
        dailyBackup()
        PosEngine(
            store = RoomPosStore(db.dao()),
            clock = { System.currentTimeMillis() },
            printer = printer,
            shop = DefaultData.SHOP,
        )
    }

    fun backupNow() = backup.backup(SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()))

    /** Replaces the database with [file] and restarts the app so everything reloads. */
    fun restoreAndRestart(file: java.io.File) {
        db.close()
        backup.restore(file)
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
        Runtime.getRuntime().exit(0)
    }

    /** First start of each day copies yesterday's database before it is opened. */
    private fun dailyBackup() {
        if (!getDatabasePath(AppDatabase.NAME).exists()) return
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (backup.list().any { it.name.startsWith("pos-$day") }) return
        runCatching { backup.backup(SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())) }
    }
}

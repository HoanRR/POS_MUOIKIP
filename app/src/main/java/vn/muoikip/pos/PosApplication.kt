package vn.muoikip.pos

import android.app.Application
import vn.muoikip.pos.core.DefaultData
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.data.AppDatabase
import vn.muoikip.pos.data.RoomPosStore
import vn.muoikip.pos.printer.SunmiPrinter

/** Owns the single PosEngine for the whole app, so it survives activity recreation. */
class PosApplication : Application() {
    val printer by lazy { SunmiPrinter(this).also { it.connect() } }

    val engine: PosEngine by lazy {
        PosEngine(
            store = RoomPosStore(AppDatabase.open(this).dao()),
            clock = { System.currentTimeMillis() },
            printer = printer,
            shop = DefaultData.SHOP,
        )
    }
}

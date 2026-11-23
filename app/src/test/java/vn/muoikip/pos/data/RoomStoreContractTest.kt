package vn.muoikip.pos.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.muoikip.pos.core.PosStore
import vn.muoikip.pos.core.PosStoreContract

/** Runs the PosStore contract against Room on a real SQLite file (via Robolectric). */
@RunWith(RobolectricTestRunner::class)
class RoomStoreContractTest : PosStoreContract() {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "contract-test.db"
    private var db: AppDatabase? = null

    private fun open(): PosStore {
        db?.close()
        return RoomPosStore(AppDatabase.open(context, dbName).also { db = it }.dao())
    }

    override fun newStore(): PosStore {
        db?.close(); db = null
        context.deleteDatabase(dbName)
        return open()
    }

    override fun reopen(): PosStore = open()

    @After fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }
}

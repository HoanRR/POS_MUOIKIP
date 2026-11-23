package vn.muoikip.pos.printer

import android.content.Context
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import vn.muoikip.pos.core.PrintRow
import vn.muoikip.pos.core.Receipt
import vn.muoikip.pos.core.ReceiptLayout
import vn.muoikip.pos.core.ReceiptPrinter

/** Renders [ReceiptLayout] rows on the built-in 80mm Sunmi printer (woyou.aidlservice.jiuiv5). */
class SunmiPrinter(private val context: Context) : ReceiptPrinter {
    @Volatile private var service: SunmiPrinterService? = null

    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(s: SunmiPrinterService) { service = s }
        override fun onDisconnected() { service = null }
    }

    fun connect() {
        runCatching { InnerPrinterManager.getInstance().bindService(context, callback) }
    }

    fun disconnect() {
        runCatching { InnerPrinterManager.getInstance().unBindService(context, callback) }
    }

    override fun print(receipt: Receipt): String? {
        val p = service ?: return "Chưa kết nối được máy in. Thử lại sau vài giây."
        stateError(runCatching { p.updatePrinterState() }.getOrDefault(1))?.let { return it }
        val rows = ReceiptLayout.rows(receipt)
        // Preferred: large bold receipt drawn as an image. Falls back to the printer's own font.
        runCatching {
            p.printerInit(null)
            p.setAlignment(1, null)
            ReceiptRenderer.segments(rows).forEach { seg ->
                when (seg) {
                    is android.graphics.Bitmap -> { p.printBitmap(seg, null); seg.recycle() }
                    is PrintRow.Qr -> { p.lineWrap(1, null); p.printQRCode(seg.payload, 9, 1, null); p.lineWrap(1, null) }
                }
            }
            p.lineWrap(4, null)
            p.cutPaper(null)
        }.onSuccess { return null }
        return runCatching {
            p.printerInit(null)
            rows.forEach { row ->
                when (row) {
                    is PrintRow.Title -> {
                        p.setAlignment(1, null); p.printTextWithFont(row.text + "\n", "", 36f, null)
                    }
                    is PrintRow.Center -> {
                        p.setAlignment(1, null); p.printTextWithFont(row.text + "\n", "", 24f, null)
                    }
                    is PrintRow.Text -> {
                        p.setAlignment(0, null); p.printTextWithFont(row.text + "\n", "", 24f, null)
                    }
                    is PrintRow.Item -> {
                        p.setAlignment(0, null)
                        p.setFontSize(24f, null)
                        p.printColumnsString(
                            arrayOf(row.name, row.quantity, row.amount),
                            intArrayOf(6, 1, 3), intArrayOf(0, 1, 2), null,
                        )
                    }
                    is PrintRow.Amount -> {
                        p.setAlignment(0, null)
                        p.setFontSize(if (row.emphasized) 30f else 24f, null)
                        p.printColumnsString(arrayOf(row.label, row.value), intArrayOf(1, 1), intArrayOf(0, 2), null)
                    }
                    is PrintRow.Qr -> {
                        p.setAlignment(1, null)
                        p.printQRCode(row.payload, 8, 1, null) // module size 8, error level M
                        p.lineWrap(1, null)
                    }
                    PrintRow.Divider -> {
                        p.setAlignment(0, null); p.printTextWithFont("-".repeat(48) + "\n", "", 24f, null)
                    }
                }
            }
            p.lineWrap(4, null)
            p.cutPaper(null)
            null
        }.getOrElse { "Lỗi máy in: ${it.message}" }
    }

    /** Sunmi printer states; 1 = normal. */
    private fun stateError(state: Int): String? = when (state) {
        1 -> null
        2 -> "Máy in đang khởi động, thử lại sau vài giây."
        3, 505, 507 -> "Máy in lỗi kết nối."
        4 -> "Máy in hết giấy. Thay giấy rồi bấm In lại."
        5 -> "Máy in quá nóng, chờ một lát rồi In lại."
        6 -> "Nắp máy in đang mở. Đóng nắp rồi In lại."
        7 -> "Dao cắt giấy bị lỗi."
        else -> null
    }
}

package vn.muoikip.pos.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ReceiptKind { PRE_BILL, BILL }

data class ReceiptItem(val name: String, val quantity: Int, val unitPrice: Vnd, val amount: Vnd)

data class Receipt(
    val shop: ShopInfo,
    val kind: ReceiptKind,
    /** "Sân trước · Bàn 3" or "Phòng VIP 1". */
    val placeLabel: String,
    val openedAt: Long,
    val printedAt: Long,
    val items: List<ReceiptItem>,
    /** e.g. "Tiền phòng VIP 2 – 2 giờ 15 phút"; null for normal tables. */
    val roomFeeLabel: String?,
    val roomFee: Vnd?,
    val total: Vnd,
    val cashGiven: Vnd?,
    val change: Vnd?,
)

fun formatVnd(amount: Vnd): String {
    val digits = kotlin.math.abs(amount).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return (if (amount < 0) "-" else "") + grouped + "đ"
}

/** Printer-independent rows for an 80mm receipt. The Sunmi printer renders these. */
sealed interface PrintRow {
    data class Title(val text: String) : PrintRow
    data class Center(val text: String) : PrintRow
    data class Text(val text: String) : PrintRow
    /** Item row: name, quantity, amount. */
    data class Item(val name: String, val quantity: String, val amount: String) : PrintRow
    data class Amount(val label: String, val value: String, val emphasized: Boolean = false) : PrintRow
    data object Divider : PrintRow
}

object ReceiptLayout {
    fun rows(r: Receipt, timeZone: TimeZone = TimeZone.getDefault()): List<PrintRow> {
        val fmt = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.US).apply { this.timeZone = timeZone }
        fun t(ms: Long) = fmt.format(Date(ms))
        return buildList {
            add(PrintRow.Title(r.shop.name))
            add(PrintRow.Center(r.shop.address))
            add(PrintRow.Center("ĐT: ${r.shop.phone}"))
            add(PrintRow.Divider)
            add(PrintRow.Title(if (r.kind == ReceiptKind.PRE_BILL) "TẠM TÍNH" else "HÓA ĐƠN"))
            add(PrintRow.Text(r.placeLabel))
            add(PrintRow.Text("Giờ vào: ${t(r.openedAt)}"))
            add(PrintRow.Text("Giờ in:  ${t(r.printedAt)}"))
            add(PrintRow.Divider)
            add(PrintRow.Item("Món", "SL", "Thành tiền"))
            r.items.forEach {
                add(PrintRow.Item(it.name, it.quantity.toString(), formatVnd(it.amount)))
                if (it.quantity > 1) add(PrintRow.Text("   ${formatVnd(it.unitPrice)} x ${it.quantity}"))
            }
            if (r.roomFeeLabel != null && r.roomFee != null) {
                add(PrintRow.Item(r.roomFeeLabel, "", formatVnd(r.roomFee)))
            }
            add(PrintRow.Divider)
            add(PrintRow.Amount("TỔNG CỘNG", formatVnd(r.total), emphasized = true))
            if (r.cashGiven != null) add(PrintRow.Amount("Khách đưa", formatVnd(r.cashGiven)))
            if (r.change != null) add(PrintRow.Amount("Tiền thối", formatVnd(r.change)))
            add(PrintRow.Divider)
            add(
                PrintRow.Center(
                    if (r.kind == ReceiptKind.PRE_BILL) "Phiếu tạm tính – chưa thanh toán" else "Cảm ơn quý khách!"
                )
            )
        }
    }
}

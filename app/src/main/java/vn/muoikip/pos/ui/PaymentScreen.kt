package vn.muoikip.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.muoikip.pos.core.CheckoutResult
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.core.Receipt
import vn.muoikip.pos.core.formatVnd

/** Confirm screen: every item and the total, one button prints the bill and closes the table. */
@Composable
fun PaymentScreen(
    engine: PosEngine,
    tableId: String,
    refresh: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") refresh
    val table = engine.table(tableId)
    val order = engine.order(tableId)
    var failed by remember { mutableStateOf<Pair<Receipt, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // ---------- Items ----------
        Column(Modifier.weight(1.3f).fillMaxHeight().background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kiểm tra món – ${table.label}", style = typeFor(30), fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Món", style = typeFor(20), color = PosColors.Muted, modifier = Modifier.weight(1f))
                Text("SL", style = typeFor(20), color = PosColors.Muted, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                Text("Thành tiền", style = typeFor(20), color = PosColors.Muted, modifier = Modifier.width(170.dp), textAlign = TextAlign.End)
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f)) {
                items(order?.order?.lines.orEmpty().sortedBy { it.seq }, key = { it.id }) { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = typeFor(26), fontWeight = FontWeight.Bold)
                            Text(l.unitPrice?.let { formatVnd(it) } ?: "Chưa nhập giá", style = typeFor(20),
                                color = if (l.unitPrice == null) PosColors.Danger else PosColors.Muted)
                        }
                        Text("${l.quantity}", style = typeFor(28), fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                        Text(l.amount?.let { formatVnd(it) } ?: "—", style = typeFor(26), fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(170.dp), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
                if (order?.roomFee != null) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(order.roomFeeLabel ?: "Tiền phòng", style = typeFor(24), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(formatVnd(order.roomFee), style = typeFor(26), fontWeight = FontWeight.Bold, modifier = Modifier.width(170.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }

        // ---------- Total & print ----------
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Tổng tiền", style = typeFor(30), color = PosColors.Muted)
            Text(formatVnd(order?.total ?: 0), style = typeFor(80), fontWeight = FontWeight.Black)
            if (engine.bankAccount() != null) {
                Text("Hóa đơn có mã QR để khách chuyển khoản.", style = typeFor(22), color = PosColors.Muted)
            }
            Spacer(Modifier.weight(1f))
            BigButton(
                "In hóa đơn",
                {
                    when (val r = engine.checkout(tableId)) {
                        is CheckoutResult.Paid -> if (r.printError == null) onDone() else failed = r.receipt to r.printError
                        is CheckoutResult.MissingPrices -> message = "Còn món chưa nhập giá. Quay lại để nhập."
                        CheckoutResult.NothingToPay -> message = "Bàn chưa có gì để thanh toán."
                        is CheckoutResult.InsufficientCash -> Unit // not used: no cash step
                    }
                },
                Modifier.fillMaxWidth(), color = PosColors.Pay, height = 120.dp, fontSize = 40,
                enabled = order != null,
            )
            BigButton("← Quay lại", onBack, Modifier.fillMaxWidth(), color = PosColors.Selected, height = 80.dp)
        }
    }

    failed?.let { (receipt, error) ->
        // The table is already closed; only the paper is missing.
        MessageDialog(
            message = "Đã thanh toán ${receipt.placeLabel} nhưng chưa in được hóa đơn:\n$error",
            onDismiss = { failed = null; onDone() },
            actionText = "In lại",
            onAction = {
                val again = engine.reprint(receipt)
                if (again == null) { failed = null; onDone() } else failed = receipt to again
            },
        )
    }
    message?.let { MessageDialog(it, onDismiss = { message = null }) }
}

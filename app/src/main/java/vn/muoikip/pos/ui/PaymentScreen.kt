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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.muoikip.pos.core.CheckoutResult
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.core.Receipt
import vn.muoikip.pos.core.formatVnd

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
    var cash by remember { mutableLongStateOf(0L) }
    var paid by remember { mutableStateOf<Receipt?>(null) }
    var printError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val total = paid?.total ?: order?.total ?: 0
    val change = cash - total

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Thanh toán – ${table.label}", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("Tổng tiền", fontSize = 28.sp, color = PosColors.Muted)
            Text(formatVnd(total), fontSize = 72.sp, fontWeight = FontWeight.Black)
            Text("Khách đưa", fontSize = 28.sp, color = PosColors.Muted)
            Text(formatVnd(cash), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
            Text(
                if (change >= 0) "Tiền thối: ${formatVnd(change)}" else "Còn thiếu: ${formatVnd(-change)}",
                fontSize = 44.sp, fontWeight = FontWeight.Black,
                color = if (change >= 0) PosColors.Pay else PosColors.Danger,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("← Quay lại", onBack, Modifier.weight(1f), color = PosColors.Selected, height = 96.dp)
                BigButton(
                    "Thanh toán & In",
                    {
                        when (val r = engine.checkout(tableId, cash)) {
                            is CheckoutResult.Paid -> { paid = r.receipt; printError = r.printError }
                            is CheckoutResult.InsufficientCash -> message = "Khách đưa chưa đủ. Tổng: ${formatVnd(r.total)}"
                            is CheckoutResult.MissingPrices -> message = "Còn món chưa nhập giá. Quay lại để nhập."
                            CheckoutResult.NothingToPay -> message = "Bàn chưa có gì để thanh toán."
                        }
                    },
                    Modifier.weight(1.4f), color = PosColors.Pay, height = 96.dp, fontSize = 30,
                    enabled = paid == null && change >= 0 && order != null,
                )
            }
        }
        Column(Modifier.weight(0.8f).fillMaxHeight().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Đủ tiền", { cash = total }, Modifier.weight(1.3f), color = PosColors.Pay, fontSize = 22)
                listOf(100_000L, 200_000L, 500_000L).forEach { v ->
                    BigButton("${v / 1000}K", { cash = v }, Modifier.weight(1f), color = PosColors.Primary, fontSize = 22)
                }
            }
            NumberPad(cash, { cash = it }, Modifier.fillMaxWidth(), keyHeight = 84.dp)
        }
    }

    paid?.let { receipt ->
        val err = printError
        MessageDialog(
            message = buildString {
                append("Đã thanh toán ${table.label}.\nTiền thối: ${formatVnd(receipt.change ?: 0)}")
                if (err != null) append("\n\nChưa in được hóa đơn: $err")
            },
            onDismiss = { paid = null; onDone() },
            actionText = if (err != null) "In lại" else "In thêm bản",
            onAction = { printError = engine.reprint(receipt) },
        )
    }
    message?.let { MessageDialog(it, onDismiss = { message = null }) }
}

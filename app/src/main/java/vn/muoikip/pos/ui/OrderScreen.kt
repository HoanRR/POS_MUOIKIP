package vn.muoikip.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.muoikip.pos.core.OrderLine
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.core.formatVnd

@Composable
fun OrderScreen(
    engine: PosEngine,
    tableId: String,
    refresh: Int,
    onBack: () -> Unit,
    onPay: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") refresh
    val table = engine.table(tableId)
    val order = engine.order(tableId)
    val categories = engine.categories()
    var categoryId by rememberSaveable { mutableStateOf(categories.first().id) }

    var priceLine by remember { mutableStateOf<OrderLine?>(null) }
    var confirmRemove by remember { mutableStateOf<OrderLine?>(null) }
    var editRoomFee by remember { mutableStateOf(false) }
    var confirmCancelRoom by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize()) {
        // ---------- Menu (left) ----------
        Column(Modifier.weight(0.56f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("← Sơ đồ bàn", onBack, color = PosColors.Selected)
                Text(table.label, fontSize = 32.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.forEach { c ->
                    val sel = c.id == categoryId
                    BigButton(
                        c.name, { categoryId = c.id },
                        color = if (sel) PosColors.Selected else PosColors.Chip,
                        textColor = if (sel) Color.White else PosColors.Ink,
                        height = 64.dp,
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(engine.menu(categoryId), key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.height(110.dp).clickable { engine.addItem(tableId, item.id) },
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(item.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(
                                item.price?.let { formatVnd(it) } ?: "Giá nhập khi bán",
                                fontSize = 20.sp,
                                color = if (item.price == null) PosColors.Danger else PosColors.Muted,
                            )
                        }
                    }
                }
            }
        }

        // ---------- Order (right) ----------
        Column(
            Modifier.weight(0.44f).fillMaxHeight().background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            table.elapsedMs?.let { Text("Đã ngồi: ${elapsedText(it)}", fontSize = 22.sp, color = PosColors.Muted) }
            if (order == null || (order.order.lines.isEmpty() && order.roomFee == null)) {
                Text("Chưa có món. Chạm vào món bên trái để thêm.", fontSize = 24.sp, color = PosColors.Muted, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(order.order.lines.sortedBy { it.seq }, key = { it.id }) { line ->
                        LineRow(
                            line = line,
                            onMinus = {
                                if (line.quantity <= 1) confirmRemove = line
                                else engine.setQuantity(tableId, line.id, line.quantity - 1)
                            },
                            onPlus = { engine.setQuantity(tableId, line.id, line.quantity + 1) },
                            onPrice = { priceLine = line },
                            onRemove = { confirmRemove = line },
                        )
                        HorizontalDivider()
                    }
                    if (order.roomFee != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable { editRoomFee = true }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(order.roomFeeLabel ?: "Tiền phòng", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(formatVnd(order.roomFee), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PosColors.Primary)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 3.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TỔNG", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatVnd(order?.total ?: 0), fontSize = 40.sp, fontWeight = FontWeight.Black)
            }
            if (order != null && order.order.lines.isEmpty() && table.zone.timed) {
                BigButton("Hủy mở phòng", { confirmCancelRoom = true }, Modifier.fillMaxWidth(), color = PosColors.Chip, textColor = PosColors.Danger, height = 60.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(
                    "In tạm tính",
                    { engine.printPreBill(tableId)?.let { message = it } },
                    Modifier.weight(1f), color = PosColors.Primary, height = 84.dp, enabled = order != null,
                )
                BigButton(
                    "Thanh toán",
                    {
                        val missing = order?.missingPriceLineIds.orEmpty()
                        if (missing.isNotEmpty()) priceLine = order!!.order.lines.first { it.id == missing.first() }
                        else onPay()
                    },
                    Modifier.weight(1f), color = PosColors.Pay, height = 84.dp, fontSize = 28, enabled = order != null,
                )
            }
        }
    }

    priceLine?.let { line ->
        NumberPadDialog(
            title = if (line.unitPrice == null) "Nhập giá: ${line.name}" else "Đổi giá: ${line.name}",
            initial = line.unitPrice ?: 0,
            onDismiss = { priceLine = null },
            onConfirm = { engine.setLinePrice(tableId, line.id, it); priceLine = null },
        )
    }
    confirmRemove?.let { line ->
        ConfirmDialog(
            message = "Xóa \"${line.name}\" khỏi bàn?",
            confirmText = "Xóa",
            onDismiss = { confirmRemove = null },
            onConfirm = { engine.removeLine(tableId, line.id); confirmRemove = null },
        )
    }
    if (editRoomFee && order?.roomFee != null) {
        NumberPadDialog(
            title = "Sửa tiền phòng",
            initial = order.roomFee,
            onDismiss = { editRoomFee = false },
            onConfirm = { engine.setRoomFeeOverride(tableId, it); editRoomFee = false },
            extraLabel = "Tính lại theo giờ",
            onExtra = { engine.setRoomFeeOverride(tableId, null); editRoomFee = false },
        )
    }
    if (confirmCancelRoom) {
        ConfirmDialog(
            message = "Hủy mở ${table.label}? Không tính tiền phòng.",
            confirmText = "Hủy phòng",
            onDismiss = { confirmCancelRoom = false },
            onConfirm = { engine.cancelEmpty(tableId); confirmCancelRoom = false; onBack() },
        )
    }
    message?.let { m ->
        MessageDialog(m, onDismiss = { message = null }, actionText = "In lại", onAction = {
            message = engine.printPreBill(tableId)
        })
    }
}

@Composable
private fun LineRow(
    line: OrderLine,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onPrice: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                line.amount?.let { formatVnd(it) } ?: "Chưa nhập giá",
                fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = if (line.amount == null) PosColors.Danger else PosColors.Ink,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("−", onMinus, Modifier.width(64.dp), color = PosColors.Chip, textColor = PosColors.Ink, height = 56.dp, fontSize = 30)
            Text("${line.quantity}", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
            BigButton("+", onPlus, Modifier.width(64.dp), color = PosColors.Chip, textColor = PosColors.Ink, height = 56.dp, fontSize = 30)
            BigButton(
                line.unitPrice?.let { formatVnd(it) } ?: "Nhập giá",
                onPrice,
                Modifier.weight(1f),
                color = if (line.unitPrice == null) PosColors.Danger else PosColors.Chip,
                textColor = if (line.unitPrice == null) Color.White else PosColors.Primary,
                height = 56.dp, fontSize = 20,
            )
            BigButton("Xóa", onRemove, color = PosColors.Chip, textColor = PosColors.Danger, height = 56.dp, fontSize = 20)
        }
    }
}

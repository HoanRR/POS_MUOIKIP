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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
    onSwitchTable: (String) -> Unit,
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
    var picking by remember { mutableStateOf<String?>(null) } // "move" or "merge"

    Row(Modifier.fillMaxSize()) {
        // ---------- Menu (left) ----------
        Column(Modifier.weight(0.56f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("← Sơ đồ bàn", onBack, color = PosColors.Primary)
                Text(table.label, style = typeFor(32), fontWeight = FontWeight.Black)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(engine.menu(categoryId), key = { it.id }) { item ->
                    // Immediate feedback: the tile shows how many of this item the table already has.
                    val onTable = order?.order?.lines?.filter { it.menuItemId == item.id }?.sumOf { it.quantity } ?: 0
                    Surface(
                        onClick = { engine.addItem(tableId, item.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (onTable > 0) PosColors.PrimarySoft else PosColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(
                            if (onTable > 0) 3.dp else 1.dp,
                            if (onTable > 0) PosColors.Primary else PosColors.Border,
                        ),
                        modifier = Modifier.height(112.dp),
                    ) {
                        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.price?.let { formatVnd(it) } ?: "Giá nhập khi bán",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.price == null) PosColors.Danger else PosColors.Muted,
                                    modifier = Modifier.weight(1f),
                                )
                                if (onTable > 0) {
                                    Text("Đã gọi $onTable", style = MaterialTheme.typography.labelMedium, color = PosColors.Primary)
                                }
                            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BigButton("Chuyển bàn", { picking = "move" }, color = PosColors.Chip, textColor = PosColors.Primary, height = 60.dp, fontSize = 20, enabled = order != null)
                BigButton("Gộp bàn", { picking = "merge" }, color = PosColors.Chip, textColor = PosColors.Primary, height = 60.dp, fontSize = 20,
                    enabled = order != null && !table.zone.timed)
                table.elapsedMs?.let { Text("Đã ngồi: ${elapsedText(it)}", style = typeFor(22), color = PosColors.Muted) }
            }
            if (order == null || (order.order.lines.isEmpty() && order.roomFee == null)) {
                Text("Chưa có món. Chạm vào món bên trái để thêm.", style = typeFor(24), color = PosColors.Muted, modifier = Modifier.weight(1f))
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
                                Text(order.roomFeeLabel ?: "Tiền phòng", style = typeFor(22), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(formatVnd(order.roomFee), style = typeFor(24), fontWeight = FontWeight.Bold, color = PosColors.Primary)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 3.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TỔNG", style = typeFor(30), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatVnd(order?.total ?: 0), style = typeFor(40), fontWeight = FontWeight.Black)
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
            confirmText = "Xóa món",
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
    picking?.let { mode ->
        val move = mode == "move"
        TablePickerDialog(
            title = if (move) "Chuyển ${table.label} sang bàn trống:" else "Gộp món của ${table.label} vào bàn:",
            targets = if (move) engine.moveTargets(tableId) else engine.mergeTargets(tableId),
            emptyText = if (move) "Không còn bàn trống cùng loại." else "Không có bàn nào khác đang có khách.",
            onDismiss = { picking = null },
            onPick = { target ->
                if (move) engine.moveTable(tableId, target) else engine.mergeInto(tableId, target)
                picking = null
                onSwitchTable(target)
            },
        )
    }
    message?.let { m ->
        MessageDialog(m, onDismiss = { message = null }, actionText = "In lại", onAction = {
            message = engine.printPreBill(tableId)
        })
    }
}

@Composable
private fun TablePickerDialog(
    title: String,
    targets: List<vn.muoikip.pos.core.TableView>,
    emptyText: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var chosen by remember { mutableStateOf<vn.muoikip.pos.core.TableView?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = typeFor(26), fontWeight = FontWeight.Bold) },
        text = {
            if (targets.isEmpty()) Text(emptyText, style = typeFor(24))
            else Column(
                Modifier.height(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                targets.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            BigButton(
                                t.label.replace(" · ", "\n"), { chosen = t }, Modifier.weight(1f), height = 84.dp, fontSize = 18,
                                color = if (t.occupied) PosColors.TableBusy else PosColors.Chip, textColor = PosColors.Ink,
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { BigButton("Đóng", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
    chosen?.let { t ->
        ConfirmDialog(
            message = "Xác nhận: ${title.removeSuffix(":")} ${t.label}?",
            confirmText = "Xác nhận",
            confirmColor = PosColors.Pay,
            onDismiss = { chosen = null },
            onConfirm = { onPick(t.table.id) },
        )
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.name, style = typeFor(24), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                line.amount?.let { formatVnd(it) } ?: "Chưa nhập giá",
                style = typeFor(24), fontWeight = FontWeight.Bold,
                color = if (line.amount == null) PosColors.Danger else PosColors.Ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("−", onMinus, Modifier.width(64.dp), color = PosColors.Chip, textColor = PosColors.Ink, height = 56.dp, fontSize = 30)
            Text("${line.quantity}", style = typeFor(28), fontWeight = FontWeight.Black, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
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

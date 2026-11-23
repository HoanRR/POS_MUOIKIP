package vn.muoikip.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import vn.muoikip.pos.core.BusinessDay
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.core.TableView
import vn.muoikip.pos.core.Zone
import vn.muoikip.pos.core.formatVnd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Báo cáo ----------------

@Composable
fun ReportPanel(engine: PosEngine) {
    var dayAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val (from, until) = BusinessDay.range(dayAt)
    val report = engine.report(from, until)
    val bills = engine.paidBills(from, until).reversed()
    val dayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    var message by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("‹ Hôm trước", { dayAt -= 24 * 3600_000L }, color = PosColors.Chip, textColor = PosColors.Ink, height = 60.dp)
                Text("Ngày ${dayFmt.format(Date(from))}", style = typeFor(26), fontWeight = FontWeight.Bold)
                BigButton("Hôm sau ›", { dayAt += 24 * 3600_000L }, color = PosColors.Chip, textColor = PosColors.Ink, height = 60.dp,
                    enabled = until <= System.currentTimeMillis() + 24 * 3600_000L - 1)
            }
            Text("Tính từ 6:00 sáng đến 6:00 sáng hôm sau", style = typeFor(18), color = PosColors.Muted)
            Text("Doanh thu: ${formatVnd(report.revenue)}", style = typeFor(40), fontWeight = FontWeight.Black, color = PosColors.Pay)
            Text("Số hóa đơn: ${report.billCount}   ·   Tiền phòng: ${formatVnd(report.roomFees)}", style = typeFor(24))
            HorizontalDivider()
            Text("Món bán được", style = typeFor(24), fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.background(Color.White).padding(8.dp)) {
                items(report.items, key = { it.name }) { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(l.name, style = typeFor(22), modifier = Modifier.weight(1f))
                        Text("x${l.quantity}", style = typeFor(22), modifier = Modifier.padding(horizontal = 16.dp))
                        Text(formatVnd(l.amount), style = typeFor(22), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hóa đơn trong ngày", style = typeFor(24), fontWeight = FontWeight.Bold)
            LazyColumn {
                items(bills, key = { it.id }) { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(timeFmt.format(Date(b.closedAt ?: 0)), style = typeFor(22), modifier = Modifier.padding(end = 12.dp))
                        Text(b.placeLabel ?: "—", style = typeFor(22), modifier = Modifier.weight(1f))
                        Text(formatVnd(b.total ?: 0), style = typeFor(22), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                        BigButton("In lại", { engine.reprintPaid(b.id)?.let { message = it } }, color = PosColors.Chip,
                            textColor = PosColors.Primary, height = 56.dp, fontSize = 20)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    message?.let { MessageDialog(it, onDismiss = { message = null }) }
}

// ---------------- Khu & bàn ----------------

@Composable
fun ZonesPanel(engine: PosEngine) {
    var renaming by remember { mutableStateOf<Zone?>(null) }
    var deletingZone by remember { mutableStateOf<Zone?>(null) }
    var deletingTable by remember { mutableStateOf<TableView?>(null) }
    var rateFor by remember { mutableStateOf<TableView?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(engine.zones(), key = { it.id }) { z ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(z.name + if (z.timed) " (tính giờ)" else "", style = typeFor(28), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    BigButton("Đổi tên", { renaming = z }, color = PosColors.Chip, textColor = PosColors.Primary, height = 60.dp)
                    BigButton("Xóa khu", {
                        if (engine.canDeleteZone(z.id)) deletingZone = z else message = "Khu ${z.name} còn bàn đang có khách (hoặc là khu cuối cùng)."
                    }, color = PosColors.Chip, textColor = PosColors.Danger, height = 60.dp)
                }
                engine.tables(z.id).chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            Column(Modifier.weight(1f).background(PosColors.Chip).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (z.timed) "${z.name} ${t.table.number}" else "Bàn ${t.table.number}", style = typeFor(22), fontWeight = FontWeight.Bold)
                                if (z.timed) {
                                    BigButton("${formatVnd(t.table.hourlyRate ?: 0)}/giờ", { rateFor = t }, color = PosColors.Background,
                                        textColor = PosColors.Primary, height = 52.dp, fontSize = 18)
                                }
                                BigButton("Xóa", {
                                    if (engine.canDeleteTable(t.table.id)) deletingTable = t else message = "${t.label} đang có khách, không xóa được."
                                }, color = PosColors.Background, textColor = PosColors.Danger, height = 52.dp, fontSize = 18)
                            }
                        }
                        repeat(6 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                    }
                }
                HorizontalDivider(thickness = 2.dp)
            }
        }
    }

    renaming?.let { z ->
        TextInputDialog("Đổi tên khu", initial = z.name, onDismiss = { renaming = null }) { engine.renameZone(z.id, it); renaming = null }
    }
    deletingZone?.let { z ->
        ConfirmDialog("Xóa khu ${z.name} và tất cả bàn trong khu?", "Xóa khu", onDismiss = { deletingZone = null },
            onConfirm = { engine.deleteZone(z.id); deletingZone = null })
    }
    deletingTable?.let { t ->
        ConfirmDialog("Xóa ${t.label}?", "Xóa bàn", onDismiss = { deletingTable = null },
            onConfirm = { engine.deleteTable(t.table.id); deletingTable = null })
    }
    rateFor?.let { t ->
        NumberPadDialog("Giá ${t.label} mỗi giờ", t.table.hourlyRate ?: 0, onDismiss = { rateFor = null },
            onConfirm = { engine.setRoomRate(t.table.id, it); rateFor = null })
    }
    message?.let { MessageDialog(it, onDismiss = { message = null }) }
}

// ---------------- Mã PIN ----------------

@Composable
fun PinPanel(engine: PosEngine) {
    var entering by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            if (engine.hasPin()) "Đang bật mã PIN: phải nhập mã mới vào được Cài đặt."
            else "Chưa có mã PIN: ai cũng vào được Cài đặt (sửa menu, giá phòng, ngân hàng).",
            style = typeFor(26),
        )
        Text("Màn bán hàng (gọi món, thanh toán) không bao giờ cần mã PIN.", style = typeFor(20), color = PosColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(if (engine.hasPin()) "Đổi mã PIN" else "Đặt mã PIN", { entering = true }, color = PosColors.Pay)
            if (engine.hasPin()) BigButton("Tắt mã PIN", { confirmClear = true }, color = PosColors.Chip, textColor = PosColors.Danger)
        }
    }
    if (entering) {
        PinDialog("Nhập mã PIN mới (4–6 số)", onDismiss = { entering = false }) { pin ->
            if (pin.length in 4..6) { engine.setPin(pin); entering = false; message = "Đã lưu mã PIN. Hãy ghi nhớ mã này." }
            else message = "Mã PIN cần 4 đến 6 chữ số."
        }
    }
    if (confirmClear) {
        ConfirmDialog("Tắt mã PIN?", "Tắt mã PIN", onDismiss = { confirmClear = false }, onConfirm = { engine.setPin(null); confirmClear = false })
    }
    message?.let { MessageDialog(it, onDismiss = { message = null }) }
}

/** Digits-only dialog that shows dots instead of the PIN. */
@Composable
fun PinDialog(title: String, error: String? = null, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = typeFor(28), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("●".repeat(pin.length).ifEmpty { " " }, style = typeFor(44), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                if (error != null) Text(error, color = PosColors.Danger, style = typeFor(22))
                listOf("123", "456", "789", "C0⌫").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            BigButton(k.toString(), {
                                pin = when (k) { 'C' -> ""; '⌫' -> pin.dropLast(1); else -> (pin + k).take(6) }
                            }, Modifier.weight(1f), color = PosColors.Chip, textColor = PosColors.Ink, height = 64.dp, fontSize = 30)
                        }
                    }
                }
            }
        },
        confirmButton = { BigButton("Xong", { onSubmit(pin); pin = "" }, color = PosColors.Pay, enabled = pin.isNotEmpty()) },
        dismissButton = { BigButton("Hủy", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
}

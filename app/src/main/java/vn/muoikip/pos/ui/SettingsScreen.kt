package vn.muoikip.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.muoikip.pos.core.MenuItem
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.core.Vnd
import vn.muoikip.pos.core.formatVnd
import java.io.File

/** Callbacks into the app shell for things the engine does not own. */
class SettingsActions(
    val listBackups: () -> List<File>,
    val backupNow: () -> File,
    val restore: (File) -> Unit,
)

private enum class SettingsTab(val title: String) {
    REPORT("Báo cáo"), MENU("Thực đơn"), ZONES("Khu & bàn"), BANK("Ngân hàng"), PIN("Mã PIN"), BACKUP("Sao lưu")
}

@Composable
fun SettingsScreen(engine: PosEngine, refresh: Int, actions: SettingsActions, onBack: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") refresh
    var tab by rememberSaveable { mutableStateOf(SettingsTab.REPORT) }
    var unlocked by rememberSaveable { mutableStateOf(!engine.hasPin()) }
    var pinError by remember { mutableStateOf<String?>(null) }
    if (!unlocked) {
        PinDialog("Nhập mã PIN để vào Cài đặt", error = pinError, onDismiss = onBack) { pin ->
            if (engine.checkPin(pin)) unlocked = true else pinError = "Sai mã PIN"
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            BigButton("← Sơ đồ bàn", onBack, color = PosColors.Primary)
            SettingsTab.entries.forEach { t ->
                val sel = t == tab
                BigButton(
                    t.title, { tab = t },
                    color = if (sel) PosColors.Selected else PosColors.Chip,
                    textColor = if (sel) Color.White else PosColors.Ink,
                )
            }
        }
        when (tab) {
            SettingsTab.REPORT -> ReportPanel(engine)
            SettingsTab.MENU -> MenuEditor(engine)
            SettingsTab.ZONES -> ZonesPanel(engine)
            SettingsTab.BANK -> BankPanel(engine)
            SettingsTab.PIN -> PinPanel(engine)
            SettingsTab.BACKUP -> BackupPanel(actions)
        }
    }
}

@Composable
private fun MenuEditor(engine: PosEngine) {
    engine.observeChanges()
    val categories = engine.categories()
    var categoryId by rememberSaveable { mutableStateOf(categories.first().id) }
    val editingItems = engine.menuForEditing(categoryId)
    var editing by remember { mutableStateOf<MenuItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.forEach { c ->
                val sel = c.id == categoryId
                BigButton(
                    c.name, { categoryId = c.id }, Modifier.fillMaxWidth(),
                    color = if (sel) PosColors.Selected else PosColors.Chip,
                    textColor = if (sel) Color.White else PosColors.Ink,
                )
            }
            BigButton("+ Thêm nhóm", { addingCategory = true }, Modifier.fillMaxWidth(), color = PosColors.Background, textColor = PosColors.Primary)
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("+ Thêm món", { adding = true }, color = PosColors.Pay)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(editingItems, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            item.name, style = typeFor(24), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            color = if (item.active) PosColors.Ink else PosColors.Muted,
                        )
                        Text(
                            if (!item.active) "Đang ẩn" else item.price?.let { formatVnd(it) + if (item.soldByWeight) "/kg" else "" } ?: "Giá nhập khi bán",
                            style = typeFor(22), color = if (item.active) PosColors.Ink else PosColors.Danger,
                        )
                        BigButton("Sửa", { editing = item }, color = PosColors.Chip, textColor = PosColors.Primary, height = 60.dp)
                        BigButton(
                            if (item.active) "Ẩn" else "Hiện",
                            { engine.setMenuItemActive(item.id, !item.active) },
                            color = PosColors.Chip, textColor = if (item.active) PosColors.Danger else PosColors.Pay, height = 60.dp,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) {
        MenuItemDialog(
            title = "Thêm món", initialName = "", initialPrice = null, initialCategoryId = categoryId, initialByWeight = false, engine = engine,
            onDismiss = { adding = false },
            onSave = { name, price, cat, byWeight -> engine.addMenuItem(cat, name, price, byWeight); adding = false },
        )
    }
    editing?.let { item ->
        MenuItemDialog(
            title = "Sửa món", initialName = item.name, initialPrice = item.price, initialCategoryId = item.categoryId, initialByWeight = item.soldByWeight, engine = engine,
            onDismiss = { editing = null },
            onSave = { name, price, cat, byWeight -> engine.updateMenuItem(item.id, name, price, cat, byWeight); editing = null },
        )
    }
    if (addingCategory) {
        TextInputDialog("Tên nhóm món mới", onDismiss = { addingCategory = false }) {
            categoryId = engine.addCategory(it).id
            addingCategory = false
        }
    }
}

@Composable
private fun MenuItemDialog(
    title: String,
    initialName: String,
    initialPrice: Vnd?,
    initialCategoryId: String,
    engine: PosEngine,
    onDismiss: () -> Unit,
    initialByWeight: Boolean,
    onSave: (String, Vnd?, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf(initialPrice) }
    var categoryId by remember { mutableStateOf(initialCategoryId) }
    var byWeight by remember { mutableStateOf(initialByWeight) }
    var pickingPrice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = typeFor(28), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Tên món") },
                    singleLine = true, textStyle = typeFor(26), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(
                        price?.let { "Giá: ${formatVnd(it)}" } ?: "Giá: nhập khi bán",
                        { pickingPrice = true }, Modifier.weight(1f), color = PosColors.Chip, textColor = PosColors.Primary,
                    )
                    BigButton("Nhập khi bán", { price = null }, color = if (price == null) PosColors.Selected else PosColors.Chip,
                        textColor = if (price == null) Color.White else PosColors.Ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Bán theo phần", { byWeight = false }, Modifier.weight(1f),
                        color = if (!byWeight) PosColors.Selected else PosColors.Chip, textColor = if (!byWeight) Color.White else PosColors.Ink, height = 60.dp, fontSize = 20)
                    BigButton("Bán theo ký (giá/kg)", { byWeight = true }, Modifier.weight(1f),
                        color = if (byWeight) PosColors.Selected else PosColors.Chip, textColor = if (byWeight) Color.White else PosColors.Ink, height = 60.dp, fontSize = 20)
                }
                Text("Nhóm:", style = typeFor(22))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    engine.categories().forEach { c ->
                        val sel = c.id == categoryId
                        BigButton(c.name, { categoryId = c.id }, color = if (sel) PosColors.Selected else PosColors.Chip,
                            textColor = if (sel) Color.White else PosColors.Ink, height = 60.dp, fontSize = 18)
                    }
                }
            }
        },
        confirmButton = { BigButton("Lưu", { onSave(name, price, categoryId, byWeight) }, color = PosColors.Pay, enabled = name.isNotBlank()) },
        dismissButton = { BigButton("Hủy", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
    if (pickingPrice) {
        NumberPadDialog("Giá món", price ?: 0, onDismiss = { pickingPrice = false }, onConfirm = { price = it; pickingPrice = false })
    }
}

@Composable
fun TextInputDialog(title: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = typeFor(28), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                textStyle = typeFor(26), modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { BigButton("Lưu", { onConfirm(text) }, color = PosColors.Pay, enabled = text.isNotBlank()) },
        dismissButton = { BigButton("Hủy", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
}

@Composable
private fun BankPanel(engine: PosEngine) {
    engine.observeChanges()
    val current = engine.bankAccount()
    var bin by remember { mutableStateOf(current?.bin) }
    var account by remember { mutableStateOf(current?.account.orEmpty()) }
    var holder by remember { mutableStateOf(current?.holder.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chọn ngân hàng", style = typeFor(24), fontWeight = FontWeight.Bold)
            vn.muoikip.pos.core.BANKS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        val sel = b.bin == bin
                        BigButton(
                            b.shortName, { bin = b.bin }, Modifier.weight(1f), height = 60.dp, fontSize = 20,
                            color = if (sel) PosColors.Selected else PosColors.Chip,
                            textColor = if (sel) Color.White else PosColors.Ink,
                        )
                    }
                    repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Mã QR có sẵn số tiền sẽ in trên hóa đơn và phiếu tạm tính. Khách quét bằng app ngân hàng.",
                style = typeFor(20), color = PosColors.Muted,
            )
            OutlinedTextField(
                // Wallet accounts (MoMo, ZaloPay) can contain letters, so allow A–Z as well as digits.
                value = account, onValueChange = { v -> account = v.uppercase().filter { it.isLetterOrDigit() && it.code < 128 }.take(19) },
                label = { Text("Số tài khoản") }, singleLine = true, textStyle = typeFor(28),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = holder, onValueChange = { holder = vn.muoikip.pos.core.VietQr.ascii(it).uppercase().take(40) },
                label = { Text("Tên chủ tài khoản (không dấu)") }, singleLine = true, textStyle = typeFor(28),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                bin?.let { "Ngân hàng: ${vn.muoikip.pos.core.bankName(it)}" } ?: "Chưa chọn ngân hàng",
                style = typeFor(24), fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(
                    "Lưu", {
                        engine.setBankAccount(vn.muoikip.pos.core.BankAccount(bin!!, account, holder))
                        message = "Đã lưu. Hóa đơn từ giờ có mã QR chuyển khoản."
                    },
                    Modifier.weight(1f), color = PosColors.Pay, enabled = bin != null && account.length >= 6,
                )
                BigButton("Bỏ mã QR", { confirmClear = true }, color = PosColors.Chip, textColor = PosColors.Danger, enabled = current != null)
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog("Bỏ mã QR khỏi hóa đơn?", "Bỏ mã QR", onDismiss = { confirmClear = false }, onConfirm = {
            engine.setBankAccount(null); bin = null; account = ""; holder = ""; confirmClear = false
        })
    }
    message?.let { MessageDialog(it, onDismiss = { message = null }) }
}

@Composable
private fun BackupPanel(actions: SettingsActions) {
    var files by remember { mutableStateOf(actions.listBackups()) }
    var confirmRestore by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Máy tự sao lưu mỗi ngày khi mở app (giữ 30 bản gần nhất). Thư mục: Android/data/vn.muoikip.pos/files/backups",
            style = typeFor(20), color = PosColors.Muted,
        )
        BigButton("Sao lưu ngay", {
            message = runCatching { "Đã sao lưu: ${actions.backupNow().name}" }.getOrElse { "Sao lưu lỗi: ${it.message}" }
            files = actions.listBackups()
        }, color = PosColors.Pay)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(files, key = { it.name }) { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(backupLabel(f), style = typeFor(22), modifier = Modifier.weight(1f))
                    BigButton("Khôi phục", { confirmRestore = f }, color = PosColors.Chip, textColor = PosColors.Danger, height = 60.dp)
                }
                HorizontalDivider()
            }
        }
    }
    confirmRestore?.let { f ->
        ConfirmDialog(
            message = "Khôi phục dữ liệu về bản ${backupLabel(f)}?\nMọi thay đổi sau thời điểm đó sẽ mất. App sẽ tự khởi động lại.",
            confirmText = "Khôi phục",
            onDismiss = { confirmRestore = null },
            onConfirm = { actions.restore(f) },
        )
    }
    message?.let { MessageDialog(it, onDismiss = { message = null }) }
}

/** "pos-20261123-2000.db" -> "20:00 ngày 23/11/2026". */
private fun backupLabel(f: File): String {
    val m = Regex("pos-(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})").find(f.name) ?: return f.name
    val (y, mo, d, h, mi) = m.destructured
    return "$h:$mi ngày $d/$mo/$y"
}

package vn.muoikip.pos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.muoikip.pos.core.DefaultData
import vn.muoikip.pos.core.PosEngine
import vn.muoikip.pos.core.TableView
import vn.muoikip.pos.core.formatVnd

@Composable
fun TableMapScreen(
    engine: PosEngine,
    refresh: Int,
    selectedZoneId: String?,
    onSelectZone: (String) -> Unit,
    onOpenTable: (String) -> Unit,
    onSettings: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") refresh
    val zones = engine.zones()
    val zone = zones.firstOrNull { it.id == selectedZoneId } ?: zones.first()
    val tables = engine.tables(zone.id)

    var addingZone by remember { mutableStateOf(false) }
    var addingTable by remember { mutableStateOf(false) }
    var confirmOpenRoom by remember { mutableStateOf<TableView?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MƯỜI KIP", style = typeFor(30), fontWeight = FontWeight.Black, color = PosColors.Ink)
            Spacer(Modifier.width(8.dp))
            zones.forEach { z ->
                val busy = engine.tables(z.id).count { it.occupied }
                val selected = z.id == zone.id
                BigButton(
                    text = if (busy > 0) "${z.name} ($busy)" else z.name,
                    onClick = { onSelectZone(z.id) },
                    color = if (selected) PosColors.Selected else PosColors.Chip,
                    textColor = if (selected) Color.White else PosColors.Ink,
                    fontSize = 26,
                )
            }
            BigButton("+ Thêm khu", { addingZone = true }, color = PosColors.Background, textColor = PosColors.Primary, fontSize = 22)
            BigButton("⚙ Cài đặt", onSettings, color = PosColors.Background, textColor = PosColors.Muted, fontSize = 22)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tables, key = { it.table.id }) { t ->
                TableTile(t) {
                    if (t.zone.timed && !t.occupied) confirmOpenRoom = t else onOpenTable(t.table.id)
                }
            }
            item {
                Surface(
                    onClick = { addingTable = true },
                    shape = MaterialTheme.shapes.large,
                    color = PosColors.PrimarySoft,
                    border = BorderStroke(2.dp, PosColors.Primary),
                    modifier = Modifier.height(170.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = PosColors.Primary, modifier = Modifier.size(48.dp))
                        Text(if (zone.timed) "Thêm phòng" else "Thêm bàn", style = MaterialTheme.typography.titleMedium, color = PosColors.Primary)
                    }
                }
            }
        }
    }

    if (addingZone) {
        AddZoneDialog(onDismiss = { addingZone = false }) { name, timed ->
            val z = engine.addZone(name, timed)
            addingZone = false
            onSelectZone(z.id)
        }
    }
    if (addingTable) {
        val nextNo = (tables.maxOfOrNull { it.table.number } ?: 0) + 1
        if (zone.timed) {
            NumberPadDialog(
                title = "Giá phòng ${zone.name} $nextNo mỗi giờ",
                initial = DefaultData.DEFAULT_ROOM_RATE,
                onDismiss = { addingTable = false },
                onConfirm = { engine.addTable(zone.id, it); addingTable = false },
            )
        } else {
            ConfirmDialog(
                message = "Thêm Bàn $nextNo vào khu ${zone.name}?",
                confirmText = "Thêm bàn",
                confirmColor = PosColors.Pay,
                onDismiss = { addingTable = false },
                onConfirm = { engine.addTable(zone.id); addingTable = false },
            )
        }
    }
    confirmOpenRoom?.let { t ->
        ConfirmDialog(
            message = "Mở ${t.label}? Đồng hồ tính giờ bắt đầu chạy.",
            confirmText = "Mở phòng",
            confirmColor = PosColors.Pay,
            onDismiss = { confirmOpenRoom = null },
            onConfirm = {
                engine.openRoom(t.table.id)
                confirmOpenRoom = null
                onOpenTable(t.table.id)
            },
        )
    }
}

/**
 * Status is never colour alone: a busy table also gets an icon + "Có khách" label and a thicker
 * border; an empty one says "Trống".
 */
@Composable
private fun TableTile(t: TableView, onClick: () -> Unit) {
    val ink = if (t.occupied) PosColors.TableBusyInk else PosColors.Ink
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (t.occupied) PosColors.TableBusy else PosColors.TableEmpty,
        border = BorderStroke(if (t.occupied) 4.dp else 2.dp, if (t.occupied) PosColors.TableBusyBorder else PosColors.Border),
        shadowElevation = if (t.occupied) 2.dp else 0.dp,
        modifier = Modifier.height(170.dp).semantics(mergeDescendants = true) {},
    ) {
        Column(
            Modifier.padding(Space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (t.zone.timed) "${t.zone.name} ${t.table.number}" else "${t.table.number}",
                style = if (t.zone.timed) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
                color = ink,
            )
            if (t.occupied) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("Có khách", style = MaterialTheme.typography.labelMedium, color = ink)
                }
                Text(formatVnd(t.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
                t.elapsedMs?.let { Text(elapsedText(it), style = MaterialTheme.typography.bodyMedium, color = ink) }
                if (t.hasMissingPrices) {
                    Text("Chưa nhập giá", style = MaterialTheme.typography.labelMedium, color = PosColors.Danger)
                }
            } else {
                Text("Trống", style = MaterialTheme.typography.bodyMedium, color = PosColors.Muted)
            }
        }
    }
}

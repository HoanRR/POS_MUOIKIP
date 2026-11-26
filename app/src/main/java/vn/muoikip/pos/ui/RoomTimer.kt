package vn.muoikip.pos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vn.muoikip.pos.core.formatVnd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wall clock that ticks every second while shown. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L) // align ticks to whole seconds
        }
    }
    return now
}

private fun hhmmss(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

private fun clockText(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

/** Compact live timer, same height as "Chuyển bàn" / "Gộp bàn". Tapping opens [RoomDialog]. */
@Composable
fun RoomTimerButton(openedAt: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = PosColors.TableBusy,
        border = BorderStroke(2.dp, PosColors.TableBusyBorder),
        modifier = modifier.height(60.dp),
    ) {
        Row(Modifier.padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Text("Đã ngồi ", style = MaterialTheme.typography.labelMedium, color = PosColors.TableBusyInk)
            Text(
                hhmmss(now - openedAt),
                // Tabular digits: the clock doesn't jiggle as numbers change.
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = PosColors.TableBusyInk,
            )
        }
    }
}

/** Small window with the room's settings: live clock, start time, hourly rate, room fee. */
@Composable
fun RoomDialog(
    title: String,
    openedAt: Long,
    hourlyRate: Long,
    roomFee: Long,
    feeIsManual: Boolean,
    onEditStart: () -> Unit,
    onEditRate: () -> Unit,
    onEditFee: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now = rememberNow()
    @Composable
    fun Line(label: String, value: String, action: String, onAction: () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = PosColors.Muted, modifier = Modifier.width(150.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            BigButton(action, onAction, color = PosColors.Chip, textColor = PosColors.Primary, height = 56.dp, fontSize = 20)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(
                    "Đã ngồi ${hhmmss(now - openedAt)}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    color = PosColors.TableBusyInk,
                )
                Line("Giờ vào", clockText(openedAt), "Sửa giờ", onEditStart)
                Line("Giá phòng", "${formatVnd(hourlyRate)}/giờ", "Sửa giá", onEditRate)
                Line("Tiền phòng", formatVnd(roomFee) + if (feeIsManual) " (sửa tay)" else "", "Sửa tiền", onEditFee)
            }
        },
        confirmButton = { BigButton("Xong", onDismiss, color = PosColors.Pay) },
    )
}

/**
 * Start-time editor without a keyboard: nudge buttons in minutes, never past "now".
 * Large steps first because the usual fix is "they came half an hour ago".
 */
@Composable
fun StartTimeDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var value by remember { mutableLongStateOf(initial) }
    val now = rememberNow()
    AlertDialog(
        onDismissRequest = onDismiss,
        // Wider than the platform default so four large nudge buttons fit per row.
        modifier = Modifier.width(760.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Sửa giờ vào", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = PosColors.Primary, modifier = Modifier.size(40.dp))
                    Text(" Vào lúc ${clockText(value)}", style = MaterialTheme.typography.displayMedium)
                }
                Text(
                    "Tính đến giờ: ${hhmmss(now - value)}",
                    style = MaterialTheme.typography.bodyLarge, color = PosColors.Muted, textAlign = TextAlign.Center,
                )
                listOf(listOf(-60, -15, -5, -1), listOf(+1, +5, +15, +60)).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        row.forEach { m ->
                            val next = value + m * 60_000L
                            BigButton(
                                (if (m > 0) "+" else "−") + (if (kotlin.math.abs(m) == 60) "1 giờ" else "${kotlin.math.abs(m)} phút"),
                                { value = next },
                                Modifier.weight(1f),
                                color = PosColors.Chip, textColor = PosColors.Ink, height = 64.dp, fontSize = 20,
                                enabled = next <= now,
                            )
                        }
                    }
                }
                BigButton("Đặt là bây giờ", { value = now }, Modifier.fillMaxWidth(), color = PosColors.Chip,
                    textColor = PosColors.Primary, height = 60.dp, fontSize = 20)
            }
        },
        confirmButton = { BigButton("Lưu giờ vào", { onConfirm(value) }, color = PosColors.Pay) },
        dismissButton = { BigButton("Quay lại", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
}

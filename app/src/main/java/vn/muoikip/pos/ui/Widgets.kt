package vn.muoikip.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.muoikip.pos.core.Vnd
import vn.muoikip.pos.core.formatVnd

/** Maps a requested size onto the theme's type roles so no screen hardcodes sp values. */
@Composable
fun typeFor(size: Int): androidx.compose.ui.text.TextStyle {
    val t = MaterialTheme.typography
    return when {
        size >= 64 -> t.displayLarge
        size >= 50 -> t.displayMedium
        size >= 38 -> t.headlineLarge
        size >= 31 -> t.headlineMedium
        size >= 27 -> t.titleLarge
        size >= 23 -> t.titleMedium
        size >= 21 -> t.bodyLarge
        size >= 19 -> t.bodyMedium
        else -> t.labelMedium
    }
}

/** Leading/trailing glyphs in a label become real Material icons (never text symbols). */
private fun iconFor(text: String): Triple<ImageVector?, String, ImageVector?> = when {
    // Backspace has no icon in material-icons-core; a plain word beats a symbol for older users.
    text == "⌫" -> Triple(null, "Xóa 1", null)
    text.startsWith("← ") -> Triple(Icons.AutoMirrored.Filled.ArrowBack, text.removePrefix("← "), null)
    text.startsWith("+ ") -> Triple(Icons.Filled.Add, text.removePrefix("+ "), null)
    text.startsWith("⚙ ") -> Triple(Icons.Filled.Settings, text.removePrefix("⚙ "), null)
    text.startsWith("‹ ") -> Triple(Icons.AutoMirrored.Filled.KeyboardArrowLeft, text.removePrefix("‹ "), null)
    text.endsWith(" ›") -> Triple(null, text.removeSuffix(" ›"), Icons.AutoMirrored.Filled.KeyboardArrowRight)
    else -> Triple(null, text, null)
}

/**
 * The one button used everywhere: ≥56dp tall (Material minimum is 48dp), ripple feedback,
 * clear disabled state, optional icon derived from the label.
 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PosColors.Primary,
    textColor: Color = Color.White,
    height: Dp = 72.dp,
    fontSize: Int = 24,
    enabled: Boolean = true,
) {
    val (leading, label, trailing) = iconFor(text)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp).height(maxOf(height, 56.dp)),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = PosColors.Chip.copy(alpha = 0.6f),
            disabledContentColor = PosColors.Muted.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
    ) {
        val iconSize = if (fontSize >= 30) 36.dp else 28.dp
        // Icons always sit next to a visible label, so they are decorative (no content description).
        leading?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = typeFor(fontSize), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        trailing?.let {
            Spacer(Modifier.width(Space.s))
            Icon(it, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

/** Digits pad for money. Keys: 1–9, 000, 0, ⌫ and C. */
@Composable
fun NumberPad(value: Vnd, onChange: (Vnd) -> Unit, modifier: Modifier = Modifier, keyHeight: Dp = 72.dp) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", "⌫")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    BigButton(
                        k,
                        onClick = {
                            val s = value.toString().let { if (it == "0") "" else it }
                            val next = when (k) {
                                "⌫" -> s.dropLast(1)
                                else -> (s + k).take(10)
                            }
                            onChange(next.toLongOrNull() ?: 0)
                        },
                        modifier = Modifier.weight(1f),
                        color = PosColors.Chip,
                        textColor = PosColors.Ink,
                        height = keyHeight,
                        fontSize = 30,
                    )
                }
            }
        }
        BigButton("Xóa hết", { onChange(0) }, Modifier.fillMaxWidth(), color = PosColors.Chip, textColor = PosColors.Danger, height = 60.dp)
    }
}

@Composable
fun NumberPadDialog(
    title: String,
    initial: Vnd,
    onDismiss: () -> Unit,
    onConfirm: (Vnd) -> Unit,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null,
) {
    var value by remember { mutableLongStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = typeFor(28), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatVnd(value), style = typeFor(44), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                NumberPad(value, { value = it }, keyHeight = 64.dp)
                if (extraLabel != null && onExtra != null) {
                    BigButton(extraLabel, onExtra, Modifier.fillMaxWidth(), color = PosColors.Chip, textColor = PosColors.Primary, height = 60.dp)
                }
            }
        },
        confirmButton = { BigButton("Xong", { onConfirm(value) }, color = PosColors.Pay) },
        dismissButton = { BigButton("Hủy", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
}

@Composable
fun ConfirmDialog(
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmColor: Color = PosColors.Danger,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message, style = typeFor(28)) },
        confirmButton = { BigButton(confirmText, onConfirm, color = confirmColor) },
        dismissButton = { BigButton("Quay lại", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
}

@Composable
fun MessageDialog(
    message: String,
    onDismiss: () -> Unit,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message, style = typeFor(28)) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (actionText != null && onAction != null) BigButton(actionText, onAction, color = PosColors.Primary)
                BigButton("Đóng", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink)
            }
        },
    )
}

@Composable
fun AddZoneDialog(onDismiss: () -> Unit, onConfirm: (name: String, timed: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var timed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm khu mới", style = typeFor(28), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("Tên khu") }, singleLine = true,
                    textStyle = typeFor(26),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(
                        "Khu thường", { timed = false }, Modifier.weight(1f),
                        color = if (!timed) PosColors.Selected else PosColors.Chip,
                        textColor = if (!timed) Color.White else PosColors.Ink,
                    )
                    BigButton(
                        "Khu tính giờ (VIP)", { timed = true }, Modifier.weight(1f),
                        color = if (timed) PosColors.Selected else PosColors.Chip,
                        textColor = if (timed) Color.White else PosColors.Ink,
                    )
                }
            }
        },
        confirmButton = {
            BigButton("Thêm", { onConfirm(name, timed) }, color = PosColors.Pay, enabled = name.isNotBlank())
        },
        dismissButton = { BigButton("Hủy", onDismiss, color = PosColors.Chip, textColor = PosColors.Ink) },
    )
}

fun elapsedText(ms: Long): String {
    val totalMin = ms / 60_000
    return if (totalMin < 60) "$totalMin phút" else "${totalMin / 60} giờ ${"%02d".format(totalMin % 60)} phút"
}

@Composable
fun SectionPadding(content: @Composable () -> Unit) {
    Column(Modifier.padding(16.dp)) { content() }
}

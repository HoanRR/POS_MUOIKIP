package vn.muoikip.pos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** High-contrast palette with large type: the main users are in their 50s. */
object PosColors {
    val Background = Color(0xFFF2F0EB)
    val Ink = Color(0xFF1A1A1A)
    val Muted = Color(0xFF555555)
    val TableEmpty = Color(0xFFFFFFFF)
    val TableBusy = Color(0xFFFFB74D)
    val Primary = Color(0xFF1565C0)
    val Pay = Color(0xFF2E7D32)
    val Danger = Color(0xFFC62828)
    val Selected = Color(0xFF263238)
    val Chip = Color(0xFFDDE3EA)
}

private val Typo = Typography(
    bodyLarge = TextStyle(fontSize = 22.sp),
    bodyMedium = TextStyle(fontSize = 20.sp),
    titleLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PosColors.Primary,
            background = PosColors.Background,
            surface = PosColors.Background,
            onBackground = PosColors.Ink,
            onSurface = PosColors.Ink,
            error = PosColors.Danger,
        ),
        typography = Typo,
        content = content,
    )
}

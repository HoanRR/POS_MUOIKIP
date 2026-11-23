package vn.muoikip.pos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens. Direction from the ui-ux-pro-max design system ("Accessible & Ethical": trust
 * blue + confirming green, high contrast, large text) mapped onto Material 3.
 *
 * Every text/background pair used on screen meets WCAG 4.5:1. The pay green is darker than the
 * generated #16A34A so white button text stays readable (≈5:1).
 *
 * Light theme only on purpose: one fixed device in a lit shop, used by people in their 50s; a
 * second theme is more to get wrong than it is worth here.
 */
object PosColors {
    val Background = Color(0xFFF0F9FF)   // sky-50
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F172A)          // slate-900, 17:1 on white
    val Muted = Color(0xFF475569)        // slate-600, 7.6:1 on white
    val Border = Color(0xFFCBD5E1)       // slate-300
    val Primary = Color(0xFF0369A1)      // sky-700, 5.9:1 with white
    val PrimarySoft = Color(0xFFE0F2FE)  // sky-100
    val Pay = Color(0xFF15803D)          // green-700, 5.0:1 with white
    val Danger = Color(0xFFB91C1C)       // red-700, 6.5:1 with white
    val DangerSoft = Color(0xFFFEE2E2)
    val Selected = Color(0xFF0C4A6E)     // sky-900, nav / selected tabs
    val Chip = Color(0xFFE2E8F0)         // slate-200, neutral tonal buttons
    val TableEmpty = Surface
    val TableBusy = Color(0xFFFEF3C7)    // amber-100
    val TableBusyBorder = Color(0xFFD97706) // amber-600
    val TableBusyInk = Color(0xFF78350F) // amber-900, 9:1 on amber-100
}

/** 4/8dp spacing rhythm. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * Large type scale for older eyes on a 14" landscape screen. Screens use these roles,
 * never raw sp values.
 */
private val PosTypography = Typography(
    displayLarge = TextStyle(fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Black),
    displayMedium = TextStyle(fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

private val PosShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PosColors.Primary,
            onPrimary = Color.White,
            primaryContainer = PosColors.PrimarySoft,
            onPrimaryContainer = PosColors.Selected,
            secondary = PosColors.Pay,
            onSecondary = Color.White,
            background = PosColors.Background,
            onBackground = PosColors.Ink,
            surface = PosColors.Surface,
            onSurface = PosColors.Ink,
            surfaceVariant = PosColors.Chip,
            onSurfaceVariant = PosColors.Muted,
            outline = PosColors.Border,
            error = PosColors.Danger,
            onError = Color.White,
            errorContainer = PosColors.DangerSoft,
            // Dialogs and menus use these; keep them neutral white instead of Material's tinted default.
            surfaceContainerLowest = PosColors.Surface,
            surfaceContainerLow = PosColors.Surface,
            surfaceContainer = PosColors.Surface,
            surfaceContainerHigh = PosColors.Surface,
            surfaceContainerHighest = PosColors.Surface,
            surfaceTint = PosColors.Surface,
        ),
        typography = PosTypography,
        shapes = PosShapes,
        content = content,
    )
}

package vn.muoikip.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import vn.muoikip.pos.core.PosEngine

/** Where the cashier is. Kept as strings so it survives recreation. */
private const val MAP = "map"

@Composable
fun PosApp(engine: PosEngine, settings: SettingsActions) {
    val revision by engine.revision.collectAsState()
    // Ticks every 20 s so room timers and fees refresh on screen.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(20_000); tick++ }
    }
    var screen by rememberSaveable { mutableStateOf(MAP) }
    var tableId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedZoneId by rememberSaveable { mutableStateOf<String?>(null) }

    val refresh = revision + tick // read so every screen recomposes on change or tick

    Box(Modifier.fillMaxSize().background(PosColors.Background)) {
        when (screen) {
            MAP -> TableMapScreen(
                engine = engine,
                refresh = refresh,
                selectedZoneId = selectedZoneId,
                onSelectZone = { selectedZoneId = it },
                onOpenTable = { tableId = it; screen = "order" },
                onSettings = { screen = "settings" },
            )
            "settings" -> {
                BackHandler { screen = MAP }
                SettingsScreen(engine, refresh, settings, onBack = { screen = MAP })
            }
            "order" -> {
                BackHandler { screen = MAP }
                OrderScreen(
                    engine = engine,
                    tableId = tableId!!,
                    refresh = refresh,
                    onBack = { screen = MAP },
                    onPay = { screen = "pay" },
                    onSwitchTable = { tableId = it },
                )
            }
            "pay" -> {
                BackHandler { screen = "order" }
                PaymentScreen(
                    engine = engine,
                    tableId = tableId!!,
                    refresh = refresh,
                    onBack = { screen = "order" },
                    onDone = { screen = MAP },
                )
            }
        }
    }
}

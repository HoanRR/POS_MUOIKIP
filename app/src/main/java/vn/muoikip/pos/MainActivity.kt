package vn.muoikip.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import vn.muoikip.pos.ui.PosApp
import vn.muoikip.pos.ui.PosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PosApplication
        app.printer // bind the printer service early so it is ready at the first print
        setContent {
            PosTheme { PosApp(app.engine) }
        }
    }
}

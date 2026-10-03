package app.placeholder.journal

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.placeholder.journal.ui.navigation.AppNavHost
import app.placeholder.journal.ui.splash.SplashIntro
import app.placeholder.journal.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Ivory runs edge to edge: transparent status + navigation bars with dark icons (light theme only).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // 3-button navigation would otherwise get a translucent white scrim over the ivory.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false

        val coldStart = savedInstanceState == null
        setContent {
            AppTheme {
                // The intro plays once per cold start (not on rotation / process restore).
                var showIntro by rememberSaveable { mutableStateOf(coldStart) }
                Box {
                    AppNavHost() // composes and loads underneath the intro
                    if (showIntro) SplashIntro(onFinished = { showIntro = false })
                }
            }
        }
    }
}

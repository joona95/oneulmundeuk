package app.oneulmundeuk

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.oneulmundeuk.data.settings.AppSettings
import app.oneulmundeuk.ui.theme.AppTokens
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.oneulmundeuk.ui.navigation.AppNavHost
import app.oneulmundeuk.ui.splash.SplashIntro
import app.oneulmundeuk.ui.theme.AppTheme

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
        val settingsStore = (application as JournalApplication).container.settingsStore
        setContent {
            // 내 감정 조각: the chosen shape reaches every EmotionMarker through the theme tokens.
            val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            AppTheme(tokens = AppTokens(markerShape = settings.markerShape)) {
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

package com.quipu.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quipu.app.ui.QuipuRoot
import com.quipu.app.ui.theme.QuipuTheme
import com.quipu.app.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as QuipuApplication).repository.settings
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle()
            val dark = isDarkTheme(appearance)
            // Los iconos de las barras del sistema siguen al tema elegido, no solo al del teléfono.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            QuipuTheme(appearance = appearance, darkTheme = dark) {
                QuipuRoot()
            }
        }
    }

    private companion object {
        // Los mismos velos que usa enableEdgeToEdge() por defecto.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

package com.quipu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quipu.app.domain.AccentColor
import com.quipu.app.domain.Appearance
import com.quipu.app.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF1D4ED8),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE9EEF3),
    surfaceContainerHighest = Color(0xFFE2E8F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF263238),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF172554),
    background = Color(0xFF0B1215),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111A1E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E2A30),
    onSurfaceVariant = Color(0xFFA8B5BD),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF2B3A42),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    surfaceContainerLowest = Color(0xFF0A1014),
    surfaceContainerLow = Color(0xFF0F181C),
    surfaceContainer = Color(0xFF151F24),
    surfaceContainerHigh = Color(0xFF1B272D),
    surfaceContainerHighest = Color(0xFF223037),
)

/** Tonos de un color de acento en claro y en oscuro (paleta de Tailwind). */
private data class AccentTones(
    val light: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val onDark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
)

private fun tonesOf(accent: AccentColor): AccentTones = when (accent) {
    // El original: coincide con LightColors/DarkColors.
    AccentColor.TEAL -> AccentTones(
        Color(0xFF0F766E), Color(0xFFCCFBF1), Color(0xFF134E4A),
        Color(0xFF5EEAD4), Color(0xFF042F2E), Color(0xFF115E59), Color(0xFFCCFBF1),
    )
    AccentColor.BLUE -> AccentTones(
        Color(0xFF1D4ED8), Color(0xFFDBEAFE), Color(0xFF1E3A8A),
        Color(0xFF93C5FD), Color(0xFF172554), Color(0xFF1E40AF), Color(0xFFDBEAFE),
    )
    AccentColor.PURPLE -> AccentTones(
        Color(0xFF7E22CE), Color(0xFFF3E8FF), Color(0xFF581C87),
        Color(0xFFD8B4FE), Color(0xFF3B0764), Color(0xFF6B21A8), Color(0xFFF3E8FF),
    )
    AccentColor.ORANGE -> AccentTones(
        Color(0xFFC2410C), Color(0xFFFFEDD5), Color(0xFF7C2D12),
        Color(0xFFFDBA74), Color(0xFF431407), Color(0xFF9A3412), Color(0xFFFFEDD5),
    )
    AccentColor.PINK -> AccentTones(
        Color(0xFFBE185D), Color(0xFFFCE7F3), Color(0xFF831843),
        Color(0xFFF9A8D4), Color(0xFF500724), Color(0xFF9D174D), Color(0xFFFCE7F3),
    )
}

private fun ColorScheme.withAccent(accent: AccentColor, dark: Boolean): ColorScheme {
    val t = tonesOf(accent)
    return if (dark) {
        copy(primary = t.dark, onPrimary = t.onDark, primaryContainer = t.darkContainer, onPrimaryContainer = t.onDarkContainer)
    } else {
        copy(primary = t.light, onPrimary = Color.White, primaryContainer = t.lightContainer, onPrimaryContainer = t.onLightContainer)
    }
}

/** Si la interfaz debe ir en oscuro según el ajuste de tema. */
@Composable
fun isDarkTheme(appearance: Appearance): Boolean = when (appearance.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Colores semánticos que Material 3 no define: dinero a favor, dinero en contra y "próximo". */
@Immutable
data class ExtraColors(
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val soon: Color,
    val soonContainer: Color,
)

private val LightExtra = ExtraColors(
    positive = Color(0xFF15803D),
    positiveContainer = Color(0xFFDCFCE7),
    negative = Color(0xFFC2410C),
    negativeContainer = Color(0xFFFFEDD5),
    soon = Color(0xFF1D4ED8),
    soonContainer = Color(0xFFDBEAFE),
)

private val DarkExtra = ExtraColors(
    positive = Color(0xFF86EFAC),
    positiveContainer = Color(0xFF14532D),
    negative = Color(0xFFFDBA74),
    negativeContainer = Color(0xFF7C2D12),
    soon = Color(0xFF93C5FD),
    soonContainer = Color(0xFF1E3A8A),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object QuipuColors {
    val extra: ExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtraColors.current
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun QuipuTheme(
    appearance: Appearance = Appearance(),
    darkTheme: Boolean = isDarkTheme(appearance),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors: ColorScheme = when {
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> (if (darkTheme) DarkColors else LightColors).withAccent(appearance.accent, darkTheme)
    }
    val extra = if (darkTheme) DarkExtra else LightExtra
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colors,
            shapes = AppShapes,
            content = content,
        )
    }
}

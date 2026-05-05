package com.example.barandgrillpos.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DarkBackground = Color(0xFF0A0A0A)
val SurfaceColor = Color(0xFF161616)
val PrimaryOrange = Color(0xFFFF8C00) // Deep Orange
val OrangeGradientStart = Color(0xFFFFA726) // Light Orange
val OrangeGradientEnd = Color(0xFFE65100) // Dark Orange
val CharcoalGray = Color(0xFF2C2C2C)
val AccentOrange = Color(0xFFFFB300) // Amber/Orange
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val SuccessGreen = Color(0xFF00C853)
val ErrorRed = Color(0xFFFF5252)
val GlassOverlay = Color(0x66161616)

private val POSColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    secondary = OrangeGradientStart,
    tertiary = AccentOrange,
    background = DarkBackground,
    surface = SurfaceColor,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onTertiary = DarkBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed,
    surfaceVariant = CharcoalGray,
    onSurfaceVariant = TextSecondary
)

@Composable
fun BarAndGrillPOSTheme(
    seedColor: Long? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (seedColor != null) {
        POSColorScheme.copy(primary = Color(seedColor))
    } else {
        POSColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

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

val DarkBackground = Color(0xFF0A0C10)
val SurfaceColor = Color(0xFF111827)
val PrimaryOrange = Color(0xFF3B82F6) // KLIX Blue (Legacy name kept for compatibility)
val OrangeGradientStart = Color(0xFF60A5FA)
val OrangeGradientEnd = Color(0xFF2563EB)
val CharcoalGray = Color(0xFF1E293B)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)
val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val GlassOverlay = Color(0x66111827)

private val POSColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    secondary = OrangeGradientStart,
    tertiary = OrangeGradientEnd,
    background = DarkBackground,
    surface = SurfaceColor,
    onPrimary = Color.White,
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

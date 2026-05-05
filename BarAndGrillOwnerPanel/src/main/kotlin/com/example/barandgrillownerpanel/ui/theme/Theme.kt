package com.example.barandgrillownerpanel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0A0C10)
val SurfaceColor = Color(0xFF111827)
val PrimaryOrange = Color(0xFF3B82F6) // KLIX Blue (Legacy name kept for compatibility)
val CharcoalGray = Color(0xFF1E293B) // Darker border slate
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFAAAAAA)
val SuccessGreen = Color(0xFF00C853)
val ErrorRed = Color(0xFFFF5252)

private val POSColorScheme = darkColorScheme(
    primary = PrimaryOrange,
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
    primaryOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = if (primaryOverride != null) {
        POSColorScheme.copy(
            primary = primaryOverride,
            secondary = primaryOverride.copy(alpha = 0.8f)
        )
    } else {
        POSColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

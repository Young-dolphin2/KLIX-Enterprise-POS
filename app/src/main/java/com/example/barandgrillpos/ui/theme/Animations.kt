package com.example.barandgrillpos.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A modifier that applies a smooth fade-in and slide-up animation when the component enters the composition.
 * Matching the premium vibe of the KLIX Owner Panel.
 */
@Composable
fun Modifier.entranceAnimation(
    delayMillis: Int = 0,
    durationMillis: Int = 600
): Modifier {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing)
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing)
    )
    
    return this
        .graphicsLayer(alpha = alpha, translationY = offsetY)
}

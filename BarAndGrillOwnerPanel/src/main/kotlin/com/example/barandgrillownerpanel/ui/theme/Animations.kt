package com.example.barandgrillownerpanel.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A modifier that applies a subtle scale-up effect and shadow enhancement when hovered.
 */
@Composable
fun Modifier.hoverEffect(
    scale: Float = 1.02f,
    duration: Int = 200
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isHovered) scale else 1.0f,
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
    )
    
    return this
        .hoverable(interactionSource)
        .scale(animatedScale)
}

/**
 * A modifier that applies a smooth fade-in and slide-up animation when the component enters the composition.
 */
@Composable
fun Modifier.entranceAnimation(
    delayMillis: Int = 0,
    durationMillis: Int = 500
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
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing)
    )
    
    return this
        .graphicsLayer(alpha = alpha, translationY = offsetY)
}

/**
 * A wrapper for tab content that provides a professional slide-and-fade transition between tabs.
 */
@Composable
fun AnimatedTabContent(
    targetState: Any,
    content: @Composable (Any) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            (fadeIn(animationSpec = tween(300, easing = LinearEasing)) +
             slideInHorizontally(animationSpec = tween(400, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 10 })
                .togetherWith(fadeOut(animationSpec = tween(200, easing = LinearEasing)))
        }
    ) { state ->
        content(state)
    }
}



package com.example.barandgrillpos.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    // 50/50 Split Layout for Desktop
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Deep dark background
    ) {
        // Left Side: Shooting Stars Animation
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF050505), Color(0xFF111827))
                    )
                )
                .clip(RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp))
        ) {
            ShootingStarsCanvas()
            
            // Subtle overlay text on the graphics side
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(48.dp)
            ) {
                Text(
                    text = "The future of commerce.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }

        // Right Side: Content & Actions
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "KLIX",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Universal Enterprise POS",
                fontSize = 22.sp,
                color = Color(0xFF3B82F6), // KLIX Accent Blue
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Manage any business. Scale anywhere.\nSeamless service for retail, restaurants, and beyond.",
                fontSize = 18.sp,
                color = Color.Gray,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(64.dp))
            
            Button(
                onClick = {
                    onColorSelected(0xFF3B82F6) // Force KLIX brand color
                    onComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(56.dp)
                    .fillMaxWidth(0.6f)
            ) {
                Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}

data class Star(
    var x: Float,
    var y: Float,
    var length: Float,
    var speed: Float,
    var alpha: Float,
    var angle: Float
)

@Composable
fun ShootingStarsCanvas() {
    val stars = remember {
        List(15) {
            Star(
                x = Random.nextFloat() * 2000f,
                y = Random.nextFloat() * 2000f,
                length = Random.nextFloat() * 100f + 50f,
                speed = Random.nextFloat() * 10f + 5f,
                alpha = Random.nextFloat(),
                angle = 45f // moving diagonally
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Just reading 'time' forces recomposition
        time.hashCode()

        val width = size.width
        val height = size.height

        for (star in stars) {
            // Update star position
            star.x -= star.speed
            star.y += star.speed

            // Reset if out of bounds
            if (star.x < -star.length || star.y > height + star.length) {
                star.x = width + Random.nextFloat() * 500f
                star.y = -Random.nextFloat() * 500f
                star.speed = Random.nextFloat() * 10f + 5f
                star.alpha = Random.nextFloat() * 0.8f + 0.2f
            }

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = star.alpha),
                        Color.Transparent
                    ),
                    start = Offset(star.x, star.y),
                    end = Offset(star.x + star.length, star.y - star.length)
                ),
                start = Offset(star.x, star.y),
                end = Offset(star.x + star.length, star.y - star.length),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}


package com.example.barandgrillownerpanel.ui.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.DarkBackground
import com.example.barandgrillownerpanel.ui.theme.SurfaceColor
import com.example.barandgrillownerpanel.ui.theme.entranceAnimation
import com.example.barandgrillownerpanel.ui.main.AppState

@Composable
fun MasterAuthLayout(
    currentState: AppState,
    content: @Composable (AppState) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10)) // Charcoal darkest base
            .entranceAnimation()
    ) {
        val isMobile = maxWidth < 800.dp

        if (isMobile) {
            // Mobile Layout: Single Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E2433), Color(0xFF0A0C10))
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // Branding Header for Mobile
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "KLIX",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "POS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 6.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))

                // Dynamic Content Panel for Mobile
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    SurfaceColor.copy(alpha = 0.95f),
                                    Color(0xFF0F172A).copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = currentState,
                        animationSpec = tween(1100)
                    ) { animatingState ->
                        content(animatingState)
                    }
                }
            }
        } else {
            // Desktop Layout: Side-by-Side (Original)
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left Side: Brand & Visuals (Permanent)
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1E2433), Color(0xFF0A0C10)),
                                center = Offset(0f, 1000f),
                                radius = 2500f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Abstract 3D-like Infinity Shape
                    Canvas(modifier = Modifier.size(550.dp)) {
                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE2E8F0),
                                Color(0xFF64748B),
                                Color(0xFF3B82F6),
                                Color(0xFF0F172A)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        )

                        for (i in 0..7) {
                            drawArc(
                                brush = gradient,
                                startAngle = 45f + (i * 25f),
                                sweepAngle = 280f,
                                useCenter = false,
                                topLeft = Offset(50f + i * 25f, 50f + i * 25f),
                                size = Size(size.width - 100f - i * 50f, size.height - 100f - i * 50f),
                                style = Stroke(width = 35f - i * 3f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Branding Text overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 80.dp)
                    ) {
                        Text(
                            text = "KLIX",
                            fontSize = 84.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "ENTERPRISE POS",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 8.sp
                        )
                    }
                }

                // Right Side: Dynamic Content Panel (Glassmorphic)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    SurfaceColor.copy(alpha = 0.95f),
                                    Color(0xFF0F172A).copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                ) {
                    Crossfade(
                        targetState = currentState,
                        animationSpec = tween(1100)
                    ) { animatingState ->
                        content(animatingState)
                    }
                }
            }
        }
    }
}



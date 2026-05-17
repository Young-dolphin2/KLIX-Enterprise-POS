package com.example.barandgrillownerpanel.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.PrimaryOrange
import com.example.barandgrillownerpanel.ui.theme.TextPrimary
import com.example.barandgrillownerpanel.ui.theme.TextSecondary
import kotlinx.coroutines.delay

import java.util.prefs.Preferences

@Composable
fun LockScreen(
    onUnlock: () -> Unit
) {
    // Read PIN from prefs — owner sets this in Settings; fallback to "1234" if none saved yet
    val storedPin = remember {
        Preferences.userRoot()
            .node("com.example.barandgrillownerpanel")
            .get("admin_pin", "1234")
    }

    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (pin == storedPin) {
                onUnlock()
            } else {
                isError = true
                delay(1000)
                pin = ""
                isError = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.barandgrillownerpanel.ui.theme.DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // Abstract Background Graphics
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val gradient = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color(0xFF1E2433), com.example.barandgrillownerpanel.ui.theme.DarkBackground),
                center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2),
                radius = size.width
            )
            drawRect(gradient)

            val circleGradient = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(com.example.barandgrillownerpanel.ui.theme.PrimaryOrange.copy(alpha = 0.05f), Color(0xFF0F172A).copy(alpha = 0.02f)),
            )
            drawCircle(circleGradient, radius = 600f, center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.2f))
            drawCircle(circleGradient, radius = 400f, center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f))
        }

        // Glassmorphic Container
        Box(
            modifier = Modifier
                .width(400.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            com.example.barandgrillownerpanel.ui.theme.SurfaceColor.copy(alpha = 0.85f),
                            Color(0xFF0F172A).copy(alpha = 0.95f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(48.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isError) Color.Red else PrimaryOrange,
                    modifier = Modifier.size(56.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    "Terminal Locked",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    "Enter PIN to resume",
                    color = TextSecondary,
                    fontSize = 16.sp
                )

                Spacer(Modifier.height(32.dp))

                // PIN Dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { i ->
                        val filled = pin.length > i
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) (if (isError) Color.Red else PrimaryOrange) 
                                    else Color.White.copy(0.1f)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))

                // Number Pad
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "DEL"))) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (key in row) {
                                if (key.isEmpty()) {
                                    Spacer(Modifier.size(64.dp))
                                } else {
                                    Button(
                                        onClick = {
                                            if (key == "DEL") {
                                                if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            } else if (pin.length < 4) {
                                                pin += key
                                            }
                                        },
                                        modifier = Modifier.size(64.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(0.05f),
                                            contentColor = TextPrimary
                                        )
                                    ) {
                                        Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

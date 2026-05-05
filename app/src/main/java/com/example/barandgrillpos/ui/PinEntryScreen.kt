package com.example.barandgrillpos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillpos.ui.theme.*

@Composable
fun PinEntryScreen(
    employeeName: String,
    onPinEntered: (String) -> Unit,
    onLogout: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val maxPinLength = 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = PrimaryOrange,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Hello, $employeeName",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "Enter your 4-digit PIN to access",
            color = TextSecondary,
            fontSize = 14.sp
        )
        
        Spacer(Modifier.height(48.dp))

        // PIN Indicators
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (i in 1..maxPinLength) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (pin.length >= i) PrimaryOrange else CharcoalGray)
                )
            }
        }

        Spacer(Modifier.height(64.dp))

        // Keypad
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Logout", "0", "Delete")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in 0..3) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (col in 0..2) {
                        val key = keys[row * 3 + col]
                        KeypadButton(
                            text = key,
                            onClick = {
                                when (key) {
                                    "Delete" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    "Logout" -> onLogout()
                                    else -> if (pin.length < maxPinLength) {
                                        pin += key
                                        if (pin.length == maxPinLength) {
                                            onPinEntered(pin)
                                            pin = "" // Reset for next time
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, onClick: () -> Unit) {
    val isSpecial = text == "Logout" || text == "Delete"
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape),
        color = if (isSpecial) Color.Transparent else SurfaceColor,
        contentColor = if (text == "Logout") ErrorRed else if (text == "Delete") TextSecondary else TextPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "Delete") {
                Icon(Icons.Default.Backspace, contentDescription = null)
            } else {
                Text(
                    text = text,
                    fontSize = if (isSpecial) 14.sp else 24.sp,
                    fontWeight = if (isSpecial) FontWeight.Bold else FontWeight.Black
                )
            }
        }
    }
}

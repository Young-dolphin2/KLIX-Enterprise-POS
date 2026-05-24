package com.example.barandgrillownerpanel.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.utils.Logger
import com.example.barandgrillownerpanel.utils.PinHasher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shake by remember { mutableStateOf(false) }
    
    // Rate limiting state
    var failedAttempts by remember { mutableStateOf(0) }
    var isLockedOut by remember { mutableStateOf(false) }
    var lockoutEndTime by remember { mutableStateOf(0L) }
    var countdown by remember { mutableStateOf(0) }
    
    // Load stored PIN hash
    val storedPinHash by produceState(initialValue = "") {
        value = LocalDatabase.getAppSettings()?.adminPin ?: ""
    }

    // Countdown timer for lockout
    LaunchedEffect(isLockedOut, lockoutEndTime) {
        while (isLockedOut && System.currentTimeMillis() < lockoutEndTime) {
            countdown = ((lockoutEndTime - System.currentTimeMillis()) / 1000).toInt()
            delay(1000)
        }
        if (isLockedOut && System.currentTimeMillis() >= lockoutEndTime) {
            isLockedOut = false
            failedAttempts = 0
            countdown = 0
        }
    }

    // Auto-submit when 4 digits entered
    LaunchedEffect(pin) {
        if (pin.length == 4 && isLockedOut) {
            errorMessage = "Locked out. Wait ${countdown}s."
            pin = ""
            return@LaunchedEffect
        }
        if (pin.length == 4 && storedPinHash.isNotEmpty()) {
            if (PinHasher.verify(pin, storedPinHash)) {
                // Success — reset failed attempts
                failedAttempts = 0
                onUnlock()
            } else {
                // Failed attempt
                failedAttempts++
                pin = ""
                shake = true
                delay(300)
                shake = false
                
                val attemptsLeft = 5 - failedAttempts
                when {
                    failedAttempts >= 10 -> {
                        isLockedOut = true
                        lockoutEndTime = System.currentTimeMillis() + 300_000 // 5 min
                        errorMessage = "Too many attempts. Locked out for 5 minutes."
                    }
                    failedAttempts >= 5 -> {
                        isLockedOut = true
                        lockoutEndTime = System.currentTimeMillis() + 60_000 // 1 min
                        errorMessage = "Too many attempts. Locked out for 1 minute."
                    }
                    failedAttempts >= 3 -> {
                        delay(3000) // 3 second delay
                        errorMessage = "Wrong PIN. $attemptsLeft attempts remaining."
                    }
                    else -> {
                        errorMessage = "Wrong PIN. $attemptsLeft attempts remaining."
                    }
                }
                Logger.warn("LOCK", "Failed PIN attempt #$failedAttempts")
            }
        }
    }

    // Countdown display
    LaunchedEffect(isLockedOut, lockoutEndTime) {
        while (isLockedOut && System.currentTimeMillis() < lockoutEndTime) {
            countdown = ((lockoutEndTime - System.currentTimeMillis()) / 1000).toInt()
            delay(1000)
        }
        if (isLockedOut && System.currentTimeMillis() >= lockoutEndTime) {
            isLockedOut = false
            failedAttempts = 0
            countdown = 0
            errorMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1A1D23))
                .padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = PrimaryOrange,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Terminal Locked",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Enter your admin PIN to unlock",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))

            // Error message
            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Lockout countdown
            if (isLockedOut && countdown > 0) {
                Text(
                    "Unlock available in ${countdown}s",
                    color = PrimaryOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // PIN dots display
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    Box(
                        modifier = Modifier
                            .size(if (shake) 18.dp else 20.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < pin.length) PrimaryOrange
                                else Color(0xFF334155)
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Numeric keypad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key ->
                        val buttonModifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                        
                        when (key) {
                            "" -> Spacer(buttonModifier)
                            "⌫" -> OutlinedButton(
                                onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                modifier = buttonModifier,
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isLockedOut,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) { Text("⌫", fontSize = 20.sp) }
                            else -> OutlinedButton(
                                onClick = { if (pin.length < 4) pin += key },
                                modifier = buttonModifier,
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isLockedOut,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) { Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

package com.example.barandgrillpos

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillpos.ui.theme.*
import com.example.barandgrillpos.utils.SecurityUtils
import kotlinx.coroutines.launch
import com.example.barandgrillpos.data.local.AppDatabase
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeProfileScreen(
    currentName: String,
    currentRole: String,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPinDialog by remember { mutableStateOf(false) }
    var tempPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MY PROFILE", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryOrange) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Profile Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(PrimaryOrange, OrangeGradientEnd))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentName.take(2).uppercase(),
                    color = DarkBackground,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(currentName, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(currentRole, color = PrimaryOrange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(48.dp))

            // Change PIN Button
            Button(
                onClick = { showPinDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryOrange)
                Spacer(Modifier.width(12.dp))
                Text("CHANGE PIN", color = TextPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Logout Button
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            db.clearAllTables()
                        } catch (_: Exception) { }
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                border = BorderStroke(1.dp, ErrorRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = ErrorRed)
                Spacer(Modifier.width(12.dp))
                Text("LOGOUT", color = ErrorRed, fontWeight = FontWeight.Bold)
            }
        }
    }

    // PIN Change Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = SurfaceColor,
            title = { Text("Change PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter new 4-digit PIN", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) tempPin = it },
                        singleLine = true,
                        placeholder = { Text("New PIN", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryOrange,
                            focusedBorderColor = PrimaryOrange
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempPin.length == 4) {
                            scope.launch(Dispatchers.IO) {
                                val hashed = SecurityUtils.hashPin(tempPin)
                                val db = AppDatabase.getDatabase(context)
                                db.cacheDao().updateEmployeePinByName(currentName, hashed)
                            }
                            showPinDialog = false
                            tempPin = ""
                        }
                    },
                    enabled = tempPin.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("SAVE", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

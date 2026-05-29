package com.example.barandgrillpos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillpos.data.local.EmployeeEntity
import com.example.barandgrillpos.ui.theme.*
import com.example.barandgrillpos.utils.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeLoginScreen(
    employees: List<EmployeeEntity>,
    onEmployeeLoggedIn: (EmployeeEntity) -> Unit
) {
    var enteredName by remember { mutableStateOf("") }
    var enteredId by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Employee Login",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Use the employee name, owner-assigned ID, and PIN to sign in.",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = enteredName,
            onValueChange = { enteredName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Employee Name") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedBorderColor = PrimaryOrange,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary,
                cursorColor = PrimaryOrange
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = enteredId,
            onValueChange = { enteredId = it.uppercase() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Employee ID") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedBorderColor = PrimaryOrange,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary,
                cursorColor = PrimaryOrange
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = enteredPin,
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) enteredPin = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("4-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedBorderColor = PrimaryOrange,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary,
                cursorColor = PrimaryOrange
            )
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage!!, color = ErrorRed, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                errorMessage = null
                val employee = employees.find {
                    it.id.equals(enteredId.trim(), ignoreCase = true) &&
                        it.name.equals(enteredName.trim(), ignoreCase = true)
                }
                if (employee == null) {
                    errorMessage = "Employee not found. Check name and ID."
                    return@Button
                }

                val pinHash = employee.pinHash
                if (pinHash != null) {
                    if (enteredPin.length == 4 && SecurityUtils.verifyPin(enteredPin, pinHash)) {
                        onEmployeeLoggedIn(employee)
                    } else {
                        errorMessage = "Incorrect PIN."
                    }
                } else {
                    if (enteredPin.isBlank()) {
                        onEmployeeLoggedIn(employee)
                    } else {
                        errorMessage = "This employee has no PIN set. Leave the PIN field empty."
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = enteredName.isNotBlank() && enteredId.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
        ) {
            Text("Sign In", color = DarkBackground, fontWeight = FontWeight.Bold)
        }

        if (employees.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No active employees found. Please sync or ask the owner to add you.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
}

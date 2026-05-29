package com.example.barandgrillpos.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.barandgrillpos.data.remote.SupabaseManager
import com.example.barandgrillpos.utils.AppLogger
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        // Left: KLIX gold spiral / ring branding
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(32.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF080808)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(180.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF1A1A1A), Color(0xFF060606)),
                            center = Offset(130f, 130f),
                            radius = 260f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(180.dp))
                        .border(6.dp, Color(0xFFD4AF37), RoundedCornerShape(180.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .border(4.dp, Color(0xFFD4AF37), RoundedCornerShape(180.dp))
                    )
                }
                Text(
                    "KLIX",
                    color = Color(0xFFD4AF37),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Right: Form with charcoal background and silver text
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 80.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "Welcome to KLIX",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC0C0C0)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Sign in to your enterprise workspace",
                color = Color(0xFFC0C0C0),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF111111))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                    .padding(28.dp)
            ) {
                Column {
                    // Username label + field
                    Text("Username", color = Color(0xFFC0C0C0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    CustomTextField(value = email, onValueChange = { email = it }, placeholder = "Email Address", icon = Icons.Default.Email)

                    Spacer(Modifier.height(16.dp))

                    // Password label + field
                    Text("Password", color = Color(0xFFC0C0C0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    CustomTextField(value = password, onValueChange = { password = it }, placeholder = "Password", icon = Icons.Default.Lock, isPassword = true)
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            if (errorMessage != null) {
                Text(text = errorMessage!!, color = Color(0xFFEF4444), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill out all fields"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            SupabaseManager.client.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            onLoginSuccess()
                        } catch (e: Exception) {
                            AppLogger.e("LoginScreen", "Login failed", e)
                            errorMessage = "Login failed: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                else Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onNavigateToSignUp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Don't have an account? Sign up", color = Color(0xFFC0C0C0))
            }
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = SolidColor(Color(0xFFD4AF37)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF151515), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFFD4AF37), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Color(0xFFB0B0B0), fontSize = 16.sp)
                    innerTextField()
                }
            }
        }
    )
}

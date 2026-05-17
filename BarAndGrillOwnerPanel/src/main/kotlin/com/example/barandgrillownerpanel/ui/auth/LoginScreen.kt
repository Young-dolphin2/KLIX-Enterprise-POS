package com.example.barandgrillownerpanel.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import com.example.barandgrillownerpanel.utils.Logger
import com.example.barandgrillownerpanel.ui.theme.entranceAnimation

@Composable
fun LoginScreen(
    funOnLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onDevBypass: () -> Unit = funOnLoginSuccess
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockoutEndTime by remember { mutableStateOf(0L) }
    val isLockedOut = lockoutEndTime > System.currentTimeMillis()

    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) 32.dp else 100.dp
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource("icon_klix.png"),
                    contentDescription = "KLIX Logo",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(80.dp))

                Text(
                    text = "Welcome to KLIX",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Sign in to manage your business",
                    color = Color(0xFF94A3B8),
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(56.dp))

                // Email Field
                Text("Email Address", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Enter your email",
                    icon = Icons.Outlined.Email
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Password Field
                Text("Password", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "••••••••",
                    icon = Icons.Outlined.VisibilityOff,
                    isPassword = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Forgot Password
                Text(
                    text = "Forgot Password?",
                    color = Color(0xFF60A5FA),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { /* Handle forgot password */ }
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = errorMessage!!, color = Color(0xFFEF4444), fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Sign In Button
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Button(
                        onClick = {
                            if (isLockedOut) return@Button
                            
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
                                    failedAttempts = 0
                                    Logger.info("AUTH", "Login successful for user: $email")
                                    funOnLoginSuccess()
                                } catch (e: Exception) {
                                    Logger.warn("AUTH", "Failed login attempt for user: $email - ${e.message}")
                                    failedAttempts++
                                    val now = System.currentTimeMillis()
                                    when {
                                        failedAttempts >= 10 -> {
                                            Logger.fatal("SECURITY", "Account $email permanently locked after 10 failed attempts.")
                                            lockoutEndTime = Long.MAX_VALUE
                                            errorMessage = "SECURITY LOCK: Maximum attempts reached. Contact Admin."
                                        }
                                        failedAttempts >= 5 -> {
                                            Logger.warn("SECURITY", "Account $email locked for 5 minutes after 5 failed attempts.")
                                            lockoutEndTime = now + (5 * 60 * 1000)
                                            errorMessage = "Too many failed attempts. Locked for 5 minutes."
                                        }
                                        failedAttempts >= 3 -> {
                                            Logger.info("SECURITY", "Account $email locked for 1 minute after 3 failed attempts.")
                                            lockoutEndTime = now + (60 * 1000)
                                            errorMessage = "Too many failed attempts. Locked for 1 minute."
                                        }
                                        else -> {
                                            errorMessage = "Login failed: Invalid credentials."
                                        }
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        enabled = !isLoading && !isLockedOut
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else if (isLockedOut) {
                            Text("Locked Out", color = Color.White.copy(0.5f))
                        } else {
                            Text("Sign In", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Dev Bypass Button (Explicit)
                OutlinedButton(
                    onClick = onDevBypass,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text("Developer Bypass (Skip Login)")
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Or continue with
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Divider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                    Text("Or continue with", color = Color(0xFF94A3B8), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    Divider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Social Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { /* Google */ },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text("Google")
                    }
                    OutlinedButton(
                        onClick = { /* Apple */ },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text("Apple")
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Don't have an account? ", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text("Sign Up", color = Color(0xFF60A5FA), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onNavigateToSignUp() })
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Terms of Service | Privacy Policy",
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    isPassword: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = SolidColor(Color(0xFF60A5FA)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF0F172A).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (value.isNotEmpty()) Color(0xFF60A5FA).copy(alpha = 0.5f) else Color(0xFF334155),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color(0xFF475569), fontSize = 16.sp)
                    }
                    innerTextField()
                }
                if (icon != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
            }
        }
    )
}


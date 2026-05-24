@file:Suppress("DEPRECATION")

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
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.utils.Logger
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import com.example.barandgrillownerpanel.ui.theme.entranceAnimation

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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
                    painter = painterResource("icon_klix.png"),
                    contentDescription = "KLIX Logo",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Create an Account",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Sign up to start managing your business",
                    color = Color(0xFF94A3B8),
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(48.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm Password Field
                Text("Confirm Password", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = "••••••••",
                    icon = Icons.Outlined.VisibilityOff,
                    isPassword = true
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = errorMessage!!, color = Color(0xFFEF4444), fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Sign Up Button
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter email and password"
                                return@Button
                            }
                            if (password != confirmPassword) {
                                errorMessage = "Passwords do not match"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    SupabaseManager.client.auth.signUpWith(Email) {
                                        this.email = email
                                        this.password = password
                                    }
                                    onSignUpSuccess()
                                } catch (e: Exception) {
                                    Logger.error("SIGNUP", "Sign up failed", e)
                                    errorMessage = "Sign up failed: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Create Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Dev Bypass Button (Explicit)
                OutlinedButton(
                    onClick = onSignUpSuccess,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text("Developer Bypass (Skip Login)")
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Already have an account? ", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text("Sign In", color = Color(0xFF60A5FA), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onNavigateToLogin() })
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


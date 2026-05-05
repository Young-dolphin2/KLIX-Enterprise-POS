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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10)) // Charcoal darkest base
            .entranceAnimation()
    ) {
        // Left Side: Brand & Visuals
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
            // Abstract 3D-like Infinity Shape using Canvas (Silver/Chrome/Blue)
            Canvas(modifier = Modifier.size(550.dp)) {
                val gradient = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE2E8F0), // Bright silver
                        Color(0xFF64748B), // Dark silver
                        Color(0xFF3B82F6), // Accent blue
                        Color(0xFF0F172A)  // Deep shadow
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

        // Right Side: Form Panel (Glassmorphic Charcoal)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF111827).copy(alpha = 0.95f),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 100.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource("icon_klix.png"),
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
                                    e.printStackTrace()
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
}


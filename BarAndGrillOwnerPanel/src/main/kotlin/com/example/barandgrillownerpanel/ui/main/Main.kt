package com.example.barandgrillownerpanel.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.auth.LoginScreen
import com.example.barandgrillownerpanel.ui.auth.MasterAuthLayout
import com.example.barandgrillownerpanel.ui.auth.LockScreen
import com.example.barandgrillownerpanel.ui.onboarding.OnboardingScreen
import com.example.barandgrillownerpanel.ui.dashboard.DashboardScreen
import com.example.barandgrillownerpanel.ui.dashboard.AppSettings
import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.ui.theme.PrimaryOrange
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

enum class AppState {
    SPLASH, WELCOME, LOGIN, SIGNUP, ONBOARDING, DASHBOARD, LOCKED
}

@Composable
fun MainApp() {
    var currentState by remember { mutableStateOf(AppState.SPLASH) }
    var appSettings by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(Unit) {
        LocalDatabase.initialize()
        delay(4000) // 4-second splash with animations

        val localSettings = LocalDatabase.getAppSettings()
        if (localSettings != null) {
            appSettings = localSettings
            currentState = AppState.DASHBOARD
        } else {
            currentState = AppState.WELCOME
        }
    }

    // Inactivity Lock Screen Logic
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        val listener = java.awt.event.AWTEventListener {
            lastInteractionTime = System.currentTimeMillis()
        }
        val mask = java.awt.AWTEvent.MOUSE_EVENT_MASK or
                java.awt.AWTEvent.KEY_EVENT_MASK or
                java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask)
        onDispose {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }

    LaunchedEffect(currentState, appSettings?.lockTimeoutMinutes) {
        while (true) {
            delay(10000) // Check every 10 seconds
            val timeoutMinutes = appSettings?.lockTimeoutMinutes ?: 0
            if (timeoutMinutes > 0 && currentState == AppState.DASHBOARD) {
                val idleMillis = System.currentTimeMillis() - lastInteractionTime
                if (idleMillis > timeoutMinutes * 60 * 1000L) {
                    currentState = AppState.LOCKED
                }
            }
        }
    }

    val authGroup = listOf(AppState.WELCOME, AppState.LOGIN, AppState.SIGNUP, AppState.ONBOARDING)
    val targetSection = if (currentState in authGroup) "AUTH" else currentState.name

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(
            targetState = targetSection,
            animationSpec = tween(durationMillis = 2000)
        ) { section ->
            when (section) {
                "SPLASH_BOOT" -> SplashScreen("Starting KLIX POS...")
                "AUTH" -> {
                    MasterAuthLayout(currentState = currentState) { animatingState ->
                        when (animatingState) {
                            AppState.WELCOME -> com.example.barandgrillownerpanel.ui.auth.AuthSelectionScreen(
                                onNavigateToLogin = { currentState = AppState.LOGIN }
                            )
                            AppState.LOGIN -> LoginScreen(
                                funOnLoginSuccess = {
                                    val localSettings = LocalDatabase.getAppSettings()
                                    if (localSettings != null) {
                                        appSettings = localSettings
                                        currentState = AppState.DASHBOARD
                                    } else {
                                        currentState = AppState.ONBOARDING
                                    }
                                },
                                onNavigateToSignUp = { currentState = AppState.SIGNUP },
                                onDevBypass = {
                                    appSettings = LocalDatabase.getAppSettings()
                                        ?: AppSettings()
                                    currentState = AppState.DASHBOARD
                                }
                            )
                            AppState.SIGNUP -> com.example.barandgrillownerpanel.ui.auth.SignUpScreen(
                                onSignUpSuccess = { currentState = AppState.ONBOARDING },
                                onNavigateToLogin = { currentState = AppState.LOGIN }
                            )
                            AppState.ONBOARDING -> OnboardingScreen(
                                onComplete = {
                                    appSettings = LocalDatabase.getAppSettings()
                                    currentState = AppState.DASHBOARD
                                }
                            )
                            else -> {}
                        }
                    }
                }
                AppState.DASHBOARD.name -> {
                    if (appSettings != null) {
                        DashboardScreen(
                            initialSettings = appSettings!!,
                            onSettingsChange = { appSettings = it },
                            onLogout = { currentState = AppState.WELCOME },
                            onLock = { currentState = AppState.LOCKED }
                        )
                    } else {
                        SplashScreen("Recovering settings...")
                        LaunchedEffect(Unit) {
                            delay(1000)
                            currentState = AppState.WELCOME
                        }
                    }
                }
                AppState.LOCKED.name -> {
                    LockScreen(
                        onUnlock = { currentState = AppState.DASHBOARD }
                    )
                }
            }
        }
    }
}

@Composable
fun SplashScreen(message: String = "Starting KLIX POS...") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.barandgrillownerpanel.ui.theme.DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val gradient = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color(0xFF1E2433), com.example.barandgrillownerpanel.ui.theme.DarkBackground),
                center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2),
                radius = size.width * 0.8f
            )
            drawRect(gradient)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "KLIX",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ENTERPRISE POS",
                color = PrimaryOrange,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 6.sp
            )
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(
                color = PrimaryOrange,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(message, color = Color.White.copy(0.5f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

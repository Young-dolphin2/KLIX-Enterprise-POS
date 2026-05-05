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
import com.example.barandgrillownerpanel.ui.auth.LoginScreen
import com.example.barandgrillownerpanel.ui.onboarding.OnboardingScreen
import com.example.barandgrillownerpanel.ui.dashboard.DashboardScreen
import com.example.barandgrillownerpanel.ui.dashboard.AppSettings
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.ui.theme.PrimaryOrange
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.util.prefs.Preferences

enum class AppState {
    SPLASH, LOGIN, WELCOME_LOAD, ONBOARDING, DASHBOARD
}

@Composable
fun MainApp() {
    var currentState by remember { mutableStateOf(AppState.SPLASH) }
    var appSettings by remember { mutableStateOf<AppSettings?>(null) }
    
    LaunchedEffect(Unit) {
        LocalDatabase.initialize()
        delay(1500) // Aesthetic delay for splash
        
        val user = SupabaseManager.client.auth.currentSessionOrNull()?.user
        if (user == null) {
            currentState = AppState.LOGIN
        } else {
            // Try loading from Local Database first (Source of Truth)
            val localSettings = LocalDatabase.getAppSettings()
            if (localSettings != null) {
                appSettings = localSettings
                currentState = AppState.DASHBOARD
            } else {
                // Fallback to Preferences (Legacy)
                val prefs = Preferences.userRoot().node("com.example.barandgrillownerpanel")
                val onboarded = prefs.getBoolean("is_onboarded", false)
                if (onboarded) {
                    val settingsJson = prefs.get("app_settings", null)
                    if (settingsJson != null) {
                        try {
                            appSettings = Json.decodeFromString(settingsJson)
                            currentState = AppState.DASHBOARD
                        } catch (e: Exception) {
                            currentState = AppState.ONBOARDING
                        }
                    } else {
                        currentState = AppState.ONBOARDING
                    }
                } else {
                    currentState = AppState.ONBOARDING
                }
            }
        }
    }

    Crossfade(targetState = currentState, animationSpec = tween(500)) { state ->
        when (state) {
            AppState.SPLASH -> SplashScreen("Initializing Klix POS...")
            AppState.LOGIN -> LoginScreen(
                funOnLoginSuccess = { 
                    currentState = AppState.WELCOME_LOAD 
                },
                onNavigateToSignUp = { /* TODO */ }
            )
            AppState.WELCOME_LOAD -> {
                LaunchedEffect(Unit) {
                    delay(2000)
                    currentState = AppState.ONBOARDING
                }
                SplashScreen("Welcome to Klix! Preparing your workspace...")
            }
            AppState.ONBOARDING -> OnboardingScreen(onComplete = { 
                // Reload settings from Local Database
                appSettings = LocalDatabase.getAppSettings()
                currentState = AppState.DASHBOARD 
            })
            AppState.DASHBOARD -> {
                if (appSettings != null) {
                    DashboardScreen(appSettings!!)
                } else {
                    // Emergency fallback if settings are missing
                    SplashScreen("Recovering settings...")
                    LaunchedEffect(Unit) {
                        delay(1000)
                        currentState = AppState.ONBOARDING
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PrimaryOrange, strokeWidth = 4.dp)
            Spacer(Modifier.height(24.dp))
            Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

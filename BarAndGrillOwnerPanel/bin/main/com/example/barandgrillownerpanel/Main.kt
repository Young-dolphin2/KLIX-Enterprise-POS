package com.example.barandgrillownerpanel

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.barandgrillownerpanel.ui.theme.BarAndGrillPOSTheme
import com.example.barandgrillownerpanel.ui.dashboard.DashboardScreen
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.runtime.*
import java.util.prefs.Preferences
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import com.example.barandgrillownerpanel.ui.auth.LoginScreen
import com.example.barandgrillownerpanel.ui.auth.SignUpScreen
import com.example.barandgrillownerpanel.ui.auth.AuthSelectionScreen
import com.example.barandgrillownerpanel.ui.auth.SplashScreen
import com.example.barandgrillownerpanel.ui.auth.WelcomeLoadScreen
import com.example.barandgrillownerpanel.ui.onboarding.OnboardingScreen
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.barandgrillownerpanel.utils.Logger

enum class Screen {
    SPLASH, AUTH_SELECTION, LOGIN, SIGN_UP, WELCOME_LOAD, ONBOARDING, DASHBOARD
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Preview
fun main() = application {
    val currentScreenState = mutableStateOf<Screen?>(null)
    val lastActivityState = mutableStateOf(System.currentTimeMillis())
    val isLockedState = mutableStateOf(false)
    val errorState = mutableStateOf<Throwable?>(null)

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        Logger.fatal("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
        errorState.value = throwable
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = AppFlavor.current.windowTitle,
        state = WindowState(placement = WindowPlacement.Maximized),
        icon = androidx.compose.ui.res.painterResource(AppFlavor.current.iconResource),
        onKeyEvent = {
            lastActivityState.value = System.currentTimeMillis()
            false
        }
    ) {
        val prefs = remember { Preferences.userRoot().node("com.example.barandgrillownerpanel") }
        val scope = rememberCoroutineScope()
        var currentScreen by currentScreenState
        val sessionStatus by SupabaseManager.client.auth.sessionStatus.collectAsState()
        var lastActivity by lastActivityState
        var isLocked by isLockedState
        val idleTimeout = 5 * 60 * 1000L // 5 minutes

        // Hoist app settings
        var appSettings by remember { mutableStateOf(com.example.barandgrillownerpanel.ui.dashboard.AppSettings()) }

        // Initial Load of settings from Prefs
        LaunchedEffect(Unit) {
            val bName = prefs.get("business_name", "KLIX ENTERPRISE POS")
            val country = prefs.get("country", "Malawi")
            val symbol = prefs.get("currency_symbol", "MK")
            val color = prefs.get("primary_color_hex", "#3B82F6")
            val methodsJson = prefs.get("payment_methods_json", null)
            
            val methods = methodsJson?.let {
                try {
                    kotlinx.serialization.json.Json.decodeFromString<List<com.example.barandgrillownerpanel.ui.dashboard.PaymentMethod>>(it)
                } catch (e: Exception) { null }
            }
            
            appSettings = appSettings.copy(
                businessName = bName,
                country = country,
                currencySymbol = symbol,
                primaryColorHex = color,
                paymentMethods = methods ?: appSettings.paymentMethods
            )
        }

        LaunchedEffect(Unit) {
            currentScreen = Screen.SPLASH
            delay(3000) // 3 seconds splash
            
            val isOnboarded = prefs.getBoolean("is_onboarded", false)
            if (sessionStatus is SessionStatus.Authenticated) {
                if (!isOnboarded) {
                    currentScreen = Screen.ONBOARDING
                } else {
                    currentScreen = Screen.DASHBOARD
                }
            } else {
                currentScreen = Screen.AUTH_SELECTION
            }
        }

        // Handle session changes
        LaunchedEffect(sessionStatus) {
            val isOnboarded = prefs.getBoolean("is_onboarded", false)
            if (sessionStatus is SessionStatus.Authenticated) {
                if (currentScreen == Screen.LOGIN || currentScreen == Screen.SIGN_UP || currentScreen == Screen.AUTH_SELECTION) {
                    if (!isOnboarded) {
                        currentScreen = Screen.WELCOME_LOAD
                    } else {
                        currentScreen = Screen.DASHBOARD
                    }
                }
            } else {
                if (currentScreen == Screen.DASHBOARD) {
                    currentScreen = Screen.AUTH_SELECTION
                }
            }
        }

        LaunchedEffect(currentScreen, isLocked) {
            if (currentScreen == Screen.DASHBOARD && !isLocked) {
                while (true) {
                    delay(1000)
                    if (System.currentTimeMillis() - lastActivity > idleTimeout) {
                        isLocked = true
                    }
                }
            }
        }

        val currentPrimaryColor = remember(appSettings.primaryColorHex) {
            try {
                androidx.compose.ui.graphics.Color(java.lang.Long.parseLong(appSettings.primaryColorHex.removePrefix("#"), 16) or 0xFF000000L)
            } catch (e: Exception) {
                androidx.compose.ui.graphics.Color(0xFFFF9800)
            }
        }

        BarAndGrillPOSTheme(primaryOverride = currentPrimaryColor) {
            // We use a Box to capture all pointer events for the idle timer
            Box(
                modifier = Modifier.fillMaxSize().onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) {
                    lastActivity = System.currentTimeMillis()
                }.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press) {
                    lastActivity = System.currentTimeMillis()
                }
            ) {
                var globalError by errorState
                
                GlobalErrorBoundary(
                    error = globalError,
                    onReset = { globalError = null }
                ) {
                    when (currentScreen) {
                        null, Screen.SPLASH -> {
                            SplashScreen()
                        }
                        Screen.WELCOME_LOAD -> {
                            WelcomeLoadScreen {
                                currentScreen = Screen.ONBOARDING
                            }
                        }
                        Screen.ONBOARDING -> OnboardingScreen(
                            onComplete = {
                                // Reload settings after onboarding
                                val bName = prefs.get("business_name", "KLIX ENTERPRISE POS")
                                val color = prefs.get("primary_color_hex", "#3B82F6")
                                appSettings = appSettings.copy(businessName = bName, primaryColorHex = color)
                                currentScreen = Screen.DASHBOARD
                            }
                        )
                        Screen.AUTH_SELECTION -> AuthSelectionScreen(
                            onNavigateToLogin = { currentScreen = Screen.LOGIN },
                            onNavigateToSignUp = { currentScreen = Screen.SIGN_UP }
                        )
                        Screen.LOGIN -> LoginScreen(
                            funOnLoginSuccess = { 
                                if (prefs.getBoolean("is_onboarded", false)) {
                                    currentScreen = Screen.DASHBOARD 
                                } else {
                                    currentScreen = Screen.WELCOME_LOAD
                                }
                            },
                            onNavigateToSignUp = { currentScreen = Screen.SIGN_UP }
                        )
                        Screen.SIGN_UP -> SignUpScreen(
                            onSignUpSuccess = { 
                                currentScreen = Screen.WELCOME_LOAD
                            },
                            onNavigateToLogin = { currentScreen = Screen.LOGIN }
                        )
                        Screen.DASHBOARD -> DashboardScreen(
                            initialSettings = appSettings,
                            onSettingsChange = { 
                                appSettings = it
                                // Persist to local prefs for next launch
                                prefs.put("business_name", it.businessName)
                                prefs.put("primary_color_hex", it.primaryColorHex)
                                prefs.put("currency_symbol", it.currencySymbol)
                                prefs.put("payment_methods_json", kotlinx.serialization.json.Json.encodeToString(it.paymentMethods))
                            },
                            onLogout = {
                                scope.launch {
                                    try {
                                        SupabaseManager.client.auth.signOut()
                                        // Also clear prefs for deep logout? 
                                        // Maybe not everything, but definitely the session.
                                        // Supabase auth handles session clearing.
                                        currentScreen = Screen.ONBOARDING
                                    } catch (e: Exception) {
                                        Logger.error("AUTH", "Logout failed", e)
                                    }
                                }
                            },
                            onLock = { isLocked = true }
                        )
                    }
                }

                if (isLocked && currentScreen == Screen.DASHBOARD) {
                    com.example.barandgrillownerpanel.ui.auth.LockScreen(
                        onUnlock = {
                            isLocked = false
                            lastActivity = System.currentTimeMillis()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GlobalErrorBoundary(
    error: Throwable?,
    onReset: () -> Unit,
    content: @Composable () -> Unit
) {
    if (error != null) {
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Error,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFFEF5350),
                    modifier = androidx.compose.ui.Modifier.size(64.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))
                androidx.compose.material3.Text(
                    "A critical error occurred",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    fontSize = 24.sp
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    "The application encountered an unexpected problem. This event has been logged.",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
                        .padding(16.dp)
                ) {
                    androidx.compose.material3.Text(
                        error.message ?: error.toString(),
                        color = androidx.compose.ui.graphics.Color(0xFFEF5350),
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(32.dp))
                androidx.compose.material3.Button(
                    onClick = onReset,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFFF9800)
                    )
                ) {
                    androidx.compose.material3.Text("Restart UI", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    } else {
        content()
    }
}

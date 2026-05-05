package com.example.barandgrillownerpanel.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.data.local.LocalDatabase
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.ui.theme.PrimaryOrange
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.prefs.Preferences
import kotlinx.serialization.encodeToString

enum class OnboardingStep {
    BUSINESS_INFO,
    LOCATION,
    CURRENCY,
    PAYMENTS,
    BRANCH,
    FINALIZING
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(OnboardingStep.BUSINESS_INFO) }
    var businessName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("$") }
    var currencyCode by remember { mutableStateOf("USD") }
    var phoneNumber by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("Main Branch") }
    var branchType by remember { mutableStateOf("BAR") }
    
    val settings = remember { 
        com.example.barandgrillownerpanel.ui.dashboard.AppSettings(
            businessName = "",
            country = "",
            currencySymbol = "$",
            primaryColorHex = "#FF5722",
            paymentMethods = listOf(
                com.example.barandgrillownerpanel.ui.dashboard.PaymentMethod("CASH"),
                com.example.barandgrillownerpanel.ui.dashboard.PaymentMethod("MOBILE_MONEY", "M-Pesa"),
                com.example.barandgrillownerpanel.ui.dashboard.PaymentMethod("BANK_TRANSFER")
            )
        ) 
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(500.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = when (currentStep) {
                    OnboardingStep.BUSINESS_INFO -> "Tell us about your business"
                    OnboardingStep.LOCATION -> "Where are you located?"
                    OnboardingStep.CURRENCY -> "Set your currency"
                    OnboardingStep.PAYMENTS -> "Payment Methods"
                    OnboardingStep.BRANCH -> "Create your first branch"
                    OnboardingStep.FINALIZING -> "Finalizing..."
                },
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(8.dp))
            
            if (currentStep != OnboardingStep.FINALIZING) {
                Text(
                    text = "Step ${currentStep.ordinal + 1} of 5",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn(tween(300)) with fadeOut(tween(300)) }
            ) { step ->
                when (step) {
                    OnboardingStep.BUSINESS_INFO -> BusinessInfoStep(businessName, phoneNumber, { businessName = it }, { phoneNumber = it }) {
                        if (businessName.isNotBlank()) currentStep = OnboardingStep.LOCATION
                    }
                    OnboardingStep.LOCATION -> LocationStep(country, { country = it }) {
                        if (country.isNotBlank()) currentStep = OnboardingStep.CURRENCY
                    }
                    OnboardingStep.CURRENCY -> CurrencyStep(currencySymbol, currencyCode, { currencySymbol = it }, { currencyCode = it }) {
                        currentStep = OnboardingStep.PAYMENTS
                    }
                    OnboardingStep.PAYMENTS -> PaymentsStep(settings.paymentMethods.map { it.toString() }) {
                        currentStep = OnboardingStep.BRANCH
                    }
                    OnboardingStep.BRANCH -> BranchStep(branchName, branchType, { branchName = it }, { branchType = it }) {
                        if (branchName.isNotBlank()) {
                            currentStep = OnboardingStep.FINALIZING
                            errorMessage = null
                            scope.launch {
                                try {
                                    // 1. Create Branch in local DB
                                    val branchId = UUID.randomUUID().toString()
                                    LocalDatabase.saveBranch(BranchDto(
                                        id = branchId,
                                        name = branchName,
                                        type = branchType,
                                        is_active = true
                                    ))

                                    // 2. Save Settings to Local Database
                                    val finalSettings = settings.copy(
                                        businessName = businessName,
                                        country = country,
                                        currencySymbol = currencySymbol
                                    )
                                    LocalDatabase.saveAppSettings(
                                        settings = finalSettings,
                                        phone = phoneNumber,
                                        country = country,
                                        currencyCode = currencyCode,
                                        isOnboarded = true
                                    )

                                    // 3. Save to Preferences for legacy support
                                    val prefs = Preferences.userRoot().node("com.example.barandgrillownerpanel")
                                    prefs.putBoolean("is_onboarded", true)
                                    prefs.put("app_settings", kotlinx.serialization.json.Json.encodeToString(finalSettings))

                                    // 4. Push to Supabase (Non-blocking)
                                    scope.launch {
                                        try {
                                            SupabaseManager.client.auth.updateUser {
                                                data {
                                                    put("business_name", kotlinx.serialization.json.JsonPrimitive(businessName))
                                                    put("country", kotlinx.serialization.json.JsonPrimitive(country))
                                                    put("currency_code", kotlinx.serialization.json.JsonPrimitive(currencyCode))
                                                    put("currency_symbol", kotlinx.serialization.json.JsonPrimitive(currencySymbol))
                                                }
                                            }
                                            SupabaseManager.client.postgrest["app_settings"].upsert(
                                                mapOf(
                                                    "business_name" to businessName,
                                                    "country" to country,
                                                    "currency_symbol" to currencySymbol
                                                )
                                            )
                                        } catch (e: Exception) {
                                            println("Supabase sync failed: ${e.message}")
                                        }
                                    }
                                    
                                    onComplete()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    errorMessage = e.message ?: "An unknown error occurred during finalization."
                                    currentStep = OnboardingStep.BRANCH
                                }
                            }
                        }
                    }
                    OnboardingStep.FINALIZING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryOrange)
                            Spacer(Modifier.height(16.dp))
                            Text("Saving your business profile...", color = Color.White)
                            if (errorMessage != null) {
                                Spacer(Modifier.height(16.dp))
                                Text(errorMessage!!, color = Color.Red, fontSize = 12.sp)
                                Button(onClick = { currentStep = OnboardingStep.BRANCH }) {
                                    Text("Go Back and Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessInfoStep(name: String, phone: String, onNameChange: (String) -> Unit, onPhoneChange: (String) -> Unit, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Business Name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = PrimaryOrange,
                cursorColor = PrimaryOrange,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = PrimaryOrange,
                cursorColor = PrimaryOrange,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
        ) {
            Text("Continue", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun LocationStep(country: String, onCountryChange: (String) -> Unit, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = country,
            onValueChange = onCountryChange,
            label = { Text("Country") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = PrimaryOrange,
                cursorColor = PrimaryOrange,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
        ) {
            Text("Continue", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun CurrencyStep(symbol: String, code: String, onSymbolChange: (String) -> Unit, onCodeChange: (String) -> Unit, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = symbol,
                onValueChange = onSymbolChange,
                label = { Text("Symbol") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = PrimaryOrange,
                    cursorColor = PrimaryOrange,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Code (e.g. USD)") },
                modifier = Modifier.weight(2f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = PrimaryOrange,
                    cursorColor = PrimaryOrange,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
        ) {
            Text("Continue", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun PaymentsStep(methods: List<String>, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("The following payment methods will be enabled by default:", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        methods.forEach { method ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryOrange)
                    Spacer(Modifier.width(16.dp))
                    Text(method, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
        ) {
            Text("Looks Good", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun BranchStep(name: String, type: String, onNameChange: (String) -> Unit, onTypeChange: (String) -> Unit, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Branch Name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = PrimaryOrange,
                cursorColor = PrimaryOrange,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(Modifier.height(16.dp))
        
        Text("Branch Type", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf("BAR", "KITCHEN", "RETAIL").forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { onTypeChange(t) },
                    label = { Text(t) },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryOrange,
                        selectedLabelColor = Color.White,
                        labelColor = Color.Gray
                    )
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
        ) {
            Text("Finish Setup", color = Color.White, fontSize = 18.sp)
        }
    }
}

package com.example.barandgrillownerpanel.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
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
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.ui.theme.PrimaryOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
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
        modifier = Modifier.fillMaxSize()
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 100.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
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
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (currentStep != OnboardingStep.FINALIZING) {
                    val progress = (currentStep.ordinal + 1) / 5f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = com.example.barandgrillownerpanel.ui.theme.PrimaryOrange,
                        trackColor = Color(0xFF334155)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Step ${currentStep.ordinal + 1} of 5",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(48.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }
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

                                    onComplete()
                                } catch (e: Exception) {
                                    com.example.barandgrillownerpanel.utils.Logger.error("ONBOARDING", "Onboarding exception", e)
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
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Business Name", color = Color(0xFF94A3B8)) },
            placeholder = { Text("e.g., Mike's Bar & Grill", color = Color(0xFF475569)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = PrimaryOrange
            ),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number", color = Color(0xFF94A3B8)) },
            placeholder = { Text("e.g., +265 991 234 567", color = Color(0xFF475569)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = PrimaryOrange
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(16.dp),
            enabled = name.isNotBlank()
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun LocationStep(country: String, onCountryChange: (String) -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Which country is your business registered in?", color = Color(0xFF94A3B8), fontSize = 16.sp)
        OutlinedTextField(
            value = country,
            onValueChange = onCountryChange,
            label = { Text("Country", color = Color(0xFF94A3B8)) },
            placeholder = { Text("e.g., Malawi", color = Color(0xFF475569)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = PrimaryOrange
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(16.dp),
            enabled = country.isNotBlank()
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun CurrencyStep(symbol: String, code: String, onSymbolChange: (String) -> Unit, onCodeChange: (String) -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Set your local currency", color = Color(0xFF94A3B8), fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = symbol,
                onValueChange = onSymbolChange,
                label = { Text("Symbol", color = Color(0xFF94A3B8)) },
                placeholder = { Text("MK", color = Color(0xFF475569)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = PrimaryOrange
                ),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Currency Code", color = Color(0xFF94A3B8)) },
                placeholder = { Text("MWK", color = Color(0xFF475569)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = PrimaryOrange
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun PaymentsStep(methods: List<String>, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Choose your default payment methods", color = Color(0xFF94A3B8), fontSize = 16.sp)
        Text("You can change these later in Settings", color = Color(0xFF475569), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun BranchStep(name: String, type: String, onNameChange: (String) -> Unit, onTypeChange: (String) -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Create your first branch", color = Color(0xFF94A3B8), fontSize = 16.sp)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Branch Name", color = Color(0xFF94A3B8)) },
            placeholder = { Text("e.g., Downtown Branch", color = Color(0xFF475569)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = PrimaryOrange
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Text("Business Type", color = Color(0xFF94A3B8), fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("BAR" to "🍺 Bar / Restaurant", "SHOP" to "🏪 Retail Shop", "RENTAL" to "🚗 Car Rental", "GYM" to "💪 Gym").forEach { (value, label) ->
                FilterChip(
                    selected = type == value,
                    onClick = { onTypeChange(value) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryOrange.copy(alpha = 0.2f),
                        selectedLabelColor = PrimaryOrange
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(16.dp),
            enabled = name.isNotBlank()
        ) {
            Text("Create & Finish", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.barandgrillownerpanel.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.prefs.Preferences
import kotlin.random.Random
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.ui.dashboard.PaymentMethod
import com.example.barandgrillownerpanel.models.BranchDto

enum class OnboardingStep {
    BUSINESS_INFO, REGIONAL, PAYMENTS, BRANCH, FINALIZING
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.BUSINESS_INFO) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Setup Data
    var businessName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("Malawi") }
    var currencyCode by remember { mutableStateOf("MWK") }
    var currencySymbol by remember { mutableStateOf("MK") }
    val paymentMethods = remember { mutableStateListOf<PaymentMethod>(PaymentMethod("CASH")) }
    var branchName by remember { mutableStateOf("") }
    var branchType by remember { mutableStateOf("BAR") }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).entranceAnimation()) {
        // Left Side: Visuals
        Box(modifier = Modifier.weight(0.8f).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF111827))))) {
            ShootingStarsCanvas()
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(48.dp)) {
                Text("Step ${currentStep.ordinal + 1} of 5", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                Text(
                    text = when(currentStep) {
                        OnboardingStep.BUSINESS_INFO -> "Tell us about your business."
                        OnboardingStep.REGIONAL -> "Where are you located?"
                        OnboardingStep.PAYMENTS -> "How do you get paid?"
                        OnboardingStep.BRANCH -> "Setup your first branch."
                        OnboardingStep.FINALIZING -> "Finalizing your setup..."
                    },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Right Side: Steps
        Box(modifier = Modifier.weight(1.2f).fillMaxHeight().padding(64.dp), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(targetState = currentStep) { step ->
                    when (step) {
                        OnboardingStep.BUSINESS_INFO -> BusinessInfoStep(businessName, phoneNumber, { businessName = it }, { phoneNumber = it }) { 
                            if (businessName.isNotBlank()) currentStep = OnboardingStep.REGIONAL 
                        }
                        OnboardingStep.REGIONAL -> RegionalStep(country, currencyCode, currencySymbol, { country = it }, { currencyCode = it }, { currencySymbol = it }) {
                            currentStep = OnboardingStep.PAYMENTS
                        }
                        OnboardingStep.PAYMENTS -> PaymentsStep(paymentMethods) {
                            currentStep = OnboardingStep.BRANCH
                        }
                        OnboardingStep.BRANCH -> BranchStep(branchName, branchType, { branchName = it }, { branchType = it }) {
                            if (branchName.isNotBlank()) {
                                 currentStep = OnboardingStep.FINALIZING
                                errorMessage = null
                                scope.launch {
                                    // SAVE TO SUPABASE
                                    try {
                                        // 1. Create Branch
                                        val newBranch = BranchDto(name = branchName, type = branchType)
                                        val insertedBranch = SupabaseManager.client.postgrest["branches"].insert(newBranch) { select() }.decodeSingle<BranchDto>()
                                        
                                        // 2. Save Business Profile to Supabase & Local Prefs
                                        val settings = com.example.barandgrillownerpanel.ui.dashboard.AppSettings(
                                            businessName = businessName,
                                            phoneNumber = phoneNumber,
                                            country = country,
                                            currencyCode = currencyCode,
                                            currencySymbol = currencySymbol,
                                            paymentMethods = paymentMethods.toList()
                                        )
                                        
                                        val prefs = java.util.prefs.Preferences.userRoot().node("com.example.barandgrillownerpanel")
                                        prefs.putBoolean("is_onboarded", true)
                                        prefs.put("business_name", businessName)
                                        prefs.put("country", country)
                                        prefs.put("currency_symbol", currencySymbol)
                                        prefs.put("currency_code", currencyCode)
                                        prefs.put("phone_number", phoneNumber)
                                        prefs.put("payment_methods_json", kotlinx.serialization.json.Json.encodeToString(settings.paymentMethods))

                                        // Push to Supabase User Metadata for Sync with Mobile App
                                        try {
                                            SupabaseManager.client.auth.updateUser {
                                                data {
                                                    put("business_name", businessName)
                                                    put("country", country)
                                                    put("currency_code", currencyCode)
                                                    put("currency_symbol", currencySymbol)
                                                    put("payment_methods", kotlinx.serialization.json.Json.encodeToString(settings.paymentMethods))
                                                    put("primary_color_hex", settings.primaryColorHex)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            println("Failed to update user metadata: ${e.message}")
                                        }
                                        
                                        // 3. Save to app_settings table (Critical for Mobile Sync)
                                        try {
                                            SupabaseManager.client.postgrest["app_settings"].upsert(
                                                mapOf(
                                                    "business_name" to businessName,
                                                    "country" to country,
                                                    "currency_symbol" to currencySymbol,
                                                    "primary_color_hex" to settings.primaryColorHex,
                                                    "payment_options" to settings.paymentMethods
                                                )
                                            )
                                        } catch (e: Exception) {
                                            println("Failed to upsert app_settings: ${e.message}")
                                        }

                                        onComplete()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        errorMessage = e.message ?: "An unknown error occurred during finalization."
                                        currentStep = OnboardingStep.BRANCH // Fallback to retry
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
}

@Composable
fun BusinessInfoStep(name: String, phone: String, onNameChange: (String) -> Unit, onPhoneChange: (String) -> Unit, onNext: () -> Unit) {
    Column {
        Text("Business Details", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = onNameChange, label = { Text("Trading Name") },
            modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = phone, onValueChange = onPhoneChange, label = { Text("Primary Contact Phone") },
            modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)) {
            Text("Next: Regional Settings", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RegionalStep(country: String, code: String, symbol: String, onCountry: (String) -> Unit, onCode: (String) -> Unit, onSymbol: (String) -> Unit, onNext: () -> Unit) {
    Column {
        Text("Regional Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("This helps us set the correct currency and tax formats.", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(value = country, onValueChange = onCountry, label = { Text("Country") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = code, onValueChange = onCode, label = { Text("Currency Code (e.g. USD)") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = symbol, onValueChange = onSymbol, label = { Text("Symbol") }, modifier = Modifier.weight(0.5f))
        }
        
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)) {
            Text("Next: Payment Options", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PaymentsStep(methods: MutableList<PaymentMethod>, onNext: () -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Payment Methods", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, null, tint = PrimaryOrange) }
        }
        Text("What payment options do your customers have?", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        
        LazyColumn(modifier = Modifier.height(300.dp).fillMaxWidth()) {
            items(methods) { method ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when(method.type) {
                            "MOBILE_MONEY" -> Icons.Default.PhoneAndroid
                            "BANK_TRANSFER" -> Icons.Default.AccountBalance
                            "POS" -> Icons.Default.CreditCard
                            else -> Icons.Default.Payments
                        }, null, tint = PrimaryOrange)
                        Spacer(Modifier.width(16.dp))
                        Text(method.toString(), color = Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = { methods.remove(method) }) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)) {
            Text("Next: Initial Branch", fontWeight = FontWeight.Bold)
        }
    }

    if (showAddDialog) {
        var type by remember { mutableStateOf("MOBILE_MONEY") }
        var operator by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Payment Option") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type", color = TextSecondary)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("CASH", "MOBILE_MONEY", "BANK_TRANSFER", "POS", "CHEQUE").forEach { t ->
                            FilterChip(
                                selected = type == t, 
                                onClick = { type = t }, 
                                label = { Text(t) }, 
                                modifier = Modifier.padding(end = 4.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryOrange, selectedLabelColor = DarkBackground)
                            )
                        }
                    }
                    if (type == "MOBILE_MONEY" || type == "BANK_TRANSFER") {
                        OutlinedTextField(value = operator, onValueChange = { operator = it }, label = { Text("Operator/Bank Name") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    methods.add(PaymentMethod(type, operator.ifBlank { null }))
                    showAddDialog = false
                    operator = ""
                }) { Text("Add") }
            }
        )
    }
}

@Composable
fun BranchStep(name: String, type: String, onName: (String) -> Unit, onType: (String) -> Unit, onNext: () -> Unit) {
    Column {
        Text("Your First Branch", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = name, onValueChange = onName, label = { Text("Branch Name (e.g. Main Street Bar)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Business Sector", color = TextSecondary, fontSize = 12.sp)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "BAR", "RESTAURANT", "SHOP", "HARDWARE", 
                "BOUTIQUE", "CAR_RENTAL", "GYM", "PHARMACY"
            ).forEach { t ->
                Box(
                    modifier = Modifier.width(120.dp).height(50.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (type == t) PrimaryOrange else Color.White.copy(0.05f))
                        .border(1.dp, if (type == t) PrimaryOrange else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                        .clickable { onType(t) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(t.replace("_", " "), color = if (type == t) DarkBackground else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)) {
            Text("Complete Setup", fontWeight = FontWeight.Black, color = Color.Black)
        }
    }
}

data class Star(
    var x: Float,
    var y: Float,
    var length: Float,
    var speed: Float,
    var alpha: Float,
    var angle: Float
)

@Composable
fun ShootingStarsCanvas() {
    val stars = remember {
        List(15) {
            Star(
                x = Random.nextFloat() * 2000f,
                y = Random.nextFloat() * 2000f,
                length = Random.nextFloat() * 100f + 50f,
                speed = Random.nextFloat() * 10f + 5f,
                alpha = Random.nextFloat(),
                angle = 45f // moving diagonally
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Just reading 'time' forces recomposition
        time.hashCode()

        val width = size.width
        val height = size.height

        for (star in stars) {
            // Update star position
            star.x -= star.speed
            star.y += star.speed

            // Reset if out of bounds
            if (star.x < -star.length || star.y > height + star.length) {
                star.x = width + Random.nextFloat() * 500f
                star.y = -Random.nextFloat() * 500f
                star.speed = Random.nextFloat() * 10f + 5f
                star.alpha = Random.nextFloat() * 0.8f + 0.2f
            }

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = star.alpha),
                        Color.Transparent
                    ),
                    start = Offset(star.x, star.y),
                    end = Offset(star.x + star.length, star.y - star.length)
                ),
                start = Offset(star.x, star.y),
                end = Offset(star.x + star.length, star.y - star.length),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

package com.example.barandgrillownerpanel.subscription

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.utils.Logger
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ============================================================
// CHECKOUT DIALOG
// ============================================================

@Composable
fun CheckoutDialog(
    tenantId: String,
    email: String,
    phone: String,
    businessName: String,
    onDismiss: () -> Unit,
    onCheckoutComplete: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf(SubscriptionPlan.PRO) }
    var selectedInterval by remember { mutableStateOf(BillingInterval.MONTHLY) }
    var selectedGateway by remember { mutableStateOf(PaymentGateway.PAYCHANGU) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checkoutUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val price = when (selectedInterval) {
        BillingInterval.MONTHLY -> selectedPlan.monthlyMwK
        BillingInterval.YEARLY -> selectedPlan.yearlyMwK
    }

    val displayPrice = SubscriptionManager.formatPrice(price, selectedGateway.currency)
    val displayInterval = selectedInterval.displayName.lowercase()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 650.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1D23)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Title
                Text(
                    "Upgrade to KLIX Pro",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    "Get full access to all premium features",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Plan Selection
                Text("Choose your plan", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlanCard(
                        plan = SubscriptionPlan.PRO,
                        isSelected = selectedPlan == SubscriptionPlan.PRO,
                        interval = selectedInterval,
                        onClick = { selectedPlan = SubscriptionPlan.PRO },
                        modifier = Modifier.weight(1f)
                    )
                    PlanCard(
                        plan = SubscriptionPlan.ENTERPRISE,
                        isSelected = selectedPlan == SubscriptionPlan.ENTERPRISE,
                        interval = selectedInterval,
                        onClick = { selectedPlan = SubscriptionPlan.ENTERPRISE },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Billing Interval Toggle
                Text("Billing period", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IntervalChip(
                        label = "Monthly",
                        price = SubscriptionManager.formatPrice(selectedPlan.monthlyMwK, selectedGateway.currency),
                        isSelected = selectedInterval == BillingInterval.MONTHLY,
                        onClick = { selectedInterval = BillingInterval.MONTHLY },
                        modifier = Modifier.weight(1f)
                    )
                    IntervalChip(
                        label = "Yearly (Save ${calculateSavings(selectedPlan)}%)",
                        price = SubscriptionManager.formatPrice(selectedPlan.yearlyMwK, selectedGateway.currency),
                        isSelected = selectedInterval == BillingInterval.YEARLY,
                        onClick = { selectedInterval = BillingInterval.YEARLY },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Payment Method
                Text("Payment method", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethodChip(
                        name = "PayChangu",
                        description = "Airtel Money / TNM Mpamba / Card",
                        currency = "MWK",
                        icon = Icons.Default.PhoneAndroid,
                        isSelected = selectedGateway == PaymentGateway.PAYCHANGU,
                        onClick = { selectedGateway = PaymentGateway.PAYCHANGU },
                        modifier = Modifier.weight(1f)
                    )
                    PaymentMethodChip(
                        name = "Flutterwave",
                        description = "Card / Mobile Money",
                        currency = "USD",
                        icon = Icons.Default.Public,
                        isSelected = selectedGateway == PaymentGateway.FLUTTERWAVE,
                        onClick = { selectedGateway = PaymentGateway.FLUTTERWAVE },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Total display
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryOrange.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total", color = TextSecondary, fontSize = 12.sp)
                            Text(displayPrice, color = PrimaryOrange, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Text("per $displayInterval", color = TextSecondary, fontSize = 12.sp)
                        }
                        if (selectedGateway == PaymentGateway.PAYCHANGU) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("via PayChangu", color = TextSecondary, fontSize = 11.sp)
                                Text("Settles in MWK", color = SuccessGreen, fontSize = 10.sp)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("via Flutterwave", color = TextSecondary, fontSize = 11.sp)
                                Text("Settles in USD", color = SuccessGreen, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage!!, color = ErrorRed, fontSize = 12.sp, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(16.dp))

                // Upgrade Button
                Button(
                    onClick = {
                        if (isLoading) return@Button
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val phoneForGateway = if (selectedGateway == PaymentGateway.PAYCHANGU) phone else ""
                                val result = SubscriptionManager.createCheckout(
                                    gateway = selectedGateway,
                                    plan = selectedPlan,
                                    interval = selectedInterval,
                                    tenantId = tenantId,
                                    email = email,
                                    phone = phoneForGateway,
                                    businessName = businessName
                                )

                                result.fold(
                                    onSuccess = { response ->
                                        checkoutUrl = response.checkout_url
                                        val opened = SubscriptionManager.openCheckoutUrl(response.checkout_url)
                                        if (opened) {
                                            onDismiss()
                                            Logger.info("CHECKOUT", "Browser opened: ${response.checkout_url}")
                                        } else {
                                            errorMessage = "Could not open browser. Copy this URL: ${response.checkout_url}"
                                        }
                                    },
                                    onFailure = { error ->
                                        errorMessage = error.message ?: "Payment initiation failed"
                                        Logger.error("CHECKOUT", "Checkout failed", error)
                                    }
                                )
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Unexpected error"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryOrange,
                        disabledContainerColor = PrimaryOrange.copy(alpha = 0.4f)
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Pay $displayPrice",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Security note
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Secured by ${selectedGateway.displayName}",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ============================================================
// COMPONENTS
// ============================================================

@Composable
fun PlanCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    interval: BillingInterval,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val price = when (interval) {
        BillingInterval.MONTHLY -> plan.monthlyMwK
        BillingInterval.YEARLY -> plan.yearlyMwK
    }
    val borderColor = if (isSelected) PrimaryOrange else Color.White.copy(alpha = 0.15f)
    val bgColor = if (isSelected) PrimaryOrange.copy(alpha = 0.1f) else Color.Transparent

    Surface(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    plan.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) PrimaryOrange else TextPrimary
                )
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(
                    SubscriptionManager.formatPrice(price),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    "/${interval.displayName.lowercase()}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun IntervalChip(
    label: String,
    price: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) PrimaryOrange.copy(alpha = 0.15f) else Color(0xFF2A2D35)
    val borderColor = if (isSelected) PrimaryOrange else Color.Transparent

    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.dp else 0.dp, borderColor),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryOrange else TextPrimary)
            Text(price, fontSize = 11.sp, color = if (isSelected) PrimaryOrange.copy(alpha = 0.8f) else TextSecondary)
        }
    }
}

@Composable
fun PaymentMethodChip(
    name: String,
    description: String,
    currency: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) PrimaryOrange.copy(alpha = 0.15f) else Color(0xFF2A2D35)
    val borderColor = if (isSelected) PrimaryOrange else Color.White.copy(alpha = 0.1f)

    Surface(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.dp else 0.dp, borderColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) PrimaryOrange else TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryOrange else TextPrimary)
                Text(description, fontSize = 10.sp, color = TextSecondary)
                Text(currency, fontSize = 10.sp, color = SuccessGreen)
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ============================================================
// HELPERS
// ============================================================

private fun calculateSavings(plan: SubscriptionPlan): Int {
    val monthlyTotal = plan.monthlyMwK * 12
    val yearlyTotal = plan.yearlyMwK
    return ((monthlyTotal - yearlyTotal) * 100 / monthlyTotal).coerceAtLeast(0)
}

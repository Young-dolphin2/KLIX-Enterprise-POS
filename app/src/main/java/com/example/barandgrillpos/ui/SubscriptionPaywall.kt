package com.example.barandgrillpos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillpos.data.sync.SubscriptionInfo
import com.example.barandgrillpos.data.sync.SubscriptionStatus
import com.example.barandgrillpos.ui.theme.DarkBackground
import com.example.barandgrillpos.ui.theme.ErrorRed
import com.example.barandgrillpos.ui.theme.PrimaryOrange
import com.example.barandgrillpos.ui.theme.SurfaceColor
import com.example.barandgrillpos.ui.theme.TextPrimary
import com.example.barandgrillpos.ui.theme.TextSecondary
import com.example.barandgrillpos.ui.theme.entranceAnimation

@Composable
fun SubscriptionPaywall(
    info: SubscriptionInfo,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).entranceAnimation(),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (info.status == SubscriptionStatus.EXPIRED) Icons.Default.Error else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (info.status == SubscriptionStatus.EXPIRED) ErrorRed else PrimaryOrange,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when (info.status) {
                        SubscriptionStatus.EXPIRED -> "Subscription Expired"
                        SubscriptionStatus.NONE -> "No Active Subscription"
                        SubscriptionStatus.PENDING -> "Subscription Pending"
                        else -> "Subscription Required"
                    },
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (info.status) {
                        SubscriptionStatus.EXPIRED -> "Your license has expired. Please renew it from the Owner Panel to continue using the POS."
                        SubscriptionStatus.NONE -> "You don't have an active license. Please purchase one from the Owner Panel."
                        SubscriptionStatus.PENDING -> "Your payment is being processed. This usually takes a few minutes."
                        else -> "An error occurred while checking your license. Please try again."
                    },
                    color = TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh Status", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Log in to the KLIX Owner Panel on your desktop to manage subscriptions.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

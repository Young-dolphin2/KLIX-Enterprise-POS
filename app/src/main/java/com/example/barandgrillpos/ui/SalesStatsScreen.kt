package com.example.barandgrillpos.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillpos.models.SaleRecord
import com.example.barandgrillpos.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesStatsScreen(
    saleHistory: List<SaleRecord>,
    onBackToPOS: () -> Unit,
    context: android.content.Context? = null,
    onReprint: suspend (SaleRecord) -> Unit
) {
    val totalRevenue = saleHistory.sumOf { it.totalAmount }
    val totalOrders = saleHistory.size
    val avgOrder = if (totalOrders > 0) totalRevenue / totalOrders else 0.0
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MY SALES", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackToPOS) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryOrange) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KPICard(
                    label = "Total Revenue",
                    value = "MK${String.format("%,.0f", totalRevenue)}",
                    icon = Icons.Default.Payments,
                    modifier = Modifier.weight(1f)
                )
                KPICard(
                    label = "Orders",
                    value = "$totalOrders",
                    icon = Icons.Default.Receipt,
                    modifier = Modifier.weight(0.8f)
                )
            }

            KPICard(
                label = "Average Order",
                value = "MK${String.format("%,.0f", avgOrder)}",
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.fillMaxWidth()
            )

            Text("RECENT TRANSACTIONS", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (saleHistory.isEmpty()) {
                    Text("No sales recorded yet", color = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 20.dp))
                }
                saleHistory.reversed().take(10).forEach { sale ->
                    TransactionRow(
                        sale = sale,
                        onReprint = {
                            scope.launch {
                                onReprint(sale)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun KPICard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun TransactionRow(
    sale: SaleRecord,
    onReprint: () -> Unit
) {
    var isPrinting by remember { mutableStateOf(false) }
    val sdf = remember { java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US) }
    val time = remember(sale.timestamp) { sdf.format(java.util.Date(sale.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Receipt icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CharcoalGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Order #${sale.id.take(4).uppercase()}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(time, color = TextSecondary, fontSize = 12.sp)
                if (sale.paymentMethod.isNotEmpty()) {
                    Text(
                        sale.paymentMethod,
                        color = when(sale.paymentMethod) {
                            "Airtel Money" -> Color(0xFFFF4848)
                            "TNM Mpamba"   -> Color(0xFF4CAF50)
                            "Bank"         -> Color(0xFFB0BEC5)
                            else           -> Color(0xFFFFD54F)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text("MK${String.format("%,.2f", sale.totalAmount)}", color = PrimaryOrange, fontWeight = FontWeight.Black, fontSize = 16.sp)

            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = {
                    if (!isPrinting) {
                        isPrinting = true
                        onReprint()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ isPrinting = false }, 3000)
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CharcoalGray)
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryOrange,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Reprint Receipt",
                        tint = PrimaryOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

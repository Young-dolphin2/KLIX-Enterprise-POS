package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.barandgrillownerpanel.ui.theme.hoverEffect

@OptIn(SupabaseExperimental::class)
@Composable
fun OverviewTab(
    saleHistory: List<SaleRecord>,
    settings: AppSettings,
    allSaleHistory: List<SaleRecord> = saleHistory,
    inventoryItems: List<InventoryItem> = emptyList(),
    branches: List<BranchDto> = emptyList(),
    selectedBranch: BranchDto? = null,
    onBranchChange: (BranchDto?) -> Unit = {},
    onNavigateToInventory: () -> Unit = {}
) {
    val today = LocalDate.now().toString()
    val todaySales = saleHistory.filter { 
        val saleDate = java.time.Instant.ofEpochMilli(it.timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString()
        saleDate == today
    }

    val todayRevenue = todaySales.sumOf { it.totalAmount }
    val todayOrders = todaySales.size

    // Employee Stats
    val employeeStats = saleHistory.groupBy { it.soldBy }
        .mapValues { (_, sales) -> sales.sumOf { it.totalAmount } }
        .toList()
        .sortedByDescending { it.second }

    val topEmployee = employeeStats.firstOrNull()

    // Hourly Trend Logic
    val hourlySales = saleHistory.groupBy { 
        java.time.Instant.ofEpochMilli(it.timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .hour 
    }.mapValues { it.value.sumOf { s -> s.totalAmount } }
    
    val maxHourly = (hourlySales.values.maxOrNull() ?: 1.0).toFloat()
    val chartPoints = (0..23).map { hour ->
        (hourlySales[hour]?.toFloat() ?: 0f) / maxHourly
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Overview", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        // Branch Filter Bar
        BranchFilterBar(
            branches = branches,
            selectedBranch = selectedBranch,
            onBranchChange = onBranchChange
        )

        Spacer(modifier = Modifier.height(20.dp))

        // KPI row
        val totalRevenue = saleHistory.sumOf { it.totalAmount }
        val avgOrderValue = if (saleHistory.isEmpty()) 0.0 else totalRevenue / saleHistory.size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KPICard(
                title = "Today's Revenue",
                value = "${settings.currencySymbol} ${String.format("%,.0f", todayRevenue)}",
                icon = Icons.Default.AttachMoney,
                iconTint = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            KPICard(
                title = "Today's Orders",
                value = "$todayOrders",
                icon = Icons.Default.Receipt,
                iconTint = PrimaryOrange,
                modifier = Modifier.weight(1f)
            )
            KPICard(
                title = "Total Revenue",
                value = "${settings.currencySymbol} ${String.format("%,.0f", totalRevenue)}",
                icon = Icons.Default.TrendingUp,
                iconTint = Color(0xFF4FC3F7),
                modifier = Modifier.weight(1f)
            )
            KPICard(
                title = "Top Employee",
                value = topEmployee?.first ?: "N/A",
                icon = Icons.Default.Star,
                iconTint = Color(0xFFFFD700),
                modifier = Modifier.weight(1f)
            )
            val lowStockCount = inventoryItems.count { it.currentStock <= it.lowStockThreshold }
            KPICard(
                title = "Critical Stock",
                value = "$lowStockCount",
                icon = Icons.Default.Warning,
                iconTint = if (lowStockCount > 0) ErrorRed else SuccessGreen,
                modifier = Modifier.weight(1f).clickable { onNavigateToInventory() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth().height(400.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Trend Chart
            Card(
                modifier = Modifier.weight(1.5f).fillMaxHeight().hoverEffect(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Hourly Sales Trend", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Real-time Revenue Trends", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        AreaChart(
                            data = chartPoints,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Employee Leaderboard
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight().hoverEffect(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Employee Performance", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (employeeStats.isEmpty()) {
                        Text("No sales data available", color = TextSecondary)
                    } else {
                        employeeStats.forEachIndexed { index, stat ->
                            EmployeePerformanceRow(
                                name = stat.first,
                                amount = stat.second,
                                rank = index + 1,
                                currency = settings.currencySymbol
                            )
                            if (index < employeeStats.size - 1) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun EmployeePerformanceRow(name: String, amount: Double, rank: Int, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (rank == 1) PrimaryOrange else Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (name.isNotEmpty()) name.take(1).uppercase() else "?",
                    color = if (rank == 1) DarkBackground else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(if (name.isEmpty()) "Unknown" else name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Sales Rep", color = TextSecondary, fontSize = 11.sp)
            }
        }
        Text("$currency ${String.format("%,.0f", amount)}", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun AreaChart(data: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.size < 2) {
            // Draw a flat line or nothing if not enough data
            return@Canvas
        }
        val width = size.width
        val height = size.height
        val spacing = width / (data.size - 1)
        
        val path = Path().apply {
            val firstVal = data.firstOrNull() ?: 0f
            moveTo(0f, height - (firstVal * height))
            for (i in 1 until data.size) {
                val x = i * spacing
                val y = height - (data.getOrElse(i) { 0f } * height)
                
                // Use quadratic bezier for smoothness
                val prevX = (i - 1) * spacing
                val prevY = height - (data[i - 1] * height)
                quadraticTo(prevX, prevY, (prevX + x) / 2, (prevY + y) / 2)
                lineTo(x, y)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        // Draw the background gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(PrimaryOrange.copy(alpha = 0.4f), Color.Transparent)
            )
        )

        // Draw the line
        drawPath(
            path = path,
            color = PrimaryOrange,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}


@Composable
fun KPICard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp).hoverEffect(),
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Text(value, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

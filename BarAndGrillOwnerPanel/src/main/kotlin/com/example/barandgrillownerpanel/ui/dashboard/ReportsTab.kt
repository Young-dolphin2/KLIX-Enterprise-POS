package com.example.barandgrillownerpanel.ui.dashboard

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportsTab(
    saleHistory: List<SaleRecord>,
    inventoryItems: List<InventoryItem>,
    branches: List<BranchDto>,
    settings: AppSettings
) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM").withZone(ZoneId.systemDefault())

    // Branch performance
    val branchRevenue: Map<String, Double> = saleHistory
        .filter { it.branchId != null }
        .groupBy { it.branchId!! }
        .mapValues { (_, sales) -> sales.sumOf { it.totalAmount } }

    val topBranch = branches.maxByOrNull { branchRevenue[it.id ?: ""] ?: 0.0 }

    // Employee performance
    val employeePerformance = saleHistory
        .filter { it.soldBy.isNotBlank() }
        .groupBy { it.soldBy }
        .map { (name, sales) ->
            Triple(name, sales.size, sales.sumOf { it.totalAmount })
        }
        .sortedByDescending { it.third }

    // Low stock items
    val lowStockItems = inventoryItems.filter { it.currentStock <= it.lowStockThreshold }

    // Today vs yesterday revenue
    val today = java.time.LocalDate.now()
    val todayRevenue = saleHistory.filter {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }.sumOf { it.totalAmount }
    val yesterdayRevenue = saleHistory.filter {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == today.minusDays(1)
    }.sumOf { it.totalAmount }
    val revenueGrowth = if (yesterdayRevenue > 0) ((todayRevenue - yesterdayRevenue) / yesterdayRevenue * 100) else 0.0

    // Fetch expenses for profit calculation
    val allExpenses = remember { mutableStateListOf<ExpenseDto>() }
    LaunchedEffect(Unit) {
        try {
            val fetched = SupabaseManager.client.postgrest["expenses"].select().decodeAs<List<ExpenseDto>>()
            allExpenses.clear()
            allExpenses.addAll(fetched)
        } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("REPORTS", "Failed fetching expenses", e) }
    }

    // Overall profit
    val totalExpenses = allExpenses.sumOf { it.amount }
    val totalRevenue = saleHistory.sumOf { it.totalAmount }
    val overallProfit = totalRevenue - totalExpenses

    // Per-branch profit
    val branchProfit: Map<String, Double> = branches.associate { branch ->
        val rev = branchRevenue[branch.id ?: ""] ?: 0.0
        val exp = allExpenses.filter { it.branchId == branch.id }.sumOf { it.amount }
        (branch.id ?: "") to (rev - exp)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Business Reports", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Text("Performance analytics across all branches", color = TextSecondary, fontSize = 13.sp)
            }
            
            Button(
                onClick = { exportSalesToCSV(saleHistory, settings) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        // TOP KPI ROW
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ReportKpiCard(
                "Total Revenue",
                "${settings.currencySymbol} ${String.format("%,.0f", totalRevenue)}",
                Icons.Default.AttachMoney,
                PrimaryOrange,
                Modifier.weight(1f)
            )
            ReportKpiCard(
                "Total Expenses",
                "${settings.currencySymbol} ${String.format("%,.0f", totalExpenses)}",
                Icons.Default.MoneyOff,
                ErrorRed,
                Modifier.weight(1f)
            )
            ReportKpiCard(
                "Overall Profit",
                "${settings.currencySymbol} ${String.format("%,.0f", overallProfit)}",
                if (overallProfit >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                if (overallProfit >= 0) SuccessGreen else ErrorRed,
                Modifier.weight(1f)
            )
            ReportKpiCard(
                "Today's Revenue",
                "${settings.currencySymbol} ${String.format("%,.0f", todayRevenue)}",
                Icons.Default.Today,
                SuccessGreen,
                Modifier.weight(1f)
            )
            ReportKpiCard(
                "Revenue Growth",
                "${if (revenueGrowth >= 0) "+" else ""}${String.format("%.1f", revenueGrowth)}%",
                if (revenueGrowth >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                if (revenueGrowth >= 0) SuccessGreen else ErrorRed,
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        // BRANCH COMPARISON + TOP PERFORMER
        Row(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Branch Revenue Comparison
            Card(
                modifier = Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                    Text("Branch Revenue Comparison", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    if (branches.isEmpty()) {
                        Text("No branches configured.", color = TextSecondary)
                    } else {
                        val maxRevenue = branches.maxOfOrNull { branchRevenue[it.id ?: ""] ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
                        branches.forEach { branch ->
                            val rev = branchRevenue[branch.id ?: ""] ?: 0.0
                            val fraction = (rev / maxRevenue).toFloat().coerceIn(0f, 1f)
                            val color = if (branch.id == topBranch?.id) PrimaryOrange else Color(0xFF4FC3F7)
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (branch.id == topBranch?.id) {
                                            Icon(Icons.Default.EmojiEvents, null, tint = PrimaryOrange, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(branch.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("MK ${String.format("%,.0f", rev)}", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(0.15f))) {
                                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
                                }
                            }
                        }
                    }
                }
            }

            // Top performer card
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = PrimaryOrange.copy(0.12f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, null, tint = PrimaryOrange, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Top Performing Branch", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(topBranch?.name ?: "--", color = PrimaryOrange, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    val topRev = branchRevenue[topBranch?.id ?: ""] ?: 0.0
                    Text("MK ${String.format("%,.0f", topRev)}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Total Revenue", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // PER-BRANCH PROFIT BREAKDOWN
        if (branches.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Profit Per Branch", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                    Text("Revenue minus recorded expenses", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        branches.forEach { branch ->
                            val rev = branchRevenue[branch.id ?: ""] ?: 0.0
                            val exp = allExpenses.filter { it.branchId == branch.id }.sumOf { it.amount }
                            val profit = branchProfit[branch.id ?: ""] ?: 0.0
                            val isProfit = profit >= 0
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isProfit) SuccessGreen.copy(0.1f) else ErrorRed.copy(0.1f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (branch.type == "LIQUOR_SHOP") Icons.Default.LocalDrink else Icons.Default.Storefront,
                                            null, tint = PrimaryOrange, modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(branch.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("Revenue", color = TextSecondary, fontSize = 10.sp)
                                    Text("MK ${String.format("%,.0f", rev)}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Expenses", color = TextSecondary, fontSize = 10.sp)
                                    Text("MK ${String.format("%,.0f", exp)}", color = ErrorRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkBackground)
                                    Text("Net Profit", color = TextSecondary, fontSize = 10.sp)
                                    Text(
                                        "${if (isProfit) "+" else ""}MK ${String.format("%,.0f", profit)}",
                                        color = if (isProfit) SuccessGreen else ErrorRed,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                        // OVERALL column
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (overallProfit >= 0) PrimaryOrange.copy(0.15f) else ErrorRed.copy(0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Leaderboard, null, tint = PrimaryOrange, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("OVERALL", color = PrimaryOrange, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("Revenue", color = TextSecondary, fontSize = 10.sp)
                                Text("MK ${String.format("%,.0f", totalRevenue)}", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Expenses", color = TextSecondary, fontSize = 10.sp)
                                Text("MK ${String.format("%,.0f", totalExpenses)}", color = ErrorRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkBackground)
                                Text("Net Profit", color = TextSecondary, fontSize = 10.sp)
                                Text(
                                    "${if (overallProfit >= 0) "+" else ""}MK ${String.format("%,.0f", overallProfit)}",
                                    color = if (overallProfit >= 0) PrimaryOrange else ErrorRed,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // EMPLOYEE PERFORMANCE + LOW STOCK ALERTS
        Row(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Employee leaderboard
            Card(
                modifier = Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                    Text("Staff Leaderboard", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                    Text("Ranked by total revenue generated", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(16.dp))
                    if (employeePerformance.isEmpty()) {
                        Text("No sales data.", color = TextSecondary)
                    } else {
                        employeePerformance.take(8).forEachIndexed { idx, (name, orders, rev) ->
                            val rankColor = when (idx) {
                                0 -> Color(0xFFFFD700)
                                1 -> Color(0xFFC0C0C0)
                                2 -> Color(0xFFCD7F32)
                                else -> TextSecondary
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(rankColor.copy(0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("#${idx + 1}", color = rankColor, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("$orders orders", color = TextSecondary, fontSize = 10.sp)
                                }
                                Text("MK ${String.format("%,.0f", rev)}", color = rankColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Low stock alerts
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFFCA28), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Low Stock Alerts", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                    }
                    Text("${lowStockItems.size} items need restocking", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    if (lowStockItems.isEmpty()) {
                        Text("✅ All stock levels healthy", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            lowStockItems.take(7).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ErrorRed.copy(0.1f)).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Inventory2, null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(item.category, color = TextSecondary, fontSize = 9.sp)
                                    }
                                    Text("${item.currentStock.toInt()} ${item.unit}", color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
private fun ReportKpiCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 18.sp)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

private fun exportSalesToCSV(sales: List<SaleRecord>, settings: AppSettings) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            val fileName = "KLIX_Sales_Report_$timestamp.csv"
            val userHome = System.getProperty("user.home")
            val downloadsDir = java.io.File(userHome, "Downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            
            val file = java.io.File(downloadsDir, fileName)
            
            java.io.FileWriter(file).use { writer ->
                // Header
                writer.append("Sale ID,Date,Amount (${settings.currencySymbol}),Payment Method,Sold By,Branch ID\n")
                
                // Rows
                sales.forEach { sale ->
                    val date = java.time.Instant.ofEpochMilli(sale.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    
                    writer.append("${sale.id},")
                    writer.append("$date,")
                    writer.append("${sale.totalAmount},")
                    writer.append("${sale.paymentMethod},")
                    writer.append("${sale.soldBy.replace(",", " ")},")
                    writer.append("${sale.branchId ?: "N/A"}\n")
                }
            }
            
            // Try to open the directory
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(downloadsDir)
                }
            } catch (_: Exception) {}
            
        } catch (e: Exception) {
            com.example.barandgrillownerpanel.utils.Logger.error("REPORTS", "Failed export", e)
        }
    }
}

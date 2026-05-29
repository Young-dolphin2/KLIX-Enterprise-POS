package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@OptIn(SupabaseExperimental::class)
@Composable
fun SalesStatsTab(
    branches: List<BranchDto> = emptyList(),
    selectedBranch: BranchDto? = null,
    onBranchChange: (BranchDto?) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val saleHistory = remember { mutableStateListOf<SaleRecord>() }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Item cache to avoid redundant fetching
    val saleItemsCache = remember { mutableStateMapOf<String, List<SaleItem>>() }

    // Initial Fetch & Refresh
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        try {
            // 1. Fetch all sales
            val salesList = SupabaseManager.client?.postgrest?.get("sales")?.select()
                ?.decodeAs<List<SaleDto>>() ?: emptyList()

            // 2. Extract IDs to fetch relevant items
            val saleIds = salesList.map { it.id }

            if (saleIds.isNotEmpty()) {
                // 3. Fetch all related sale items
                val allSaleItems = SupabaseManager.client?.postgrest?.get("sale_items")?.select {
                        filter {
                            isIn("sale_id", saleIds)
                        }
                    }?.decodeAs<List<SaleItemDto>>() ?: emptyList()

                // 4. Group items by sale_id
                val groupedItems = allSaleItems.groupBy { it.saleId }

                // 5. Update Cache mapping
                groupedItems.forEach { (saleId, items) ->
                    saleItemsCache[saleId] = items.map { dto ->
                        SaleItem(
                            item = MenuItem("0", dto.name, dto.price, dto.category, ""),
                            quantity = dto.quantity
                        )
                    }
                }
            }

            // 6. Map to SaleRecord for UI
            val newHistory = salesList.map { dto ->
                SaleRecord(
                    id = dto.orderId,
                    branchId = dto.branchId,
                    items = saleItemsCache[dto.id] ?: emptyList(),
                    totalAmount = dto.totalAmount,
                    paymentMethod = dto.paymentMethod,
                    soldBy = dto.soldBy,
                    timestamp = try {
                        java.time.OffsetDateTime.parse(dto.timestamp).toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                )
            }
            
            saleHistory.clear()
            saleHistory.addAll(newHistory.sortedByDescending { it.timestamp })
            isLoading = false
            // 7. Setup Realtime Listener
            val channel = SupabaseManager.client?.realtime?.channel("public:sales")
            val changeFlow = channel?.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction.Insert>(schema = "public") {
                table = "sales"
            }
            
            channel?.subscribe(blockUntilSubscribed = true)
            
            // Collect new sales and re-fetch
            launch {
                changeFlow?.collect {
                // Short delay to ensure sale_items are inserted by the POS app
                kotlinx.coroutines.delay(1000)
                
                // Re-fetch everything to ensure consistency
                val updatedSalesList = SupabaseManager.client?.postgrest?.get("sales")?.select()
                    ?.decodeAs<List<SaleDto>>() ?: emptyList()
                    
                val updatedSaleIds = updatedSalesList.map { it.id }
                
                if (updatedSaleIds.isNotEmpty()) {
                    val updatedItems = SupabaseManager.client?.postgrest?.get("sale_items")?.select { filter { isIn("sale_id", updatedSaleIds) } }
                        ?.decodeAs<List<SaleItemDto>>() ?: emptyList()
                        
                    val newGroupedItems = updatedItems.groupBy { it.saleId }
                    newGroupedItems.forEach { (sId, items) ->
                        saleItemsCache[sId] = items.map { dto ->
                            SaleItem(item = MenuItem("0", dto.name, dto.price, dto.category, ""), quantity = dto.quantity)
                        }
                    }
                }
                
                val updatedHistory = updatedSalesList.map { dto ->
                    SaleRecord(
                        id = dto.orderId,
                        branchId = dto.branchId,
                        items = saleItemsCache[dto.id] ?: emptyList(),
                        totalAmount = dto.totalAmount,
                        paymentMethod = dto.paymentMethod,
                        soldBy = dto.soldBy,
                        timestamp = try { java.time.OffsetDateTime.parse(dto.timestamp).toInstant().toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
                    )
                }
                
                saleHistory.clear()
                saleHistory.addAll(updatedHistory.sortedByDescending { it.timestamp })
                }
            }
            
        } catch (e: Exception) {
            com.example.barandgrillownerpanel.utils.Logger.error("SALES_STATS", "Realtime subscription error", e)
            errorMessage = e.message ?: e.toString()
            isLoading = false
        }
    }
 
    val filteredHistory = if (selectedBranch == null) {
        saleHistory
    } else {
        val childIds = branches
            .filter { it.parentId == selectedBranch.id }
            .mapNotNull { it.id }
            .toSet()
        val relevantIds = buildSet {
            selectedBranch.id?.let { add(it) }
            addAll(childIds)
        }
        saleHistory.filter { sale -> sale.branchId in relevantIds }
    }

    val totalRevenue = filteredHistory.sumOf { it.totalAmount }
    val totalOrders = filteredHistory.size
    val avgOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sales Statistics", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            if (isLoading) {
                Spacer(modifier = Modifier.width(16.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PrimaryOrange, strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        BranchFilterBar(branches = branches, selectedBranch = selectedBranch, onBranchChange = onBranchChange)
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB00020).copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, Color(0xFFB00020))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFB00020))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Error: $errorMessage", color = TextPrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { errorMessage = null; isLoading = true }) {
                        Text("Retry", color = PrimaryOrange)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // KPI Section
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DesktopKPICard(
                title = "Total Period Revenue",
                value = "MK ${String.format("%,.0f", totalRevenue)}",
                icon = Icons.Default.Payments,
                color = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            DesktopKPICard(
                title = "Transactions",
                value = "$totalOrders",
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                color = PrimaryOrange,
                modifier = Modifier.weight(1f)
            )
            DesktopKPICard(
                title = "Average Order",
                value = "MK ${String.format("%,.0f", avgOrderValue)}",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Income Streams & Category Breakdown
        Row(modifier = Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Category Breakdown
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Category Breakdown", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val foodSales = filteredHistory.sumOf { record ->
                        record.items.filter { it.item.category == "FOOD" }.sumOf { it.item.price * it.quantity }
                    }
                    val drinkSales = filteredHistory.sumOf { record ->
                        record.items.filter { it.item.category == "DRINKS" }.sumOf { it.item.price * it.quantity }
                    }
                    val total = foodSales + drinkSales
                    
                    CategoryBar("FOOD", foodSales, total, PrimaryOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    CategoryBar("DRINKS", drinkSales, total, Color(0xFF4FC3F7))
                }
            }

            // Income Streams Chart (The user specifically asked for this)
            Card(
                modifier = Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Income Streams", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val airtelSales = filteredHistory.filter { it.paymentMethod == "Airtel Money" }.sumOf { it.totalAmount }
                    val tnmSales = filteredHistory.filter { it.paymentMethod == "TNM Mpamba" }.sumOf { it.totalAmount }
                    val bankSales = filteredHistory.filter { it.paymentMethod == "Bank" }.sumOf { it.totalAmount }
                    val cashSales = filteredHistory.filter { it.paymentMethod == "Cash" || it.paymentMethod.isEmpty() }.sumOf { it.totalAmount }
                    
                    val grandTotal = airtelSales + tnmSales + bankSales + cashSales
                    
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        IncomeStreamRow("Airtel Money", airtelSales, grandTotal, Color(0xFFFF4848)) // Red
                        Spacer(modifier = Modifier.height(12.dp))
                        IncomeStreamRow("TNM Mpamba", tnmSales, grandTotal, Color(0xFF4CAF50)) // Green
                        Spacer(modifier = Modifier.height(12.dp))
                        IncomeStreamRow("Bank", bankSales, grandTotal, Color(0xFFB0BEC5)) // Silver/Grey
                        Spacer(modifier = Modifier.height(12.dp))
                        IncomeStreamRow("Cash", cashSales, grandTotal, Color(0xFFFFD54F)) // Yellow
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Recent Transactions Table
        Text("Recent Transactions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Order ID", modifier = Modifier.weight(1f), color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Method", modifier = Modifier.weight(1f), color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Items", modifier = Modifier.weight(2f), color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Total", modifier = Modifier.weight(1f), color = TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                
                if (filteredHistory.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions found", color = TextSecondary)
                    }
                }

                filteredHistory.forEach { sale ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sale.id, modifier = Modifier.weight(1f), color = TextPrimary)
                        
                        // Payment Method Tag
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when(sale.paymentMethod) {
                                "Airtel Money" -> Color(0xFFFF4848).copy(alpha = 0.1f)
                                "TNM Mpamba"   -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                "Bank"         -> Color(0xFFB0BEC5).copy(alpha = 0.1f)
                                else           -> Color(0xFFFFD54F).copy(alpha = 0.1f)
                            },
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                sale.paymentMethod.ifEmpty { "Cash" }, 
                                color = when(sale.paymentMethod) {
                                    "Airtel Money" -> Color(0xFFFF4848)
                                    "TNM Mpamba"   -> Color(0xFF4CAF50)
                                    "Bank"         -> Color(0xFFB0BEC5)
                                    else           -> Color(0xFFFFD54F)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }
                        
                        Text(sale.items.joinToString { "${it.quantity}x ${it.item.name}" }, modifier = Modifier.weight(2f), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("MK ${String.format("%,.0f", sale.totalAmount)}", modifier = Modifier.weight(1f), color = PrimaryOrange, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun IncomeStreamRow(label: String, value: Double, total: Double, color: Color) {
    val ratio = if (total > 0) (value / total).toFloat() else 0f
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = TextPrimary, fontSize = 14.sp)
            }
            Text("MK ${String.format("%,.0f", value)}", color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(SurfaceColor)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(ratio).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun CategoryBar(label: String, value: Double, total: Double, color: Color) {
    val ratio = if (total > 0) (value / total).toFloat() else 0f
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 14.sp)
            Text("MK ${String.format("%,.0f", value)} (${String.format("%.1f", ratio * 100)}%)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(SurfaceColor)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(ratio).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun DesktopKPICard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(140.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(value, color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
    }
}






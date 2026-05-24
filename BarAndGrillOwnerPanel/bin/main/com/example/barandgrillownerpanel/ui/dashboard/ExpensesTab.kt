package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@Composable
fun ExpensesTab(
    branches: List<BranchDto>,
    selectedBranch: BranchDto?,
    onBranchChange: (BranchDto?) -> Unit,
    saleHistory: List<SaleRecord>,
    expenses: List<ExpenseDto>,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val expenseCategories = listOf(
        "Utilities", "Salaries", "Breakages", "Transport", "Rent",
        "Marketing", "Maintenance", "Supplies", "Other"
    )

    val filteredExpenses = remember(expenses, selectedBranch) {
        if (selectedBranch == null) expenses
        else expenses.filter { it.branchId == selectedBranch.id }
    }

    val totalExpenses = filteredExpenses.sumOf { it.amount }
    val totalRevenue = saleHistory.sumOf { it.totalAmount }
    val isBreakEven = totalRevenue >= totalExpenses
    val breakEvenPercent = if (totalExpenses > 0) ((totalRevenue - totalExpenses) / totalExpenses * 100) else 0.0

    // Category totals
    val categoryTotals = filteredExpenses.groupBy { it.category }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }

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
                Text("Expense Management", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Text("Track and manage business expenditure", color = TextSecondary, fontSize = 13.sp)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Expense", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Branch Filter
        BranchFilterBar(
            branches = branches,
            selectedBranch = selectedBranch,
            onBranchChange = onBranchChange
        )

        Spacer(Modifier.height(20.dp))

        // Top KPI Row
        Row(
            modifier = Modifier.fillMaxWidth().height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Break-even Card
            Card(
                modifier = Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = if (isBreakEven) SuccessGreen.copy(0.15f) else ErrorRed.copy(0.15f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isBreakEven) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isBreakEven) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isBreakEven) "✅ PAST BREAK-EVEN" else "⚠️ BELOW BREAK-EVEN",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isBreakEven) SuccessGreen else ErrorRed,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "${if (isBreakEven) "+" else ""}${String.format("%.1f", breakEvenPercent)}%",
                        fontSize = 28.sp, fontWeight = FontWeight.Black,
                        color = if (isBreakEven) SuccessGreen else ErrorRed
                    )
                    Text("Revenue vs Expenses", color = TextSecondary, fontSize = 11.sp)
                }
            }
            // Total Expenses Card
            ExpenseKpiCard("Total Expenses", "MK ${String.format("%,.0f", totalExpenses)}", Icons.Default.MoneyOff, ErrorRed, Modifier.weight(1f))
            // Total Revenue Card
            ExpenseKpiCard("Total Revenue", "MK ${String.format("%,.0f", totalRevenue)}", Icons.Default.AttachMoney, SuccessGreen, Modifier.weight(1f))
            // Net Profit Card
            val netProfit = totalRevenue - totalExpenses
            ExpenseKpiCard(
                "Net Profit",
                "MK ${String.format("%,.0f", netProfit)}",
                Icons.Default.AccountBalance,
                if (netProfit >= 0) PrimaryOrange else ErrorRed,
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Category breakdown and expense list side by side
        Row(
            modifier = Modifier.fillMaxWidth().height(450.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT: Expense List
            Card(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                    Text("Expense Log", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${filteredExpenses.size} entries", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = PrimaryOrange)
                    } else if (filteredExpenses.isEmpty()) {
                        Text("No expenses recorded yet.", color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredExpenses.sortedByDescending { it.created_at }) { expense ->
                                ExpenseRow(expense, branches)
                            }
                        }
                    }
                }
            }

            // RIGHT: Category summary
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                    Text("Top Expense Categories", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))

                    if (categoryTotals.isEmpty()) {
                        Text("No data yet", color = TextSecondary)
                    } else {
                        categoryTotals.take(5).forEachIndexed { idx, (cat, amt) ->
                            val percent = if (totalExpenses > 0) (amt / totalExpenses).toFloat() else 0f
                            val catColor = expenseCategoryColor(cat)

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                        .background(catColor.copy(0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${idx + 1}", color = catColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(cat, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${(percent * 100).toInt()}%", color = catColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(catColor.copy(0.15f))
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(percent).fillMaxHeight()
                                                .background(catColor)
                                        )
                                    }
                                    Text("MK ${String.format("%,.0f", amt)}", color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Expense Dialog
    if (showAddDialog) {
        var expCategory by remember { mutableStateOf(expenseCategories.first()) }
        var expDescription by remember { mutableStateOf("") }
        var expAmount by remember { mutableStateOf("") }
        var expBranch by remember { mutableStateOf(selectedBranch ?: branches.firstOrNull()) }
        var isSaving by remember { mutableStateOf(false) }
        var catExpanded by remember { mutableStateOf(false) }
        var branchExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showAddDialog = false },
            containerColor = SurfaceColor,
            title = { Text("Add Business Expense", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Branch selector
                    if (branches.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = expBranch?.name ?: "Select Branch",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Branch", color = TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { branchExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                            )
                            DropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                                branches.forEach { b ->
                                    DropdownMenuItem(text = { Text(b.name, color = TextPrimary) }, onClick = { expBranch = b; branchExpanded = false })
                                }
                            }
                        }
                    }

                    // Category
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = expCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { catExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                        )
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                            expenseCategories.forEach { c ->
                                DropdownMenuItem(text = { Text(c, color = TextPrimary) }, onClick = { expCategory = c; catExpanded = false })
                            }
                        }
                    }

                    OutlinedTextField(
                        value = expDescription,
                        onValueChange = { expDescription = it },
                        label = { Text("Description", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                    )
                    OutlinedTextField(
                        value = expAmount,
                        onValueChange = { expAmount = it },
                        label = { Text("Amount (MK)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = expAmount.toDoubleOrNull() ?: 0.0
                        if (amount <= 0) {
                            // In a real app we'd show a Snackbar or error text. 
                            // For now, we just disable the button or prevent the launch.
                            return@Button
                        }
                        
                        // Basic description validation: ensure it's not just whitespace or suspicious characters
                        if (expDescription.trim().length < 3) return@Button

                        isSaving = true
                        scope.launch {
                            try {
                                val dto = ExpenseDto(
                                    branchId = expBranch?.id,
                                    category = expCategory,
                                    description = expDescription.trim(),
                                    amount = amount
                                )
                                SupabaseManager.client.postgrest["expenses"].insert(dto)
                                onRefresh()
                                showAddDialog = false
                            } catch (e: Exception) {
                                com.example.barandgrillownerpanel.utils.Logger.error("EXPENSES", "Expense action failed", e)
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    enabled = !isSaving && expDescription.isNotBlank() && expAmount.isNotBlank() && (expAmount.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Save Expense", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun ExpenseKpiCard(
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
            Text(value, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseDto, branches: List<BranchDto>) {
    val branchName = branches.find { it.id == expense.branchId }?.name ?: "General"
    val color = expenseCategoryColor(expense.category)

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(expenseCategoryIcon(expense.category), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("${expense.category} • $branchName", color = TextSecondary, fontSize = 11.sp)
            }
            Text(
                "MK ${String.format("%,.0f", expense.amount)}",
                color = ErrorRed,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

private fun expenseCategoryColor(category: String): Color = when (category) {
    "Utilities" -> Color(0xFF4FC3F7)
    "Salaries" -> Color(0xFFAB47BC)
    "Breakages" -> Color(0xFFEF5350)
    "Transport" -> Color(0xFF26A69A)
    "Rent" -> Color(0xFFFF7043)
    "Marketing" -> Color(0xFF42A5F5)
    "Maintenance" -> Color(0xFFFFCA28)
    "Supplies" -> Color(0xFF66BB6A)
    else -> TextSecondary
}

private fun expenseCategoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    "Utilities" -> Icons.Default.Bolt
    "Salaries" -> Icons.Default.Person
    "Breakages" -> Icons.Default.BrokenImage
    "Transport" -> Icons.Default.DirectionsCar
    "Rent" -> Icons.Default.Home
    "Marketing" -> Icons.Default.Campaign
    "Maintenance" -> Icons.Default.Build
    "Supplies" -> Icons.Default.ShoppingCart
    else -> Icons.Default.Receipt
}

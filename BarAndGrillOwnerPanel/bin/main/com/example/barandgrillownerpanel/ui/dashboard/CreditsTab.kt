package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.models.CreditDto
import com.example.barandgrillownerpanel.models.InventoryItemDto
import com.example.barandgrillownerpanel.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime



@Composable
fun CreditsTab(
    credits: List<CreditDto>,
    branches: List<BranchDto>,
    selectedBranch: BranchDto?,
    onBranchChange: (BranchDto?) -> Unit,
    onSaveCredit: (CreditDto) -> Unit,
    onSaveInventoryCredit: (CreditInventorySubmission) -> Unit,
    onSettleCredit: (CreditDto) -> Unit,
    allInventoryItems: List<InventoryItem>
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val givenCredits = credits.filter { it.creditType == "GIVEN" && !it.isSettled }
    val receivedCredits = credits.filter { it.creditType == "RECEIVED" && !it.isSettled }
    val settledCredits = credits.filter { it.isSettled }

    val totalGiven = givenCredits.sumOf { it.amount }
    val totalReceived = receivedCredits.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Credits & Debts", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Record")
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Record", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        BranchFilterBar(branches = branches, selectedBranch = selectedBranch, onBranchChange = onBranchChange)
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DesktopKPICard(
                title = "Money Owed To Us (Given)",
                value = "MK ${String.format("%,.0f", totalGiven)}",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                color = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            DesktopKPICard(
                title = "Money We Owe (Received)",
                value = "MK ${String.format("%,.0f", totalReceived)}",
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                color = ErrorRed,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Tab selection for filtering view
        var viewMode by remember { mutableStateOf("GIVEN") } // GIVEN, RECEIVED, SETTLED
        
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            listOf("GIVEN" to "Customers Owe Us", "RECEIVED" to "We Owe Suppliers", "SETTLED" to "Settled History")?.forEach { (mode, label) ->
                val isSelected = viewMode == mode
                TextButton(
                    onClick = { viewMode = mode },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isSelected) PrimaryOrange else TextSecondary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        val displayList = when(viewMode) {
            "GIVEN" -> givenCredits
            "RECEIVED" -> receivedCredits
            else -> settledCredits
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = CharcoalGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Date", modifier = Modifier.weight(1f), color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text(if(viewMode=="GIVEN") "Customer" else "Supplier", modifier = Modifier.weight(1.5f), color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Description", modifier = Modifier.weight(2f), color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Amount", modifier = Modifier.weight(1f), color = TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Text("Actions", modifier = Modifier.width(100.dp), color = TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }

                if (displayList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No records found", color = TextSecondary)
                        }
                    }
                }

                items(displayList) { credit ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val dateText = try {
                            val odt = OffsetDateTime.parse(credit.created_at ?: "")
                            odt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        } catch (e: Exception) { "Unknown" }
                        
                        Text(dateText, modifier = Modifier.weight(1f), color = TextPrimary)
                        Text(credit.contactName, modifier = Modifier.weight(1.5f), color = TextPrimary, fontWeight = FontWeight.Bold)
                        Column(modifier = Modifier.weight(2f)) {
                            Text(credit.description, color = TextPrimary, fontSize = 14.sp)
                            if (!credit.notes.isNullOrEmpty()) {
                                Text(credit.notes, color = TextSecondary, fontSize = 12.sp)
                            }
                            if (viewMode == "SETTLED" && credit.settledAt != null) {
                                val settledDate = try {
                                    OffsetDateTime.parse(credit.settledAt).format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                                } catch (e: Exception) { "" }
                                Text("Settled: $settledDate", color = SuccessGreen, fontSize = 12.sp)
                            }
                        }
                        
                        val amountColor = if (credit.creditType == "GIVEN") SuccessGreen else ErrorRed
                        Text("MK ${String.format("%,.0f", credit.amount)}", modifier = Modifier.weight(1f), color = amountColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        
                        Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.Center) {
                            if (!credit.isSettled) {
                                Button(
                                    onClick = { onSettleCredit(credit) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha=0.2f), contentColor = SuccessGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Settle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Settled", tint = SuccessGreen)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCreditDialog(
            branches = branches,
            selectedBranch = selectedBranch,
            allInventoryItems = allInventoryItems,
            onDismiss = { showAddDialog = false },
            onSave = { submission ->
                when (submission) {
                    is CreditFormSubmission.Other -> onSaveCredit(submission.credit)
                    is CreditFormSubmission.Inventory -> onSaveInventoryCredit(submission.payload)
                }
                showAddDialog = false
            }
        )
    }
}

private fun creditInventoryPackMultiplier(packageType: String): Int = when (packageType) {
    "Crate (20)" -> 20
    "Crate (6)" -> 6
    "Spirits Bottle (25 shots)" -> 25
    "Wine Bottle (5 glasses)" -> 5
    else -> 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCreditDialog(
    branches: List<BranchDto>,
    selectedBranch: BranchDto?,
    allInventoryItems: List<InventoryItem>,
    onDismiss: () -> Unit,
    onSave: (CreditFormSubmission) -> Unit
) {
    val mainCategories = listOf("FOOD", "DRINKS", "SIDES", "DESSERTS")
    val subCategoryMap = mapOf(
        "FOOD" to listOf("Burgers", "Pizza", "Steak", "Salads", "Sides", "Other"),
        "DRINKS" to listOf("Beer", "Wine", "Cocktails", "Whiskey", "Gin", "Vodka", "Rum", "Minerals", "Ciders", "Brandy", "Liquers", "Tequila", "Champagne", "Cognac"),
        "SIDES" to listOf("Fries", "Rice", "Vegetables", "Other"),
        "DESSERTS" to listOf("Cake", "Ice Cream", "Other")
    )
    val packageOptions = listOf("Crate (20)", "Crate (6)", "Spirits Bottle (25 shots)", "Wine Bottle (5 glasses)", "Unit (1)")

    var branchId by remember { mutableStateOf(selectedBranch?.id) }
    var contactName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var creditType by remember { mutableStateOf("GIVEN") }
    var notes by remember { mutableStateOf("") }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var entryType by remember { mutableStateOf("OTHER") }
    var inventoryFlow by remember { mutableStateOf("INBOUND") } // INBOUND = we owe + stock in; OUTBOUND = they owe + stock out
    var linkedInventoryRowId by remember { mutableStateOf<String?>(null) }
    var inventoryName by remember { mutableStateOf("") }
    var nameSuggestionsExpanded by remember { mutableStateOf(false) }
    var inventoryCategory by remember { mutableStateOf("DRINKS") }
    var inventorySubcategory by remember { mutableStateOf("Other") }
    var inventoryCostText by remember { mutableStateOf("") }
    var inventoryRetailText by remember { mutableStateOf("") }
    var inventoryThresholdText by remember { mutableStateOf("10") }
    var packageType by remember { mutableStateOf("Unit (1)") }
    var packageQuantity by remember { mutableIntStateOf(1) }
    var syncCloneBranches by remember { mutableStateOf(setOf<BranchDto>()) }
    var newItemCategoryExpanded by remember { mutableStateOf(false) }
    var newItemSubcategoryExpanded by remember { mutableStateOf(false) }
    var packTypeMenuExpanded by remember { mutableStateOf(false) }

    val branchInventory = remember(branchId, allInventoryItems) {
        allInventoryItems.filter { inv -> inv.branchId == branchId }
    }

    val nameSuggestions = remember(inventoryName, branchInventory) {
        val q = inventoryName.trim()
        if (q.length < 1) return@remember emptyList<InventoryItem>()
        branchInventory
            .filter { it.name.contains(q, ignoreCase = true) }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<InventoryItem> { !it.name.startsWith(q, ignoreCase = true) }
                    .thenBy { it.name.lowercase() }
            )
            .take(12)
    }

    val resolvedExistingId: String? = linkedInventoryRowId ?: run {
        val n = inventoryName.trim()
        if (n.isBlank()) null
        else branchInventory.filter { it.name.equals(n, ignoreCase = true) }.singleOrNull()?.id
    }

    val matchedRow: InventoryItem? = resolvedExistingId?.let { id -> branchInventory.find { it.id == id } }
    val isExistingItem = matchedRow != null
    val totalPackUnits = (packageQuantity * creditInventoryPackMultiplier(packageType)).toDouble()

    LaunchedEffect(inventoryFlow, matchedRow?.id) {
        if (inventoryFlow == "OUTBOUND" && matchedRow != null && inventoryCostText.isBlank()) {
            inventoryCostText = formatMoneyField(matchedRow.unitCost)
        }
    }

    fun applyPick(item: InventoryItem) {
        linkedInventoryRowId = item.id
        inventoryName = item.name
        inventoryCategory = item.category
        inventorySubcategory = item.subcategory
        inventoryCostText = formatMoneyField(item.unitCost)
        inventoryRetailText = formatMoneyField(item.retailPrice)
        inventoryThresholdText = item.lowStockThreshold.toInt().coerceAtLeast(0).toString()
        nameSuggestionsExpanded = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Credit Record", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = entryType == "OTHER",
                        onClick = { entryType = "OTHER" },
                        label = { Text("Other") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = entryType == "INVENTORY",
                        onClick = {
                            entryType = "INVENTORY"
                            inventoryFlow = "INBOUND"
                            linkedInventoryRowId = null
                        },
                        label = { Text("Inventory") },
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = branchDropdownExpanded,
                    onExpandedChange = { branchDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = branches.find { it.id == branchId }?.name ?: "Select Branch",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Branch", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = branchDropdownExpanded,
                        onDismissRequest = { branchDropdownExpanded = false }
                    ) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = {
                                    branchId = branch.id
                                    branchDropdownExpanded = false
                                    linkedInventoryRowId = null
                                }
                            )
                        }
                    }
                }

                if (entryType == "OTHER") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = creditType == "GIVEN",
                            onClick = { creditType = "GIVEN" },
                            label = { Text("Customer Owes Us") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SuccessGreen.copy(alpha = 0.2f), selectedLabelColor = SuccessGreen),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = creditType == "RECEIVED",
                            onClick = { creditType = "RECEIVED" },
                            label = { Text("We Owe Supplier") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ErrorRed.copy(alpha = 0.2f), selectedLabelColor = ErrorRed),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = inventoryFlow == "INBOUND",
                            onClick = { inventoryFlow = "INBOUND" },
                            label = { Text("Stock on credit (we owe)") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ErrorRed.copy(alpha = 0.2f), selectedLabelColor = ErrorRed),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = inventoryFlow == "OUTBOUND",
                            onClick = {
                                inventoryFlow = "OUTBOUND"
                                linkedInventoryRowId = null
                            },
                            label = { Text("Stock taken (they owe us)") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SuccessGreen.copy(alpha = 0.2f), selectedLabelColor = SuccessGreen),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PrimaryOrange.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PrimaryOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (inventoryFlow == "INBOUND")
                                "Adds units to inventory and records money you owe the supplier (Received)."
                            else
                                "Removes units from inventory and records money owed to you (Given). Pick an existing item.",
                            color = PrimaryOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = {
                        Text(
                            when {
                                entryType == "INVENTORY" -> "Supplier / counterparty name"
                                creditType == "GIVEN" -> "Customer Name"
                                else -> "Supplier Name"
                            },
                            color = TextSecondary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                if (entryType == "OTHER") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("What was given?", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (MK)", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                } else {
                    if (branchId == null) {
                        Text("Select a branch to search inventory and sync stock.", color = ErrorRed, fontSize = 12.sp)
                    }

                    ExposedDropdownMenuBox(
                        expanded = nameSuggestionsExpanded && nameSuggestions.isNotEmpty(),
                        onExpandedChange = { nameSuggestionsExpanded = it && nameSuggestions.isNotEmpty() }
                    ) {
                        OutlinedTextField(
                            value = inventoryName,
                            onValueChange = { v ->
                                inventoryName = v
                                val stillLinked = linkedInventoryRowId?.let { id ->
                                    branchInventory.find { it.id == id }?.name?.equals(v.trim(), ignoreCase = true) == true
                                } ?: false
                                if (!stillLinked) linkedInventoryRowId = null
                                nameSuggestionsExpanded = v.isNotBlank() && nameSuggestions.isNotEmpty()
                            },
                            label = { Text("Item name (suggestions from this branch)", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = nameSuggestionsExpanded && nameSuggestions.isNotEmpty(),
                            onDismissRequest = { nameSuggestionsExpanded = false },
                            modifier = Modifier.background(SurfaceColor)
                        ) {
                            nameSuggestions.forEach { inv ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(inv.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            Text("Stock ${inv.currentStock.toInt()} · Retail MK ${String.format("%,.0f", inv.retailPrice)}", color = TextSecondary, fontSize = 11.sp)
                                        }
                                    },
                                    onClick = { applyPick(inv) }
                                )
                            }
                        }
                    }

                    if (isExistingItem && matchedRow != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Matched inventory row — selling price stays on the menu.", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${matchedRow.category} · ${matchedRow.subcategory}", color = TextSecondary, fontSize = 11.sp)
                                Text("Selling price (unchanged): MK ${String.format("%,.0f", matchedRow.retailPrice)}", color = TextPrimary, fontSize = 12.sp)
                                Text("Current stock: ${matchedRow.currentStock.toInt()} ${matchedRow.unit}", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    } else if (entryType == "INVENTORY" && inventoryFlow == "INBOUND") {
                        Text("New item — same details as Inventory → Add New Item.", color = TextSecondary, fontSize = 12.sp)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = inventoryCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category", color = TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { newItemCategoryExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                            DropdownMenu(
                                expanded = newItemCategoryExpanded,
                                onDismissRequest = { newItemCategoryExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.5f).background(SurfaceColor)
                            ) {
                                mainCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat, color = TextPrimary) },
                                        onClick = {
                                            inventoryCategory = cat
                                            inventorySubcategory = subCategoryMap[cat]?.first() ?: "Other"
                                            newItemCategoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = inventorySubcategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Subcategory", color = TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { newItemSubcategoryExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                            DropdownMenu(
                                expanded = newItemSubcategoryExpanded,
                                onDismissRequest = { newItemSubcategoryExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.5f).background(SurfaceColor)
                            ) {
                                subCategoryMap[inventoryCategory]?.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub, color = TextPrimary) },
                                        onClick = {
                                            inventorySubcategory = sub
                                            newItemSubcategoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = inventoryCostText,
                                onValueChange = { inventoryCostText = it },
                                label = { Text("Cost price", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                            OutlinedTextField(
                                value = inventoryRetailText,
                                onValueChange = { inventoryRetailText = it },
                                label = { Text("Retail price", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                        }
                        OutlinedTextField(
                            value = inventoryThresholdText,
                            onValueChange = { inventoryThresholdText = it },
                            label = { Text("Low alert threshold (units)", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                        )
                        Text("Inventory intake (packs)", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1.5f)) {
                                OutlinedTextField(
                                    value = packageType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Pack type", color = TextSecondary) },
                                    trailingIcon = {
                                        IconButton(onClick = { packTypeMenuExpanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                                )
                                DropdownMenu(expanded = packTypeMenuExpanded, onDismissRequest = { packTypeMenuExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                                    packageOptions.forEach { opt ->
                                        DropdownMenuItem(text = { Text(opt, color = TextPrimary) }, onClick = { packageType = opt; packTypeMenuExpanded = false })
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(4.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { if (packageQuantity > 1) packageQuantity-- }) {
                                    Icon(Icons.Default.Remove, contentDescription = null, tint = PrimaryOrange)
                                }
                                Text("$packageQuantity", color = TextPrimary, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { packageQuantity++ }) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryOrange)
                                }
                            }
                        }
                        Text("Total units: ${totalPackUnits.toInt()}", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val otherBranches = branches.filter { it.id != null && it.id != branchId }
                        if (otherBranches.isNotEmpty()) {
                            Text("Also create 0-stock copy in:", color = TextSecondary, fontSize = 12.sp)
                            otherBranches.forEach { ob ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = syncCloneBranches.contains(ob),
                                        onCheckedChange = { chk ->
                                            syncCloneBranches = if (chk) syncCloneBranches + ob else syncCloneBranches - ob
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                                    )
                                    Text(ob.name, color = TextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = true, onCheckedChange = {}, enabled = false, colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange))
                            Text("Menu auto-synced from inventory", color = TextPrimary, fontSize = 13.sp)
                        }
                    }

                    if (isExistingItem || inventoryFlow == "OUTBOUND") {
                        Text("Quantity to ${if (inventoryFlow == "INBOUND") "add" else "remove"}", color = TextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1.5f)) {
                                OutlinedTextField(
                                    value = packageType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Pack type", color = TextSecondary) },
                                    trailingIcon = {
                                        IconButton(onClick = { packTypeMenuExpanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                                )
                                DropdownMenu(expanded = packTypeMenuExpanded, onDismissRequest = { packTypeMenuExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                                    packageOptions.forEach { opt ->
                                        DropdownMenuItem(text = { Text(opt, color = TextPrimary) }, onClick = { packageType = opt; packTypeMenuExpanded = false })
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(4.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { if (packageQuantity > 1) packageQuantity-- }) {
                                    Icon(Icons.Default.Remove, contentDescription = null, tint = PrimaryOrange)
                                }
                                Text("$packageQuantity", color = TextPrimary, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { packageQuantity++ }) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryOrange)
                                }
                            }
                        }
                        Text("Total units: ${totalPackUnits.toInt()}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (isExistingItem && inventoryFlow == "INBOUND") {
                        OutlinedTextField(
                            value = inventoryCostText,
                            onValueChange = { inventoryCostText = it },
                            label = { Text("New cost price (buying price)", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }

                    if (inventoryFlow == "OUTBOUND" && isExistingItem && matchedRow != null) {
                        OutlinedTextField(
                            value = inventoryCostText,
                            onValueChange = { inventoryCostText = it },
                            label = { Text("Unit cost for credit amount (default: item cost)", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Credit amount MK (optional — auto from units × cost)", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            val costParsed = inventoryCostText.toDoubleOrNull()
            val retailParsed = inventoryRetailText.toDoubleOrNull()
            val thresholdParsed = inventoryThresholdText.toDoubleOrNull()
            val otherAmount = amountText.toDoubleOrNull()

            val outboundCost = when {
                costParsed != null -> costParsed
                matchedRow != null -> matchedRow.unitCost
                else -> null
            }
            val defaultInboundCredit = if (costParsed != null) totalPackUnits * costParsed else null
            val defaultOutboundCredit = if (outboundCost != null) totalPackUnits * outboundCost else null

            val isOtherValid = otherAmount != null && otherAmount > 0 && contactName.trim().length >= 3 && description.trim().isNotBlank()

            val ambiguousName = inventoryName.trim().isNotBlank() &&
                branchInventory.count { it.name.equals(inventoryName.trim(), ignoreCase = true) } > 1 &&
                linkedInventoryRowId == null

            val stockOkOutbound = inventoryFlow != "OUTBOUND" || matchedRow == null || totalPackUnits <= matchedRow.currentStock

            val isInventoryValid = branchId != null &&
                contactName.trim().length >= 3 &&
                inventoryName.isNotBlank() &&
                !ambiguousName &&
                stockOkOutbound &&
                totalPackUnits > 0 &&
                when {
                    entryType != "INVENTORY" -> true
                    inventoryFlow == "OUTBOUND" -> resolvedExistingId != null && defaultOutboundCredit != null && defaultOutboundCredit > 0
                    isExistingItem -> costParsed != null && costParsed >= 0.0 && defaultInboundCredit != null && defaultInboundCredit > 0
                    else -> retailParsed != null && retailParsed >= 0.0 &&
                        thresholdParsed != null && thresholdParsed >= 0.0 &&
                        (costParsed != null && costParsed >= 0.0) && (defaultInboundCredit ?: 0.0) > 0
                }

            Button(
                onClick = {
                    if (entryType == "OTHER") {
                        val amt = amountText.toDoubleOrNull() ?: return@Button
                        onSave(
                            CreditFormSubmission.Other(
                                CreditDto(
                                    branchId = branchId,
                                    contactName = contactName.trim(),
                                    description = description.trim(),
                                    amount = amt,
                                    creditType = creditType,
                                    notes = notes.trim()
                                )
                            )
                        )
                        return@Button
                    }

                    val bid = branchId ?: return@Button
                    val nameTrim = inventoryName.trim()

                    when {
                        inventoryFlow == "OUTBOUND" -> {
                            val row = matchedRow ?: return@Button
                            val uid = resolvedExistingId ?: return@Button
                            val uc = outboundCost ?: return@Button
                            val amt = otherAmount ?: (totalPackUnits * uc)
                            val dto = InventoryItemDto(
                                name = row.name,
                                category = row.category,
                                subcategory = row.subcategory,
                                stock_quantity = row.currentStock,
                                min_threshold = row.lowStockThreshold,
                                cost_price = uc,
                                sellingPrice = row.retailPrice,
                                unit = row.unit,
                                branchId = bid
                            )
                            val credit = CreditDto(
                                branchId = bid,
                                contactName = contactName.trim(),
                                description = "Stock taken — they owe us: ${row.name} −${totalPackUnits.toInt()} units",
                                amount = amt,
                                creditType = "GIVEN",
                                notes = notes.trim()
                            )
                            onSave(
                                CreditFormSubmission.Inventory(
                                    CreditInventorySubmission(
                                        credit = credit,
                                        inventoryItem = dto,
                                        kind = InventoryCreditKind.EXISTING_OUTBOUND,
                                        existingInventoryId = uid,
                                        quantityDelta = totalPackUnits,
                                        cloneBranches = emptySet()
                                    )
                                )
                            )
                        }
                        isExistingItem -> {
                            val row = matchedRow ?: return@Button
                            val uid = resolvedExistingId ?: return@Button
                            val cp = costParsed ?: return@Button
                            val amt = otherAmount ?: (totalPackUnits * cp)
                            val dto = InventoryItemDto(
                                name = row.name,
                                category = row.category,
                                subcategory = row.subcategory,
                                stock_quantity = 0.0,
                                min_threshold = row.lowStockThreshold,
                                cost_price = cp,
                                sellingPrice = row.retailPrice,
                                unit = row.unit,
                                branchId = bid
                            )
                            val credit = CreditDto(
                                branchId = bid,
                                contactName = contactName.trim(),
                                description = "Stock on credit (we owe): ${row.name} +${totalPackUnits.toInt()} units",
                                amount = amt,
                                creditType = "RECEIVED",
                                notes = notes.trim()
                            )
                            onSave(
                                CreditFormSubmission.Inventory(
                                    CreditInventorySubmission(
                                        credit = credit,
                                        inventoryItem = dto,
                                        kind = InventoryCreditKind.EXISTING_INBOUND,
                                        existingInventoryId = uid,
                                        quantityDelta = totalPackUnits,
                                        cloneBranches = emptySet()
                                    )
                                )
                            )
                        }
                        else -> {
                            val cp = costParsed ?: return@Button
                            val rp = retailParsed ?: return@Button
                            val th = thresholdParsed ?: return@Button
                            val amt = otherAmount ?: (totalPackUnits * cp)
                            val insertDto = InventoryItemDto(
                                name = nameTrim,
                                category = inventoryCategory.trim().ifBlank { "DRINKS" },
                                subcategory = inventorySubcategory.trim().ifBlank { "Other" },
                                stock_quantity = totalPackUnits,
                                min_threshold = th,
                                cost_price = cp,
                                sellingPrice = rp,
                                unit = "Units",
                                branchId = bid
                            )
                            val credit = CreditDto(
                                branchId = bid,
                                contactName = contactName.trim(),
                                description = "Stock on credit (we owe): $nameTrim +${totalPackUnits.toInt()} units (new item)",
                                amount = amt,
                                creditType = "RECEIVED",
                                notes = notes.trim()
                            )
                            onSave(
                                CreditFormSubmission.Inventory(
                                    CreditInventorySubmission(
                                        credit = credit,
                                        inventoryItem = insertDto,
                                        kind = InventoryCreditKind.NEW_INBOUND,
                                        existingInventoryId = null,
                                        quantityDelta = totalPackUnits,
                                        cloneBranches = syncCloneBranches
                                    )
                                )
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                enabled = if (entryType == "OTHER") isOtherValid else isInventoryValid
            ) { Text("Save Record", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = SurfaceColor
    )
}

private fun formatMoneyField(v: Double): String {
    if (v % 1.0 == 0.0) return v.toInt().toString()
    return v.toString()
}



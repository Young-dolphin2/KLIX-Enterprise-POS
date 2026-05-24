package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.*
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin



private data class PortionLinkDraft(
    val menuItemName: String,
    val portionsPerSale: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryTab(
    inventoryItems: List<InventoryItem>,           // filtered list for display
    allInventoryItems: MutableList<InventoryItem>, // full mutable list for add/update
    menuItems: MutableList<DesktopMenuItem>,        // hoisted menu list (so inventory->menu sync reflects immediately)
    saleHistory: List<SaleRecord>,
    settings: AppSettings,
    branches: List<BranchDto> = emptyList(),
    selectedBranch: BranchDto? = null,
    onBranchChange: (BranchDto?) -> Unit = {},
    customCategories: List<String> = listOf("FOOD", "DRINKS", "SIDES", "DESSERTS", "KITCHEN"),
    customSubcategories: Map<String, List<String>> = mapOf(
        "DRINKS" to listOf("Beer", "Wine", "Spirits")
    )
) {
    val scope = rememberCoroutineScope()
    var showAdjustDialog by remember { mutableStateOf<InventoryItem?>(null) }
    var showRentDialog by remember { mutableStateOf<InventoryItem?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    // Search + filter state
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("ALL") }
    var filterSubcategory by remember { mutableStateOf("ALL") }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // TOP HEADER BAR: 4 CHARTS
        // The original header row with search and add button is replaced by InventoryHeader
        // and the 4 charts are now in a separate row.
        
        // Assuming InventoryHeader is a new composable that handles the add item button
        // and potentially search, as implied by the removal of searchQuery.
        // For now, I'll just put a placeholder or re-add the add button if InventoryHeader is not defined elsewhere.
        // Based on the provided snippet, the add button logic is moved to InventoryHeader.
        // Since InventoryHeader is not provided, I'll assume it exists and handles the add button.
        // If it doesn't exist, this will cause a compilation error.
        // For the purpose of this edit, I'll assume InventoryHeader is defined elsewhere and handles the add button.
        
        // The provided snippet starts with InventoryHeader, so I'll include it.
        // If InventoryHeader is not defined, this will be a compilation error.
        // I'll assume it's a new composable that needs to be created or is already present.
        // For now, I'll just include the call as provided.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            InventoryHeader(
                onAddClick = { showAddItemDialog = true },
                settings = settings
            )
            
            var showGuide by remember { mutableStateOf(false) }
            IconButton(onClick = { showGuide = true }) {
                Icon(Icons.Default.HelpOutline, "Guide", tint = PrimaryOrange)
            }
            
            com.example.barandgrillownerpanel.ui.components.GuideOverlay(
                steps = com.example.barandgrillownerpanel.ui.components.Guides.INVENTORY_GUIDE,
                isVisible = showGuide,
                onDismiss = { showGuide = false }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Branch Filter Bar
        BranchFilterBar(
            branches = branches,
            selectedBranch = selectedBranch,
            onBranchChange = onBranchChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Chart Row (4 charts) - This replaces the original "TOP HEADER BAR: 4 CHARTS"
        Row(modifier = Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartCard("Stock Distribution", modifier = Modifier.weight(1.2f)) { RotatingStockPie(inventoryItems) }
            ChartCard("Inventory Health", modifier = Modifier.weight(1f)) { InventoryHealthGauge(inventoryItems, settings) }
            ChartCard("Stock Value (Cost)", modifier = Modifier.weight(1f)) { InventoryValueChart(inventoryItems, isCost = true) }
            ChartCard("Potential Revenue", modifier = Modifier.weight(1f)) { InventoryValueChart(inventoryItems, isCost = false) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().height(700.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Main Inventory List (replaces the left Card)
            Card(
                modifier = Modifier.weight(2.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Inventory", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val kitchenCount = inventoryItems.count { it.category.equals("KITCHEN", ignoreCase = true) || it.category.equals("FOOD", ignoreCase = true) }
                            if (kitchenCount > 0) {
                                Text("$kitchenCount Kitchen Items", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("•", color = TextSecondary, fontSize = 12.sp)
                            }
                            Text("${inventoryItems.size} Total Items", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Search + Filter row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search items...", color = TextSecondary.copy(alpha = 0.9f), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(18.dp)) {
                                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            modifier = Modifier.weight(1.8f).height(52.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, 
                                unfocusedTextColor = TextPrimary, 
                                focusedBorderColor = PrimaryOrange,
                                unfocusedBorderColor = Color.White.copy(0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        // Category filter
                        var catFilterExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = filterCategory,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Category", color = TextSecondary.copy(alpha = 0.9f), fontSize = 12.sp) },
                                trailingIcon = { IconButton(onClick = { catFilterExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary) } },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(0.1f)),
                                shape = RoundedCornerShape(8.dp)
                            )
                            DropdownMenu(expanded = catFilterExpanded, onDismissRequest = { catFilterExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                                (listOf("ALL") + customCategories).forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat, color = TextPrimary) }, onClick = { filterCategory = cat; filterSubcategory = "ALL"; catFilterExpanded = false })
                                }
                            }
                        }
                        // Subcategory filter
                        val subOptions = if (filterCategory == "ALL") listOf("ALL") else listOf("ALL") + (customSubcategories[filterCategory] ?: emptyList())
                        var subFilterExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = filterSubcategory,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Subcategory", color = TextSecondary.copy(alpha = 0.9f), fontSize = 12.sp) },
                                trailingIcon = { IconButton(onClick = { subFilterExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary) } },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(0.1f)),
                                shape = RoundedCornerShape(8.dp)
                            )
                            DropdownMenu(expanded = subFilterExpanded, onDismissRequest = { subFilterExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                                subOptions.forEach { sub ->
                                    DropdownMenuItem(text = { Text(sub, color = TextPrimary) }, onClick = { filterSubcategory = sub; subFilterExpanded = false })
                                }
                            }
                        }
                        // Clear filters button
                        if (filterCategory != "ALL" || filterSubcategory != "ALL" || searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; filterCategory = "ALL"; filterSubcategory = "ALL" }) {
                                Icon(Icons.Default.FilterListOff, "Clear filters", tint = PrimaryOrange)
                            }
                        }
                    }

                    // Table Header
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("Item", Modifier.weight(2f), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Stock / Cap", Modifier.weight(1.2f), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Status", Modifier.weight(1f), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Actions", Modifier.width(80.dp), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    HorizontalDivider(color = Color.White.copy(0.05f))

                    // Apply search + filter
                    val displayItems = remember(inventoryItems, searchQuery, filterCategory, filterSubcategory) {
                        inventoryItems.filter { item ->
                            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery.trim(), ignoreCase = true)
                            val matchesCat = filterCategory == "ALL" || item.category.equals(filterCategory, ignoreCase = true)
                            val matchesSub = filterSubcategory == "ALL" || item.subcategory.equals(filterSubcategory, ignoreCase = true)
                            matchesSearch && matchesCat && matchesSub
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items = displayItems) { item ->
                            InventoryRow(
                                item = item, 
                                settings = settings, 
                                branches = branches, 
                                onAdjust = { showAdjustDialog = item },
                                onRent = { showRentDialog = item }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // RIGHT: THE 3 SPECIALIZED CHARTS
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ChartCard("ORDER CYCLE TIME", Modifier.weight(1f)) {
                    RotatingCycleTimeChart(inventoryItems)
                }
                ChartCard("STOCK TO CAPACITY", Modifier.weight(1f)) {
                    RotatingCapacityDonut(inventoryItems)
                }
                ChartCard("BUSINESS ACTIVITY", Modifier.weight(1f)) {
                    SalesThermometer(saleHistory, settings)
                }
                // NEW: 3 BEST SELLING ITEMS
                ChartCard("TOP 3 BEST SELLERS", Modifier.weight(1.2f)) {
                    BestSellersList(inventoryItems, saleHistory)
                }
            }
        }
    }

    if (showAddItemDialog) {
        var newItemName by remember { mutableStateOf("") }
        var newItemCategory by remember { mutableStateOf(customCategories.firstOrNull() ?: "DRINKS") }
        var newItemSubcategory by remember { mutableStateOf(customSubcategories[newItemCategory]?.firstOrNull() ?: "") }
        var newItemCost by remember { mutableStateOf("") }
        var newItemRetail by remember { mutableStateOf("") }
        var newItemStock by remember { mutableStateOf("0") }
        val packageOptions = listOf("Crate (20)", "Crate (6)", "Unit (1)")
        var packageType by remember { mutableStateOf(packageOptions[0]) }
        var packageQuantity by remember { mutableStateOf(1) }
        var newItemThreshold by remember { mutableStateOf("10") }
        var newItemTrackPortions by remember { mutableStateOf(false) }
        var newItemPortionsPerUnit by remember { mutableStateOf("10") }
        val newItemPortionLinks = remember { mutableStateListOf<PortionLinkDraft>().apply { repeat(4) { add(PortionLinkDraft("", "")) } } }
        var addPortionMenuExpanded by remember { mutableStateOf(false) }
        var newItemSoldByShot by remember { mutableStateOf(false) }
        var newItemBottleVolumeMl by remember { mutableStateOf("1000") }
        var newItemShotSizeMl by remember { mutableStateOf("35.7") }
        var syncToMenu by remember { mutableStateOf(true) }
        var syncBranches by remember { mutableStateOf(setOf<BranchDto>()) }
        var isSaving by remember { mutableStateOf(false) }
        var dialogBranch by remember(selectedBranch) { mutableStateOf(selectedBranch ?: branches.firstOrNull()) }
        // New category / subcategory dialogs
        var showNewCategoryDialog by remember { mutableStateOf(false) }
        var newCategoryInput by remember { mutableStateOf("") }
        var showNewSubcategoryDialog by remember { mutableStateOf(false) }
        var newSubcategoryInput by remember { mutableStateOf("") }

        LaunchedEffect(dialogBranch) {
            val isBar = dialogBranch?.name?.contains("Bar", ignoreCase = true) == true
            if (isBar && customCategories.contains("DRINKS")) {
                newItemCategory = "DRINKS"
                newItemSubcategory = customSubcategories["DRINKS"]?.firstOrNull() ?: ""
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showAddItemDialog = false },
            containerColor = SurfaceColor,
            title = { Text("Add New Inventory Item", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Branch Selector (only show if selectedBranch is null and branches exist)
                    if (selectedBranch == null && branches.isNotEmpty()) {
                        var branchExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = dialogBranch?.name ?: "Select Branch",
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Branch", color = TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { branchExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                            DropdownMenu(
                                expanded = branchExpanded,
                                onDismissRequest = { branchExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.5f).background(SurfaceColor)
                            ) {
                                branches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name, color = TextPrimary) },
                                        onClick = {
                                            dialogBranch = branch
                                            branchExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Smart Presets Row
                    Text("Industry Quick Presets", color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Spirit Shot" to { 
                                newItemCategory = "DRINKS"; newItemSubcategory = "Spirits"; newItemSoldByShot = true; 
                                newItemBottleVolumeMl = "750"; newItemShotSizeMl = "35"; newItemTrackPortions = false 
                            },
                            "Wine Glass" to { 
                                newItemCategory = "DRINKS"; newItemSubcategory = "Wine"; newItemSoldByShot = true; 
                                newItemBottleVolumeMl = "750"; newItemShotSizeMl = "175"; newItemTrackPortions = false 
                            },
                            "Draft Keg" to { 
                                newItemCategory = "DRINKS"; newItemSubcategory = "Beer"; newItemSoldByShot = true; 
                                newItemBottleVolumeMl = "50000"; newItemShotSizeMl = "568"; newItemTrackPortions = false 
                            }
                        ).forEach { (label, apply) ->
                            OutlinedButton(
                                onClick = apply,
                                modifier = Modifier.weight(1f).height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, PrimaryOrange.copy(0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                    }

                    val allNames = allInventoryItems.map { it.name }.distinct().sorted()
                    var nameExpanded by remember { mutableStateOf(false) }
                    val filteredNames = allNames.filter { it.contains(newItemName, ignoreCase = true) && it != newItemName }

                    ExposedDropdownMenuBox(
                        expanded = nameExpanded,
                        onExpandedChange = { nameExpanded = !nameExpanded }
                    ) {
                        OutlinedTextField(
                            value = newItemName,
                            onValueChange = { 
                                newItemName = it 
                                nameExpanded = it.isNotBlank() && allInventoryItems.any { item -> 
                                    item.name.contains(it.trim(), ignoreCase = true) && item.name != it 
                                }
                            },
                            label = { Text("Item Name", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange),
                            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable)
                        )
                        if (filteredNames.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = nameExpanded,
                                onDismissRequest = { nameExpanded = false },
                                modifier = Modifier.background(SurfaceColor)
                            ) {
                                filteredNames.forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = { Text(suggestion, color = TextPrimary) },
                                        onClick = {
                                            newItemName = suggestion
                                            nameExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // Category Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        var categoryExpanded by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = newItemCategory,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Category", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { categoryExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                        )
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.5f).background(SurfaceColor)
                        ) {
                            customCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(cat, color = TextPrimary, modifier = Modifier.weight(1f))
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                                                .postgrest["categories"]
                                                                .delete { filter { com.example.barandgrillownerpanel.models.CategoryDto::name eq cat } }
                                                                
                                                            @Suppress("UNCHECKED_CAST")
                                                            (customCategories as? MutableList<String>)?.remove(cat)
                                                            @Suppress("UNCHECKED_CAST")
                                                            (customSubcategories as? MutableMap<String, MutableList<String>>)?.remove(cat)
                                                            
                                                            if (newItemCategory == cat) {
                                                                newItemCategory = customCategories.firstOrNull() ?: ""
                                                                newItemSubcategory = customSubcategories[newItemCategory]?.firstOrNull() ?: ""
                                                            }
                                                        } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY", "Inventory load failed", e) }
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        newItemCategory = cat
                                        newItemSubcategory = customSubcategories[cat]?.firstOrNull() ?: ""
                                        categoryExpanded = false
                                        if (!cat.equals("FOOD", ignoreCase = true)) {
                                            newItemTrackPortions = false
                                        }
                                    }
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(0.1f))
                            DropdownMenuItem(
                                text = { Text("+ New Category", color = PrimaryOrange, fontWeight = FontWeight.Bold) },
                                onClick = { categoryExpanded = false; showNewCategoryDialog = true }
                            )
                        }
                    }

                    if (!newItemTrackPortions) {
                        // Subcategory Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            var subcategoryExpanded by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = newItemSubcategory,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Subcategory", color = TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { subcategoryExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                            DropdownMenu(
                                expanded = subcategoryExpanded,
                                onDismissRequest = { subcategoryExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.5f).background(SurfaceColor)
                            ) {
                                (customSubcategories[newItemCategory] ?: emptyList<String>()).forEach { sub ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(sub, color = TextPrimary, modifier = Modifier.weight(1f))
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                                                    .postgrest["categories"]
                                                                    .delete { 
                                                                        filter { 
                                                                            com.example.barandgrillownerpanel.models.CategoryDto::name eq sub
                                                                            com.example.barandgrillownerpanel.models.CategoryDto::parentName eq newItemCategory 
                                                                        } 
                                                                    }
                                                                    
                                                                @Suppress("UNCHECKED_CAST")
                                                                val subsList = (customSubcategories as? MutableMap<String, MutableList<String>>)?.get(newItemCategory)
                                                                subsList?.remove(sub)
                                                                
                                                                if (newItemSubcategory == sub) {
                                                                    newItemSubcategory = subsList?.firstOrNull() ?: ""
                                                                }
                                                            } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY", "Inventory action failed", e) }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Subcategory", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            newItemSubcategory = sub
                                            subcategoryExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(0.1f))
                                DropdownMenuItem(
                                    text = { Text("+ New Subcategory", color = PrimaryOrange, fontWeight = FontWeight.Bold) },
                                    onClick = { subcategoryExpanded = false; showNewSubcategoryDialog = true }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newItemCost,
                            onValueChange = { newItemCost = it },
                            label = { Text("Cost Price", color = TextSecondary) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                        )
                        if (!newItemTrackPortions) {
                            OutlinedTextField(
                                value = newItemRetail,
                                onValueChange = { newItemRetail = it },
                                label = { Text("Retail Price", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                        }
                    }
                    // Package Type & Quantity Selector
                    Text("Inventory Intake", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        var pkgExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1.5f)) {
                            OutlinedTextField(
                                value = packageType,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Pack Type", color = TextSecondary) },
                                trailingIcon = {
                                    IconButton(onClick = { pkgExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange)
                            )
                            DropdownMenu(expanded = pkgExpanded, onDismissRequest = { pkgExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                                packageOptions.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt, color = TextPrimary) }, onClick = { packageType = opt; pkgExpanded = false })
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(4.dp)),
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

                    val multiplier = when(packageType) {
                        "Crate (20)" -> 20
                        "Crate (6)" -> 6
                        "Spirits Bottle (25 shots)" -> 25
                        "Wine Bottle (5 glasses)" -> 5
                        else -> 1
                    }
                    val totalStock = packageQuantity * multiplier
                    
                    Text("Total Calculated Stock: $totalStock Units", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    OutlinedTextField(
                        value = newItemThreshold,
                        onValueChange = { newItemThreshold = it },
                        label = { Text("Low Alert Threshold (Units)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                    )
                    if (newItemCategory.equals("FOOD", ignoreCase = true)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = newItemTrackPortions,
                                onCheckedChange = {
                                    newItemTrackPortions = it
                                    if (it) newItemSoldByShot = false
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                            )
                            Text("Track by portions (kitchen/raw materials)", color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                    if (newItemTrackPortions) {
                        OutlinedTextField(
                            value = newItemPortionsPerUnit,
                            onValueChange = { newItemPortionsPerUnit = it },
                            label = { Text("Portions per stock unit", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                        )
                        val menuOptions = menuItems
                            .filter { it.branchId == dialogBranch?.id || it.branchId == null }
                            .filter { it.category.equals("FOOD", ignoreCase = true) }
                            .map { it.name.trim() }
                            .distinct()
                            .sorted()
                        Text("Portion mapping (Select menu items and set portions consumed per plate)", color = TextSecondary, fontSize = 12.sp)
                        
                        newItemPortionLinks.forEachIndexed { idx, link ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var slotExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = link.menuItemName,
                                        onValueChange = {},
                                        readOnly = true,
                                        placeholder = { Text("Select menu item", fontSize = 11.sp, color = TextSecondary) },
                                        trailingIcon = {
                                            IconButton(onClick = { slotExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedBorderColor = PrimaryOrange,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    )
                                    DropdownMenu(expanded = slotExpanded, onDismissRequest = { slotExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                                        DropdownMenuItem(
                                            text = { Text("(None)", color = TextSecondary) },
                                            onClick = {
                                                newItemPortionLinks[idx] = link.copy(menuItemName = "")
                                                slotExpanded = false
                                            }
                                        )
                                        menuOptions.forEach { name ->
                                            DropdownMenuItem(
                                                text = { Text(name, color = TextPrimary) },
                                                onClick = {
                                                    newItemPortionLinks[idx] = link.copy(menuItemName = name)
                                                    slotExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = link.portionsPerSale,
                                    onValueChange = { v -> 
                                        if (v.isEmpty() || v.toDoubleOrNull() != null) {
                                            newItemPortionLinks[idx] = link.copy(portionsPerSale = v)
                                        }
                                    },
                                    placeholder = { Text("Portions", fontSize = 10.sp, color = TextSecondary) },
                                    modifier = Modifier.width(90.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryOrange,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                if (idx >= 4) { // Allow removing extra slots if any, but keep 4 default
                                    IconButton(onClick = { newItemPortionLinks.removeAt(idx) }) {
                                        Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        
                        TextButton(
                            onClick = { newItemPortionLinks.add(PortionLinkDraft("", "")) },
                            colors = ButtonDefaults.textButtonColors(contentColor = PrimaryOrange)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Text("Add more slots", fontSize = 12.sp)
                        }
                    }
                    if (!newItemCategory.equals("FOOD", ignoreCase = true)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = newItemSoldByShot,
                                onCheckedChange = {
                                    newItemSoldByShot = it
                                    if (it) newItemTrackPortions = false
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                            )
                            Text("Sold by shot/glass (deduct partial bottle)", color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                    if (newItemSoldByShot) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PrimaryOrange.copy(0.1f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Inventory, null, tint = PrimaryOrange, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Pro Tip: Spirits are usually sold in 35ml or 50ml shots. Wine is usually 125ml, 175ml or 250ml.",
                                    fontSize = 11.sp, color = PrimaryOrange
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newItemBottleVolumeMl,
                                onValueChange = { newItemBottleVolumeMl = it },
                                label = { Text("Bottle volume (ml)", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                            OutlinedTextField(
                                value = newItemShotSizeMl,
                                onValueChange = { newItemShotSizeMl = it },
                                label = { Text("Shot size (ml)", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = !newItemCategory.equals("KITCHEN", ignoreCase = true) && !newItemTrackPortions,
                            onCheckedChange = {},
                            enabled = false,
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                        )
                        Text("Menu auto-sync (disabled for kitchen/portion-tracked items)", color = TextPrimary, fontSize = 14.sp)
                    }
                    val otherBranches = branches.filter { it.id != dialogBranch?.id && it.id != null }
                    if (otherBranches.isNotEmpty()) {
                        Text("Also create copy (0 stock) in:", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        otherBranches.forEach { ob ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = syncBranches.contains(ob),
                                    onCheckedChange = { chk -> 
                                        if (chk) syncBranches = syncBranches + ob 
                                        else syncBranches = syncBranches - ob
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                                )
                                Text(ob.name, color = TextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val multiplier = when(packageType) {
                            "Crate (20)" -> 20
                            "Crate (6)" -> 6
                            else -> 1
                        }
                        val calculatedStock = (packageQuantity * multiplier).toDouble()
                        val sanitizedPortionLinks = newItemPortionLinks.mapNotNull { row ->
                            if (row.menuItemName.isBlank()) return@mapNotNull null
                            val p = row.portionsPerSale.toDoubleOrNull() ?: 1.0 // Default to 1 if empty/invalid but name is selected
                            row.copy(portionsPerSale = p.toString())
                        }

                        val insertDto = com.example.barandgrillownerpanel.models.InventoryItemDto(
                            name = newItemName.trim(),
                            category = newItemCategory.trim(),
                            subcategory = if (newItemTrackPortions) "" else newItemSubcategory.trim(),
                            stock_quantity = calculatedStock,
                            min_threshold = newItemThreshold.toDoubleOrNull() ?: 10.0,
                            cost_price = newItemCost.toDoubleOrNull() ?: 0.0,
                            sellingPrice = newItemRetail.toDoubleOrNull() ?: (newItemCost.toDoubleOrNull() ?: 0.0) * 1.5,
                            unit = "Units",
                            isPortionTracked = newItemTrackPortions,
                            portionsPerUnit = newItemPortionsPerUnit.toDoubleOrNull(),
                            linkedMenuItemName = sanitizedPortionLinks.firstOrNull()?.menuItemName,
                            soldByShot = newItemSoldByShot,
                            bottleVolumeMl = newItemBottleVolumeMl.toDoubleOrNull(),
                            shotSizeMl = newItemShotSizeMl.toDoubleOrNull(),
                            branchId = dialogBranch?.id
                        )
                        isSaving = true
                        scope.launch {
                            try {
                                val insertedItems = insertInventoryWithCloneBranchesAndMenu(
                                    insertDto = insertDto,
                                    cloneBranches = syncBranches,
                                    menuItems = menuItems,
                                    allInventoryItems = allInventoryItems,
                                    syncMenu = syncToMenu && !newItemCategory.equals("KITCHEN", ignoreCase = true) && !newItemTrackPortions,
                                    allBranches = branches
                                )
                                if (newItemTrackPortions && sanitizedPortionLinks.isNotEmpty()) {
                                    insertedItems.forEach { inserted ->
                                        val generatedId = inserted.id ?: return@forEach
                                        upsertPortionMappings(
                                            inventoryId = generatedId,
                                            branchId = inserted.branchId,
                                            links = sanitizedPortionLinks
                                        )
                                    }
                                }
                                showAddItemDialog = false
                            } catch (e: Exception) {
                                com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY", "Inventory update failed", e)
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    enabled = !isSaving && newItemName.isNotBlank() && (branches.isEmpty() || dialogBranch != null)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save Item", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }, enabled = !isSaving) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )

        // New Category Dialog
        if (showNewCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showNewCategoryDialog = false; newCategoryInput = "" },
                containerColor = SurfaceColor,
                title = { Text("Add Category", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
                text = {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it.uppercase() },
                        label = { Text("Category Name", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cat = newCategoryInput.trim()
                            if (cat.isNotBlank() && !customCategories.contains(cat)) {
                                @Suppress("UNCHECKED_CAST")
                                (customCategories as? MutableList<String>)?.add(cat)
                                if (!customSubcategories.containsKey(cat)) {
                                    @Suppress("UNCHECKED_CAST")
                                    (customSubcategories as? MutableMap<String, MutableList<String>>)?.put(cat, mutableListOf())
                                }
                                scope.launch {
                                    try {
                                        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["categories"]
                                            .insert(com.example.barandgrillownerpanel.models.CategoryInsertDto(name = cat))
                                    } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY", "Inventory delete failed", e) }
                                }
                                newItemCategory = cat
                                newItemSubcategory = ""
                            }
                            showNewCategoryDialog = false
                            newCategoryInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        enabled = newCategoryInput.isNotBlank()
                    ) { Text("Add", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewCategoryDialog = false; newCategoryInput = "" }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // New Subcategory Dialog
        if (showNewSubcategoryDialog) {
            AlertDialog(
                onDismissRequest = { showNewSubcategoryDialog = false; newSubcategoryInput = "" },
                containerColor = SurfaceColor,
                title = { Text("Add Subcategory to $newItemCategory", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
                text = {
                    OutlinedTextField(
                        value = newSubcategoryInput,
                        onValueChange = { newSubcategoryInput = it.uppercase() },
                        label = { Text("Subcategory Name", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val sub = newSubcategoryInput.trim()
                            if (sub.isNotBlank()) {
                                @Suppress("UNCHECKED_CAST")
                                val mutableSubs = customSubcategories as? MutableMap<String, MutableList<String>>
                                val existing = mutableSubs?.getOrPut(newItemCategory) { mutableListOf() }
                                if (existing != null && !existing.contains(sub)) {
                                    existing.add(sub)
                                    scope.launch {
                                        try {
                                            com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                                .postgrest["categories"]
                                                .insert(com.example.barandgrillownerpanel.models.CategoryInsertDto(name = sub, parentName = newItemCategory))
                                        } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY", "Import failed", e) }
                                    }
                                }
                                newItemSubcategory = sub
                            }
                            showNewSubcategoryDialog = false
                            newSubcategoryInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        enabled = newSubcategoryInput.isNotBlank()
                    ) { Text("Add", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewSubcategoryDialog = false; newSubcategoryInput = "" }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }

    if (showAdjustDialog != null) {
        AdjustStockDialog(
            item = showAdjustDialog!!,
            menuItems = menuItems,
            onDismiss = { showAdjustDialog = null },
            onSave = { amount, type, newName ->
                val dialogItemId = showAdjustDialog?.id
                val index = allInventoryItems.indexOfFirst { it.id == dialogItemId }
                if (index != -1) {
                    val currentItem = allInventoryItems[index]
                    val updatedItem = when (type) {
                        "RECEIVE" -> currentItem.copy(currentStock = currentItem.currentStock + amount, name = newName)
                        "WASTE" -> currentItem.copy(currentStock = (currentItem.currentStock - amount).coerceAtLeast(0.0), name = newName)
                        "CAPACITY" -> currentItem.copy(capacity = amount, name = newName)
                        else -> currentItem.copy(name = newName)
                    }
                    
                    // Optimistic UI update
                    allInventoryItems[index] = updatedItem
                    
                    // Sync to Supabase
                    if (type != "CAPACITY") {
                        scope.launch {
                            try {
                                com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.postgrest["inventory"]
                                    .update(
                                        {
                                            set("stock_quantity", updatedItem.currentStock)
                                            set("name", newName)
                                        }
                                    ) {
                                        filter {
                                            eq("id", updatedItem.id)
                                        }
                                    }
                            } catch (e: Exception) {
                                com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY", "Export failed", e)
                                // Revert UI if sync fails
                                allInventoryItems[index] = currentItem
                            }
                        }
                    } else {
                         // Note: We are currently mocking capacity in DashboardScreen based on min_threshold.
                         // To persist capacity changes properly, we would update min_threshold in Supabase here.
                         scope.launch {
                             try {
                                com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.postgrest["inventory"]
                                    .update(
                                        {
                                            set("min_threshold", (updatedItem.capacity / 5))
                                            set("name", newName)
                                        }
                                    ) {
                                        filter {
                                            eq("id", updatedItem.id)
                                        }
                                    }
                            } catch(e: Exception) {
                                e.printStackTrace()
                                allInventoryItems[index] = currentItem
                            }
                         }
                    }
                }
                showAdjustDialog = null
            },
            onSavePrices = priceAdj@{ cost, retail, newName, isPortionTracked, portionsPerUnit, linkedMenuItemName, soldByShot, bottleVolumeMl, shotSizeMl, portionLinks ->
                val dialogItemId = showAdjustDialog?.id ?: return@priceAdj
                val index = allInventoryItems.indexOfFirst { it.id == dialogItemId }
                if (index == -1) return@priceAdj
                val currentItem = allInventoryItems[index]
                val updatedItem = currentItem.copy(
                    unitCost = cost,
                    retailPrice = retail,
                    name = newName,
                    isPortionTracked = isPortionTracked,
                    portionsPerUnit = portionsPerUnit,
                    linkedMenuItemName = linkedMenuItemName,
                    soldByShot = soldByShot,
                    bottleVolumeMl = bottleVolumeMl,
                    shotSizeMl = shotSizeMl
                )
                allInventoryItems[index] = updatedItem
                scope.launch {
                    try {
                        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.postgrest["inventory"]
                            .update(
                                {
                                    set("cost_price", cost)
                                    set("selling_price", retail)
                                    set("name", newName)
                                    set("is_portion_tracked", isPortionTracked)
                                    set("portions_per_unit", portionsPerUnit)
                                    set("linked_menu_item_name", linkedMenuItemName)
                                    set("sold_by_shot", soldByShot)
                                    set("bottle_volume_ml", bottleVolumeMl)
                                    set("shot_size_ml", shotSizeMl)
                                }
                            ) {
                                filter { eq("id", currentItem.id) }
                            }
                        if (isPortionTracked) {
                            upsertPortionMappings(
                                inventoryId = currentItem.id,
                                branchId = currentItem.branchId,
                                links = portionLinks
                            )
                        } else {
                            clearPortionMappings(currentItem.id)
                        }
                        upsertMenuRetailForInventoryItem(
                            name = newName,
                            category = updatedItem.category,
                            subcategory = updatedItem.subcategory,
                            branchId = updatedItem.branchId,
                            retailPrice = retail,
                            menuItems = menuItems
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        allInventoryItems[index] = currentItem
                    }
                }
                showAdjustDialog = null
            },
            onDelete = {
                val toDelete = showAdjustDialog
                if (toDelete != null) {
                    scope.launch {
                        try {
                            // Delete from Inventory
                            com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.postgrest["inventory"]
                                .delete { filter { eq("id", toDelete.id) } }
                            
                            // Also delete from Menu Items (since they share ID or name-based alignment)
                            com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.postgrest["menu_items"]
                                .delete { filter { eq("id", toDelete.id) } }
                            
                            // Update local state
                            allInventoryItems.removeAll { it.id == toDelete.id }
                            menuItems.removeAll { it.id == toDelete.id }
                            
                            showAdjustDialog = null
                        } catch (e: Exception) {
                            com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY", "Inventory search failed", e)
                        }
                    }
                }
            }
        )
    }

    if (showRentDialog != null) {
        val asset = showRentDialog!!
        RentAssetDialog(
            item = asset,
            onDismiss = { showRentDialog = null },
            onConfirm = { customer, notes, newStatus ->
                scope.launch {
                    try {
                        val client = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                        
                        // 1. Update Inventory Status & Stock
                        val newStock = if (newStatus == "RENTED") 0.0 else 1.0
                        client.postgrest["inventory"].update({
                            set("status", newStatus)
                            set("stock_quantity", newStock)
                        }) {
                            filter { eq("id", asset.id) }
                        }

                        // 2. Log History
                        val history = AssetHistoryDto(
                            inventoryId = asset.id ?: "",
                            customerId = customer?.id,
                            actionType = if (newStatus == "RENTED") "CHECK_OUT" else "CHECK_IN",
                            notes = notes,
                            branchId = asset.branchId
                        )
                        client.postgrest["asset_history"].insert(history)

                        // 3. Update Local State
                        val idx = allInventoryItems.indexOfFirst { it.id == asset.id }
                        if (idx >= 0) {
                            allInventoryItems[idx] = allInventoryItems[idx].copy(
                                status = newStatus,
                                currentStock = newStock
                            )
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                showRentDialog = null
            }
        )
    }
}

// --- SHARED COMPONENTS ---

@Composable
fun ChartCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text(title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

// --- 1. TOTAL STOCK LEVEL (IMPROVED PIE/DONUT CHART) ---
// --- 1. TOTAL STOCK LEVEL (PIE/DONUT CHART) - SMOOTH TRANSITIONS ---
@Composable
fun RotatingStockPie(items: List<InventoryItem>) {
    val totalStock by remember(items) { derivedStateOf { items.sumOf { it.currentStock }.coerceAtLeast(0.0) } }
    val composition by remember(items) {
        derivedStateOf {
            items
                .groupBy { item ->
                    if (item.category.equals("DRINKS", ignoreCase = true) && item.subcategory.isNotBlank()) {
                        item.subcategory.uppercase()
                    } else {
                        item.category.uppercase()
                    }
                }
                .mapValues { (_, rows) -> rows.sumOf { it.currentStock }.coerceAtLeast(0.0) }
                .filterValues { it > 0.0 }
                .toList()
                .sortedByDescending { it.second }
        }
    }

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("NO ITEMS", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    val palette = listOf(
        PrimaryOrange, Color(0xFF4CAF50), Color(0xFF42A5F5), Color(0xFFFFC107),
        Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF26A69A), Color(0xFFFF7043)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(112.dp)) {
                var startAngle = -90f
                composition.forEachIndexed { index, (_, value) ->
                    val sweepAngle = if (totalStock > 0) ((value / totalStock) * 360.0).toFloat() else 0f
                    drawArc(
                        color = palette[index % palette.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${totalStock.toInt()}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Text("UNITS", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 150.dp)) {
            composition.take(6).forEachIndexed { index, pair ->
                val label = pair.first
                val pct = if (totalStock > 0) (pair.second / totalStock) * 100.0 else 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(palette[index % palette.size]))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "$label ${String.format("%.0f", pct)}%",
                        fontSize = 9.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// --- 2. INVENTORY HEALTH (GUAGE CHART) ---
@Composable
fun InventoryHealthGauge(items: List<InventoryItem>, settings: AppSettings) {
    val totalCapacity = items.sumOf { it.capacity }
    val currentStock = items.sumOf { it.currentStock }
    val healthPercentage = if (totalCapacity > 0) (currentStock / totalCapacity).coerceIn(0.0, 1.0).toFloat() else 0f
    
    val lowThreshold = (settings.lowStockThresholdPercent / 100f)
    
    val healthLabel = when {
        healthPercentage < lowThreshold / 2 -> "BAD"
        healthPercentage < lowThreshold -> "AVERAGE"
        healthPercentage < 0.6f -> "FAIR"
        healthPercentage < 0.8f -> "GOOD"
        else -> "HEALTHY!!"
    }
    
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val strokeWidth = 12.dp.toPx()
            // Background track
            drawArc(
                color = Color.White.copy(0.05f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            // Progress
            drawArc(
                brush = Brush.horizontalGradient(listOf(ErrorRed, SuccessGreen)),
                startAngle = 180f,
                sweepAngle = 180f * healthPercentage,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 10.dp)) {
            Text(healthLabel, fontWeight = FontWeight.Black, color = PrimaryOrange, fontSize = 16.sp)
            Text("${(healthPercentage * 100).toInt()}%", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// --- 3 & 4. INVENTORY VALUE (PIE CHARTS) ---
@Composable
fun InventoryValueChart(items: List<InventoryItem>, isCost: Boolean) {
    val total = items.sumOf { 
        if (isCost) {
            it.currentStock * it.unitCost 
        } else {
            // For Potential Revenue:
            // Sync items (Drinks) use Retail Price.
            // Raw Materials (Food/Kitchen) use Cost Price to avoid overestimation.
            val isRawMaterial = it.category.equals("FOOD", ignoreCase = true) || 
                                it.category.equals("KITCHEN", ignoreCase = true) || 
                                it.isPortionTracked
            if (isRawMaterial) it.currentStock * it.unitCost else it.currentStock * it.retailPrice
        }
    }

    // Breakdown for potential revenue
    val drinksPotential = if (!isCost) {
        items.filter { it.category.equals("DRINKS", ignoreCase = true) }.sumOf { it.currentStock * it.retailPrice }
    } else 0.0

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val plusSuffix = if (!isCost) "+" else ""
        Text("MK ${String.format("%,.0f", total)}$plusSuffix", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (!isCost && total > 0 && drinksPotential > 0) {
            // Draw pie chart of drink subcategories
            val drinksItems = items.filter { it.category.equals("DRINKS", ignoreCase = true) && it.currentStock > 0 }
            val subcategoryGroups = drinksItems.groupBy { it.subcategory.uppercase() }
                .mapValues { (_, subItems) -> subItems.sumOf { it.currentStock * it.retailPrice } }
                .toList()
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            if (subcategoryGroups.isNotEmpty()) {
                val colors = listOf(PrimaryOrange, SuccessGreen, Color(0xFF4FC3F7), Color(0xFFFFCC00), Color(0xFFE040FB))
                
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        var currentAngle = -90f
                        subcategoryGroups.forEachIndexed { index, (_, value) ->
                            val sweepAngle = ((value / drinksPotential) * 360f).toFloat()
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = currentAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(8.dp.toPx(), cap = StrokeCap.Butt)
                            )
                            currentAngle += sweepAngle
                        }
                    }
                }
                
                // Show a mini legend for the top 3 subcategories
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    subcategoryGroups.take(3).forEachIndexed { index, (subcat, value) ->
                        val pct = ((value / drinksPotential) * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors[index % colors.size]))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$subcat $pct%", fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }
            } else {
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawArc(PrimaryOrange, 0f, 360f, false, style = Stroke(8.dp.toPx()))
                }
            }
        } else {
            // Static or cost chart
            Canvas(modifier = Modifier.size(80.dp)) {
                drawArc(PrimaryOrange, 0f, 360f, false, style = Stroke(8.dp.toPx()))
                drawArc(SuccessGreen, 45f, 130f, false, style = Stroke(8.dp.toPx()))
            }
        }
    }
}

// --- SIDEBAR 1: ORDER CYCLE TIME (SEMI CIRCLE) ---
@Composable
fun RotatingCycleTimeChart(items: List<InventoryItem>) {
    var currentIndex by remember { mutableStateOf(0) }
    
    if (items.isEmpty()) return

    LaunchedEffect(items) {
        while(true) {
            delay(10000)
            val size = items.size
            if (size > 0) {
                currentIndex = (currentIndex + 1) % size
            }
        }
    }
    
    val currentItem = items.getOrNull(currentIndex) ?: return
    
    AnimatedContent(
        targetState = currentItem,
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
        label = "CycleTimeRotation"
    ) { item ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(90.dp)) {
                    drawArc(PrimaryOrange.copy(0.1f), 180f, 180f, true)
                    val sweep = if (item.capacity > 0) (item.currentStock / item.capacity).toFloat() else 0f
                    drawArc(PrimaryOrange, 180f, 180f * sweep, true)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.name.uppercase(), fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp)
            Text("2 Receives / Week", color = PrimaryOrange, fontSize = 10.sp)
        }
    }
}

// --- SIDEBAR 2: STOCK TO CAPACITY (DONUT) ---
@Composable
fun RotatingCapacityDonut(items: List<InventoryItem>) {
    var currentIndex by remember { mutableStateOf(0) }
    
    if (items.isEmpty()) return

    LaunchedEffect(items) {
        while(true) {
            delay(10000)
            val size = items.size
            if (size > 0) {
                currentIndex = (currentIndex + 1) % size
            }
        }
    }
    
    val currentItem = items.getOrNull(currentIndex) ?: return
    val percentage = if (currentItem.capacity > 0) (currentItem.currentStock / currentItem.capacity).coerceIn(0.0, 1.0).toFloat() else 0f

    AnimatedContent(
        targetState = currentItem,
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
        label = "CapacityRotation"
    ) { item ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawCircle(Color.White.copy(0.05f), style = Stroke(10.dp.toPx()))
                    drawArc(
                        color = if (percentage < 0.3f) ErrorRed else SuccessGreen,
                        startAngle = -90f,
                        sweepAngle = 360f * percentage,
                        useCenter = false,
                        style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text("${(percentage * 100).toInt()}%", fontWeight = FontWeight.Black, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.name, fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// --- SIDEBAR 3: BUSINESS ACTIVITY (IMPROVED THERMOMETER) ---
@Composable
fun SalesThermometer(saleHistory: List<SaleRecord>, settings: AppSettings) {
    val dailyGoal = settings.dailySalesGoal
    val today = LocalDate.now().toString()
    val todaySales = saleHistory.filter { 
        java.time.Instant.ofEpochMilli(it.timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString() == today
    }
    val currentSales = todaySales.sumOf { it.totalAmount }
    val fillFactor = (currentSales / dailyGoal).coerceIn(0.01, 1.0).toFloat()
    
    val mercuryColor = when {
        fillFactor > 0.9f -> Color(0xFFFF1744) // Intense Red
        fillFactor > 0.6f -> Color(0xFFFF9100) // Deep Orange
        else -> Color(0xFF00E676) // Vibrant Green
    }
// ... [Mercury drawing code remains same]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 12.dp)) {
            Box(modifier = Modifier.height(140.dp).width(40.dp), contentAlignment = Alignment.BottomCenter) {
                // Glass Outer Tube with Glow
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tubeWidth = 20.dp.toPx()
                    val bulbRadius = 18.dp.toPx()
                    val centerX = size.width / 2
                    
                    // Main Tube Path
                    val tubePath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = centerX - tubeWidth / 2,
                                top = 0f,
                                right = centerX + tubeWidth / 2,
                                bottom = size.height - bulbRadius,
                                cornerRadius = CornerRadius(tubeWidth / 2)
                            )
                        )
                    }
                    
                    // Draw outer glass shadow/border
                    drawPath(tubePath, Color.White.copy(0.1f))
                    drawPath(tubePath, Color.White.copy(0.2f), style = Stroke(1.dp.toPx()))
                    
                    // Scale Marks
                    for (i in 0..6) {
                        val y = (size.height - bulbRadius * 2) * (i / 6f)
                        drawLine(
                            color = Color.White.copy(0.3f),
                            start = Offset(centerX + tubeWidth / 2, y),
                            end = Offset(centerX + tubeWidth / 2 + 5.dp.toPx(), y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
                
                // Mercury Column
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .fillMaxHeight(fillFactor)
                        .padding(bottom = 20.dp) // Start above the bulb
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                        .background(Brush.verticalGradient(listOf(mercuryColor, mercuryColor.copy(0.7f))))
                ) {
                    // Glare on the column
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().align(Alignment.CenterStart).background(Color.White.copy(0.2f)))
                }

                // Bulb at the bottom
                Canvas(modifier = Modifier.size(40.dp).align(Alignment.BottomCenter)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(mercuryColor.copy(0.8f), mercuryColor),
                            center = Offset(size.width * 0.3f, size.height * 0.3f)
                        ),
                        radius = size.minDimension / 2
                    )
                    // High-quality glare
                    drawCircle(
                        color = Color.White.copy(0.4f),
                        radius = size.minDimension / 6,
                        center = Offset(size.width * 0.35f, size.height * 0.35f)
                    )
                }
            }
        }
        
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text("${settings.currencySymbol} ${String.format("%,.0f", currentSales)}", fontWeight = FontWeight.Black, color = mercuryColor, fontSize = 20.sp)
            Text("Target: ${settings.currencySymbol} ${String.format("%,.0f", dailyGoal)}", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mercuryColor.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    if (fillFactor > 0.8f) "🔥 CRITICAL ACTIVITY" else "MODERATE FLOW",
                    fontWeight = FontWeight.Black,
                    color = mercuryColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun InventoryHeader(onAddClick: () -> Unit, settings: AppSettings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Inventory Management", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text("Track and adjust your stock levels in real-time", color = TextSecondary, fontSize = 14.sp)
        }
        
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add New Item", fontWeight = FontWeight.Bold)
        }
    }
}

// --- NEW SECTION: TOP 3 BEST SELLERS (REALLY POPULAR ITEMS) ---
@Composable
fun BestSellersList(inventoryItems: List<InventoryItem>, saleHistory: List<SaleRecord>) {
    val bestSellers: List<Pair<InventoryItem, Double>> = remember(saleHistory, inventoryItems) {
        val allItems: List<SaleItem> = saleHistory.flatMap { it.items }
        val grouped: Map<String, List<SaleItem>> = allItems.groupBy { it.item.name }
        val counted: Map<String, Double> = grouped.mapValues { entry ->
            entry.value.sumOf { s: SaleItem -> s.quantity.toDouble() }
        }
        val popular: List<Pair<String, Double>> = counted.toList()
            .sortedByDescending { it.second }
            .take(3)
            
        popular.mapNotNull { pair: Pair<String, Double> ->
            val name: String = pair.first
            val count: Double = pair.second
            val invItem: InventoryItem? = inventoryItems.find { it.name == name }
            if (invItem != null) Pair(invItem, count) else null
        }
    }
    
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        bestSellers.forEachIndexed { index, data ->
            val item = data.first
            val count = data.second
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(0.04f))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Rank Circle
                Box(
                    modifier = Modifier.size(20.dp).clip(CircleShape).background(
                        when(index) {
                            0 -> Color(0xFFFFD700)
                            1 -> Color(0xFFC0C0C0)
                            else -> Color(0xFFCD7F32)
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                Text(item.category, color = TextSecondary, fontSize = 9.sp)
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text("${count.toInt()} sold", color = PrimaryOrange, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
    }
}

// --- TABLE ROW ---

@Composable
fun InventoryRow(item: InventoryItem, settings: AppSettings, branches: List<BranchDto>, onAdjust: () -> Unit, onRent: () -> Unit) {
    val lowStockValue = item.capacity * (settings.lowStockThresholdPercent / 100.0)
    val stockStatus = when {
        item.currentStock <= 0 -> "Out of Stock"
        item.currentStock <= lowStockValue -> "Low Stock"
        else -> "In Stock"
    }
    val displayStatus = if (item.status != "AVAILABLE") item.status.replace("_", " ") else stockStatus
    
    val statusColor = when (displayStatus) {
        "Out of Stock", "RENTED" -> ErrorRed
        "Low Stock", "MAINTENANCE" -> PrimaryOrange
        else -> SuccessGreen
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(2f)) {
            Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${item.category} • ${item.subcategory}", color = TextSecondary, fontSize = 11.sp)
        }
        
        Column(Modifier.weight(1.2f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.currentStock.toInt()}", color = TextPrimary, fontWeight = FontWeight.Black)
                Text(" / ${item.capacity.toInt()}", color = TextSecondary, fontSize = 12.sp)
            }
            LinearProgressIndicator(
                progress = { (if (item.capacity > 0) item.currentStock / item.capacity else 0.0).toFloat() },
                modifier = Modifier.width(60.dp).height(4.dp).clip(CircleShape),
                color = statusColor,
                trackColor = Color.White.copy(0.05f)
            )
        }

        Box(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(statusColor.copy(0.1f)).padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(displayStatus.uppercase(), color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }

        Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.Center) {
                // Rent Out Action (Only for Car Rental type)
                if (branches.find { it.id == item.branchId }?.type == "CAR_RENTAL") {
                    IconButton(onClick = onRent, modifier = Modifier.size(32.dp)) {
                        Icon(if (item.status == "RENTED") Icons.Default.KeyOff else Icons.Default.Key, null, tint = if (item.status == "RENTED") SuccessGreen else PrimaryOrange)
                    }
                }
                IconButton(onClick = onAdjust, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.EditAttributes, null, tint = PrimaryOrange)
                }
            }
        }
    }
}

private fun formatAdjustPriceField(v: Double): String =
    if (kotlin.math.abs(v % 1.0) < 1e-9) v.toInt().toString() else v.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustStockDialog(
    item: InventoryItem,
    menuItems: List<DesktopMenuItem>,
    onDismiss: () -> Unit,
    onSave: (Double, String, String) -> Unit,
    onSavePrices: (Double, Double, String, Boolean, Double?, String?, Boolean, Double?, Double?, List<PortionLinkDraft>) -> Unit,
    onDelete: () -> Unit
) {
    var adjustType by remember { mutableStateOf("RECEIVE") }
    val packSizes = listOf("Crate 20" to 20.0, "Crate 10" to 10.0, "Crate 6" to 6.0, "Unit (1)" to 1.0)
    var selectedPackSize by remember { mutableStateOf(packSizes.last()) }
    var amount by remember { mutableStateOf(0.0) }
    var amountText by remember { mutableStateOf("0") }
    var editableName by remember(item.id) { mutableStateOf(item.name) }
    var costText by remember(item.id) { mutableStateOf(formatAdjustPriceField(item.unitCost)) }
    var retailText by remember(item.id) { mutableStateOf(formatAdjustPriceField(item.retailPrice)) }
    var trackPortions by remember(item.id) { mutableStateOf(item.isPortionTracked) }
    var portionsPerUnitText by remember(item.id) { mutableStateOf(item.portionsPerUnit?.let(::formatAdjustPriceField) ?: "10") }
    var linkedMenuNameText by remember(item.id) { mutableStateOf(item.linkedMenuItemName ?: "") }
    val portionLinks = remember { mutableStateListOf<PortionLinkDraft>() }
    var portionMenuExpanded by remember { mutableStateOf(false) }
    var soldByShot by remember(item.id) { mutableStateOf(item.soldByShot) }
    var bottleVolumeMlText by remember(item.id) { mutableStateOf(item.bottleVolumeMl?.let(::formatAdjustPriceField) ?: "1000") }
    var shotSizeMlText by remember(item.id) { mutableStateOf(item.shotSizeMl?.let(::formatAdjustPriceField) ?: "50") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val totalToChange = amount * selectedPackSize.second
    LaunchedEffect(item.id) {
        portionLinks.clear()
        try {
            val existing = fetchPortionMappings(item.id)
            if (existing.isNotEmpty()) {
                portionLinks.addAll(existing.map { PortionLinkDraft(it.menu_item_name, formatAdjustPriceField(it.portions_per_sale)) })
            } else if (item.linkedMenuItemName != null) {
                portionLinks.add(PortionLinkDraft(item.linkedMenuItemName, item.portionsPerUnit?.let(::formatAdjustPriceField) ?: "1"))
            }
        } catch (_: Exception) {
            if (item.linkedMenuItemName != null && portionLinks.isEmpty()) {
                portionLinks.add(PortionLinkDraft(item.linkedMenuItemName, item.portionsPerUnit?.let(::formatAdjustPriceField) ?: "1"))
            }
        }
        // Ensure at least 4 slots for easier editing
        if (portionLinks.size < 4) {
            repeat(4 - portionLinks.size) { portionLinks.add(PortionLinkDraft("", "")) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Column {
                Text("Inventory Adjustment", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                Text(item.name, color = PrimaryOrange, fontSize = 14.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editableName,
                    onValueChange = { editableName = it },
                    label = { Text("Item Name", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            adjustType = "RECEIVE"
                            amount = 0.0
                            amountText = "0"
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (adjustType == "RECEIVE") SuccessGreen else CharcoalGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("RECEIVE", color = if (adjustType == "RECEIVE") Color.Black else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            adjustType = "WASTE"
                            amount = 0.0
                            amountText = "0"
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (adjustType == "WASTE") ErrorRed else CharcoalGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("WASTE", color = if (adjustType == "WASTE") Color.White else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            adjustType = "CAPACITY"
                            amount = item.capacity
                            amountText = item.capacity.toInt().toString()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (adjustType == "CAPACITY") PrimaryOrange else CharcoalGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CAPACITY", color = if (adjustType == "CAPACITY") Color.Black else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            adjustType = "PRICES"
                            costText = formatAdjustPriceField(item.unitCost)
                            retailText = formatAdjustPriceField(item.retailPrice)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (adjustType == "PRICES") PrimaryOrange.copy(alpha = 0.85f) else CharcoalGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("PRICES", color = if (adjustType == "PRICES") Color.Black else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (adjustType == "PRICES") {
                    Text("Cost & retail (MK). Retail syncs to the menu for this branch.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it },
                            label = { Text(if (costText.isBlank()) "Cost Price (Required)" else "Cost price", color = if (costText.isBlank()) ErrorRed else TextSecondary) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                        )
                        if (!trackPortions) {
                            OutlinedTextField(
                                value = retailText,
                                onValueChange = { retailText = it },
                                label = { Text("Retail price", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = trackPortions,
                            onCheckedChange = {
                                trackPortions = it
                                if (it) soldByShot = false
                            },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                        )
                        Text("Track by portions (kitchen)", color = TextPrimary, fontSize = 12.sp)
                    }
                    if (trackPortions) {
                        OutlinedTextField(
                            value = portionsPerUnitText,
                            onValueChange = { portionsPerUnitText = it },
                            label = { Text("Portions per stock unit", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                        )
                        val selectableMenuMap = menuItems
                            .filter { it.category.equals("FOOD", ignoreCase = true) || it.category.equals("KITCHEN", ignoreCase = true) }
                            .map { it.name.trim() }
                            .distinct()
                            .sorted()

                        Text("Portion mapping (Select menu items and set portions consumed per plate)", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        
                        portionLinks.forEachIndexed { idx, link ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var slotExp by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = link.menuItemName,
                                        onValueChange = {},
                                        readOnly = true,
                                        placeholder = { Text("Select menu item", fontSize = 11.sp, color = TextSecondary) },
                                        trailingIcon = {
                                            IconButton(onClick = { slotExp = true }) {
                                                Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedBorderColor = PrimaryOrange,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    )
                                    DropdownMenu(expanded = slotExp, onDismissRequest = { slotExp = false }, modifier = Modifier.background(SurfaceColor)) {
                                        DropdownMenuItem(
                                            text = { Text("(None)", color = TextSecondary) },
                                            onClick = {
                                                portionLinks[idx] = link.copy(menuItemName = "")
                                                slotExp = false
                                            }
                                        )
                                        selectableMenuMap.forEach { name ->
                                            DropdownMenuItem(
                                                text = { Text(name, color = TextPrimary) },
                                                onClick = {
                                                    portionLinks[idx] = link.copy(menuItemName = name)
                                                    slotExp = false
                                                }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = link.portionsPerSale,
                                    onValueChange = { v -> 
                                        if (v.isEmpty() || v.toDoubleOrNull() != null) {
                                            portionLinks[idx] = link.copy(portionsPerSale = v)
                                        }
                                    },
                                    placeholder = { Text("Qty", fontSize = 10.sp, color = TextSecondary) },
                                    modifier = Modifier.width(70.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryOrange,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                if (idx >= 4) {
                                    IconButton(onClick = { portionLinks.removeAt(idx) }) {
                                        Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        
                        TextButton(
                            onClick = { portionLinks.add(PortionLinkDraft("", "")) },
                            colors = ButtonDefaults.textButtonColors(contentColor = PrimaryOrange)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Text("Add more slots", fontSize = 12.sp)
                    }
                }
                    if (!item.category.equals("FOOD", ignoreCase = true)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = soldByShot,
                                onCheckedChange = {
                                    soldByShot = it
                                    if (it) trackPortions = false
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                            )
                            Text("Sold by shot/glass", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                    if (soldByShot) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bottleVolumeMlText,
                                onValueChange = { bottleVolumeMlText = it },
                                label = { Text("Bottle ml", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                            OutlinedTextField(
                                value = shotSizeMlText,
                                onValueChange = { shotSizeMlText = it },
                                label = { Text("Shot ml", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange)
                            )
                        }
                    }
                } else {
                    if (adjustType == "RECEIVE") {
                        Text("Pack Size", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = DarkBackground)) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(selectedPackSize.first, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ArrowDropDown, null, tint = PrimaryOrange)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(SurfaceColor).width(250.dp)) {
                                packSizes.forEach { p -> DropdownMenuItem(text = { Text(p.first, color = TextPrimary) }, onClick = { selectedPackSize = p; expanded = false }) }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Text(
                        text = when (adjustType) {
                            "RECEIVE" -> "Number of Packs"
                            "WASTE" -> "Waste Quantity (${item.unit})"
                            else -> "Set Max Capacity (${item.unit})"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { amount = (amount - 1).coerceAtLeast(1.0); amountText = amount.toInt().toString() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(CharcoalGray)) { Icon(Icons.Default.Remove, null, tint = TextPrimary) }
                        OutlinedTextField(
                            value = amountText, 
                            onValueChange = { amountText = it; amount = it.toDoubleOrNull() ?: 1.0 }, 
                            modifier = Modifier.width(120.dp).padding(horizontal = 8.dp), 
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, fontSize = 18.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryOrange,
                                unfocusedBorderColor = Color.White.copy(0.1f)
                            )
                        )
                        IconButton(onClick = { amount = (amount + 1); amountText = amount.toInt().toString() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(PrimaryOrange)) { Icon(Icons.Default.Add, null, tint = DarkBackground) }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PrimaryOrange.copy(0.1f)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (adjustType) {
                                    "RECEIVE" -> "Total to add:"
                                    "WASTE" -> "Total to remove:"
                                    else -> "Total Capacity will be:"
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                if (adjustType == "CAPACITY") "${amount.toInt()} ${item.unit}" else "$totalToChange ${item.unit}",
                                color = PrimaryOrange,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (adjustType == "RECEIVE") {
                                Text("Current Stock: ${item.currentStock.toInt()}", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    if (adjustType == "WASTE") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DELETE PRODUCT", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val costOk = costText.toDoubleOrNull()?.let { it >= 0 } == true
            val retailOk = if (trackPortions) true else (retailText.toDoubleOrNull()?.let { it >= 0 } == true)
            val pricesValid = adjustType == "PRICES" && editableName.isNotBlank() && costOk && retailOk
            Button(
                onClick = {
                    if (adjustType == "PRICES") {
                        val c = costText.toDoubleOrNull() ?: return@Button
                        val r = if (trackPortions) 0.0 else (retailText.toDoubleOrNull() ?: return@Button)
                        val sanitizedLinks = portionLinks.mapNotNull { row ->
                            if (row.menuItemName.isBlank()) return@mapNotNull null
                            val p = row.portionsPerSale.toDoubleOrNull() ?: 1.0 // Default to 1 if empty/invalid but name is selected
                            row.copy(portionsPerSale = p.toString())
                        }
                        onSavePrices(
                            c,
                            r,
                            editableName.trim(),
                            trackPortions,
                            portionsPerUnitText.toDoubleOrNull(),
                            linkedMenuNameText.trim().ifBlank { null },
                            soldByShot,
                            bottleVolumeMlText.toDoubleOrNull(),
                            shotSizeMlText.toDoubleOrNull(),
                            sanitizedLinks
                        )
                    } else if (adjustType == "CAPACITY") {
                        onSave(amount, adjustType, editableName)
                    } else {
                        onSave(totalToChange, adjustType, editableName)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                enabled = if (adjustType == "PRICES") pricesValid else true
            ) {
                Text(
                    when (adjustType) {
                        "PRICES" -> "Save prices & sync menu"
                        "CAPACITY" -> "Update Capacity"
                        else -> "Update Inventory"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceColor,
            title = { Text("Delete inventory item?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will permanently delete \"${item.name}\" from inventory (and linked menu row by id).", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

private suspend fun fetchPortionMappings(inventoryId: String): List<IngredientMenuPortionRow> {
    return com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
        .postgrest["ingredient_menu_portions"]
        .select { filter { eq("inventory_id", inventoryId) } }
        .decodeAs<List<IngredientMenuPortionRow>>()
}

private suspend fun clearPortionMappings(inventoryId: String) {
    com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
        .postgrest["ingredient_menu_portions"]
        .delete { filter { eq("inventory_id", inventoryId) } }
}

private suspend fun upsertPortionMappings(
    inventoryId: String,
    branchId: String?,
    links: List<PortionLinkDraft>
) {
    clearPortionMappings(inventoryId)
    if (links.isEmpty()) return
    val rows = links.mapNotNull { link ->
        val portions = link.portionsPerSale.toDoubleOrNull()
        if (link.menuItemName.isBlank() || portions == null || portions <= 0.0) null
        else IngredientMenuPortionRow(
            inventory_id = inventoryId,
            menu_item_name = link.menuItemName.trim(),
            portions_per_sale = portions,
            branch_id = branchId
        )
    }
    if (rows.isNotEmpty()) {
        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
            .postgrest["ingredient_menu_portions"]
            .insert(rows)
    }
}

private suspend fun upsertPortionMappingsByInventoryName(
    inventoryName: String,
    branchId: String?,
    links: List<PortionLinkDraft>
) {
    val rows = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
        .postgrest["inventory"]
        .select {
            filter { eq("name", inventoryName) }
        }
        .decodeAs<List<com.example.barandgrillownerpanel.models.InventoryItemDto>>()
    val inv = rows.firstOrNull { it.branchId == branchId } ?: return
    val id = inv.id ?: return
    upsertPortionMappings(id, branchId, links)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RentAssetDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (customer: CustomerDto?, notes: String, status: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf<List<CustomerDto>>(emptyList()) }
    var selectedCustomer by remember { mutableStateOf<CustomerDto?>(null) }
    var notes by remember { mutableStateOf("") }
    var returnDate by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isCheckIn = item.status == "RENTED"

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isLoading = true
            try {
                customers = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                    .postgrest["customers"]
                    .select {
                        filter {
                            or {
                                ilike("name", "%$searchQuery%")
                                ilike("phone", "%$searchQuery%")
                            }
                        }
                    }.decodeAs<List<CustomerDto>>()
            } catch (e: Exception) { e.printStackTrace() }
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Text(if (isCheckIn) "Check-In Asset" else "Check-Out Asset", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(modifier = Modifier.width(400.dp)) {
                Text(item.name, color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))

                if (!isCheckIn) {
                    Text("Select Customer", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or phone...", color = TextSecondary.copy(0.5f), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange),
                        singleLine = true
                    )
                    
                    if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = PrimaryOrange)

                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp).padding(top = 4.dp)) {
                        items(customers) { cust ->
                            val isSelected = selectedCustomer?.id == cust.id
                            Surface(
                                onClick = { selectedCustomer = cust },
                                color = if (isSelected) PrimaryOrange.copy(0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null, tint = if (isSelected) PrimaryOrange else TextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(cust.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(cust.phone, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Expected Return Date", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = returnDate,
                        onValueChange = { returnDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange)
                    )
                } else {
                    Text("This asset is currently RENTED. Performing Check-In will make it AVAILABLE for new clients.", color = TextSecondary, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Notes (Mileage, Fuel, Condition)", color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = PrimaryOrange),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onConfirm(selectedCustomer, notes, if (isCheckIn) "AVAILABLE" else "RENTED") 
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isCheckIn) SuccessGreen else PrimaryOrange),
                enabled = isCheckIn || selectedCustomer != null
            ) {
                Text(if (isCheckIn) "CONFIRM CHECK-IN" else "CONFIRM CHECK-OUT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

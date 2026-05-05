package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.ui.theme.*



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuControlTab(
    menuItems: MutableList<DesktopMenuItem>,
    inventoryItems: List<com.example.barandgrillownerpanel.ui.dashboard.InventoryItem>,
    branches: List<BranchDto> = emptyList(),
    selectedBranch: BranchDto? = null,
    onBranchChange: (BranchDto?) -> Unit = {},
    onSaveItem: (com.example.barandgrillownerpanel.models.MenuItemDto) -> Unit = {},
    /** Second arg is the item name before this edit (for inventory sync). */
    onUpdateItem: (DesktopMenuItem, String) -> Unit = { _, _ -> },
    /** Quick price change only; syncs matching inventory [selling_price]. */
    onAdjustMenuPrice: (DesktopMenuItem, newPrice: Double) -> Unit = { _, _ -> },
    onDeleteItem: (DesktopMenuItem) -> Unit = {},
    onRefresh: () -> Unit = {},
    customCategories: List<String> = listOf("FOOD", "DRINKS", "SIDES", "DESSERTS", "SHISHA"),
    customSubcategories: Map<String, List<String>> = mapOf(
        "DRINKS" to listOf("Beer", "Wine", "Spirits")
    )
) {
    fun aggregateMenuItems(items: List<DesktopMenuItem>): List<DesktopMenuItem> {
        return items
            .groupBy { "${it.name.trim().lowercase()}|${it.category.trim().uppercase()}|${it.subcategory.trim().lowercase()}" }
            .mapNotNull { (key, grouped) ->
                val base = grouped.firstOrNull() ?: return@mapNotNull null
                DesktopMenuItem(
                    id = "agg:$key",
                    name = base.name,
                    price = base.price,
                    category = base.category,
                    subcategory = base.subcategory,
                    branchId = null,
                    ingredients = base.ingredients
                )
            }
            .sortedBy { it.name }
    }

    val flavor = com.example.barandgrillownerpanel.AppFlavor.current
    val categories = customCategories
    val subcategoriesMap = customSubcategories
    val scope = rememberCoroutineScope()

    var itemName by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var selectedCategory by remember(categories) { mutableStateOf(categories.firstOrNull() ?: "") }
    var selectedSubcategory by remember(categories, selectedCategory) { mutableStateOf(subcategoriesMap[selectedCategory]?.firstOrNull() ?: "") }

    var isFilterMode by remember { mutableStateOf(false) }
    var filterCategory by remember { mutableStateOf("ALL") }
    var filterSubcategory by remember { mutableStateOf("ALL") }

    // Edit price dialog state
    var editingItem by remember { mutableStateOf<DesktopMenuItem?>(null) }
    var newPriceValue by remember { mutableStateOf("") }
    var newNameValue by remember { mutableStateOf("") }
    var menuPriceAdjustItem by remember { mutableStateOf<DesktopMenuItem?>(null) }
    var menuPriceAdjustText by remember { mutableStateOf("") }
    // Custom category/subcategory dialogs
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var showNewSubcategoryDialog by remember { mutableStateOf(false) }
    var newSubcategoryInput by remember { mutableStateOf("") }
    var pendingCategoryDelete by remember { mutableStateOf<String?>(null) }
    var pendingSubcategoryDelete by remember { mutableStateOf<String?>(null) }

    // Cocktail Ingredients - up to 6
    val currentIngredients = remember { mutableStateListOf<com.example.barandgrillownerpanel.models.IngredientDto>() }
    var ingredientSearch by remember { mutableStateOf("") } // Added search state
    val isCocktail = selectedCategory.equals("COCKTAILS", ignoreCase = true) || 
                     selectedCategory.equals("MOCKTAILS", ignoreCase = true) ||
                     selectedSubcategory.equals("COCKTAILS", ignoreCase = true) ||
                     selectedSubcategory.equals("MOCKTAILS", ignoreCase = true)

    val drinkInventoryNames = remember(inventoryItems) {
        inventoryItems
            .filter { it.category.equals("DRINKS", ignoreCase = true) }
            .map { it.name }
            .distinct()
            .sorted()
    }

    LaunchedEffect(selectedCategory) {
        selectedSubcategory = subcategoriesMap[selectedCategory]?.firstOrNull() ?: ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Menu & Pricing Control", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Menu", tint = PrimaryOrange)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Branch Filter Bar - only show parents in the UI
        BranchFilterBar(
            branches = branches.filter { it.parentId == null },
            selectedBranch = selectedBranch,
            onBranchChange = onBranchChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Side: Entry Form
            Card(
                modifier = Modifier.width(350.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("Add New Item", fontWeight = FontWeight.Bold, color = PrimaryOrange, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Main Category", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    CategorySelector(
                        options = categories.toList(),
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it },
                        onAddNew = { showNewCategoryDialog = true },
                        onDelete = { catToDelete -> pendingCategoryDelete = catToDelete }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Sub Category", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    SubCategorySelector(
                        options = (subcategoriesMap[selectedCategory] ?: emptyList<String>()).toList(),
                        selected = selectedSubcategory,
                        onSelect = { selectedSubcategory = it },
                        onAddNew = { showNewSubcategoryDialog = true },
                        onDelete = { subToDelete -> pendingSubcategoryDelete = subToDelete }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = PrimaryOrange,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { itemPrice = it },
                        label = { Text("Price (MK)") },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("MK ") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = PrimaryOrange,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    if (isCocktail) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Ingredients (Deducts 1 shot from each on sale)", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                            // List of current ingredients with better styling
                            currentIngredients.forEachIndexed { idx, ing ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocalDrink, null, tint = PrimaryOrange, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(ing.inventory_name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { currentIngredients.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            
                            if (currentIngredients.size < 5) {
                                var ingExpanded by remember { mutableStateOf(false) }
                                val filteredIngredients = drinkInventoryNames
                                    .filter { name -> currentIngredients.none { it.inventory_name == name } }
                                    .filter { it.contains(ingredientSearch, ignoreCase = true) }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Add Spirit/Drink:", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedTextField(
                                                value = ingredientSearch,
                                                onValueChange = { 
                                                    ingredientSearch = it
                                                    if (it.isNotEmpty()) ingExpanded = true
                                                },
                                                placeholder = { Text("Start typing drink name...", color = TextPrimary.copy(alpha = 0.5f), fontSize = 12.sp) },
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = {
                                                    IconButton(onClick = { ingExpanded = !ingExpanded }) {
                                                        Icon(if (ingExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = PrimaryOrange)
                                                    }
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = PrimaryOrange,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                                    focusedTextColor = TextPrimary,
                                                    unfocusedTextColor = TextPrimary
                                                ),
                                                singleLine = true
                                            )
                                            
                                            DropdownMenu(
                                                expanded = ingExpanded && filteredIngredients.isNotEmpty(),
                                                onDismissRequest = { ingExpanded = false },
                                                modifier = Modifier.background(SurfaceColor).width(300.dp)
                                            ) {
                                                filteredIngredients.forEach { name ->
                                                    DropdownMenuItem(
                                                        text = { Text(name, color = TextPrimary) },
                                                        onClick = {
                                                            currentIngredients.add(com.example.barandgrillownerpanel.models.IngredientDto(name, 1.0))
                                                            ingredientSearch = ""
                                                            ingExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        
                                        IconButton(
                                            onClick = { ingExpanded = true },
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryOrange.copy(0.1f))
                                        ) {
                                            Icon(Icons.Default.Search, null, tint = PrimaryOrange)
                                        }
                                    }
                                    if (filteredIngredients.isEmpty() && ingredientSearch.isNotEmpty()) {
                                        Text("No matching drinks found", color = ErrorRed, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                            
                            if (currentIngredients.isNotEmpty()) {
                                Text("${currentIngredients.size}/5 Ingredients selected", color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (itemName.isNotBlank() && itemPrice.isNotBlank()) {
                                val priceValue = itemPrice.toDoubleOrNull() ?: 0.0
                                val newItem = DesktopMenuItem(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = itemName.trim(),
                                    price = priceValue,
                                    category = selectedCategory,
                                    subcategory = selectedSubcategory,
                                    branchId = selectedBranch?.id
                                )
                                menuItems.add(newItem)
                                // Persist to Supabase
                                onSaveItem(
                                    com.example.barandgrillownerpanel.models.MenuItemDto(
                                        id = newItem.id,
                                        name = newItem.name,
                                        price = newItem.price,
                                        category = newItem.category,
                                        subcategory = newItem.subcategory,
                                        branchId = newItem.branchId,
                                        isActive = true,
                                        ingredients = if (isCocktail) currentIngredients.toList() else null
                                    )
                                )
                                itemName = ""
                                itemPrice = ""
                                currentIngredients.clear()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Item", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Right Side: List Preview & Filtering
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = CharcoalGray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isFilterMode) "ITEM SEARCH" else "MENU PREVIEW", 
                            fontWeight = FontWeight.Bold, 
                            color = TextPrimary, 
                            fontSize = 18.sp
                        )
                        
                        TextButton(
                            onClick = { isFilterMode = !isFilterMode },
                            colors = ButtonDefaults.textButtonColors(contentColor = PrimaryOrange)
                        ) {
                            Icon(if (isFilterMode) Icons.Default.GridView else Icons.Default.FilterList, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isFilterMode) "CATEGORIZED VIEW" else "ITEMS (FILTER)", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (isFilterMode) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Category Filter Dropdown
                            FilterDropdown(
                                label = "Category",
                                options = listOf("ALL") + categories,
                                selected = filterCategory,
                                onSelect = { 
                                    filterCategory = it
                                    filterSubcategory = "ALL" // Reset subcat on cat change
                                },
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Subcategory Filter Dropdown
                            val subOptions = if (filterCategory == "ALL") listOf("ALL") 
                                            else listOf("ALL") + (subcategoriesMap[filterCategory] ?: emptyList())
                            FilterDropdown(
                                label = "Sub-category",
                                options = subOptions,
                                selected = filterSubcategory,
                                onSelect = { filterSubcategory = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredItems by remember(menuItems, isFilterMode, filterCategory, filterSubcategory, selectedBranch, branches) {
                        derivedStateOf {
                            val list = menuItems.filter { item ->
                                val branchMatch = if (selectedBranch == null) {
                                    true
                                } else {
                                    val childIds = branches.filter { it.parentId == selectedBranch.id }.mapNotNull { it.id }
                                    val relevantIds = setOfNotNull(selectedBranch.id) + childIds
                                    item.branchId in relevantIds
                                }
                                if (!branchMatch) return@filter false

                                if (!isFilterMode) true
                                else {
                                    val catMatch = filterCategory == "ALL" || item.category == filterCategory
                                    val subMatch = filterSubcategory == "ALL" || item.subcategory == filterSubcategory
                                    catMatch && subMatch
                                }
                            }
                            if (selectedBranch == null || branches.any { it.parentId == selectedBranch.id }) {
                                aggregateMenuItems(list)
                            } else {
                                list
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isFilterMode) {
                            // Categorized Grouped View
                            val grouped = filteredItems.groupBy { it.category }
                            grouped.forEach { (cat, itemsInCat) ->
                                item {
                                    Text(
                                        cat,
                                        color = PrimaryOrange,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }
                                items(itemsInCat) { item ->
                                    MenuItemRow(
                                        item = item,
                                        onEdit = {
                                            editingItem = it
                                            newPriceValue = it.price.toString()
                                            newNameValue = it.name
                                        },
                                        onAdjustMenuPrice = {
                                            menuPriceAdjustItem = it
                                            menuPriceAdjustText = it.price.toString()
                                        },
                                        onDelete = {
                                            menuItems.remove(item)
                                            onDeleteItem(item)
                                        }
                                    )
                                }
                            }
                        } else {
                            // Flat Filtered List
                            items(filteredItems) { item ->
                                MenuItemRow(
                                    item = item,
                                    onEdit = {
                                        editingItem = it
                                        newPriceValue = it.price.toString()
                                        newNameValue = it.name
                                    },
                                    onAdjustMenuPrice = {
                                        menuPriceAdjustItem = it
                                        menuPriceAdjustText = it.price.toString()
                                    },
                                    onDelete = {
                                        menuItems.remove(item)
                                        onDeleteItem(item)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Price Dialog
    if (editingItem != null) {
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Update Item - ${editingItem?.name}", color = TextPrimary) },
            text = {
                Column {
                    Text("Enter new details for ${editingItem?.name}", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newNameValue,
                        onValueChange = { newNameValue = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryOrange,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = newPriceValue,
                        onValueChange = { newPriceValue = it },
                        label = { Text("New Price (MK)") },
                        prefix = { Text("MK ") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryOrange,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = newPriceValue.toDoubleOrNull() ?: 0.0
                        val nameStr = newNameValue.trim().takeIf { it.isNotEmpty() } ?: editingItem?.name ?: ""
                        val index = menuItems.indexOfFirst { it.id == editingItem?.id }
                        if (index != -1) {
                            val previousName = menuItems[index].name
                            val updated = menuItems[index].copy(price = price, name = nameStr)
                            menuItems[index] = updated
                            onUpdateItem(updated, previousName)
                        }
                        editingItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Update", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceColor
        )
    }

    if (menuPriceAdjustItem != null) {
        val target = menuPriceAdjustItem!!
        AlertDialog(
            onDismissRequest = { menuPriceAdjustItem = null },
            title = { Text("Adjust price · ${target.name}", color = TextPrimary) },
            text = {
                Column {
                    Text("Updates menu and matching inventory retail (same name & branch).", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = menuPriceAdjustText,
                        onValueChange = { menuPriceAdjustText = it },
                        label = { Text("Price (MK)") },
                        prefix = { Text("MK ") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryOrange,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = menuPriceAdjustText.toDoubleOrNull()
                        if (p != null && p >= 0) {
                            val idx = menuItems.indexOfFirst { it.id == target.id }
                            if (idx >= 0) menuItems[idx] = menuItems[idx].copy(price = p)
                            onAdjustMenuPrice(target, p)
                        }
                        menuPriceAdjustItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { menuPriceAdjustItem = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceColor
        )
    }

    if (pendingCategoryDelete != null) {
        val catToDelete = pendingCategoryDelete!!
        AlertDialog(
            onDismissRequest = { pendingCategoryDelete = null },
            containerColor = SurfaceColor,
            title = { Text("Delete category?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This removes category \"$catToDelete\" and its subcategories from the menu configuration.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                    .postgrest["categories"]
                                    .delete { filter { com.example.barandgrillownerpanel.models.CategoryDto::name eq catToDelete } }
                                @Suppress("UNCHECKED_CAST")
                                (customCategories as? MutableList<String>)?.remove(catToDelete)
                                @Suppress("UNCHECKED_CAST")
                                (customSubcategories as? MutableMap<String, MutableList<String>>)?.remove(catToDelete)
                                if (selectedCategory == catToDelete) {
                                    selectedCategory = categories.firstOrNull() ?: ""
                                    selectedSubcategory = subcategoriesMap[selectedCategory]?.firstOrNull() ?: ""
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        pendingCategoryDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryDelete = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (pendingSubcategoryDelete != null) {
        val subToDelete = pendingSubcategoryDelete!!
        AlertDialog(
            onDismissRequest = { pendingSubcategoryDelete = null },
            containerColor = SurfaceColor,
            title = { Text("Delete subcategory?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = { Text("Delete \"$subToDelete\" from \"$selectedCategory\"?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                    .postgrest["categories"]
                                    .delete {
                                        filter {
                                            com.example.barandgrillownerpanel.models.CategoryDto::name eq subToDelete
                                            com.example.barandgrillownerpanel.models.CategoryDto::parentName eq selectedCategory
                                        }
                                    }
                                @Suppress("UNCHECKED_CAST")
                                val subsList = (customSubcategories as? MutableMap<String, MutableList<String>>)?.get(selectedCategory)
                                subsList?.remove(subToDelete)
                                if (selectedSubcategory == subToDelete) {
                                    selectedSubcategory = subsList?.firstOrNull() ?: ""
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        pendingSubcategoryDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubcategoryDelete = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

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
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
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
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                            selectedCategory = cat
                            selectedSubcategory = ""
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
            title = { Text("Add Subcategory to $selectedCategory", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = newSubcategoryInput,
                    onValueChange = { newSubcategoryInput = it.uppercase() },
                    label = { Text("Subcategory Name", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, focusedLabelColor = PrimaryOrange, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sub = newSubcategoryInput.trim()
                        if (sub.isNotBlank()) {
                            @Suppress("UNCHECKED_CAST")
                            val mutableSubs = customSubcategories as? MutableMap<String, MutableList<String>>
                            val existing = mutableSubs?.getOrPut(selectedCategory) { mutableListOf() }
                            if (existing != null && !existing.contains(sub)) {
                                existing.add(sub)
                                scope.launch {
                                    try {
                                        com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                                            .postgrest["categories"]
                                            .insert(com.example.barandgrillownerpanel.models.CategoryInsertDto(name = sub, parentName = selectedCategory))
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                            selectedSubcategory = sub
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

@Composable
fun MenuItemRow(
    item: DesktopMenuItem,
    onEdit: (DesktopMenuItem) -> Unit,
    onAdjustMenuPrice: (DesktopMenuItem) -> Unit = {},
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBackground.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.category, color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(" • ", color = TextSecondary)
                Text(item.subcategory, color = TextSecondary, fontSize = 11.sp)
                
                val hasIngredients = (item.ingredients?.isNotEmpty() == true)
                if (hasIngredients) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("LINKED", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        
        Text("MK ${item.price}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
        
        Spacer(modifier = Modifier.width(16.dp))
        
        if (!item.id.startsWith("agg:")) {
            IconButton(onClick = { onAdjustMenuPrice(item) }) {
                Icon(Icons.Default.AttachMoney, "Adjust price", tint = PrimaryOrange, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onEdit(item) }) {
                Icon(Icons.Default.Edit, "Edit", tint = SuccessGreen, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = ErrorRed, modifier = Modifier.size(20.dp))
            }
        } else {
             // For aggregated items, show a subtle info icon or just space
             Spacer(modifier = Modifier.width(40.dp)) 
        }
    }
}

@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = DarkBackground),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(label, color = TextSecondary, fontSize = 10.sp)
                    Text(selected, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ArrowDropDown, null, tint = PrimaryOrange)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceColor).width(200.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = TextPrimary) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onAddNew: (() -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) PrimaryOrange else SurfaceColor)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(option, color = if (isSelected) DarkBackground else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (onDelete != null) {
                        Box(modifier = Modifier.clickable { onDelete(option) }.padding(2.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(12.dp), tint = if (isSelected) DarkBackground else ErrorRed)
                        }
                    }
                }
            }
        }
        if (onAddNew != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent)
                    .border(1.dp, PrimaryOrange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { onAddNew() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("+ Add", color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubCategorySelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onAddNew: (() -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, if (isSelected) PrimaryOrange else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .background(if (isSelected) PrimaryOrange.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(option, color = if (isSelected) PrimaryOrange else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (onDelete != null) {
                        Box(modifier = Modifier.clickable { onDelete(option) }.padding(horizontal = 2.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(12.dp), tint = if (isSelected) PrimaryOrange else ErrorRed)
                        }
                    }
                }
            }
        }
        if (onAddNew != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, PrimaryOrange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .background(Color.Transparent)
                    .clickable { onAddNew() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("+ Add", color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs for adding new category/subcategory from MenuControlTab
// These are called from within MenuControlTab via showNewCategoryDialog state.
// The actual dialog invocations are at the bottom of MenuControlTab.
// (defined here for reuse if needed)
// ---------------------------------------------------------------------------

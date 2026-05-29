package com.example.barandgrillownerpanel.ui.dashboard

import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.models.InventoryItemDto
import com.example.barandgrillownerpanel.models.MenuItemDto
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.postgrest

// ---------------------------------------------------------------------------
// BRANCH HELPERS
// ---------------------------------------------------------------------------

/** Returns Branch 1 (child of Bar-and-Grill that isn't Bar 2). */
private fun findBar1(branches: List<BranchDto>): BranchDto? =
    branches.firstOrNull { it.name.equals("Bar 1", ignoreCase = true) }

/** Returns Branch 2 sibling of Bar 1. */
private fun findBar2(branches: List<BranchDto>): BranchDto? =
    branches.firstOrNull { it.name.equals("Bar 2", ignoreCase = true) }

/** Returns the parent "Bar and Grill" branch (no parentId). */
private fun findParentBar(branches: List<BranchDto>): BranchDto? =
    branches.firstOrNull { it.parentId == null && it.name.contains("Bar", ignoreCase = true) }

// ---------------------------------------------------------------------------
// MAIN INSERT FUNCTION
// ---------------------------------------------------------------------------

/**
 * Inserts one inventory row (primary branch), optional zero-stock clones for other branches,
 * and upserts matching menu_items — same behavior as the Inventory "Add New Item" dialog.
 *
 * Auto-sync rules:
 *  • If primaryBranchId == Bar 1  → auto clone to Bar 2 (0 stock, 0 price) unless already requested
 *  • If primaryBranchId == parent  → auto clone to Bar 1 (same stock/price) + Bar 2 (0 stock)
 */
suspend fun insertInventoryWithCloneBranchesAndMenu(
    insertDto: InventoryItemDto,
    cloneBranches: Set<BranchDto>,
    menuItems: MutableList<DesktopMenuItem>,
    allInventoryItems: MutableList<InventoryItem>,
    syncMenu: Boolean = true,
    allBranches: List<BranchDto> = emptyList()
): List<InventoryItemDto> {
    // ---- build the full set of clones needed ----
    val bar1 = findBar1(allBranches)
    val bar2 = findBar2(allBranches)
    val parent = findParentBar(allBranches)

    val extraClones = mutableSetOf<Pair<BranchDto, Boolean>>() // (branch, isZeroStock)

    val isFoodOrKitchen = insertDto.category.equals("FOOD", ignoreCase = true) || 
                          insertDto.category.equals("KITCHEN", ignoreCase = true) ||
                          insertDto.isPortionTracked

    // For DRINKS added under the parent branch → redirect primary insert to Bar 1,
    // and ensure Bar 2 gets a zero-stock clone. This prevents orphan items on the parent.
    val effectiveInsertDto = if (!isFoodOrKitchen && insertDto.branchId == parent?.id && bar1 != null) {
        insertDto.copy(branchId = bar1.id)
    } else {
        insertDto
    }

    if (!isFoodOrKitchen) {
        when (effectiveInsertDto.branchId) {
            // Adding to Bar 1 → auto-add zero-copy to Bar 2
            bar1?.id -> {
                if (bar2 != null && cloneBranches.none { it.id == bar2.id }) {
                    extraClones.add(Pair(bar2, true))
                }
            }
            // Adding to parent (only FOOD/KITCHEN reaches here now) → clone to Bar 1 same-stock, Bar 2 zero
            parent?.id -> {
                if (bar1 != null && cloneBranches.none { it.id == bar1.id }) {
                    extraClones.add(Pair(bar1, false))
                }
                if (bar2 != null && cloneBranches.none { it.id == bar2.id }) {
                    extraClones.add(Pair(bar2, true))
                }
            }
        }
    }

    // ---- insert primary row (Use upsert to prevent duplicates if user tries to "add" an existing item) ----
    val insertedList = SupabaseManager.client?.postgrest?.get("inventory")?.upsert(effectiveInsertDto) {
            onConflict = "name,branch_id"
            select()
        }
        ?.decodeAs<List<InventoryItemDto>>() ?: emptyList()

    val inserted = insertedList.firstOrNull() ?: return emptyList()
    val allCreatedDtos = mutableListOf(inserted)

    // ---- explicit clone branches from dialog ----
    val explicitClones = cloneBranches.map { ob ->
        effectiveInsertDto.copy(stock_quantity = 0.0, cost_price = 0.0, sellingPrice = 0.0, branchId = ob.id)
    }

    // ---- extra auto-sync clones ----
    val autoClones = extraClones.map { (branch, isZero) ->
        if (isZero)
            effectiveInsertDto.copy(stock_quantity = 0.0, cost_price = 0.0, sellingPrice = 0.0, branchId = branch.id)
        else
            effectiveInsertDto.copy(branchId = branch.id)
    }

    val allClonesDtos = (explicitClones + autoClones)
        .filter { it.branchId != effectiveInsertDto.branchId } // safety: never re-insert primary branch

    if (allClonesDtos.isNotEmpty()) {
        try {
                val insertedClones = SupabaseManager.client?.postgrest?.get("inventory")?.upsert(allClonesDtos) {
                    onConflict = "name,branch_id"
                    select()
                }
                ?.decodeAs<List<InventoryItemDto>>() ?: emptyList()
            allCreatedDtos.addAll(insertedClones)
        } catch (e: Exception) {
            com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY_SYNC", "Inventory sync error", e)
        }
    }

    // ---- sync menu + update local state ----
    for (itemDto in allCreatedDtos) {
        val inventoryId = itemDto.id ?: itemDto.name

        val shouldSyncMenuForItem = syncMenu &&
            !itemDto.category.equals("KITCHEN", ignoreCase = true) &&
            !itemDto.isPortionTracked
        if (shouldSyncMenuForItem) {
            val desiredMenuDto = MenuItemDto(
                id = null,
                name = itemDto.name,
                price = itemDto.sellingPrice,
                category = itemDto.category,
                subcategory = itemDto.subcategory,
                branchId = itemDto.branchId,
                isActive = true
            )
            try {
                val upserted = SupabaseManager.client?.postgrest?.get("menu_items")?.upsert(desiredMenuDto) {
                        onConflict = "name,branch_id"
                        select()
                    }
                    ?.decodeSingle<MenuItemDto>() ?: continue
                val desktop = DesktopMenuItem(
                    id = upserted.id ?: inventoryId,
                    name = upserted.name,
                    price = upserted.price,
                    category = upserted.category,
                    subcategory = upserted.subcategory,
                    branchId = upserted.branchId
                )
                val idx = menuItems.indexOfFirst {
                    it.id == desktop.id || (it.name == desktop.name && it.branchId == desktop.branchId)
                }
                if (idx >= 0) menuItems[idx] = desktop else menuItems.add(desktop)
            } catch (e: Exception) {
                com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY_SYNC", "Inventory sync worker error", e)
            }
        }

        val newInvItem = InventoryItem(
            id = inventoryId,
            name = itemDto.name,
            category = itemDto.category.uppercase(),
            subcategory = itemDto.subcategory,
            currentStock = itemDto.stock_quantity,
            capacity = (itemDto.min_threshold * 5).coerceAtLeast(10.0),
            lowStockThreshold = itemDto.min_threshold,
            unit = itemDto.unit,
            unitCost = itemDto.cost_price,
            retailPrice = itemDto.sellingPrice,
            isPortionTracked = itemDto.isPortionTracked,
            portionsPerUnit = itemDto.portionsPerUnit,
            linkedMenuItemName = itemDto.linkedMenuItemName,
            soldByShot = itemDto.soldByShot,
            bottleVolumeMl = itemDto.bottleVolumeMl,
            shotSizeMl = itemDto.shotSizeMl,
            branchId = itemDto.branchId
        )
        // Dedup: replace existing entry with same name+branch, otherwise add
        val existingIdx = allInventoryItems.indexOfFirst {
            it.name.equals(newInvItem.name, ignoreCase = true) && it.branchId == newInvItem.branchId
        }
        if (existingIdx >= 0) {
            allInventoryItems[existingIdx] = newInvItem
        } else {
            allInventoryItems.add(newInvItem)
        }
    }

    return allCreatedDtos
}

// ---------------------------------------------------------------------------
// BAR-1 → BAR-2 ONE-TIME SYNC
// ---------------------------------------------------------------------------

/**
 * Ensures every item that exists in Bar 1 also exists in Bar 2 (with 0 stock/price).
 * Safe to call multiple times — uses upsert with ON CONFLICT DO NOTHING.
 */
suspend fun syncBar1ToBar2(
    allInventoryItems: MutableList<InventoryItem>,
    allBranches: List<BranchDto>
) {
    val bar1 = findBar1(allBranches) ?: return
    val bar2 = findBar2(allBranches) ?: return

    val bar1Items = allInventoryItems.filter { it.branchId == bar1.id }
    val bar2Names = allInventoryItems
        .filter { it.branchId == bar2.id }
        .map { it.name.trim().lowercase() }
        .toSet()

    val missing = bar1Items.filter { it.name.trim().lowercase() !in bar2Names }
    if (missing.isEmpty()) return

    val cloneDtos = missing.map { inv ->
        InventoryItemDto(
            name = inv.name,
            category = inv.category,
            subcategory = inv.subcategory,
            stock_quantity = 0.0,
            min_threshold = inv.lowStockThreshold,
            cost_price = 0.0,
            sellingPrice = 0.0,
            unit = inv.unit,
            isPortionTracked = inv.isPortionTracked,
            portionsPerUnit = inv.portionsPerUnit,
            linkedMenuItemName = inv.linkedMenuItemName,
            soldByShot = inv.soldByShot,
            bottleVolumeMl = inv.bottleVolumeMl,
            shotSizeMl = inv.shotSizeMl,
            branchId = bar2.id
        )
    }

    try {
        val inserted = SupabaseManager.client?.postgrest?.get("inventory")?.upsert(cloneDtos) {
                onConflict = "name,branch_id"
                select()
            }
            ?.decodeAs<List<InventoryItemDto>>() ?: emptyList()

        inserted.forEach { dto ->
            val newItem = InventoryItem(
                id = dto.id ?: dto.name,
                name = dto.name,
                category = dto.category.uppercase(),
                subcategory = dto.subcategory,
                currentStock = 0.0,
                capacity = (dto.min_threshold * 5).coerceAtLeast(10.0),
                lowStockThreshold = dto.min_threshold,
                unit = dto.unit,
                unitCost = 0.0,
                retailPrice = 0.0,
                isPortionTracked = dto.isPortionTracked,
                portionsPerUnit = dto.portionsPerUnit,
                linkedMenuItemName = dto.linkedMenuItemName,
                soldByShot = dto.soldByShot,
                bottleVolumeMl = dto.bottleVolumeMl,
                shotSizeMl = dto.shotSizeMl,
                branchId = dto.branchId
            )
            // Only add if not already in local list
            if (allInventoryItems.none {
                it.name.equals(newItem.name, ignoreCase = true) && it.branchId == newItem.branchId
            }) {
                allInventoryItems.add(newItem)
            }
        }
    } catch (e: Exception) {
        com.example.barandgrillownerpanel.utils.Logger.error("INVENTORY_SYNC", "Inventory full sync failed", e)
    }
}

// ---------------------------------------------------------------------------
// FETCH HELPER
// ---------------------------------------------------------------------------

suspend fun fetchInventoryDtoById(rowId: String): InventoryItemDto? {
    val rows = SupabaseManager.client?.postgrest?.get("inventory")?.select { filter { eq("id", rowId) } }
        ?.decodeAs<List<InventoryItemDto>>() ?: emptyList()
    return rows.firstOrNull()
}

// ---------------------------------------------------------------------------
// MENU RETAIL SYNC
// ---------------------------------------------------------------------------

/**
 * Upserts [menu_items] for (name, branch_id) so POS menu retail matches inventory selling price.
 */
suspend fun upsertMenuRetailForInventoryItem(
    name: String,
    category: String,
    subcategory: String,
    branchId: String?,
    retailPrice: Double,
    menuItems: MutableList<DesktopMenuItem>
) {
    val desiredMenuDto = MenuItemDto(
        id = null,
        name = name.trim(),
        price = retailPrice,
        category = category,
        subcategory = subcategory,
        branchId = branchId,
        isActive = true
    )
    val upserted = SupabaseManager.client?.postgrest?.get("menu_items")?.upsert(desiredMenuDto) {
            onConflict = "name,branch_id"
            select()
        }
        ?.decodeSingle<MenuItemDto>() ?: return
    val desktop = DesktopMenuItem(
        id = upserted.id ?: name,
        name = upserted.name,
        price = upserted.price,
        category = upserted.category,
        subcategory = upserted.subcategory,
        branchId = upserted.branchId
    )
    val idx = menuItems.indexOfFirst {
        it.id == desktop.id || (it.name == desktop.name && it.branchId == desktop.branchId)
    }
    if (idx >= 0) menuItems[idx] = desktop else menuItems.add(desktop)
}

// ---------------------------------------------------------------------------
// INVENTORY PRICE PATCH FROM MENU
// ---------------------------------------------------------------------------

/**
 * When menu retail changes, update matching inventory row(s) by previous item name + branch.
 */
suspend fun patchInventorySellingPriceFromMenu(
    previousItemName: String,
    menuBranchId: String?,
    newPrice: Double,
    newItemName: String?,
    inventoryItems: MutableList<InventoryItem>
) {
    val idx = inventoryItems.indexOfFirst { inv ->
        inv.name.equals(previousItemName.trim(), ignoreCase = true) && inv.branchId == menuBranchId
    }
    if (idx < 0) return
    val inv = inventoryItems[idx]
    val finalName = newItemName?.trim()?.takeIf { it.isNotEmpty() } ?: inv.name
    SupabaseManager.client?.postgrest?.get("inventory")?.update({
        set("selling_price", newPrice)
        set("name", finalName)
    }) {
        filter { eq("id", inv.id) }
    }
    inventoryItems[idx] = inv.copy(
        retailPrice = newPrice,
        name = finalName
    )
}





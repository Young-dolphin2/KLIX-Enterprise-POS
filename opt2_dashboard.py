import re

filepath = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\BarAndGrillOwnerPanel\src\main\kotlin\com\example\barandgrillownerpanel\ui\dashboard\DashboardScreen.kt"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Find the pattern: after cache load, there's a try block with Supabase calls
# We replace: the entire try block that fetches from Supabase
# With: a call to SyncEngine.fullSync() + reload from SQLite

old_pattern = """        try {
            // Fetch Branches
            val fetchedBranches = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client
                .postgrest[\"branches\"].select().decodeAs<List<BranchDto>>()

            branches.clear()
            branches.addAll(fetchedBranches)
            appSettings = appSettings.copy(branches = fetchedBranches)"""

# Find this exact start
if old_pattern in content:
    # Now find the end: the catch block
    catch_start = content.find(old_pattern)
    # Find the corresponding "} catch (e: Exception) {"
    # Count braces from catch_start
    depth = 0
    end_idx = catch_start
    # Find the "} catch" that closes the try
    remaining = content[catch_start:]
    
    # The try block ends at line: "        } catch (e: Exception) {"
    try_end_marker = "\n            isOffline = false\n            errorLoading = null\n\n        } catch (e: Exception) {\n            e.printStackTrace()"
    
    if try_end_marker in remaining:
        end_pos = catch_start + remaining.find(try_end_marker) + len(try_end_marker)
        
        # Find the corresponding "        }"
        after_catch = content[end_pos:]
        catch_close = after_catch.find("\n        }")
        if catch_close >= 0:
            # Also find "        } finally {"
            finally_start = after_catch.find("        } finally {", catch_close)
            if finally_start >= 0:
                finally_end = finally_start + len("        } finally {")
                # Find the end of finally block
                after_finally = content[end_pos + finally_end:]
                finally_close = after_finally.find("\n        }")
                if finally_close >= 0:
                    total_end = end_pos + finally_end + finally_close + len("\n        }")
                    
                    # Get the replacement
                    replacement = """        try {
            // Use SyncEngine to pull latest data from Supabase
            com.example.barandgrillownerpanel.data.sync.SyncEngine.fullSync()
            
            // Reload all data from SQLite
            val localDb = com.example.barandgrillownerpanel.data.local.LocalDatabase
            val freshBranches = localDb.getBranches()
            if (freshBranches.isNotEmpty()) {
                branches.clear()
                branches.addAll(freshBranches)
                appSettings = appSettings.copy(branches = freshBranches)
            }
            
            val freshMenu = localDb.getMenuItems()
            if (freshMenu.isNotEmpty()) {
                menuItems.clear()
                menuItems.addAll(freshMenu.mapIndexed { index, dto ->
                    com.example.barandgrillownerpanel.ui.dashboard.DesktopMenuItem(
                        id = dto.id ?: (index + 1).toString(),
                        name = dto.name,
                        price = dto.price,
                        category = dto.category,
                        subcategory = dto.subcategory,
                        branchId = dto.branchId
                    )
                })
            }
            
            val freshInventory = localDb.getInventory()
            if (freshInventory.isNotEmpty()) {
                val mapped = freshInventory.map { dto ->
                    com.example.barandgrillownerpanel.ui.dashboard.InventoryItem(
                        id = dto.id ?: dto.name,
                        name = dto.name,
                        category = dto.category.uppercase(),
                        subcategory = dto.subcategory,
                        currentStock = dto.stock_quantity,
                        capacity = (dto.min_threshold * 5).coerceAtLeast(10.0),
                        lowStockThreshold = dto.min_threshold,
                        unit = dto.unit,
                        unitCost = dto.cost_price,
                        retailPrice = dto.sellingPrice,
                        isPortionTracked = dto.isPortionTracked,
                        portionsPerUnit = dto.portionsPerUnit,
                        linkedMenuItemName = dto.linkedMenuItemName,
                        soldByShot = dto.soldByShot,
                        bottleVolumeMl = dto.bottleVolumeMl,
                        shotSizeMl = dto.shotSizeMl,
                        status = dto.status,
                        branchId = dto.branchId
                    )
                }
                inventoryItems.clear(); inventoryItems.addAll(mapped)
            }
            
            val freshCategories = localDb.getCategories()
            if (freshCategories.isNotEmpty()) {
                customCategories.clear()
                customSubcategories.clear()
                val topLevel = freshCategories.filter { it.parentName == null }.map { it.name }
                customCategories.addAll(topLevel)
                for (cat in topLevel) {
                    val subs = freshCategories.filter { it.parentName == cat }.map { it.name }
                    customSubcategories[cat] = androidx.compose.runtime.mutableStateListOf(*subs.toTypedArray())
                }
            }
            
            val freshCredits = localDb.getRecentCredits(90)
            credits.clear()
            credits.addAll(freshCredits.sortedByDescending { it.created_at })
            
            val freshSales = localDb.getRecentSales(90)
            val mappedSales = freshSales.map { dto ->
                com.example.barandgrillownerpanel.models.SaleRecord(
                    id = dto.orderId,
                    branchId = dto.branchId,
                    items = emptyList(),
                    totalAmount = dto.totalAmount,
                    paymentMethod = dto.paymentMethod,
                    soldBy = dto.soldBy,
                    timestamp = try { java.time.OffsetDateTime.parse(dto.timestamp).toInstant().toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
                )
            }
            saleHistory.clear()
            saleHistory.addAll(mappedSales)
            
            // Refresh detailed data
            refreshSalesHistory()
            refreshExpenses()
            
            isOffline = false
            errorLoading = null

        } catch (e: Exception) {
            com.example.barandgrillownerpanel.utils.Logger.error(\"DASHBOARD\", \"Failed to refresh from SyncEngine\", e)
            if (inventoryItems.isEmpty() && branches.isEmpty()) {
                errorLoading = e.message ?: e.toString()
            } else {
                isOffline = true
            }
        } finally {
            isLoadingData = false
        }"""
                    
                    new_content = content[:catch_start] + replacement + content[total_end:]
                    with open(filepath, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print("SUCCESS: Replaced direct Supabase calls with SyncEngine + SQLite")
                else:
                    print("Could not find finally close")
            else:
                print("Could not find finally start")
        else:
            print("Could not find catch close")
    else:
        print("Could not find try end marker")
else:
    print("Could not find old pattern - checking actual text...")
    # Debug: print lines around the cache load
    lines = content.split('\n')
    for i, line in enumerate(lines):
        if "Fetch Branches" in line:
            print(f"Line {i+1}: {line}")
            break

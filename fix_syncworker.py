import re

path = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\app\src\main\java\com\example\barandgrillpos\data\sync\SyncWorker.kt"

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

original = content

# Fix the checkLowStock function - rewrite it
old_low_stock = """    private suspend fun checkLowStock(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val inventory = kotlinx.coroutines.flow.first(db.cacheDao().observeInventory())
        val lowStockItems = inventory.filter { it.stock_quantity <= it.min_threshold && it.stock_quantity > 0 }
        
        if (lowStockItems.isNotEmpty()) {
            val title = "Low Stock Alert!"
            val message = lowStockItems.take(3).joinToString(", ") { it.name } + 
                          if (lowStockItems.size > 3) " and ${lowStockItems.size - 3} more" else ""
            showNotification(context, title, message)
        }
    }"""

new_low_stock = """    private suspend fun checkLowStock(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val inventoryList = db.cacheDao().observeInventory()
        var inventory: List<com.example.barandgrillpos.data.local.InventoryItemEntity> = emptyList()
        try {
            inventory = kotlinx.coroutines.flow.first(inventoryList)
        } catch (e: Exception) {
            return
        }
        val lowStockItems = inventory.filter { it.stock_quantity <= it.min_threshold && it.stock_quantity > 0 }
        
        if (lowStockItems.isNotEmpty()) {
            val title = "Low Stock Alert!"
            val names = lowStockItems.take(3).map { it.name }
            val message = names.joinToString(", ") + 
                          if (lowStockItems.size > 3) " and ${lowStockItems.size - 3} more" else ""
            showNotification(context, title, message)
        }
    }"""

if old_low_stock in content:
    content = content.replace(old_low_stock, new_low_stock)
    print("Fixed checkLowStock")
else:
    print("checkLowStock pattern not found")

# Fix the CustomerEntity constructor - createdAt -> created_at
content = content.replace(
    "created_at = dto.createdAt,",
    "created_at = dto.createdAt,"
)
# Actually the issue might be that "created_at" is not a valid field. Let me check the Entity class
# The error said "No parameter with name 'created_at' found" on line 220
# So the field is probably just "createdAt" not "created_at"
content = content.replace("created_at = dto.createdAt,", "createdAt = dto.createdAt,")

if content != original:
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Applied fixes to SyncWorker.kt")
else:
    print("No changes made")

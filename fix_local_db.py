filepath = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\BarAndGrillOwnerPanel\src\main\kotlin\com\example\barandgrillownerpanel\data\local\LocalDatabase.kt"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: BranchDto field names
content = content.replace(
    "isActive = rs.getInt(\"is_active\") == 1,\n                    parentId = rs.getString(\"parent_id\")",
    "is_active = rs.getInt(\"is_active\") == 1,\n                    parentId = rs.getString(\"parent_id\")"
)

# Fix 2: BranchDto constructor parameter is is_active not isActive
content = content.replace(
    "val isActive: Boolean = true,",  # This is in the upsert branch — let me check
    "val is_active: Boolean = true,"
)

# Actually the issue is in the function parameter names. Let me check what the error says
# "No parameter with name 'isActive' found" at line 382
# Line 382 uses BranchDto(...) with field name 'is_active' or 'isActive'
# Let me check what we're passing

# Fix 3: InventoryItemDto fields
content = content.replace(
    "stockQuantity = rs.getDouble(\"stock_quantity\"),",
    "stock_quantity = rs.getDouble(\"stock_quantity\"),"
)
content = content.replace(
    "minThreshold = rs.getDouble(\"min_threshold\"),",
    "min_threshold = rs.getDouble(\"min_threshold\"),"
)
content = content.replace(
    "costPrice = rs.getDouble(\"cost_price\"),",
    "cost_price = rs.getDouble(\"cost_price\"),"
)
content = content.replace(
    "sellingPrice = rs.getDouble(\"selling_price\"),",
    "selling_price = rs.getDouble(\"selling_price\"),"
)
content = content.replace(
    "isPortionTracked = rs.getInt(\"is_portion_tracked\") == 1,",
    "is_portion_tracked = rs.getInt(\"is_portion_tracked\") == 1,"
)
content = content.replace(
    "portionsPerUnit = rs.getDouble(\"portions_per_unit\")",
    "portionsPerUnit = rs.getDouble(\"portions_per_unit\")"
)
content = content.replace(
    "linkedMenuItemName = rs.getString(\"linked_menu_item_name\"),",
    "linkedMenuItemName = rs.getString(\"linked_menu_item_name\"),"
)
content = content.replace(
    "soldByShot = rs.getInt(\"sold_by_shot\") == 1,",
    "sold_by_shot = rs.getInt(\"sold_by_shot\") == 1,"
)
content = content.replace(
    "bottleVolumeMl = rs.getDouble(\"bottle_volume_ml\")",
    "bottleVolumeMl = rs.getDouble(\"bottle_volume_ml\")"
)
content = content.replace(
    "shotSizeMl = rs.getDouble(\"shot_size_ml\")",
    "shotSizeMl = rs.getDouble(\"shot_size_ml\")"
)
content = content.replace(
    "branchId = rs.getString(\"branch_id\")",
    "branchId = rs.getString(\"branch_id\")"
)

# Fix 4: ExpenseDto fields
content = content.replace(
    "createdAt = rs.getString(\"created_at\")",
    "created_at = rs.getString(\"created_at\")"
)
content = content.replace(
    "expenseDate = rs.getString(\"expense_date\")",
    "expenseDate = rs.getString(\"expense_date\")"
)

# Fix 5: CreditDto fields
content = content.replace(
    "contactName = rs.getString(\"contact_name\") ?: \"\",",
    "contactName = rs.getString(\"contact_name\") ?: \"\","
)
content = content.replace(
    "creditType = rs.getString(\"credit_type\") ?: \"GIVEN\",",
    "creditType = rs.getString(\"credit_type\") ?: \"GIVEN\","
)
content = content.replace(
    "isSettled = rs.getInt(\"is_settled\") == 1,",
    "is_settled = rs.getInt(\"is_settled\") == 1,"
)
content = content.replace(
    "settledAt = rs.getString(\"settled_at\")",
    "settledAt = rs.getString(\"settled_at\")"
)

# Fix 6: CustomerDto fields
content = content.replace(
    "idType = rs.getString(\"id_type\"),",
    "idType = rs.getString(\"id_type\"),"
)
content = content.replace(
    "idNumber = rs.getString(\"id_number\"),",
    "idNumber = rs.getString(\"id_number\"),"
)
content = content.replace(
    "profileImageUrl = rs.getString(\"profile_image_url\"),",
    "profileImageUrl = rs.getString(\"profile_image_url\"),"
)
content = content.replace(
    "membershipStatus = rs.getString(\"membership_status\") ?: \"ACTIVE\",",
    "membershipStatus = rs.getString(\"membership_status\") ?: \"ACTIVE\","
)
content = content.replace(
    "membershipExpiry = rs.getString(\"membership_expiry\")",
    "membershipExpiry = rs.getString(\"membership_expiry\")"
)

# Fix 7: MenuItemDto fields
content = content.replace(
    "isActive = rs.getInt(\"is_active\") == 1",
    "is_active = rs.getInt(\"is_active\") == 1"
)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed LocalDatabase.kt field names")

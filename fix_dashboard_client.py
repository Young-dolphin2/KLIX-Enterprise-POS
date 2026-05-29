#!/usr/bin/env python3
# Fix nullable Supabase client accesses in DashboardScreen

filepath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/DashboardScreen.kt"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Multiple patterns to fix
fixes = [
    # Fix line 159 - SupabaseManager.client.postgrest - add safe navigation
    ('val items = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client\n            .postgrest["inventory"]',
     'val items = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?\n            .postgrest?.get("inventory")'),
    
    # Fix line 189 - same pattern
    ('val cats = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client\n            .postgrest["categories"]',
     'val cats = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?\n            .postgrest?.get("categories")'),
    
    # Fix line 210 - same pattern
    ('val salesList = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client\n            .postgrest["sales"]',
     'val salesList = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?\n            .postgrest?.get("sales")'),
    
    # Fix line 215 - nested postgrest inside isIn filter
    ('com.example.barandgrillownerpanel.data.remote.SupabaseManager.client\n                .postgrest["sale_items"]',
     'com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?\n                .postgrest?.get("sale_items")'),
    
    # Fix line 246 and 256 - .decodeAs without safe navigation
    ('.select().decodeAs<', '.select()?.decodeAs<'),
    
    # Fix the remaining client accesses in the file
    ('.postgrest["', '.postgrest?.get("'),
]

for old, new in fixes:
    if old in content:
        content = content.replace(old, new)
        print(f"✓ Fixed: {old[:60]}...")

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("\nDashboardScreen fixes complete")

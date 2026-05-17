import os, re

# Fix 1: Supabase import paths (gotrue -> auth) across all files
base = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\app\src\main\java"

files_to_fix = [
    r"\com\example\barandgrillpos\MainActivity.kt",
    r"\com\example\barandgrillpos\data\remote\SupabaseManager.kt",
    r"\com\example\barandgrillpos\ui\auth\LoginScreen.kt",
    r"\com\example\barandgrillpos\ui\auth\SignUpScreen.kt",
]

for rel_path in files_to_fix:
    path = base + rel_path
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    content = content.replace("import io.github.jan.supabase.gotrue.auth", "import io.github.jan.supabase.auth.auth")
    content = content.replace("import io.github.jan.supabase.gotrue.SessionStatus", "import io.github.jan.supabase.auth.status.SessionStatus")
    content = content.replace("import io.github.jan.supabase.gotrue.Auth", "import io.github.jan.supabase.auth.Auth")
    content = content.replace("import io.github.jan.supabase.gotrue.providers.builtin.Email", "import io.github.jan.supabase.auth.providers.builtin.Email")
    
    if content != original:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed: {rel_path}")

# Fix 2: SyncWorker.kt - expenseDao references (expenses were removed from POS)
sync_path = base + r"\com\example\barandgrillpos\data\sync\SyncWorker.kt"
with open(sync_path, 'r', encoding='utf-8') as f:
    content = f.read()

original = content

# Remove expense sync block entirely
content = re.sub(
    r"        // 1\) Sync Expenses.*?            }\n        }\n",
    "",
    content,
    flags=re.DOTALL
)

# Fix createdAt -> created_at in CustomerEntity constructor
content = content.replace("createdAt = dto.createdAt,", "created_at = dto.createdAt,")

# Fix the low stock check: first() is a Flow terminal operator, use first() from kotlinx.coroutines.flow
content = content.replace(
    "val inventory = db.cacheDao().observeInventory().first()",
    "val inventory = kotlinx.coroutines.flow.first(db.cacheDao().observeInventory())"
)

if content != original:
    with open(sync_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Fixed: SyncWorker.kt")

print("All fixes applied!")

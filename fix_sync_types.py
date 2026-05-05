filepath = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\BarAndGrillOwnerPanel\src\main\kotlin\com\example\barandgrillownerpanel\data\sync\SyncEngine.kt"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix PULL_LIMIT type: change Int to Long
content = content.replace(
    "private const val PULL_LIMIT = 500",
    "private const val PULL_LIMIT = 500L"
)

# Also fix PUSH_BATCH_SIZE if it's used somewhere that expects Long
# But it's only used with .take() which takes Int, so it's fine

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed SyncEngine types")

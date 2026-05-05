filepath = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\BarAndGrillOwnerPanel\src\main\kotlin\com\example\barandgrillownerpanel\ui\dashboard\DashboardScreen.kt"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import for SyncEngine and CacheManager initialize
# The imports are already there for CacheManager

# Add initialization before the subscription check block
old = """    // Check subscription on launch
    LaunchedEffect(Unit) {
        val session = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.auth.sessionStatus"""

new = """    // Initialize local cache system (SQLite primary, JSON fallback)
    LaunchedEffect(Unit) {
        com.example.barandgrillownerpanel.data.CacheManager.initialize(".")
        com.example.barandgrillownerpanel.data.sync.SyncEngine.startSyncCycle(scope)
    }

    // Check subscription on launch
    LaunchedEffect(Unit) {
        val session = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client.auth.sessionStatus"""

if old in content:
    content = content.replace(old, new)
    print("Added cache initialization!")
else:
    print("Could not find target block - checking...")
    # Debug
    idx = content.find("Check subscription on launch")
    if idx >= 0:
        print(f"Found at index {idx}")
        print(repr(content[idx:idx+200]))

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

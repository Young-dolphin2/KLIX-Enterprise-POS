# Fix postgrest result safe navigation and return type issues

# 1. Fix SyncEngine.kt - add ?. before .decodeAs
$syncEnginePath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/data/sync/SyncEngine.kt"
$text = Get-Content -Path $syncEnginePath -Raw
$text = $text.Replace(".select().decodeAs", ".select()?.decodeAs")
$text = $text.Replace(".select(""*"").decodeAs", ".select(""*"")?.decodeAs")
Set-Content -Path $syncEnginePath -Value $text
Write-Output "Fixed SyncEngine.kt postgrest safe navigation"

# 2. Fix MenuRepository.kt
$menuRepoPath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/data/repository/MenuRepository.kt"
$text = Get-Content -Path $menuRepoPath -Raw
$text = $text.Replace(".select().decodeAs", ".select()?.decodeAs")
$text = $text.Replace(".select(""*"").decodeAs", ".select(""*"")?.decodeAs")
Set-Content -Path $menuRepoPath -Value $text
Write-Output "Fixed MenuRepository.kt postgrest safe navigation"

# 3. Fix SettingsRepository.kt
$settingsRepoPath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/data/repository/SettingsRepository.kt"
$text = Get-Content -Path $settingsRepoPath -Raw
$text = $text.Replace(".select().decodeAs", ".select()?.decodeAs")
$text = $text.Replace(".select(""*"").decodeAs", ".select(""*"")?.decodeAs")
Set-Content -Path $settingsRepoPath -Value $text
Write-Output "Fixed SettingsRepository.kt postgrest safe navigation"

# 4. Fix SubscriptionManager.kt
$subMgrPath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/subscription/SubscriptionManager.kt"
$text = Get-Content -Path $subMgrPath -Raw
$text = $text.Replace(".select().decodeAs", ".select()?.decodeAs")
$text = $text.Replace(".select(""*"").decodeAs", ".select(""*"")?.decodeAs")
Set-Content -Path $subMgrPath -Value $text
Write-Output "Fixed SubscriptionManager.kt postgrest safe navigation"

# 5. Fix InventoryMenuSync.kt
$inventorySyncPath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/InventoryMenuSync.kt"
$text = Get-Content -Path $inventorySyncPath -Raw
$text = $text.Replace(".select().decodeAs", ".select()?.decodeAs")
$text = $text.Replace(".select(""*"").decodeAs", ".select(""*"")?.decodeAs")
Set-Content -Path $inventorySyncPath -Value $text
Write-Output "Fixed InventoryMenuSync.kt postgrest safe navigation"

# 6. Fix SalesStatsTab.kt
$salesStatsPath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/SalesStatsTab.kt"
$text = Get-Content -Path $salesStatsPath -Raw
$text = $text.Replace(".select().decodeAs", ".select()?.decodeAs")
$text = $text.Replace(".select(""*"").decodeAs", ".select(""*"")?.decodeAs")
Set-Content -Path $salesStatsPath -Value $text
Write-Output "Fixed SalesStatsTab.kt postgrest safe navigation"

# 7. Fix SettingsTab.kt
$settingsTabPath = "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/SettingsTab.kt"
$text = Get-Content -Path $settingsTabPath -Raw
$text = $text.Replace(".select().decodeAs", ".select()?.decodeAs")
$text = $text.Replace(".select(""*"").decodeAs", ".select(""*"")?.decodeAs")
Set-Content -Path $settingsTabPath -Value $text
Write-Output "Fixed SettingsTab.kt postgrest safe navigation"

Write-Output "All postgrest safe navigation fixes applied"

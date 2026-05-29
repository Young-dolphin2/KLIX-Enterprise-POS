$files = @(
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/data/repository/MenuRepository.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/data/repository/SettingsRepository.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/subscription/SubscriptionManager.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/InventoryMenuSync.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/SalesStatsTab.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/SettingsTab.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/data/export/PdfReportService.kt"
)

foreach ($file in $files) {
    $text = Get-Content -Path $file -Raw
    # Pattern 1: .select()\n                    .decodeAs -> .select()?\n                    .decodeAs
    $text = $text.Replace(").select()`n                    .decodeAs", ").select()?`n                    .decodeAs")
    $text = $text.Replace(").select()`r`n                    .decodeAs", ").select()?`r`n                    .decodeAs")
    # Pattern 2: Direct replacements
    $text = $text.Replace(").select()
                    .decodeAs", ").select()?
                    .decodeAs")
    Set-Content -Path $file -Value $text
    Write-Output "Processed $file"
}

Write-Output "Multiline postgrest fixes applied"

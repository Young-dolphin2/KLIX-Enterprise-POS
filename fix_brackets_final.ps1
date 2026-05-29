$files = @(
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/DashboardScreen.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/MenuControlTab.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/InventoryTab.kt"
)

foreach ($file in $files) {
    $text = Get-Content -Path $file -Raw
    # Fix pattern [index) or [idx) to [index] or [idx]
    $text = $text.Replace("[index)", "[index]")
    $text = $text.Replace("[idx)", "[idx]")
    Set-Content -Path $file -Value $text
    Write-Output "Fixed brackets in $file"
}

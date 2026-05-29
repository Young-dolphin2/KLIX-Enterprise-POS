$files = @(
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/DashboardScreen.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/MenuControlTab.kt",
  "BarAndGrillOwnerPanel/src/main/kotlin/com/example/barandgrillownerpanel/ui/dashboard/InventoryTab.kt"
)

foreach ($file in $files) {
    $text = Get-Content -Path $file -Raw
    $text = $text.Replace("]?.", "].")
    Set-Content -Path $file -Value $text
    Write-Output "Fixed $file"
}

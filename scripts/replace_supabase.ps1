$root = 'BarAndGrillOwnerPanel/src/main/kotlin'
Get-ChildItem -Path $root -Recurse -Filter *.kt | ForEach-Object {
    $path = $_.FullName
    (Get-Content $path) -replace 'SupabaseManager\\.client\\.', 'SupabaseManager.client?.' | Set-Content $path
}

$root = 'BarAndGrillOwnerPanel/src/main/kotlin'
Get-ChildItem -Path $root -Recurse -Filter *.kt | ForEach-Object {
    $path = $_.FullName
    $text = Get-Content $path -Raw
    $text = $text -replace '\"\)\s*\.', '")?.'
    $text = $text -replace '\]\s*\.', ')?.'
    Set-Content $path $text
}
Write-Output "Done"

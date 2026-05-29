$root = 'BarAndGrillOwnerPanel/src/main/kotlin'
Get-ChildItem -Path $root -Recurse -Filter *.kt | ForEach-Object {
    $path = $_.FullName
    $text = Get-Content $path -Raw
    $text = $text -replace '\["([^"\]]+)"\)\?','["$1"]?'
    $text = $text -replace '\["([^"\]]+)"\)','["$1"]'
    Set-Content $path $text
}
Write-Output 'Bracket fix complete'

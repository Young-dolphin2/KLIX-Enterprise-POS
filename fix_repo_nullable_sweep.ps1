$root = "BarAndGrillOwnerPanel"
$files = Get-ChildItem -Path $root -Recurse -Include *.kt | Select-Object -ExpandProperty FullName

foreach ($file in $files) {
    $text = Get-Content -Path $file -Raw
    $orig = $text
    # 1. Ensure client safe navigation
    $text = $text -replace "SupabaseManager\.client\s*\.postgrest", "SupabaseManager.client?.postgrest"
    $text = $text -replace "com\.example\.barandgrillownerpanel\.data\.remote\.SupabaseManager\.client\s*\.postgrest", "com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?.postgrest"

    # 2. Convert .postgrest["table"] to ?.postgrest?.get("table")
    $text = $text -replace "\.postgrest\[\"", "?.postgrest?.get(\""

    # 3. Make .select().decodeAs... null-safe: .select()?.decodeAs
    $text = [regex]::Replace($text, "\.select\(\)\.(decode[A-Za-z0-9_<>]+)", ".select()?.$1")

    # 4. Make .select()\.decodeSingle also safe
    $text = [regex]::Replace($text, "\.select\(\)\.(decodeSingle[A-Za-z0-9_<>]*)", ".select()?.$1")

    # 5. Replace occurrences of ".select()?\.decodeAs<List" followed by \)\s*\n to add ?: emptyList() where assigned to val list variables
    # We'll add ' ?: emptyList()' for common patterns 'val x = ...decodeAs<List' -> 'val x = ...decodeAs<List...>>() ?: emptyList()'
    $text = [regex]::Replace($text, "(decodeAs<List<[^>]+>>\(\))", "$1 ?: emptyList()")

    if ($text -ne $orig) {
        Set-Content -Path $file -Value $text
        Write-Output "Patched $file"
    }
}
Write-Output "Nullable sweep complete"
$process = Start-Process -FilePath ".\gradlew.bat" -ArgumentList ":BarAndGrillOwnerPanel:compileKotlin", "--console=plain" -NoNewWindow -PassThru -RedirectStandardOutput "build_out.txt" -RedirectStandardError "build_err.txt"
$process.WaitForExit()
Get-Content build_out.txt
Get-Content build_err.txt

# build_and_deploy.ps1
$gradleFile = "android\app\build.gradle"
$apkPath = "android\app\build\outputs\apk\debug\app-debug.apk"
$destDir = "C:\Google Drive\mahmudulhasandhk70\App Store\Android"

# 1. Increment Version
Write-Host "Incrementing version in $gradleFile..."
$content = Get-Content $gradleFile -Raw

# Increment VersionCode (+3)
$codeMatch = [regex]::Match($content, 'versionCode (\d+)')
$currentCode = [int]$codeMatch.Groups[1].Value
$newCode = $currentCode + 3
$content = $content -replace "versionCode \d+", "versionCode $newCode"

# Increment VersionName (+1 to minor)
$nameMatch = [regex]::Match($content, 'versionName "([\d.]+)"')
$currentName = $nameMatch.Groups[1].Value
$parts = $currentName.Split('.')
$lastPart = [int]$parts[-1]
$parts[-1] = ($lastPart + 1).ToString()
$newName = $parts -join '.'
$content = $content -replace "versionName `"$currentName`"", "versionName `"$newName`""

Set-Content -Path $gradleFile -Value $content
Write-Host "New Version: $newName, Build: $newCode"

# 2. Build
Write-Host "Building APK..."
cd android
.\gradlew assembleDebug
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed"; exit }
cd ..


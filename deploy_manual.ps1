
$apkPath = 'C:\OpenCode\Mess Manager\android\app\build\outputs\apk\debug\app-debug.apk'
$destDir = 'C:\Google Drive\mahmudulhasandhk70\App Store\Android'
$gradleFile = 'C:\OpenCode\Mess Manager\android\app\build.gradle'

if (Test-Path $apkPath) {
    $content = Get-Content $gradleFile -Raw
    $versionMatch = [regex]::Match($content, 'versionName "([\d.]+)"')
    $codeMatch = [regex]::Match($content, 'versionCode (\d+)')
    
    $currentVersion = $versionMatch.Groups[1].Value
    $currentBuild = $codeMatch.Groups[1].Value
    
    $targetName = "Mess Manager_com.mahmuduls.messmanager_v${currentVersion}_build_${currentBuild}.apk"
    $targetPath = Join-Path $destDir $targetName
    
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force }
    Get-ChildItem -Path $destDir -Filter 'Mess Manager_com.mahmuduls.messmanager_*.apk' | Remove-Item -Force
    Copy-Item -Path $apkPath -Destination $targetPath -Force
    Write-Output "Successfully deployed: $targetPath (Version: $currentVersion, Build: $currentBuild)"
} else {
    Write-Error "APK not found at $apkPath"
}

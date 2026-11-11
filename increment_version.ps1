
$gradleFile = 'C:\OpenCode\Mess Manager\android\app\build.gradle'
$jsFile = 'C:\OpenCode\Mess Manager\www\app.js'
$deployScript = 'C:\OpenCode\Mess Manager\build_and_deploy.ps1'

# Read current build.gradle for version and build
$content = Get-Content $gradleFile -Raw
$versionMatch = [regex]::Match($content, 'versionName "([\d.]+)"')
$codeMatch = [regex]::Match($content, 'versionCode (\d+)')

if ($versionMatch.Success -and $codeMatch.Success) {
    $currentVersion = $versionMatch.Groups[1].Value
    $currentCode = [int]$codeMatch.Groups[1].Value
    
    # Calculate new values: Version +1 to last digit, Build +3
    $parts = $currentVersion.Split('.')
    $lastDigit = [int]$parts[-1]
    $parts[-1] = [string]($lastDigit + 1)
    $newVersion = $parts -join '.'
    $newCode = $currentCode + 3
    
    # Update build.gradle
    $newContent = $content -replace "versionName `"$currentVersion`"", "versionName `"$newVersion`""
    $newContent = $newContent -replace "versionCode $currentCode", "versionCode $newCode"
    Set-Content -Path $gradleFile -Value $newContent
    
    # Update app.js version display strings
    $jsContent = Get-Content $jsFile -Raw
    $jsContent = $jsContent -replace "Version [\d.]+ \(build \d+\)", "Version $newVersion (build $newCode)"
    $jsContent = $jsContent -replace "ভার্সন [\d.]+ \(বিল্ড \d+\)", "ভার্সন $newVersion (বিল্ড $newCode)"
    Set-Content -Path $jsFile -Value $jsContent
    
    Write-Output "Incremented to Version: $newVersion, Build: $newCode"
} else {
    Write-Error "Could not find current version/build in build.gradle"
}

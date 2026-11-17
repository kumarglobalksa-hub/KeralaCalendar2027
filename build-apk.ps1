$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleVersion = '8.9'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'KeralaCalendar2027'
$gradleHome = Join-Path $cacheRoot "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleHome "gradle-$gradleVersion\bin\gradle.bat"
$zipPath = Join-Path $cacheRoot "gradle-$gradleVersion-bin.zip"

Write-Host 'Kerala Calendar 2027 - debug APK builder' -ForegroundColor Green

# Android Gradle Plugin 8.x requires a JDK 17+ runtime.
$javaCandidates = @()
if ($env:JAVA_HOME) { $javaCandidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
if ($env:ProgramFiles) { $javaCandidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\java.exe') }
if ($env:LOCALAPPDATA) { $javaCandidates += (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr\bin\java.exe') }
$pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
if ($pathJava) { $javaCandidates += $pathJava.Source }
$javaExe = $null
foreach ($candidate in ($javaCandidates | Select-Object -Unique)) {
    if (-not (Test-Path $candidate)) { continue }
    $javaOutput = (& $candidate -version 2>&1 | Out-String)
    $javaMatch = [regex]::Match($javaOutput, 'version "([0-9]+)')
    if ($LASTEXITCODE -eq 0 -and $javaMatch.Success -and [int]$javaMatch.Groups[1].Value -ge 17) {
        $javaExe = $candidate
        break
    }
}
if (-not $javaExe) {
    throw 'JDK 17 or newer was not found. Install Android Studio (which includes a JDK) or install a JDK 17+ and set JAVA_HOME.'
}
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
$env:PATH = "$(Split-Path -Parent $javaExe);$env:PATH"

# Find an installed Android SDK.
$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk -or -not (Test-Path $sdk)) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $defaultSdk) { $sdk = $defaultSdk }
}
if (-not $sdk -or -not (Test-Path $sdk)) {
    throw 'Android SDK not found. Install Android Studio and Android SDK Platform 35 from Tools > SDK Manager, then run this script again.'
}
if (-not (Test-Path (Join-Path $sdk 'platforms\android-35\android.jar'))) {
    throw "Android SDK Platform 35 is missing under $sdk. Install Android 15 / API 35 from Android Studio > Tools > SDK Manager."
}
$buildToolsRoot = Join-Path $sdk 'build-tools'
$buildTools = Get-ChildItem $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like '35.*' } | Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) {
    throw "Android SDK Build-Tools 35.x are missing under $sdk. Install them from Android Studio > Tools > SDK Manager."
}
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk

# Android Gradle Plugin 8.7.3 requires Gradle 8.9. Download the official Gradle distribution if needed.
if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    $downloadUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Host "Downloading Gradle $gradleVersion..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $gradleHome -Force
}
if (-not (Test-Path $gradleExe)) {
    throw 'Gradle could not be prepared. Check the download and try again.'
}

Push-Location $projectDir
try {
    & $gradleExe --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
    $apk = Join-Path $projectDir 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw 'Build completed but the expected APK file was not found.' }
    Write-Host ''
    Write-Host 'APK built successfully:' -ForegroundColor Green
    Write-Host $apk -ForegroundColor Cyan
} finally {
    Pop-Location
}

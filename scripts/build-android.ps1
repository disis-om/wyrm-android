[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release', 'MigrationRelease')]
    [string]$Configuration = 'Debug',
    [int]$VersionCode = 10100,
    [string]$VersionName = '1.1.0',
    [switch]$AllowSignedBuild
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AndroidRoot = Join-Path $ProjectRoot 'android'
$GradleWrapper = Join-Path $AndroidRoot 'gradlew.bat'
$JavaHome = 'C:\Program Files\Android\Android Studio\jbr'
$AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'

if ($Configuration -ne 'Debug' -and -not $AllowSignedBuild) {
    throw 'Signed builds require the explicit -AllowSignedBuild switch.'
}
if ($VersionCode -le 0 -or [string]::IsNullOrWhiteSpace($VersionName)) {
    throw 'VersionCode and VersionName must be valid.'
}
if (-not (Test-Path -LiteralPath $GradleWrapper)) {
    throw "Gradle wrapper not found: $GradleWrapper"
}
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw "Android Studio Java runtime not found: $JavaHome"
}
if (-not (Test-Path -LiteralPath $AndroidSdk)) {
    throw "Android SDK not found: $AndroidSdk"
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk

$Task = switch ($Configuration) {
    'Debug' { ':app:assembleDebug' }
    'Release' { ':app:assembleRelease' }
    'MigrationRelease' { ':app:assembleMigrationRelease' }
}

Push-Location $AndroidRoot
try {
    & $GradleWrapper $Task `
        "-PWYRM_VERSION_CODE=$VersionCode" `
        "-PWYRM_VERSION_NAME=$VersionName" `
        '--stacktrace'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$VariantFolder = switch ($Configuration) {
    'Debug' { 'debug' }
    'Release' { 'release' }
    'MigrationRelease' { 'migrationRelease' }
}
$OutputDirectory = Join-Path $AndroidRoot "app\build\outputs\apk\$VariantFolder"
$Apk = Get-ChildItem -LiteralPath $OutputDirectory -Filter '*.apk' -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $Apk) {
    throw "Gradle succeeded but no APK was found in $OutputDirectory"
}

Write-Output $Apk.FullName

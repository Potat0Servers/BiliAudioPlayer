param(
    [string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'),
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkRoot = $env:ANDROID_HOME,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome -and (Test-Path 'C:\Program Files\Android\openjdk\jdk-21.0.8\bin\java.exe')) {
    $JavaHome = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Set JAVA_HOME to a JDK 17 or newer installation, or pass -JavaHome.'
}
if (-not $SdkRoot) { $SdkRoot = Join-Path $projectRoot '.tools\android-sdk' }
if (-not (Test-Path (Join-Path $SdkRoot 'platforms\android-35\android.jar'))) {
    throw 'Android SDK platform 35 is missing. Run scripts\setup-android.ps1 first, or pass -SdkRoot.'
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-user-home'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-user-home'
$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
$arguments = @('--no-daemon', '--console=plain') + $Tasks
if ($Offline) { $arguments += '--offline' }
Push-Location $projectRoot
try {
    $gradleCommand = Join-Path $projectRoot '.tools\gradle-8.11.1\bin\gradle.bat'
    if (-not (Test-Path $gradleCommand)) { $gradleCommand = Join-Path $projectRoot 'gradlew.bat' }
    & $gradleCommand @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}

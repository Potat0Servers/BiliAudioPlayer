param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$AcceptLicenses
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$downloadRoot = Join-Path $toolRoot 'downloads'
$sdkRoot = Join-Path $toolRoot 'android-sdk'
if (-not $JavaHome -and (Test-Path 'C:\Program Files\Android\openjdk\jdk-21.0.8\bin\java.exe')) {
    $JavaHome = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Set JAVA_HOME to a JDK 17 or newer installation, or pass -JavaHome.'
}
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user-home'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-user-home'
New-Item -ItemType Directory -Force -Path $downloadRoot, $sdkRoot | Out-Null

function Get-VerifiedDownload {
    param([string]$Url, [string]$Path, [string]$Sha256)
    if (-not (Test-Path $Path)) {
        & curl.exe -f -L --retry 3 --connect-timeout 30 -o $Path $Url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    }
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Sha256) {
        throw "Checksum mismatch: $Path. Remove this incomplete download and retry."
    }
}

$commandLineArchive = Join-Path $downloadRoot 'commandlinetools-win-15859902_latest.zip'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
    Get-VerifiedDownload -Url 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -Path $commandLineArchive -Sha256 '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    $extractRoot = Join-Path $toolRoot 'commandline-extracted'
    Expand-Archive -LiteralPath $commandLineArchive -DestinationPath $extractRoot -Force
    $commandLineRoot = Join-Path $sdkRoot 'cmdline-tools'
    New-Item -ItemType Directory -Force -Path $commandLineRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $extractRoot 'cmdline-tools') -Destination (Join-Path $commandLineRoot 'latest') -Recurse -Force
}

if ($AcceptLicenses) {
    1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
} else {
    & $sdkManager "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
}
if ($LASTEXITCODE -ne 0) { throw "Android SDK installation failed with exit code $LASTEXITCODE." }

$gradleArchive = Join-Path $downloadRoot 'gradle-8.11.1-bin.zip'
Get-VerifiedDownload -Url 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -Path $gradleArchive -Sha256 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
if (-not (Test-Path (Join-Path $toolRoot 'gradle-8.11.1\bin\gradle.bat'))) {
    Expand-Archive -LiteralPath $gradleArchive -DestinationPath $toolRoot -Force
}
$sdkProperty = $sdkRoot.Replace('\', '/')
Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Value "sdk.dir=$sdkProperty" -Encoding utf8
Write-Host 'Android SDK is ready. Run .\scripts\build.ps1 to build and verify.'

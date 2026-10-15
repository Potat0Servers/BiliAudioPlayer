param(
    [string]$Name = 'BiliAudioPlayer_API35',
    [int]$Port = 5554,
    [int]$WaitSeconds = 180,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$InstallPackages
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$sdkRoot = Join-Path $toolRoot 'android-sdk'
if (-not $JavaHome -and (Test-Path 'C:\Program Files\Android\openjdk\jdk-21.0.8\bin\java.exe')) {
    $JavaHome = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
}
if (-not $JavaHome) { throw 'Set JAVA_HOME, or pass -JavaHome.' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user-home'
$env:ANDROID_AVD_HOME = Join-Path $toolRoot 'avd'
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME | Out-Null
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
$avdManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$systemImage = 'system-images;android-35;google_apis;x86_64'
if ($InstallPackages) {
    & $sdkManager "--sdk_root=$sdkRoot" 'emulator' $systemImage
    if ($LASTEXITCODE -ne 0) { throw 'Android emulator package installation failed.' }
}
if (-not (Test-Path $emulator) -or -not (Test-Path (Join-Path $sdkRoot 'system-images\android-35\google_apis\x86_64\system.img'))) {
    throw 'Install emulator packages first by passing -InstallPackages.'
}
$avdPath = Join-Path $env:ANDROID_AVD_HOME "$Name.avd"
if (-not (Test-Path (Join-Path $avdPath 'config.ini'))) {
    'no' | & $avdManager create avd --name $Name --path $avdPath --package $systemImage --device 'pixel'
    if ($LASTEXITCODE -ne 0) { throw 'Android virtual device creation failed.' }
}
$serial = "emulator-$Port"
$devices = & $adb devices
if (-not ($devices -match "^$serial\s")) {
    $arguments = @('-avd', $Name, '-port', $Port, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'swiftshader', '-memory', '2048')
    $emulatorProcess = Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $toolRoot 'emulator-stdout.log') -RedirectStandardError (Join-Path $toolRoot 'emulator-stderr.log')
    Write-Host "Started emulator process $($emulatorProcess.Id)."
}
$deadline = (Get-Date).AddSeconds($WaitSeconds)
do {
    $bootState = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($bootState -eq '1') {
        & $adb -s $serial shell input keyevent 82
        Write-Host "Android emulator is ready: $serial"
        exit 0
    }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
throw 'Emulator startup timed out. Inspect .tools/emulator-stdout.log and .tools/emulator-stderr.log.'

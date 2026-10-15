param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$Repository = 'Potat0Servers/BiliAudioPlayer',
    [string]$Notes = 'Install the APK to update BiliAudioPlayer. Existing login and preferences are preserved.'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Repository -ne 'Potat0Servers/BiliAudioPlayer') { throw 'Publishing is restricted to Potat0Servers/BiliAudioPlayer.' }
if (-not $SdkRoot) { $SdkRoot = Join-Path $projectRoot '.tools\android-sdk' }
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Program Files\Android\openjdk\jdk-21.0.8\bin\java.exe')) {
    $env:JAVA_HOME = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
}
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
$buildTools = Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'aapt.exe') } | Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android build-tools with aapt and apksigner are required.' }
$badging = & (Join-Path $buildTools.FullName 'aapt.exe') dump badging $ApkPath 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Could not read the APK package information.' }
$package = [regex]::Match(($badging -join "`n"), "package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
if (-not $package.Success -or $package.Groups[1].Value -ne 'io.github.biliaudioplayer') { throw 'The APK does not belong to BiliAudioPlayer.' }
$versionCode = [int]$package.Groups[2].Value
$versionName = $package.Groups[3].Value
if ($versionName -notmatch '^\d+\.\d+\.\d+$') { throw 'A release requires a stable x.y.z versionName.' }
$signature = & (Join-Path $buildTools.FullName 'apksigner.bat') verify --print-certs $ApkPath 2>&1
if ($LASTEXITCODE -ne 0 -or -not (($signature -join "`n") -match 'certificate SHA-256 digest: ([0-9a-fA-F]+)')) { throw 'APK signature verification failed.' }
$certificate = $Matches[1].ToLowerInvariant()
$debuggable = ($badging -join "`n") -match '(?m)^application-debuggable'
$variant = if ($debuggable) { 'debug' } else { 'release' }
$apkName = "BiliAudioPlayer-$versionName-$variant.apk"
$sha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$tag = "v$versionName"

function Invoke-RepositoryGit {
    param([string[]]$Arguments)
    $result = & git -C $projectRoot @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Git verification failed: $($Arguments[0])." }
    return $result
}
$branch = Invoke-RepositoryGit @('branch', '--show-current')
if ($branch -ne 'main') { throw 'Publish releases from main.' }
$dirty = Invoke-RepositoryGit @('status', '--porcelain', '--untracked-files=normal')
if ($dirty) { throw 'Commit and push all non-ignored changes before publishing. For a local build use -NoPublish.' }
$commit = Invoke-RepositoryGit @('rev-parse', 'HEAD')
$committedBuild = (Invoke-RepositoryGit @('show', 'HEAD:app/build.gradle.kts')) -join "`n"
if ($committedBuild -notmatch ('versionCode\s*=\s*' + $versionCode + '\b') -or $committedBuild -notmatch ('versionName\s*=\s*"' + [regex]::Escape($versionName) + '"')) {
    throw 'APK version does not match the committed source.'
}

$headers = $null
$token = $null
$credentialLines = $null
$credentialValues = @{}
function Invoke-GitHub {
    param([string]$Path, [string]$Method = 'Get', $Body = $null, [switch]$AllowMissing)
    try {
        $parameters = @{ Uri = "https://api.github.com/repos/$Repository/$Path"; Headers = $headers; Method = $Method }
        if ($null -ne $Body) { $parameters.ContentType = 'application/json'; $parameters.Body = ($Body | ConvertTo-Json -Depth 8 -Compress) }
        return Invoke-RestMethod @parameters
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($AllowMissing -and $status -eq 404) { return $null }
        throw "GitHub request failed ($Method $Path, HTTP $status). Check network and repository credentials."
    }
}

try {
    $token = $env:GH_TOKEN
    if (-not $token) { $token = $env:GITHUB_TOKEN }
    if (-not $token) {
        $credentialLines = "protocol=https`nhost=github.com`nusername=Potat0Servers`n`n" | git -C $projectRoot -c credential.interactive=never credential fill
        if ($LASTEXITCODE -ne 0) { throw 'No GitHub credential available. Sign in with Git Credential Manager or set GH_TOKEN.' }
        foreach ($line in $credentialLines) {
            $pair = $line -split '=', 2
            if ($pair.Count -eq 2) { $credentialValues[$pair[0]] = $pair[1] }
        }
        $token = $credentialValues['password']
    }
    if (-not $token) { throw 'No GitHub access token is available.' }
    $headers = @{ Authorization = "Bearer $token"; Accept = 'application/vnd.github+json'; 'X-GitHub-Api-Version' = '2022-11-28'; 'User-Agent' = 'BiliAudioPlayer-release' }
    $repositoryInfo = Invoke-GitHub ''
    if ($repositoryInfo.private) { throw 'Make the repository public before publishing updates for anonymous clients.' }
    $remoteMain = Invoke-GitHub 'git/ref/heads/main'
    if ($remoteMain.object.sha -ne $commit) { throw 'Push the current main commit before publishing.' }
    $remoteTag = Invoke-GitHub "git/ref/tags/$tag" -AllowMissing
    if ($remoteTag) {
        $tagObject = $remoteTag.object
        while ($tagObject.type -eq 'tag') { $tagObject = (Invoke-GitHub "git/tags/$($tagObject.sha)").object }
        if ($tagObject.sha -ne $commit) { throw 'The existing version tag points to another commit; increment the version.' }
    }

    # All generated attachment staging files live under the ignored local tools directory.
    $staging = Join-Path $projectRoot ".tools\releases\$tag"
    New-Item -ItemType Directory -Path $staging -Force | Out-Null
    $stagedApk = Join-Path $staging $apkName
    if ($ApkPath -ne $stagedApk) { Copy-Item -LiteralPath $ApkPath -Destination $stagedApk -Force }
    $metadata = [ordered]@{ versionName = $versionName; versionCode = $versionCode; apkName = $apkName; sha256 = $sha256 }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $staging 'update.json'), ($metadata | ConvertTo-Json) + "`n", $utf8)
    $metadataHash = (Get-FileHash -LiteralPath (Join-Path $staging 'update.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText((Join-Path $staging 'SHA256SUMS.txt'), "$sha256  $apkName`n$metadataHash  update.json`n", $utf8)
    $attachments = @($stagedApk, (Join-Path $staging 'update.json'), (Join-Path $staging 'SHA256SUMS.txt'))
    $release = Invoke-GitHub "releases/tags/$tag" -AllowMissing
    if (-not $release) {
        # A draft may not be returned by the tag endpoint on all GitHub API versions.
        $releases = Invoke-GitHub 'releases?per_page=100'
        $release = @($releases | Where-Object { $_.tag_name -eq $tag }) | Select-Object -First 1
    }
    if ($release -and $release.prerelease) { throw 'An existing prerelease uses this version; increment the version.' }
    if ($release -and -not $remoteTag) {
        $releaseCommit = Invoke-GitHub ("commits/" + [uri]::EscapeDataString($release.target_commitish))
        if ($releaseCommit.sha -ne $commit) { throw 'The existing draft targets another source commit; increment the version.' }
    }
    if (-not $release) {
        $release = Invoke-GitHub 'releases' -Method Post -Body @{ tag_name = $tag; target_commitish = $commit; name = $tag; body = $Notes; draft = $true; prerelease = $false }
    }
    foreach ($attachment in $attachments) {
        $name = [System.IO.Path]::GetFileName($attachment)
        $hash = (Get-FileHash -LiteralPath $attachment -Algorithm SHA256).Hash.ToLowerInvariant()
        $existing = @($release.assets | Where-Object { $_.name -eq $name }) | Select-Object -First 1
        if ($existing) {
            $existingHash = $existing.digest -replace '^sha256:', ''
            if (-not $existingHash) {
                # Older assets lack a digest; compare the downloaded bytes without exposing credentials.
                $download = Join-Path $staging "existing-$name"
                $downloadHeaders = $headers.Clone()
                $downloadHeaders.Accept = 'application/octet-stream'
                try {
                    Invoke-WebRequest -Uri $existing.url -Headers $downloadHeaders -OutFile $download | Out-Null
                    $existingHash = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant()
                } catch { throw "Unable to verify existing release attachment $name." } finally { $downloadHeaders.Clear() }
            }
            if ($existingHash -ne $hash) { throw "Release attachment $name differs. Increment the version; published assets will not be replaced." }
            continue
        }
        if (-not $release.draft) { throw "Published release is missing $name; create a new version rather than modifying a published release." }
        $uploadUrl = ($release.upload_url -replace '\{.*$', '') + '?name=' + [uri]::EscapeDataString($name)
        $uploadUri = [uri]$uploadUrl
        if ($uploadUri.Scheme -ne 'https' -or $uploadUri.Host -ne 'uploads.github.com') { throw 'Unexpected GitHub upload host.' }
        $contentType = if ($name.EndsWith('.apk')) { 'application/vnd.android.package-archive' } elseif ($name.EndsWith('.json')) { 'application/json' } else { 'text/plain' }
        try {
            Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $headers -ContentType $contentType -InFile $attachment | Out-Null
        } catch { throw "Uploading $name failed; the draft can be retried safely." }
    }
    if ($release.draft) {
        $release = Invoke-GitHub "releases/$($release.id)" -Method Patch -Body @{ draft = $false; prerelease = $false; make_latest = 'true' }
    }
    $published = Invoke-GitHub "releases/$($release.id)"
    if ($published.draft -or $published.prerelease -or @($published.assets | Where-Object { $_.name -in @($apkName, 'update.json', 'SHA256SUMS.txt') }).Count -ne 3) { throw 'Release verification failed.' }
    [pscustomobject]@{ version = $versionName; versionCode = $versionCode; commit = $commit; certificateSha256 = $certificate; sha256 = $sha256; url = $published.html_url } | ConvertTo-Json
} finally {
    if ($headers) { $headers.Clear() }
    $credentialValues.Clear()
    $credentialLines = $null
    $token = $null
}

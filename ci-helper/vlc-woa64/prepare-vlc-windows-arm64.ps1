param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,
    [string]$CacheDirectory = (Join-Path ([System.IO.Path]::GetTempPath()) "ani-vlc-windows-arm64")
)

$ErrorActionPreference = "Stop"

# VideoLAN did not publish a Windows ARM64 build for VLC 3.0.20, which is the
# version currently vendored for windows-x64 app resources. Use the first
# official 3.0.x Windows ARM64 runtime that is available instead.
$version = "3.0.23"
$url = "https://download.videolan.org/pub/videolan/vlc/$version/winarm64/vlc-$version-winarm64.zip"
$expectedSha256 = "9c0917dc521ffc8ce30e70bca7f6c9dc8fec80909d763e75cd976351dee8db0b"

$outputPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDirectory))
if ($outputPath -notmatch "[\\/]appResources[\\/]windows-arm64[\\/]lib$") {
    throw "Refusing to replace unexpected VLC output directory: $outputPath"
}

$archive = Join-Path $CacheDirectory "vlc-$version-winarm64.zip"
$extractDir = Join-Path $CacheDirectory "extract"
$vlcRoot = Join-Path $extractDir "vlc-$version"

New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null
if (!(Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -Uri $url -OutFile $archive
}

$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "Unexpected VLC archive SHA256: $actualSha256"
}

Remove-Item -LiteralPath $extractDir -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive -LiteralPath $archive -DestinationPath $extractDir -Force

foreach ($required in @("libvlc.dll", "libvlccore.dll", "plugins")) {
    if (!(Test-Path -LiteralPath (Join-Path $vlcRoot $required))) {
        throw "VLC archive is missing $required"
    }
}

Remove-Item -LiteralPath $outputPath -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
Copy-Item -Path (Join-Path $vlcRoot "*") -Destination $outputPath -Recurse -Force

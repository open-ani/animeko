<#
  Copyright (C) 2024-2026 OpenAni and contributors.

  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
  Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.

  https://github.com/open-ani/ani/blob/main/LICENSE
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$PackageDirectory
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Invoke-WithRetry {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Script,
        [Parameter(Mandatory = $true)]
        [string]$Description,
        [int]$Attempts = 5
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            return & $Script
        }
        catch {
            if ($attempt -eq $Attempts) {
                throw "Failed to $Description after $Attempts attempts. Last error: $($_.Exception.Message)"
            }

            $delaySeconds = [int][Math]::Min(60, [Math]::Pow(2, $attempt) * 5)
            Write-Warning "$Description failed on attempt $attempt/$Attempts. Retrying in $delaySeconds seconds. $($_.Exception.Message)"
            Start-Sleep -Seconds $delaySeconds
        }
    }
}

function Assert-Sha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Expected
    )

    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
    if ($actual -ne $Expected) {
        throw "SHA256 mismatch for $Path. Expected $Expected, got $actual."
    }
}

$sqliteJars = @(Get-ChildItem -LiteralPath $PackageDirectory -Recurse -Filter "sqlite-bundled-jvm-*.jar")
if ($sqliteJars.Count -ne 1) {
    throw "Expected exactly one sqlite-bundled-jvm jar under $PackageDirectory, found $($sqliteJars.Count)."
}
$sqliteJar = $sqliteJars[0]

$outputDir = "build/sqlite-woa64"
$workDir = Join-Path $outputDir "work"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$sqliteVersion = "3.50.1"
$sqliteZipVersion = "3500100"
$sqliteYear = "2025"
$sqliteZipHash = "41716B44AC8777188C4C3F1F370F01C9CB9E3B6428EB5C981D086C35DE2D9D3F"
$sqliteZip = Join-Path $workDir "sqlite-amalgamation-$sqliteZipVersion.zip"
$sqliteDir = Join-Path $workDir "sqlite-amalgamation-$sqliteZipVersion"
$sqliteC = Join-Path $sqliteDir "sqlite3.c"

$androidxSqlite262Commit = "fe30df161d480829efb21f37ff67a9f8cac9c620"
$androidxBindingHash = "F9F4747111A6635DFFD5991126247CA0F2F6C851DE1EAF4ADD3866A0518AC2E0"
$binding = Join-Path $workDir "sqlite_bindings.cpp"

# AndroidX sqlite-bundled-jvm currently publishes Windows x64, Linux, and macOS
# native binaries, but not Windows ARM64. Remove this workaround once AndroidX
# ships natives/windows_arm64/sqliteJni.dll in sqlite-bundled-jvm.
if (!(Test-Path $sqliteZip)) {
    Invoke-WithRetry `
        -Description "download SQLite amalgamation" `
        -Script {
            Invoke-WebRequest `
                -UseBasicParsing `
                -Uri "https://www.sqlite.org/$sqliteYear/sqlite-amalgamation-$sqliteZipVersion.zip" `
                -OutFile $sqliteZip
        }
}
Assert-Sha256 -Path $sqliteZip -Expected $sqliteZipHash

if (!(Test-Path $sqliteC)) {
    Expand-Archive -Path $sqliteZip -DestinationPath $workDir -Force
}

$sqliteHeader = Get-Content (Join-Path $sqliteDir "sqlite3.h") -Raw
if ($sqliteHeader -notmatch "#define\s+SQLITE_VERSION\s+`"$([regex]::Escape($sqliteVersion))`"") {
    throw "Downloaded SQLite amalgamation does not match $sqliteVersion."
}

if (!(Test-Path $binding)) {
    $encodedBinding = Invoke-WithRetry `
        -Description "download AndroidX SQLite JNI binding" `
        -Script {
            (Invoke-WebRequest `
                    -UseBasicParsing `
                    -Uri "https://android.googlesource.com/platform/frameworks/support/+/$androidxSqlite262Commit/sqlite/sqlite-bundled/src/jvmAndroidMain/jni/sqlite_bindings.cpp?format=TEXT").Content.Trim()
        }
    [System.IO.File]::WriteAllBytes($binding, [System.Convert]::FromBase64String($encodedBinding))
}
Assert-Sha256 -Path $binding -Expected $androidxBindingHash

$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$vsInstall = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.ARM64 -property installationPath
if ([string]::IsNullOrWhiteSpace($vsInstall)) {
    throw "Visual Studio ARM64 C++ tools were not found."
}

$vcvars = Join-Path $vsInstall "VC\Auxiliary\Build\vcvarsall.bat"
$javaInclude = Join-Path $env:JAVA_HOME "include"
$javaWinInclude = Join-Path $javaInclude "win32"
$sqliteObj = Join-Path $outputDir "sqlite3.obj"
$bindingObj = Join-Path $outputDir "sqlite_bindings.obj"
$sqliteDll = Join-Path $outputDir "sqliteJni.dll"

$sqliteDefines = @(
    "/DHAVE_USLEEP=1",
    "/DSQLITE_DEFAULT_AUTOVACUUM=1",
    "/DSQLITE_DEFAULT_MEMSTATUS=0",
    "/DSQLITE_DEFAULT_WAL_SYNCHRONOUS=1",
    "/DSQLITE_ENABLE_COLUMN_METADATA",
    "/DSQLITE_ENABLE_FTS3",
    "/DSQLITE_ENABLE_FTS3_PARENTHESIS",
    "/DSQLITE_ENABLE_FTS4",
    "/DSQLITE_ENABLE_FTS5",
    "/DSQLITE_ENABLE_JSON1",
    "/DSQLITE_ENABLE_MATH_FUNCTIONS",
    "/DSQLITE_ENABLE_NORMALIZE",
    "/DSQLITE_ENABLE_RTREE",
    "/DSQLITE_ENABLE_STAT4",
    "/DSQLITE_HAVE_ISNAN",
    "/DSQLITE_OMIT_BUILTIN_TEST",
    "/DSQLITE_OMIT_DEPRECATED",
    "/DSQLITE_OMIT_PROGRESS_CALLBACK",
    "/DSQLITE_OMIT_SHARED_CACHE",
    "/DSQLITE_SECURE_DELETE",
    "/DSQLITE_TEMP_STORE=3",
    "/DSQLITE_THREADSAFE=2"
) -join " "

$compile = @(
    "`"$vcvars`" arm64",
    "cl /nologo /O2 /MT /utf-8 $sqliteDefines /I`"$sqliteDir`" /Fo`"$sqliteObj`" /c `"$sqliteC`"",
    "cl /nologo /O2 /MT /EHsc /std:c++17 /utf-8 $sqliteDefines /I`"$sqliteDir`" /I`"$javaInclude`" /I`"$javaWinInclude`" /Fo`"$bindingObj`" /c `"$binding`"",
    "link /nologo /DLL /OUT:`"$sqliteDll`" /IMPLIB:`"$outputDir\sqliteJni.lib`" `"$sqliteObj`" `"$bindingObj`""
) -join " && "

cmd.exe /d /s /c $compile
if ($LASTEXITCODE -ne 0) {
    throw "Failed to build Windows ARM64 AndroidX SQLite JNI runtime."
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$entryName = "natives/windows_arm64/sqliteJni.dll"
$zip = [System.IO.Compression.ZipFile]::Open($sqliteJar.FullName, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $existing = $zip.GetEntry($entryName)
    if ($null -ne $existing) {
        $existing.Delete()
    }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip,
        (Resolve-Path -LiteralPath $sqliteDll).Path,
        $entryName,
        [System.IO.Compression.CompressionLevel]::Optimal
    ) | Out-Null
}
finally {
    $zip.Dispose()
}

Write-Host "Patched $($sqliteJar.FullName) with $entryName"

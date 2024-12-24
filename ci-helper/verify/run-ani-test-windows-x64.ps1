<#
.SYNOPSIS
  Unzip a file that contains Ani.exe somewhere inside, 
  set an environment variable, and run Ani.exe for tests.

.DESCRIPTION
  1. Cleans up any existing extracted_zip folder.
  2. Unzips the provided ZIP file into extracted_zip.
  3. Searches for Ani.exe within extracted_zip.
  4. Sets ANIMEKO_DESKTOP_TEST_TASK environment variable to the provided test string.
  5. Runs Ani.exe and checks exit code.

.PARAMETER ZipPath
  Path to the .zip file that contains Ani.exe.

.PARAMETER TestString
  String for the test, assigned to ANIMEKO_DESKTOP_TEST_TASK.

.EXAMPLE
  PS> .\run-ani-test.ps1 "C:\path\to\ani.zip" "TestName"
#>

param (
    [Parameter(Mandatory = $true)]
    [string]$ZipPath,

    [Parameter(Mandatory = $true)]
    [string]$TestString
)

# --- Step 1: Verify that the file exists ---
Write-Host "Step 1: Checking ZIP file path..."
if (!(Test-Path $ZipPath)) {
    Write-Error "Error: File not found at $ZipPath"
    exit 1
}

# --- Step 2: Cleanup old extraction directory ---
Write-Host "Step 2: Cleaning up old extraction folders..."
if (Test-Path "extracted_zip") {
    Remove-Item -Path "extracted_zip" -Recurse -Force
}

# --- Step 3: Unzip to extracted_zip ---
Write-Host "Step 3: Extracting ZIP to 'extracted_zip'..."
# PowerShell 5+ has Expand-Archive natively; adjust if needed.
# If you prefer 7z, you'd do: & 7z x $ZipPath -oextracted_zip
Expand-Archive -LiteralPath $ZipPath -DestinationPath "extracted_zip" -Force

# --- Step 4: Locate Ani.exe ---
Write-Host "Step 4: Searching for 'Ani.exe' in 'extracted_zip'..."
$aniExe = Get-ChildItem -Path "extracted_zip" -Filter "Ani.exe" -Recurse -Force | Select-Object -First 1

if (-not $aniExe) {
    Write-Error "Error: 'Ani.exe' not found anywhere in the extracted folders!"
    exit 1
}

Write-Host "Found Ani.exe at: $($aniExe.FullName)"

# --- Step 5: Set environment variable and run Ani.exe ---
Write-Host "Step 5: Setting environment variable ANIMEKO_DESKTOP_TEST_TASK to '$TestString'..."
$env:ANIMEKO_DESKTOP_TEST_TASK = $TestString

Write-Host "Running Ani.exe..."
# Start-Process to run Ani.exe, wait for it to finish, capture exit code
$process = Start-Process -FilePath $aniExe.FullName -Wait -PassThru

# --- Step 6: Check exit code ---
if ($process.ExitCode -ne 0) {
    Write-Error "Error: Ani.exe exited with code $($process.ExitCode)."
    exit $process.ExitCode
}

Write-Host "Success: Ani.exe exited with code 0."
exit 0
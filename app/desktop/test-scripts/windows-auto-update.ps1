# Set environment variables
$env:ANIMEKO_DESKTOP_TEST_TASK = "download-update-and-install"
$env:ANIMEKO_DESKTOP_TEST_ARGC = "1"
$env:ANIMEKO_DESKTOP_TEST_ARGV_0 = "https://d.myani.org/v4.0.0-release-checksum-2/ani-4.0.0-release-checksum-2-windows-x86_64.zip"

# Run the executable

Write-Host "Running Ani.exe..."
$process = Start-Process -FilePath ".\Ani.exe" -Wait -PassThru

if ($process.ExitCode -ne 0)
{
    Write-Error "Ani.exe exited with code $( $process.ExitCode )."
    exit $process.ExitCode
}

Write-Host "Success: Ani.exe exited with code 0."
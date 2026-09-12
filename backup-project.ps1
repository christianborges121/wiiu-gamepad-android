[CmdletBinding()]
param(
    [switch]$IncludeReferences,
    [switch]$IncludeCemuBin
)

$ErrorActionPreference = 'Stop'

$source = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$projectName = Split-Path $source -Leaf
$parent = Split-Path $source -Parent
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$destination = Join-Path $parent "$projectName-backup-$timestamp"

if (Test-Path $destination) {
    throw "Backup destination already exists: $destination"
}

New-Item -ItemType Directory -Path $destination | Out-Null

$excludedDirectories = @(
    (Join-Path $source 'build'),
    (Join-Path $source 'android-gamepad-app\.gradle'),
    (Join-Path $source 'android-gamepad-app\build'),
    (Join-Path $source 'android-gamepad-app\.kotlin'),
    (Join-Path $source 'Cemu\build'),
    (Join-Path $source 'Cemu\.vs'),
    (Join-Path $source 'Cemu\.idea'),
    (Join-Path $source 'Cemu\dependencies\vcpkg_installed'),
    (Join-Path $source 'Cemu\dependencies\vcpkg\downloads'),
    (Join-Path $source 'Cemu\dependencies\vcpkg\buildtrees'),
    (Join-Path $source 'Cemu\dependencies\vcpkg\packages'),
    (Join-Path $source 'vanilla\android\.gradle')
)

$excludedDirectories += Get-ChildItem -Path (Join-Path $source 'Cemu') -Directory -Filter 'cmake-build-*' -Force -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty FullName

if (-not $IncludeReferences) {
    $excludedDirectories += Join-Path $source 'references'
}

if (-not $IncludeCemuBin) {
    $excludedDirectories += Join-Path $source 'Cemu\bin'
}

# Exclude any generated vanilla/buildroot directories that exist below the source tree.
$excludedDirectories += Get-ChildItem -Path (Join-Path $source 'vanilla\buildroot') -Directory -Recurse -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -in @('build', 'output', 'staging', 'target') } |
    Select-Object -ExpandProperty FullName

$excludedFiles = @(
    (Join-Path $source 'android-gamepad-app\local.properties')
)

$robocopyArguments = @(
    $source,
    $destination,
    '/E',
    '/COPY:DAT',
    '/DCOPY:DAT',
    '/R:2',
    '/W:2',
    '/XJ',
    '/NFL',
    '/NDL',
    '/NP'
)

if ($excludedDirectories.Count -gt 0) {
    $robocopyArguments += '/XD'
    $robocopyArguments += $excludedDirectories
}

if ($excludedFiles.Count -gt 0) {
    $robocopyArguments += '/XF'
    $robocopyArguments += $excludedFiles
}

Write-Host "Backing up: $source"
Write-Host "Destination: $destination"
Write-Host "Excluded directories: $($excludedDirectories.Count)"

& robocopy @robocopyArguments
$robocopyExitCode = $LASTEXITCODE

if ($robocopyExitCode -gt 7) {
    throw "Robocopy failed with exit code $robocopyExitCode"
}

$manifest = @(
    "Source: $source",
    "Created: $(Get-Date -Format o)",
    "Robocopy exit code: $robocopyExitCode",
    "References included: $IncludeReferences",
    "Cemu bin included: $IncludeCemuBin",
    '',
    'Excluded directories:',
    ($excludedDirectories | ForEach-Object { "- $_" }),
    '',
    'Excluded files:',
    ($excludedFiles | ForEach-Object { "- $_" })
)

Set-Content -Path (Join-Path $destination 'backup-manifest.txt') -Value $manifest -Encoding UTF8

Write-Host "Backup complete: $destination"
exit 0

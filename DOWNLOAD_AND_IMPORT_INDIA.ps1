# SafeRoad - Reliable India OSM PBF Download + Local Import
# Run from the project root.
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataDir = Join-Path $projectRoot "data"
$pbfPath = Join-Path $dataDir "india-latest.osm.pbf"
$tempPath = Join-Path $dataDir "india-latest.osm.pbf.part"
$importScript = Join-Path $projectRoot "IMPORT_INDIA_PBF.ps1"

$url = "https://download.geofabrik.de/asia/india-latest.osm.pbf"

Write-Host "====================================================="
Write-Host "SafeRoad - Reliable India OSM PBF Download + Import"
Write-Host "====================================================="
Write-Host ""
Write-Host "Source: Geofabrik / OpenStreetMap"
Write-Host "India PBF is about 1.6 GB, so the download can take time."
Write-Host ""

New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

# Remove only the known corrupt/incomplete target. Keep a partial file so BITS can resume.
if (Test-Path $pbfPath) {
    $size = (Get-Item $pbfPath).Length
    if ($size -lt 100MB) {
        Write-Host "Existing PBF is only $size bytes. Removing incomplete file..."
        Remove-Item $pbfPath -Force
    } else {
        Write-Host "Existing PBF found: $([math]::Round($size/1GB,2)) GB"
        Write-Host "Testing PBF with pyosmium..."
        & python -c "import osmium; osmium.io.Reader(r'$pbfPath').header"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "PBF appears readable. Skipping download."
            & powershell -NoProfile -ExecutionPolicy Bypass -File $importScript
            exit $LASTEXITCODE
        } else {
            Write-Host "Existing PBF is invalid/incomplete. Re-downloading..."
            Remove-Item $pbfPath -Force
        }
    }
}

# Use BITS when available because it handles large downloads and interruptions better.
if (Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue) {
    Write-Host "Downloading with Windows BITS..."
    if (Test-Path $tempPath) {
        Remove-Item $tempPath -Force
    }
    Start-BitsTransfer -Source $url -Destination $tempPath -DisplayName "SafeRoad India OSM PBF"
} else {
    Write-Host "BITS unavailable. Using .NET HttpClient..."
    if (Test-Path $tempPath) {
        Remove-Item $tempPath -Force
    }
    $handler = New-Object System.Net.Http.HttpClientHandler
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromHours(6)
    try {
        $response = $client.GetAsync($url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
        $response.EnsureSuccessStatusCode()
        $stream = $response.Content.ReadAsStreamAsync().Result
        $file = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::Create)
        try {
            $stream.CopyTo($file)
        } finally {
            $file.Dispose()
            $stream.Dispose()
        }
    } finally {
        $client.Dispose()
    }
}

if (!(Test-Path $tempPath)) {
    throw "Download failed: temporary PBF was not created."
}

$downloadSize = (Get-Item $tempPath).Length
Write-Host "Downloaded size: $([math]::Round($downloadSize/1GB,2)) GB"

# India PBF is currently around 1.6 GB. Refuse obviously truncated files.
if ($downloadSize -lt 1GB) {
    throw "Downloaded file is too small ($downloadSize bytes). It is likely incomplete."
}

Write-Host "Validating PBF with pyosmium..."
& python -c "import osmium; osmium.io.Reader(r'$tempPath').header"
if ($LASTEXITCODE -ne 0) {
    throw "PBF validation failed. The download is incomplete/corrupt."
}

Move-Item -Force $tempPath $pbfPath
Write-Host "PBF validation successful."
Write-Host "Saved: $pbfPath"
Write-Host ""
Write-Host "Starting local database import..."
& powershell -NoProfile -ExecutionPolicy Bypass -File $importScript
if ($LASTEXITCODE -ne 0) {
    throw "Local PBF import failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "====================================================="
Write-Host "DONE - India PBF downloaded, validated and imported."
Write-Host "====================================================="

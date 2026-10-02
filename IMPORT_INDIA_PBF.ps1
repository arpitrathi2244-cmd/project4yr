param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$PbfPath = "$PSScriptRoot\data\india-latest.osm.pbf",
    [string]$WorkDir = "$PSScriptRoot\data\india-pbf-import",
    [int]$BatchSize = 500
)

$ErrorActionPreference = "Stop"

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "SafeRoad - Fast India OSM PBF Import" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This imports OSM data locally. It does NOT use Overpass." -ForegroundColor Green
Write-Host ""

# Python is used because pyosmium has Windows wheels and can stream PBF files.
# Official pyosmium/osmium docs: https://docs.osmcode.org/pyosmium/latest/
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    throw "Python was not found. Install Python 3.10+ and make sure 'python' works in PowerShell."
}

try {
    python -c "import osmium; print('pyosmium OK')"
}
catch {
    Write-Host "Installing pyosmium..." -ForegroundColor Yellow
    python -m pip install --upgrade osmium
}

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $PbfPath -Parent) | Out-Null

if (-not (Test-Path $PbfPath)) {
    $url = "https://download.geofabrik.de/asia/india-latest.osm.pbf"
    Write-Host "Downloading current India OSM extract (~1.6 GB)..." -ForegroundColor Yellow
    Write-Host $url -ForegroundColor DarkGray
    Invoke-WebRequest -Uri $url -OutFile $PbfPath -UseBasicParsing
}

if (-not (Test-Path $PbfPath)) {
    throw "India PBF file was not created: $PbfPath"
}

Write-Host "PBF: $PbfPath" -ForegroundColor Green
Write-Host "Starting local import..." -ForegroundColor Green

python "$PSScriptRoot\import_india_pbf.py" `
    --pbf "$PbfPath" `
    --base-url "$BaseUrl" `
    --batch-size $BatchSize `
    --work-dir "$WorkDir"

if ($LASTEXITCODE -ne 0) {
    throw "Local PBF import failed with exit code $LASTEXITCODE"
}

Write-Host "" 
Write-Host "India PBF import finished." -ForegroundColor Cyan
Write-Host "The old IMPORT_INDIA_GRID.ps1 is no longer needed for this preload." -ForegroundColor Yellow

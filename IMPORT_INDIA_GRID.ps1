param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Step = 5,
    [int]$DelaySeconds = 3
)

$types = @("SCHOOL", "HILL_AREA", "WILDLIFE")
$southStart = 6.0
$northEnd = 38.0
$westStart = 68.0
$eastEnd = 98.0

Write-Host "SafeRoad India OSM grid sync" -ForegroundColor Cyan
Write-Host "This uses small regional Overpass requests; do not close the terminal." -ForegroundColor Yellow

foreach ($type in $types) {
    Write-Host "`n=== $type ===" -ForegroundColor Green

    for ($south = $southStart; $south -lt $northEnd; $south += $Step) {
        $north = [Math]::Min($south + $Step, $northEnd)

        for ($west = $westStart; $west -lt $eastEnd; $west += $Step) {
            $east = [Math]::Min($west + $Step, $eastEnd)

            $uri = "$BaseUrl/api/safety-zones/import/bbox?south=$south&west=$west&north=$north&east=$east&zoneType=$type"
            Write-Host ("{0}: lat {1}-{2}, lon {3}-{4}" -f $type,$south,$north,$west,$east)

            try {
                $result = Invoke-RestMethod -UseBasicParsing -Method POST -Uri $uri -TimeoutSec 240
                Write-Host ("  saved/updated response items: {0}" -f @($result).Count) -ForegroundColor DarkGreen
            }
            catch {
                Write-Warning ("  chunk failed: {0}" -f $_.Exception.Message)
            }

            Start-Sleep -Seconds $DelaySeconds
        }
    }
}

Write-Host "`nIndia grid sync finished. Refresh the dashboard map." -ForegroundColor Cyan

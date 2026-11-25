param([switch]$Force)

$ErrorActionPreference = "Stop"
$target = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"

if ((Test-Path $target) -and -not $Force) {
    Write-Host ".env already exists. Run with -Force to replace it."
    exit 0
}

function New-Secret {
    $bytes = New-Object byte[] 24
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($bytes)
    $generator.Dispose()
    return [Convert]::ToBase64String($bytes).Replace("+", "A").Replace("/", "B").Replace("=", "")
}

$names = @("POSTGRES_PASSWORD", "ORDER_DB_PASSWORD", "WAREHOUSE_DB_PASSWORD", "DELIVERY_DB_PASSWORD", "TRACKING_DB_PASSWORD", "GATEWAY_API_KEYS")
$lines = foreach ($name in $names) { "$name=$(New-Secret)" }
[System.IO.File]::WriteAllText($target, (($lines -join "`n") + "`n"))

Write-Host "Created $target with new random secrets. The API key for requests is GATEWAY_API_KEYS in that file."

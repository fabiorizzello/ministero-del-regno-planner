param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$SchemasRoot = "release/schemas",

    [string]$OutputDir = "build/release"
)

$ErrorActionPreference = "Stop"

$sourceDir = Join-Path $SchemasRoot $Version
if (-not (Test-Path $sourceDir)) {
    throw "Cartella schemi non trovata: $sourceDir"
}

$targetDir = Join-Path $OutputDir $Version
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

$zipPath = Join-Path $targetDir ("schemi-{0}.zip" -f $Version)
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

$entries = Get-ChildItem -Path $sourceDir -Force
if ($entries.Count -eq 0) {
    throw "La cartella schemi e' vuota: $sourceDir"
}

Compress-Archive -Path (Join-Path $sourceDir "*") -DestinationPath $zipPath -CompressionLevel Optimal
Write-Host "Pacchetto schemi creato: $zipPath"

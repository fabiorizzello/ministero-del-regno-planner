param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [string]$MsixPath,

    [Parameter(Mandatory = $true)]
    [string]$AppInstallerPath,

    [string]$SchemaZipPath,

    [string]$OutputDir = "build/release"
)

$ErrorActionPreference = "Stop"

function Add-Asset {
    param(
        [string]$SourcePath,
        [string]$TargetDir,
        [ref]$ManifestFiles
    )

    if (-not (Test-Path $SourcePath)) {
        throw "Asset non trovato: $SourcePath"
    }

    $assetName = [System.IO.Path]::GetFileName($SourcePath)
    $targetPath = Join-Path $TargetDir $assetName
    Copy-Item -Path $SourcePath -Destination $targetPath -Force

    $hash = Get-FileHash -Path $targetPath -Algorithm SHA256
    $ManifestFiles.Value += @{
        name = $assetName
        sha256 = $hash.Hash.ToLowerInvariant()
        sizeBytes = (Get-Item $targetPath).Length
    }
}

$targetDir = Join-Path $OutputDir $Version
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

$files = @()
Add-Asset -SourcePath $MsixPath -TargetDir $targetDir -ManifestFiles ([ref]$files)
Add-Asset -SourcePath $AppInstallerPath -TargetDir $targetDir -ManifestFiles ([ref]$files)

if (-not [string]::IsNullOrWhiteSpace($SchemaZipPath)) {
    Add-Asset -SourcePath $SchemaZipPath -TargetDir $targetDir -ManifestFiles ([ref]$files)
}

$manifest = @{
    version = $Version
    createdAtUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    files = $files
}

$manifestPath = Join-Path $targetDir "release-manifest.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding UTF8

Write-Host "Bundle release creato: $targetDir"
Write-Host "Manifest: $manifestPath"

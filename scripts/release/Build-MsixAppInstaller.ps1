param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [string]$PackageIdentityName,

    [Parameter(Mandatory = $true)]
    [string]$Publisher,

    [Parameter(Mandatory = $true)]
    [string]$PackageUriBase,

    [Parameter(Mandatory = $true)]
    [string]$AppInstallerUri,

    [string]$DisplayName = "Efficaci Nel Ministero",

    [string]$PublisherDisplayName = "Efficaci Nel Ministero",

    [string]$ApplicationId = "MainApp",

    [string]$AppExecutable = "org.example.project.exe",

    [string]$Architecture = "x64",

    [string]$AppImageDir = "composeApp/build/compose/binaries/main/app",

    [string]$OutputDir = "build/msix",

    [switch]$RunGradle,

    [switch]$SkipSign,

    [string]$CertPath,

    [string]$CertPassword
)

$ErrorActionPreference = "Stop"

function Convert-ToMsixVersion {
    param([string]$InputVersion)

    $parts = $InputVersion.Split(".")
    if ($parts.Count -lt 2 -or $parts.Count -gt 4) {
        throw "Versione non valida: $InputVersion. Usa formato tipo 1.2.3 o 1.2.3.4"
    }

    foreach ($part in $parts) {
        if ($part -notmatch "^\d+$") {
            throw "Versione non valida: ogni parte deve essere numerica ($InputVersion)"
        }
    }

    $normalized = @($parts)
    while ($normalized.Count -lt 4) {
        $normalized += "0"
    }

    foreach ($part in $normalized) {
        if ([int]$part -gt 65535) {
            throw "Versione non valida per MSIX: ogni parte deve essere <= 65535 ($InputVersion)"
        }
    }

    return ($normalized -join ".")
}

function Require-Tool {
    param([string]$ToolName)
    $tool = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($null -eq $tool) {
        throw "Tool non trovato: $ToolName. Installa Windows SDK e assicurati che sia nel PATH."
    }
    return $tool.Source
}

if ($RunGradle) {
    & .\gradlew.bat :composeApp:createDistributable
    if ($LASTEXITCODE -ne 0) {
        throw "createDistributable fallito"
    }
}

$msixVersion = Convert-ToMsixVersion -InputVersion $Version
$makeAppx = Require-Tool -ToolName "makeappx.exe"

if (-not $SkipSign) {
    $null = Require-Tool -ToolName "signtool.exe"
    if ([string]::IsNullOrWhiteSpace($CertPath) -or [string]::IsNullOrWhiteSpace($CertPassword)) {
        throw "Per firmare MSIX devi passare CertPath e CertPassword, oppure usare -SkipSign."
    }
}

if (-not (Test-Path $AppImageDir)) {
    throw "Cartella app image non trovata: $AppImageDir"
}

$exeFile = Get-ChildItem -Path $AppImageDir -Recurse -File | Where-Object { $_.Name -ieq $AppExecutable } | Select-Object -First 1
if ($null -eq $exeFile) {
    throw "Eseguibile non trovato: $AppExecutable sotto $AppImageDir"
}

$resolvedAppImageDir = (Resolve-Path $AppImageDir).Path
$relativeExe = $exeFile.FullName.Substring($resolvedAppImageDir.Length).TrimStart("\", "/")
$manifestExecutable = ("app\" + $relativeExe).Replace("/", "\")

$versionDir = Join-Path $OutputDir $msixVersion
$stageDir = Join-Path $versionDir "staging"
$stageAppDir = Join-Path $stageDir "app"

if (Test-Path $versionDir) {
    Remove-Item $versionDir -Recurse -Force
}
New-Item -ItemType Directory -Path $stageAppDir -Force | Out-Null

Copy-Item -Path (Join-Path $resolvedAppImageDir "*") -Destination $stageAppDir -Recurse -Force

$manifestTemplatePath = "scripts/release/templates/AppxManifest.xml.template"
if (-not (Test-Path $manifestTemplatePath)) {
    throw "Template non trovato: $manifestTemplatePath"
}

$manifestContent = Get-Content $manifestTemplatePath -Raw
$manifestContent = $manifestContent.Replace("__IDENTITY_NAME__", $PackageIdentityName)
$manifestContent = $manifestContent.Replace("__PUBLISHER__", $Publisher)
$manifestContent = $manifestContent.Replace("__VERSION__", $msixVersion)
$manifestContent = $manifestContent.Replace("__DISPLAY_NAME__", $DisplayName)
$manifestContent = $manifestContent.Replace("__PUBLISHER_DISPLAY_NAME__", $PublisherDisplayName)
$manifestContent = $manifestContent.Replace("__APPLICATION_ID__", $ApplicationId)
$manifestContent = $manifestContent.Replace("__EXECUTABLE__", $manifestExecutable)

$manifestPath = Join-Path $stageDir "AppxManifest.xml"
Set-Content -Path $manifestPath -Value $manifestContent -Encoding UTF8

$msixFileName = "{0}_{1}_{2}.msix" -f $PackageIdentityName, $msixVersion, $Architecture
$msixPath = Join-Path $versionDir $msixFileName

& $makeAppx pack /d $stageDir /p $msixPath /o
if ($LASTEXITCODE -ne 0) {
    throw "makeappx pack fallito"
}

if (-not $SkipSign) {
    & signtool.exe sign /fd SHA256 /f $CertPath /p $CertPassword $msixPath
    if ($LASTEXITCODE -ne 0) {
        throw "Firma MSIX fallita"
    }
}

$appInstallerTemplatePath = "scripts/release/templates/AppInstaller.xml.template"
if (-not (Test-Path $appInstallerTemplatePath)) {
    throw "Template non trovato: $appInstallerTemplatePath"
}

$mainPackageUri = "{0}/{1}" -f $PackageUriBase.TrimEnd("/"), $msixFileName

$appInstallerContent = Get-Content $appInstallerTemplatePath -Raw
$appInstallerContent = $appInstallerContent.Replace("__APPINSTALLER_URI__", $AppInstallerUri)
$appInstallerContent = $appInstallerContent.Replace("__APPINSTALLER_VERSION__", $msixVersion)
$appInstallerContent = $appInstallerContent.Replace("__PACKAGE_NAME__", $PackageIdentityName)
$appInstallerContent = $appInstallerContent.Replace("__PUBLISHER__", $Publisher)
$appInstallerContent = $appInstallerContent.Replace("__PACKAGE_VERSION__", $msixVersion)
$appInstallerContent = $appInstallerContent.Replace("__ARCH__", $Architecture)
$appInstallerContent = $appInstallerContent.Replace("__MAINPACKAGE_URI__", $mainPackageUri)

$appInstallerPath = Join-Path $versionDir ("{0}.appinstaller" -f $PackageIdentityName)
Set-Content -Path $appInstallerPath -Value $appInstallerContent -Encoding UTF8

Write-Host "MSIX creato: $msixPath"
Write-Host "AppInstaller creato: $appInstallerPath"
if ($SkipSign) {
    Write-Host "Attenzione: MSIX non firmato (-SkipSign)."
}

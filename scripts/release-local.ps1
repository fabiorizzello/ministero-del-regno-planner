<#
.SYNOPSIS
Builda i pacchetti desktop locali e, opzionalmente, pubblica la release su GitHub.

.DESCRIPTION
`-PublishRemote` orchestra il flusso release completo:
1. Verifica working tree pulito
2. Aggiorna `app.version` in gradle.properties (se necessario) e committa
3. Esegue test JVM (se `-RunTests`)
4. Builda il pacchetto MSI
5. Crea tag `v<version>` (skip se esiste)
6. Push tag su origin
7. Crea/aggiorna GitHub Release via `gh` e carica asset

Ogni step e' idempotente: se il flusso fallisce a meta', un re-run
riprende da dove si era fermato senza duplicare lavoro.

Requisiti: git, gh (GitHub CLI autenticato con `gh auth login`).

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\jbr-21"

Genera l'MSI locale senza pubblicare.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -JavaHome "C:\Users\fabio\.jdks\jbr-21"

Legge la versione corrente, propone il prossimo patch/minor/major e chiede quale usare.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\jbr-21" -RunTests -PublishRemote

Flusso completo: version bump, test, build, tag, push, release.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\jbr-21" -PublishRemote -IncludeExe -ReleaseNotesFile .\release-notes.md

Pubblica con MSI + EXE e note di release custom.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [ValidatePattern('^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$')]
    [string]$Version,

    [string]$JavaHome,

    [switch]$RunTests,

    [switch]$PublishRemote,

    [switch]$IncludeExe,

    [string]$ReleaseBody,

    [string]$ReleaseNotesFile,

    [switch]$OpenOutput
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── Helpers ──────────────────────────────────────────────────────────────────

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Detail {
    param([string]$Message)
    Write-Host "    $Message" -ForegroundColor DarkGray
}

function Resolve-RepoRoot {
    $scriptDir = Split-Path -Parent $PSCommandPath
    return (Resolve-Path (Join-Path $scriptDir '..')).Path
}

function Assert-Windows {
    $isWindows = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
        [System.Runtime.InteropServices.OSPlatform]::Windows
    )
    if (-not $isWindows) {
        throw "Questo script supporta solo Windows, perche' il packaging MSI richiede tool Windows."
    }
}

function Resolve-JavaHomeValue {
    param([string]$ExplicitJavaHome)

    if ($ExplicitJavaHome) {
        if (-not (Test-Path $ExplicitJavaHome)) {
            throw "JAVA_HOME esplicito non trovato: $ExplicitJavaHome"
        }
        return (Resolve-Path $ExplicitJavaHome).Path
    }

    if ($env:JAVA_HOME) {
        if (Test-Path $env:JAVA_HOME) {
            $resolvedEnvJavaHome = (Resolve-Path $env:JAVA_HOME).Path
            if (Test-IsJetBrainsRuntimePath -JavaHomePath $resolvedEnvJavaHome) {
                return $resolvedEnvJavaHome
            }

            Write-Warning "JAVA_HOME impostato ma non punta a JetBrains Runtime: $resolvedEnvJavaHome. Cerco o scarico una JBR 21 locale."
        } else {
            Write-Warning "JAVA_HOME impostato ma non valido: $env:JAVA_HOME. Provo con una JBR 21 rilevata localmente."
        }
    }

    $jdksRoot = Join-Path $env:USERPROFILE '.jdks'
    $installedJbr = Get-InstalledJetBrainsRuntime -JdksRoot $jdksRoot
    if ($installedJbr) {
        return $installedJbr
    }

    return Ensure-JetBrainsRuntimeInstalled -JdksRoot $jdksRoot
}

function Assert-Executable {
    param([string]$Path, [string]$Label)
    if (-not (Test-Path $Path)) {
        throw "$Label non trovato: $Path"
    }
}

function Assert-JavaMajorVersionAtLeast {
    param(
        [string]$JavaExePath,
        [int]$MinimumMajorVersion
    )

    $versionOutput = & $JavaExePath --version
    if ($LASTEXITCODE -ne 0) {
        throw "Impossibile leggere la versione Java da: $JavaExePath"
    }

    $firstLine = ($versionOutput | Select-Object -First 1)
    $versionText = [string]$firstLine
    $match = [System.Text.RegularExpressions.Regex]::Match($versionText, '^(?:openjdk|java)\s+(\d+)(?:\.(\d+))?.*$')
    if (-not $match.Success) {
        throw "Formato versione Java non riconosciuto: $versionText"
    }

    $majorVersion = [int]$match.Groups[1].Value
    if ($majorVersion -lt $MinimumMajorVersion) {
        throw "JAVA_HOME deve puntare ad almeno JDK $MinimumMajorVersion. Rilevato JDK $majorVersion in: $JavaExePath"
    }
}

function Test-IsJetBrainsRuntimePath {
    param([string]$JavaHomePath)

    if (-not $JavaHomePath -or -not (Test-Path $JavaHomePath)) {
        return $false
    }

    $releaseFilePath = Join-Path $JavaHomePath 'release'
    if (-not (Test-Path $releaseFilePath)) {
        return $false
    }

    $releaseContent = [System.IO.File]::ReadAllText($releaseFilePath)
    return (
        $releaseContent -match 'JETBRAINS' -or
        $releaseContent -match 'JBR' -or
        $releaseContent -match 'JetBrains Runtime'
    )
}

function Get-InstalledJetBrainsRuntime {
    param([string]$JdksRoot)

    if (-not (Test-Path $JdksRoot)) {
        return $null
    }

    $candidates = Get-ChildItem -Path $JdksRoot -Directory |
        Where-Object { $_.Name -match 'jbr|jetbrains' } |
        Sort-Object Name -Descending

    foreach ($candidate in $candidates) {
        if (Test-IsJetBrainsRuntimePath -JavaHomePath $candidate.FullName) {
            return $candidate.FullName
        }
    }

    return $null
}

function Get-LatestJetBrainsRuntimeDownloadInfo {
    $headers = @{
        'Accept' = 'application/vnd.github+json'
        'User-Agent' = 'ministero-del-regno-planner-release-script'
        'X-GitHub-Api-Version' = '2022-11-28'
    }

    $releases = Invoke-RestMethod `
        -Uri 'https://api.github.com/repos/JetBrains/JetBrainsRuntime/releases?per_page=30' `
        -Headers $headers

    $release = $releases |
        Where-Object { $_.tag_name -like 'jbr-release-21*' } |
        Select-Object -First 1

    if (-not $release) {
        throw "Nessuna release JBR 21 trovata su JetBrains/JetBrainsRuntime."
    }

    $releasePage = Invoke-WebRequest -Uri $release.html_url -Headers $headers -UseBasicParsing
    $matches = [System.Text.RegularExpressions.Regex]::Matches(
        $releasePage.Content,
        'https://cache-redirector\.jetbrains\.com/intellij-jbr/(?<name>jbr(?:sdk(?:_jcef)?|_jcef)-21[\w.\-]*-windows-x64[\w.\-]*\.zip)'
    )

    $assetNames = @()
    foreach ($match in $matches) {
        $assetNames += $match.Groups['name'].Value
    }
    $assetNames = $assetNames | Select-Object -Unique

    $assetName = $assetNames |
        Where-Object { $_ -match '^jbrsdk-21.*-windows-x64.*\.zip$' } |
        Select-Object -First 1

    if (-not $assetName) {
        $assetName = $assetNames |
            Where-Object { $_ -match '^jbrsdk_jcef-21.*-windows-x64.*\.zip$' } |
            Select-Object -First 1
    }

    if (-not $assetName) {
        $assetName = $assetNames |
            Where-Object { $_ -match '^jbr_jcef-21.*-windows-x64.*\.zip$' } |
            Select-Object -First 1
    }

    if (-not $assetName) {
        throw "Nessun asset JBR 21 Windows x64 trovato nella pagina release $($release.tag_name)."
    }

    return [pscustomobject]@{
        ReleaseTag = $release.tag_name
        AssetName = $assetName
        DownloadUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$assetName"
    }
}

function Ensure-JetBrainsRuntimeInstalled {
    param([string]$JdksRoot)

    $cacheRoot = Join-Path $JdksRoot '_cache'
    New-Item -ItemType Directory -Path $JdksRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null

    if ($WhatIfPreference) {
        throw "JetBrains Runtime 21 non trovata in locale. Esegui senza -WhatIf per scaricarla automaticamente oppure passa -JavaHome."
    }

    Write-Step "JetBrains Runtime 21 non trovata in locale, scarico una JBRSDK ufficiale"
    $downloadInfo = Get-LatestJetBrainsRuntimeDownloadInfo
    Write-Detail "Release: $($downloadInfo.ReleaseTag)"
    Write-Detail "Asset: $($downloadInfo.AssetName)"

    $assetBaseName = [System.IO.Path]::GetFileNameWithoutExtension($downloadInfo.AssetName)
    $archivePath = Join-Path $cacheRoot $downloadInfo.AssetName
    $extractRoot = Join-Path $cacheRoot "${assetBaseName}_extract"
    $targetDir = Join-Path $JdksRoot $assetBaseName

    if (Test-IsJetBrainsRuntimePath -JavaHomePath $targetDir) {
        return $targetDir
    }

    if (-not (Test-Path $archivePath)) {
        Write-Detail "Download: $archivePath"
        Invoke-WebRequest -Uri $downloadInfo.DownloadUrl -OutFile $archivePath -UseBasicParsing
    } else {
        Write-Detail "Archivio gia' presente in cache: $archivePath"
    }

    if (Test-Path $extractRoot) {
        Remove-Item -Path $extractRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null

    Write-Detail "Estrazione archivio"
    Expand-Archive -Path $archivePath -DestinationPath $extractRoot -Force

    $javaHomeCandidate = Get-ChildItem -Path $extractRoot -Directory -Recurse |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
        Sort-Object FullName |
        Select-Object -First 1

    if (-not $javaHomeCandidate) {
        throw "Impossibile trovare bin\\java.exe dentro l'archivio JBR estratto: $archivePath"
    }

    if (Test-Path $targetDir) {
        Remove-Item -Path $targetDir -Recurse -Force
    }

    Move-Item -Path $javaHomeCandidate.FullName -Destination $targetDir

    if (-not (Test-IsJetBrainsRuntimePath -JavaHomePath $targetDir)) {
        throw "Il runtime scaricato non risulta una JetBrains Runtime valida: $targetDir"
    }

    Write-Detail "JBR pronta in: $targetDir"
    return $targetDir
}

function Assert-JetBrainsRuntime {
    param([string]$JavaHomePath)

    if (-not (Test-IsJetBrainsRuntimePath -JavaHomePath $JavaHomePath)) {
        throw @"
JAVA_HOME deve puntare a JetBrains Runtime (JBR) 21 per generare un MSI avviabile.
Il runtime incluso nell'installer viene preso dal JDK usato per il packaging: con Temurin/OpenJDK
l'app installata parte ma fallisce all'avvio quando usa Jewel DecoratedWindow.

JAVA_HOME rilevato: $JavaHomePath
Suggerimento: usa una JBR 21, ad esempio sotto $env:USERPROFILE\.jdks\jbr-21*
"@
    }
}

function Invoke-External {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )

    Write-Host "$FilePath $($Arguments -join ' ')" -ForegroundColor DarkGray
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Comando fallito con exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

function Get-NumericPackageVersion {
    param([string]$VersionText)

    return ($VersionText -replace '-.*$', '')
}

function Clear-Directory {
    param([string]$Path)

    if (Test-Path $Path) {
        Remove-Item -Path $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
}

function Get-ReleasePackagingLayout {
    param([string]$RepoRoot)

    $composeBuildRoot = Join-Path $RepoRoot 'composeApp\build'
    return [pscustomobject]@{
        InputDir = Join-Path $composeBuildRoot 'release-jpackage\input'
        RuntimeImageDir = Join-Path $composeBuildRoot 'release-jpackage\full-jbr-runtime'
        MsiOutputDir = Join-Path $composeBuildRoot 'compose\binaries\main\msi'
        ExeOutputDir = Join-Path $composeBuildRoot 'compose\binaries\main\exe'
        WixDir = Join-Path $RepoRoot 'build\wix311'
        IconPath = Join-Path $RepoRoot 'composeApp\launcher-icon.ico'
    }
}

function New-FullJetBrainsRuntimeImage {
    param(
        [string]$JavaHomePath,
        [string]$RuntimeImageDir,
        [string]$WorkingDirectory
    )

    $jlinkExe = Join-Path $JavaHomePath 'bin\jlink.exe'
    Assert-Executable -Path $jlinkExe -Label 'jlink'

    if (Test-Path $RuntimeImageDir) {
        Remove-Item -Path $RuntimeImageDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $RuntimeImageDir) -Force | Out-Null

    Invoke-External `
        -FilePath $jlinkExe `
        -Arguments @(
            '--module-path', (Join-Path $JavaHomePath 'jmods'),
            '--add-modules', 'ALL-MODULE-PATH',
            '--output', $RuntimeImageDir
        ) `
        -WorkingDirectory $WorkingDirectory
}

function Invoke-JPackageBuild {
    param(
        [string]$JavaHomePath,
        [string]$RepoRoot,
        [string]$InputDir,
        [string]$RuntimeImageDir,
        [string]$OutputDir,
        [string]$TargetFormat,
        [string]$PackageVersion
    )

    $jpackageExe = Join-Path $JavaHomePath 'bin\jpackage.exe'
    Assert-Executable -Path $jpackageExe -Label 'jpackage'

    $layout = Get-ReleasePackagingLayout -RepoRoot $RepoRoot
    Assert-Executable -Path $layout.IconPath -Label 'Icona applicazione'

    if ($TargetFormat -eq 'msi') {
        Assert-Executable -Path (Join-Path $layout.WixDir 'light.exe') -Label 'WiX light.exe'
        Assert-Executable -Path (Join-Path $layout.WixDir 'candle.exe') -Label 'WiX candle.exe'
    }

    Clear-Directory -Path $OutputDir

    $arguments = @(
        '--type', $TargetFormat,
        '--input', $InputDir,
        '--runtime-image', $RuntimeImageDir,
        '--dest', $OutputDir,
        '--name', 'scuola-di-ministero',
        '--main-jar', 'composeApp-jvm.jar',
        '--main-class', 'org.example.project.MainKt',
        '--icon', $layout.IconPath,
        '--win-dir-chooser',
        '--win-shortcut',
        '--win-menu',
        '--win-menu-group', 'Scuola di ministero',
        '--description', 'Pianificatore per il ministero del Regno',
        '--app-version', $PackageVersion,
        '--vendor', 'Scuola di ministero',
        '--copyright', 'Copyright (C) 2026 Scuola di ministero',
        '--java-options', '-Dcompose.application.configure.swing.globals=true',
        '--java-options', '-Dcompose.application.resources.dir=$APPDIR\resources',
        '--java-options', '-Dskiko.library.path=$APPDIR'
    )

    $originalPath = $env:PATH
    try {
        if ($TargetFormat -eq 'msi') {
            $env:PATH = "$($layout.WixDir);$originalPath"
        }

        Invoke-External `
            -FilePath $jpackageExe `
            -Arguments $arguments `
            -WorkingDirectory $RepoRoot
    } finally {
        $env:PATH = $originalPath
    }
}

function Get-PackageAssetPath {
    param([string]$RepoRoot, [string]$Extension)

    $root = Join-Path $RepoRoot 'composeApp\build\compose\binaries'
    if (-not (Test-Path $root)) {
        throw "Cartella output packaging non trovata: $root"
    }

    $asset = Get-ChildItem -Path $root -Recurse -Filter "*.$Extension" |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1

    if (-not $asset) {
        throw "Nessun file .$Extension prodotto sotto: $root"
    }

    return $asset.FullName
}

function Get-GradleVersion {
    param([string]$RepoRoot)

    $gradlePropsPath = Join-Path $RepoRoot 'gradle.properties'
    $content = [System.IO.File]::ReadAllText($gradlePropsPath)
    if ($content -match '(?m)^app\.version=(.+)$') {
        return $Matches[1].Trim()
    }
    throw "Chiave app.version non trovata in gradle.properties"
}

function Parse-SemanticVersion {
    param([string]$VersionText)

    $match = [System.Text.RegularExpressions.Regex]::Match(
        $VersionText,
        '^(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)(?<suffix>-[0-9A-Za-z.-]+)?$'
    )
    if (-not $match.Success) {
        throw "Versione non valida: $VersionText. Atteso formato MAJOR.MINOR.PATCH[-suffix]."
    }

    return [pscustomobject]@{
        Major = [int]$match.Groups['major'].Value
        Minor = [int]$match.Groups['minor'].Value
        Patch = [int]$match.Groups['patch'].Value
        Suffix = $match.Groups['suffix'].Value
    }
}

function Get-VersionSuggestions {
    param([string]$CurrentVersion)

    $parsed = Parse-SemanticVersion -VersionText $CurrentVersion
    return [pscustomobject]@{
        Current = $CurrentVersion
        Patch = "$($parsed.Major).$($parsed.Minor).$($parsed.Patch + 1)"
        Minor = "$($parsed.Major).$($parsed.Minor + 1).0"
        Major = "$($parsed.Major + 1).0.0"
    }
}

function Test-InteractiveConsole {
    try {
        return -not [Console]::IsInputRedirected -and -not [Console]::IsOutputRedirected
    } catch {
        return $false
    }
}

function Read-VersionChoice {
    param([pscustomobject]$Suggestions)

    Write-Host ""
    Write-Host "Versione corrente: $($Suggestions.Current)" -ForegroundColor Yellow
    Write-Host "Scegli la prossima versione:" -ForegroundColor Cyan
    Write-Host "  1) patch -> $($Suggestions.Patch)"
    Write-Host "  2) minor -> $($Suggestions.Minor)"
    Write-Host "  3) major -> $($Suggestions.Major)"
    Write-Host "  4) custom"

    while ($true) {
        $choice = Read-Host "Selezione [1-4] (default 1)"
        if ([string]::IsNullOrWhiteSpace($choice)) {
            return $Suggestions.Patch
        }

        switch ($choice.Trim()) {
            '1' { return $Suggestions.Patch }
            '2' { return $Suggestions.Minor }
            '3' { return $Suggestions.Major }
            '4' {
                $customVersion = Read-Host "Inserisci versione custom (MAJOR.MINOR.PATCH[-suffix])"
                if ($customVersion -match '^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$') {
                    return $customVersion
                }
                Write-Host "Versione non valida. Riprova." -ForegroundColor Red
            }
            default {
                Write-Host "Scelta non valida. Inserisci 1, 2, 3 o 4." -ForegroundColor Red
            }
        }
    }
}

function Resolve-TargetVersion {
    param(
        [string]$RepoRoot,
        [string]$ExplicitVersion
    )

    $currentVersion = Get-GradleVersion -RepoRoot $RepoRoot
    $suggestions = Get-VersionSuggestions -CurrentVersion $currentVersion

    if (-not [string]::IsNullOrWhiteSpace($ExplicitVersion)) {
        return [pscustomobject]@{
            CurrentVersion = $currentVersion
            TargetVersion = $ExplicitVersion.Trim()
            Suggestions = $suggestions
            SelectionMode = 'Explicit'
        }
    }

    if (-not (Test-InteractiveConsole) -or $WhatIfPreference) {
        throw @"
Versione non specificata.
Versione corrente: $currentVersion
Suggerimenti:
  patch -> $($suggestions.Patch)
  minor -> $($suggestions.Minor)
  major -> $($suggestions.Major)
Rilancia con -Version <valore>.
"@
    }

    $selectedVersion = Read-VersionChoice -Suggestions $suggestions
    return [pscustomobject]@{
        CurrentVersion = $currentVersion
        TargetVersion = $selectedVersion
        Suggestions = $suggestions
        SelectionMode = 'Interactive'
    }
}

function Update-GradlePropertiesVersion {
    param([string]$RepoRoot, [string]$Version)

    $gradlePropsPath = Join-Path $RepoRoot 'gradle.properties'
    $content = [System.IO.File]::ReadAllText($gradlePropsPath)
    $updated = [System.Text.RegularExpressions.Regex]::Replace(
        $content,
        '(?m)^app\.version=.*$',
        "app.version=$Version"
    )
    if ($updated -eq $content) {
        throw "Chiave app.version non trovata in gradle.properties"
    }
    [System.IO.File]::WriteAllText($gradlePropsPath, $updated)
}

function Assert-GitWorkingTreeClean {
    param([string]$GitExe, [string]$RepoRoot)

    $status = & $GitExe -C $RepoRoot status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Impossibile leggere lo stato git."
    }
    if (-not [string]::IsNullOrWhiteSpace(($status | Out-String))) {
        throw "Working tree non pulito. Committa o stasha le modifiche prima di rilasciare."
    }
}

# ── Validazione iniziale ─────────────────────────────────────────────────────

Assert-Windows

$repoRoot = Resolve-RepoRoot
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
$gitExe = (Get-Command git -ErrorAction Stop).Source
$ghExe = (Get-Command gh -ErrorAction SilentlyContinue)
$versionContext = Resolve-TargetVersion -RepoRoot $repoRoot -ExplicitVersion $Version
$Version = $versionContext.TargetVersion

$resolvedJavaHome = Resolve-JavaHomeValue -ExplicitJavaHome $JavaHome
$env:JAVA_HOME = $resolvedJavaHome

Assert-Executable -Path $gradleWrapper -Label 'Gradle wrapper'
$javaExePath = Join-Path $resolvedJavaHome 'bin\java.exe'
Assert-Executable -Path $javaExePath -Label 'Java'
Assert-JavaMajorVersionAtLeast -JavaExePath $javaExePath -MinimumMajorVersion 21
Assert-JetBrainsRuntime -JavaHomePath $resolvedJavaHome

if ($PublishRemote -and -not $ghExe) {
    throw "GitHub CLI (gh) non trovato. Installalo con: winget install GitHub.cli"
}

if (-not [string]::IsNullOrWhiteSpace($ReleaseBody) -and -not [string]::IsNullOrWhiteSpace($ReleaseNotesFile)) {
    throw "Usa solo uno tra -ReleaseBody e -ReleaseNotesFile."
}

if ($ReleaseNotesFile -and -not (Test-Path $ReleaseNotesFile)) {
    throw "File note release non trovato: $ReleaseNotesFile"
}

$tagName = "v$Version"

Write-Step "Repo root: $repoRoot"
Write-Step "JAVA_HOME: $resolvedJavaHome"
Write-Step "Versione attuale: $($versionContext.CurrentVersion)"
Write-Step "Versione release: $Version (tag: $tagName)"
if ($versionContext.SelectionMode -eq 'Interactive') {
    Write-Step "Versione selezionata in modo guidato"
} elseif ($versionContext.CurrentVersion -eq $Version) {
    Write-Step "Versione invariata rispetto a gradle.properties"
} else {
    Write-Step "Suggerimenti prossima semver: patch $($versionContext.Suggestions.Patch), minor $($versionContext.Suggestions.Minor), major $($versionContext.Suggestions.Major)"
}

# ── 1. Working tree pulito (per PublishRemote) ───────────────────────────────

if ($PublishRemote -and -not $WhatIfPreference) {
    Assert-GitWorkingTreeClean -GitExe $gitExe -RepoRoot $repoRoot
}

# ── 2. Version bump automatico (per PublishRemote) ───────────────────────────

if ($PublishRemote -and -not $WhatIfPreference) {
    $currentVersion = Get-GradleVersion -RepoRoot $repoRoot
    if ($currentVersion -ne $Version) {
        if ($PSCmdlet.ShouldProcess('gradle.properties', "Aggiornare app.version da $currentVersion a $Version")) {
            Write-Step "Version bump: $currentVersion -> $Version"
            Update-GradlePropertiesVersion -RepoRoot $repoRoot -Version $Version
            Invoke-External -FilePath $gitExe -Arguments @('-C', $repoRoot, 'add', 'gradle.properties') -WorkingDirectory $repoRoot
            Invoke-External -FilePath $gitExe -Arguments @('-C', $repoRoot, 'commit', '-m', "Bump version to $Version") -WorkingDirectory $repoRoot
        }
    } else {
        Write-Step "gradle.properties gia' a versione $Version"
    }
}

# ── 3. Test (opzionale) ─────────────────────────────────────────────────────

if ($RunTests) {
    if ($PSCmdlet.ShouldProcess("composeApp", "Eseguire :composeApp:jvmTest")) {
        Write-Step "Esecuzione test JVM"
        Invoke-External -FilePath $gradleWrapper -Arguments @(':composeApp:jvmTest') -WorkingDirectory $repoRoot
    }
}

# ── 4. Build pacchetto ──────────────────────────────────────────────────────

if ($PSCmdlet.ShouldProcess("composeApp", "Build package locale")) {
    Write-Step "Build package locale"
    $packagingLayout = Get-ReleasePackagingLayout -RepoRoot $repoRoot
    $numericPackageVersion = Get-NumericPackageVersion -VersionText $Version

    Invoke-External `
        -FilePath $gradleWrapper `
        -Arguments @(
            ':unzipWix',
            ':composeApp:prepareReleasePackageInput',
            "-Papp.version=$Version",
            "-Ppackaging.java.home=$resolvedJavaHome",
            '--no-daemon'
        ) `
        -WorkingDirectory $repoRoot

    Write-Step "Costruzione runtime image JBR completa"
    New-FullJetBrainsRuntimeImage `
        -JavaHomePath $resolvedJavaHome `
        -RuntimeImageDir $packagingLayout.RuntimeImageDir `
        -WorkingDirectory $repoRoot

    Write-Step "Packaging MSI tramite jpackage"
    Invoke-JPackageBuild `
        -JavaHomePath $resolvedJavaHome `
        -RepoRoot $repoRoot `
        -InputDir $packagingLayout.InputDir `
        -RuntimeImageDir $packagingLayout.RuntimeImageDir `
        -OutputDir $packagingLayout.MsiOutputDir `
        -TargetFormat 'msi' `
        -PackageVersion $numericPackageVersion

    if ($IncludeExe) {
        Write-Step "Packaging EXE tramite jpackage"
        Invoke-JPackageBuild `
            -JavaHomePath $resolvedJavaHome `
            -RepoRoot $repoRoot `
            -InputDir $packagingLayout.InputDir `
            -RuntimeImageDir $packagingLayout.RuntimeImageDir `
            -OutputDir $packagingLayout.ExeOutputDir `
            -TargetFormat 'exe' `
            -PackageVersion $numericPackageVersion
    }
}

$msiPath = $null
$exePath = $null
if (-not $WhatIfPreference) {
    Write-Step "Ricerca file MSI"
    $msiPath = Get-PackageAssetPath -RepoRoot $repoRoot -Extension 'msi'
    Write-Host "MSI creato: $msiPath" -ForegroundColor Green
    if ($IncludeExe) {
        Write-Step "Ricerca file EXE"
        $exePath = Get-PackageAssetPath -RepoRoot $repoRoot -Extension 'exe'
        Write-Host "EXE creato: $exePath" -ForegroundColor Green
    }
}

# ── 5-6. Tag + Push (per PublishRemote) ──────────────────────────────────────

if ($PublishRemote) {
    # 5. Tag
    $existingTag = & $gitExe -C $repoRoot tag --list $tagName
    $tagExists = -not [string]::IsNullOrWhiteSpace(($existingTag | Out-String))

    if ($PSCmdlet.ShouldProcess("git", "Creare tag $tagName")) {
        if ($tagExists) {
            Write-Step "Tag gia' presente: $tagName"
        } else {
            Write-Step "Creazione tag $tagName"
            Invoke-External -FilePath $gitExe -Arguments @('-C', $repoRoot, 'tag', '-a', $tagName, '-m', "Release $tagName") -WorkingDirectory $repoRoot
        }
    }

    # 6. Push
    if ($PSCmdlet.ShouldProcess("origin", "Push tag $tagName")) {
        Write-Step "Push tag $tagName"
        Invoke-External -FilePath $gitExe -Arguments @('-C', $repoRoot, 'push', 'origin', $tagName) -WorkingDirectory $repoRoot
    }
}

# ── 7. GitHub Release (per PublishRemote, via gh) ────────────────────────────

if ($PublishRemote) {
    if ($WhatIfPreference) {
        Write-Step "WhatIf: verrebbe creata/aggiornata la GitHub Release $tagName"
    } else {
        $assetPaths = @($msiPath)
        if ($IncludeExe -and $exePath) {
            $assetPaths += $exePath
        }

        $notesArgs = @()
        if (-not [string]::IsNullOrWhiteSpace($ReleaseBody)) {
            $notesArgs = @('--notes', $ReleaseBody)
        } elseif (-not [string]::IsNullOrWhiteSpace($ReleaseNotesFile)) {
            $notesArgs = @('--notes-file', (Resolve-Path $ReleaseNotesFile).Path)
        } else {
            $notesArgs = @('--generate-notes')
        }

        Push-Location $repoRoot
        try {
            $releaseExists = $false
            try {
                & $ghExe.Source release view $tagName *> $null
                if ($LASTEXITCODE -eq 0) { $releaseExists = $true }
            } catch {
                $releaseExists = $false
            }

            if ($releaseExists) {
                Write-Step "Release $tagName esistente - aggiornamento asset"

                if (-not [string]::IsNullOrWhiteSpace($ReleaseBody) -or -not [string]::IsNullOrWhiteSpace($ReleaseNotesFile)) {
                    & $ghExe.Source release edit $tagName @notesArgs
                    if ($LASTEXITCODE -ne 0) {
                        throw "Aggiornamento note release fallito (exit code $LASTEXITCODE)."
                    }
                }

                & $ghExe.Source release upload $tagName @assetPaths --clobber
                if ($LASTEXITCODE -ne 0) {
                    throw "Upload asset fallito (exit code $LASTEXITCODE)."
                }
            } else {
                Write-Step "Creazione GitHub Release $tagName"
                & $ghExe.Source release create $tagName @assetPaths --title "Scuola di Ministero $tagName" @notesArgs
                if ($LASTEXITCODE -ne 0) {
                    throw "Creazione release fallita (exit code $LASTEXITCODE)."
                }
            }
        } finally {
            Pop-Location
        }

        Write-Host "Release pubblicata." -ForegroundColor Green
    }
}

# ── Apertura cartella output (opzionale) ─────────────────────────────────────

if ($OpenOutput -and $msiPath) {
    if ($PSCmdlet.ShouldProcess($msiPath, 'Aprire cartella output MSI')) {
        Write-Step "Apertura cartella output"
        Start-Process explorer.exe "/select,`"$msiPath`""
    }
}

# ── Riepilogo ────────────────────────────────────────────────────────────────

if (-not $WhatIfPreference) {
    Write-Host ""
    if ($PublishRemote) {
        Write-Host "Release $tagName completata." -ForegroundColor Green
        Write-Host "MSI: $msiPath"
        if ($IncludeExe -and $exePath) {
            Write-Host "EXE: $exePath"
        }
    } else {
        Write-Host "Build locale completato." -ForegroundColor Green
        Write-Host "MSI: $msiPath"
        if ($IncludeExe -and $exePath) {
            Write-Host "EXE: $exePath"
        }
        Write-Host "Per pubblicare: aggiungi -PublishRemote"
    }
}

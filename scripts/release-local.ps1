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
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1"

Genera l'MSI locale senza pubblicare.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1" -RunTests -PublishRemote

Flusso completo: version bump, test, build, tag, push, release.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1" -PublishRemote -IncludeExe -ReleaseNotesFile .\release-notes.md

Pubblica con MSI + EXE e note di release custom.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
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
        if (-not (Test-Path $env:JAVA_HOME)) {
            throw "JAVA_HOME impostato ma non valido: $env:JAVA_HOME"
        }
        return (Resolve-Path $env:JAVA_HOME).Path
    }

    throw "JAVA_HOME non impostato. Passa -JavaHome oppure esporta JAVA_HOME verso un JDK 21."
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

    $versionOutput = @()
    try {
        $versionOutput = & $JavaExePath -version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Impossibile leggere la versione Java da: $JavaExePath"
        }
    } catch {
        if (-not $versionOutput) {
            throw "Impossibile leggere la versione Java da: $JavaExePath"
        }
    }

    $firstLine = ($versionOutput | Select-Object -First 1)
    $versionText = [string]$firstLine
    $match = [System.Text.RegularExpressions.Regex]::Match($versionText, 'version "(\d+)(?:\.(\d+))?.*"')
    if (-not $match.Success) {
        throw "Formato versione Java non riconosciuto: $versionText"
    }

    $majorVersion = [int]$match.Groups[1].Value
    if ($majorVersion -lt $MinimumMajorVersion) {
        throw "JAVA_HOME deve puntare ad almeno JDK $MinimumMajorVersion. Rilevato JDK $majorVersion in: $JavaExePath"
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

$resolvedJavaHome = Resolve-JavaHomeValue -ExplicitJavaHome $JavaHome
$env:JAVA_HOME = $resolvedJavaHome

Assert-Executable -Path $gradleWrapper -Label 'Gradle wrapper'
$javaExePath = Join-Path $resolvedJavaHome 'bin\java.exe'
Assert-Executable -Path $javaExePath -Label 'Java'
Assert-JavaMajorVersionAtLeast -JavaExePath $javaExePath -MinimumMajorVersion 21

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
Write-Step "Versione release: $Version (tag: $tagName)"

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

$packageTasks = @(':composeApp:packageMsi')
if ($IncludeExe) {
    $packageTasks += ':composeApp:packageExe'
}

if ($PSCmdlet.ShouldProcess("composeApp", "Build package locale")) {
    Write-Step "Build package locale"
    Invoke-External -FilePath $gradleWrapper -Arguments ($packageTasks + @("-Papp.version=$Version", '--no-daemon')) -WorkingDirectory $repoRoot
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

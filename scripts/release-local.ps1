<#
.SYNOPSIS
Builda i pacchetti desktop locali e, opzionalmente, pubblica la release su GitHub.

.DESCRIPTION
`-PublishRemote` orchestri tutto il flusso release remoto:
- build locale del pacchetto MSI
- creazione del tag `v<version>`
- push del tag su `origin`
- creazione o aggiornamento della GitHub Release
- upload del file MSI come asset scaricabile

Con `-IncludeExe` viene pubblicato anche l'EXE, se generato.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1"

Genera l'MSI locale per la versione indicata.

.EXAMPLE
$env:GH_TOKEN="..."
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1" -RunTests -PublishRemote

Esegue test JVM, crea il pacchetto locale e pubblica la release remota con l'MSI.

.EXAMPLE
$env:GH_TOKEN="..."
powershell -ExecutionPolicy Bypass -File .\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1" -RunTests -PublishRemote -IncludeExe -ReleaseNotesFile .\release-notes.md

Pubblica release remota completa con MSI, EXE e body custom della release.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$')]
    [string]$Version,

    [string]$JavaHome,

    [switch]$RunTests,

    [switch]$CreateTag,

    [switch]$PushTag,

    [switch]$PublishRelease,

    [switch]$PublishRemote,

    [switch]$IncludeExe,

    [switch]$UpdateVersionFile,

    [string]$Repository,

    [string]$GitHubToken,

    [string]$ReleaseBody,

    [string]$ReleaseNotesFile,

    [switch]$OpenOutput
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Convert-CommandOutputToString {
    param([object]$Value)

    if ($null -eq $Value) {
        return ''
    }

    if ($Value -is [System.Array]) {
        return (($Value | ForEach-Object {
            if ($null -eq $_) { '' } else { [string]$_ }
        }) -join [Environment]::NewLine).Trim()
    }

    return ([string]$Value).Trim()
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
    param(
        [string]$Path,
        [string]$Label
    )

    if (-not (Test-Path $Path)) {
        throw "$Label non trovato: $Path"
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

function Get-MsiPath {
    param([string]$RepoRoot)
    return Get-PackageAssetPath -RepoRoot $RepoRoot -Extension 'msi'
}

function Get-ExePath {
    param([string]$RepoRoot)
    return Get-PackageAssetPath -RepoRoot $RepoRoot -Extension 'exe'
}

function Get-PackageAssetPath {
    param(
        [string]$RepoRoot,
        [string]$Extension
    )

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

function Assert-GitTagAbsent {
    param(
        [string]$GitExe,
        [string]$RepoRoot,
        [string]$TagName
    )

    $existing = & $GitExe -C $RepoRoot tag --list $TagName
    if ($LASTEXITCODE -ne 0) {
        throw "Impossibile controllare i tag git."
    }
    if ($existing -contains $TagName) {
        throw "Il tag git esiste gia': $TagName"
    }
}

function Resolve-RepositorySlug {
    param(
        [string]$ExplicitRepository,
        [string]$GitExe,
        [string]$RepoRoot
    )

    if ($ExplicitRepository) {
        return $ExplicitRepository
    }

    $originUrl = & $GitExe -C $RepoRoot remote get-url origin
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($originUrl)) {
        throw "Impossibile leggere il remote origin."
    }

    if ($originUrl -match 'github\.com[:/](.+?)(?:\.git)?$') {
        return $Matches[1]
    }

    throw "Origin non riconosciuto come repository GitHub: $originUrl"
}

function Resolve-GitHubTokenValue {
    param([string]$ExplicitToken)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitToken)) {
        return $ExplicitToken
    }
    if (-not [string]::IsNullOrWhiteSpace($env:GH_TOKEN)) {
        return $env:GH_TOKEN
    }
    if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_TOKEN)) {
        return $env:GITHUB_TOKEN
    }

    throw "GitHub token mancante. Passa -GitHubToken oppure imposta GH_TOKEN/GITHUB_TOKEN."
}

function Invoke-GitHubApi {
    param(
        [string]$Method,
        [string]$Uri,
        [string]$Token,
        [object]$Body = $null,
        [string]$ContentType = 'application/json'
    )

    $headers = @{
        Authorization = "Bearer $Token"
        Accept        = 'application/vnd.github+json'
        'User-Agent'  = 'ministero-del-regno-planner-release-local'
        'X-GitHub-Api-Version' = '2022-11-28'
    }

    $params = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $headers
        ContentType = $ContentType
    }

    if ($null -ne $Body) {
        $params.Body = if ($ContentType -eq 'application/json') {
            $Body | ConvertTo-Json -Depth 10
        } else {
            $Body
        }
    }

    return Invoke-RestMethod @params
}

function Get-ReleaseByTag {
    param(
        [string]$RepositorySlug,
        [string]$TagName,
        [string]$Token
    )

    $uri = "https://api.github.com/repos/$RepositorySlug/releases/tags/$TagName"
    try {
        return Invoke-GitHubApi -Method 'GET' -Uri $uri -Token $Token
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 404) {
            return $null
        }
        throw
    }
}

function New-GitHubRelease {
    param(
        [string]$RepositorySlug,
        [string]$TagName,
        [string]$Token,
        [string]$TargetCommit,
        [string]$Body
    )

    $uri = "https://api.github.com/repos/$RepositorySlug/releases"
    $body = @{
        tag_name         = $TagName
        target_commitish = $TargetCommit
        name             = "Scuola di Ministero $TagName"
        draft            = $false
        prerelease       = $false
    }
    if ([string]::IsNullOrWhiteSpace($Body)) {
        $body.generate_release_notes = $true
    } else {
        $body.body = $Body
    }
    return Invoke-GitHubApi -Method 'POST' -Uri $uri -Token $Token -Body $body
}

function Update-GitHubRelease {
    param(
        [string]$RepositorySlug,
        [object]$Release,
        [string]$Token,
        [string]$Body
    )

    if ([string]::IsNullOrWhiteSpace($Body)) {
        return $Release
    }

    $uri = "https://api.github.com/repos/$RepositorySlug/releases/$($Release.id)"
    $bodyPayload = @{
        name = $Release.name
        body = $Body
        draft = $Release.draft
        prerelease = $Release.prerelease
    }
    return Invoke-GitHubApi -Method 'PATCH' -Uri $uri -Token $Token -Body $bodyPayload
}

function Upload-ReleaseAsset {
    param(
        [object]$Release,
        [string]$RepositorySlug,
        [string]$AssetPath,
        [string]$Token
    )

    $fileName = Split-Path -Leaf $AssetPath
    $uploadBase = ($Release.upload_url -replace '\{.*$', '')
    $uploadUri = "$uploadBase?name=$([System.Uri]::EscapeDataString($fileName))"
    $bytes = [System.IO.File]::ReadAllBytes($AssetPath)
    Invoke-GitHubApi -Method 'POST' -Uri $uploadUri -Token $Token -Body $bytes -ContentType 'application/octet-stream' | Out-Null
}

function Read-ReleaseBodyContent {
    param(
        [string]$InlineBody,
        [string]$NotesFile
    )

    if (-not [string]::IsNullOrWhiteSpace($InlineBody) -and -not [string]::IsNullOrWhiteSpace($NotesFile)) {
        throw "Usa solo uno tra -ReleaseBody e -ReleaseNotesFile."
    }

    if (-not [string]::IsNullOrWhiteSpace($InlineBody)) {
        return $InlineBody
    }

    if (-not [string]::IsNullOrWhiteSpace($NotesFile)) {
        if (-not (Test-Path $NotesFile)) {
            throw "File note release non trovato: $NotesFile"
        }
        return [System.IO.File]::ReadAllText((Resolve-Path $NotesFile).Path)
    }

    return $null
}

function Update-GradlePropertiesVersion {
    param(
        [string]$RepoRoot,
        [string]$Version
    )

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
    param(
        [string]$GitExe,
        [string]$RepoRoot
    )

    $status = & $GitExe -C $RepoRoot status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Impossibile leggere lo stato git."
    }
    if (-not [string]::IsNullOrWhiteSpace(($status | Out-String))) {
        throw "Working tree non pulito. Commit/stash le modifiche prima di usare -PushTag/-PublishRemote."
    }
}

Assert-Windows
$repoRoot = Resolve-RepoRoot
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
$gitExe = (Get-Command git -ErrorAction Stop).Source
$resolvedJavaHome = Resolve-JavaHomeValue -ExplicitJavaHome $JavaHome
$env:JAVA_HOME = $resolvedJavaHome

Assert-Executable -Path $gradleWrapper -Label 'Gradle wrapper'
Assert-Executable -Path (Join-Path $resolvedJavaHome 'bin\java.exe') -Label 'Java'

if ($PublishRemote) {
    $PublishRelease = $true
    $CreateTag = $true
    $PushTag = $true
}

if ($UpdateVersionFile -and ($PushTag -or $PublishRemote -or $PublishRelease)) {
    throw "Non usare -UpdateVersionFile insieme a -PushTag/-PublishRelease/-PublishRemote: aggiorna e committa prima il file versione."
}

$tagName = "v$Version"
$targetCommit = Convert-CommandOutputToString (& $gitExe -C $repoRoot rev-parse HEAD)
$script:RepositorySlug = Resolve-RepositorySlug -ExplicitRepository $Repository -GitExe $gitExe -RepoRoot $repoRoot
$releaseBodyContent = Read-ReleaseBodyContent -InlineBody $ReleaseBody -NotesFile $ReleaseNotesFile

Write-Step "Repo root: $repoRoot"
Write-Step "JAVA_HOME: $resolvedJavaHome"
Write-Step "Versione release: $Version"
Write-Step "Repository GitHub: $script:RepositorySlug"

if ($UpdateVersionFile) {
    if ($PSCmdlet.ShouldProcess('gradle.properties', "Aggiornare app.version a $Version")) {
        Write-Step "Aggiornamento gradle.properties"
        Update-GradlePropertiesVersion -RepoRoot $repoRoot -Version $Version
    }
}

if ($RunTests) {
    if ($PSCmdlet.ShouldProcess("composeApp", "Eseguire :composeApp:jvmTest")) {
        Write-Step "Esecuzione test JVM"
        Invoke-External -FilePath $gradleWrapper -Arguments @(':composeApp:jvmTest') -WorkingDirectory $repoRoot
    }
}

if (($PushTag -or $PublishRemote) -and -not $WhatIfPreference) {
    Assert-GitWorkingTreeClean -GitExe $gitExe -RepoRoot $repoRoot
}

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
    $msiPath = Get-MsiPath -RepoRoot $repoRoot
    Write-Host "MSI creato: $msiPath" -ForegroundColor Green
    if ($IncludeExe) {
        Write-Step "Ricerca file EXE"
        $exePath = Get-ExePath -RepoRoot $repoRoot
        Write-Host "EXE creato: $exePath" -ForegroundColor Green
    }
}

if ($CreateTag) {
    $existingTag = Convert-CommandOutputToString (& $gitExe -C $repoRoot tag --list $tagName)
    if (-not $existingTag) {
        Assert-GitTagAbsent -GitExe $gitExe -RepoRoot $repoRoot -TagName $tagName
    }
    if ($PSCmdlet.ShouldProcess("git", "Creare tag $tagName")) {
        if ($existingTag) {
            Write-Step "Tag git gia' presente: $tagName"
        } else {
            Write-Step "Creazione tag git $tagName"
            Invoke-External -FilePath $gitExe -Arguments @('-C', $repoRoot, 'tag', '-a', $tagName, '-m', "Release $tagName") -WorkingDirectory $repoRoot
            Write-Host "Tag creato." -ForegroundColor Green
        }
    }
}

if ($PushTag) {
    if ($PSCmdlet.ShouldProcess("origin", "Push tag $tagName")) {
        Write-Step "Push tag remoto $tagName"
        Invoke-External -FilePath $gitExe -Arguments @('-C', $repoRoot, 'push', 'origin', $tagName) -WorkingDirectory $repoRoot
    }
}

if ($PublishRelease) {
    if ($WhatIfPreference) {
        Write-Step "WhatIf: verrebbe creata/aggiornata la GitHub Release $tagName su $script:RepositorySlug"
        if ($IncludeExe) {
            Write-Step "WhatIf: verrebbe caricato anche l'EXE come asset aggiuntivo"
        }
    } else {
        $resolvedGitHubToken = Resolve-GitHubTokenValue -ExplicitToken $GitHubToken
        Write-Step "Pubblicazione GitHub Release"
        $release = Get-ReleaseByTag -RepositorySlug $script:RepositorySlug -TagName $tagName -Token $resolvedGitHubToken
        if (-not $release) {
            $release = New-GitHubRelease -RepositorySlug $script:RepositorySlug -TagName $tagName -Token $resolvedGitHubToken -TargetCommit $targetCommit -Body $releaseBodyContent
        } else {
            $release = Update-GitHubRelease -RepositorySlug $script:RepositorySlug -Release $release -Token $resolvedGitHubToken -Body $releaseBodyContent
        }

        $assetPaths = @($msiPath)
        if ($IncludeExe -and $exePath) {
            $assetPaths += $exePath
        }

        foreach ($assetPath in $assetPaths) {
            $fileName = Split-Path -Leaf $assetPath
            $existingAsset = $release.assets | Where-Object { $_.name -eq $fileName } | Select-Object -First 1
            if ($existingAsset) {
                Write-Step "Rimozione asset esistente $($existingAsset.name)"
                $deleteUri = "https://api.github.com/repos/$script:RepositorySlug/releases/assets/$($existingAsset.id)"
                Invoke-GitHubApi -Method 'DELETE' -Uri $deleteUri -Token $resolvedGitHubToken | Out-Null
            }

            Write-Step "Upload asset $fileName"
            Upload-ReleaseAsset -Release $release -RepositorySlug $script:RepositorySlug -AssetPath $assetPath -Token $resolvedGitHubToken
        }

        $releaseUrl = if ($release.html_url) { $release.html_url } else { "https://github.com/$script:RepositorySlug/releases/tag/$tagName" }
        Write-Host "Release pubblicata: $releaseUrl" -ForegroundColor Green
    }
}

if ($OpenOutput -and $msiPath) {
    if ($PSCmdlet.ShouldProcess($msiPath, 'Aprire cartella output MSI')) {
        Write-Step "Apertura cartella output"
        Start-Process explorer.exe "/select,`"$msiPath`""
    }
}

if (-not $WhatIfPreference) {
    Write-Host ""
    Write-Host "Passi successivi consigliati:" -ForegroundColor Cyan
    Write-Host "1. Verifica l'MSI: $msiPath"
    if ($IncludeExe -and $exePath) {
        Write-Host "2. Verifica anche l'EXE: $exePath"
    }
    Write-Host "3. Tag release previsto: $tagName"
    if ($PublishRelease) {
        Write-Host "4. Asset pubblicati su GitHub Releases"
    } else {
        Write-Host "4. Per pubblicare remoto usa -PublishRemote oppure -PublishRelease"
    }
}

param(
    [Parameter(Mandatory = $true)]
    [string]$InstallerPath,

    [Parameter(Mandatory = $true)]
    [string]$AppExecutable,

    [Parameter(Mandatory = $true)]
    [int]$AppPid,

    [Parameter(Mandatory = $true)]
    [string]$LogPath,

    [int]$WaitTimeoutSeconds = 7200
)

$ErrorActionPreference = 'Stop'

function Write-Log {
    param([string]$Message)

    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Add-Content -Path $LogPath -Value "$timestamp $Message"
}

function Ensure-ParentDirectory {
    param([string]$Path)

    $parent = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
}

try {
    Ensure-ParentDirectory -Path $LogPath
    Write-Log "Updater avviato. PID app: $AppPid"

    $deadline = (Get-Date).AddSeconds($WaitTimeoutSeconds)
    while ($true) {
        try {
            Get-Process -Id $AppPid -ErrorAction Stop | Out-Null
            if ((Get-Date) -ge $deadline) {
                throw "Timeout attesa chiusura applicazione."
            }
            Start-Sleep -Milliseconds 300
        } catch [Microsoft.PowerShell.Commands.ProcessCommandException] {
            break
        }
    }

    $msiLogPath = [System.IO.Path]::ChangeExtension($LogPath, '.msi.log')
    Write-Log "Avvio msiexec con log $msiLogPath"

    $msiProcess = Start-Process `
        -FilePath 'msiexec.exe' `
        -ArgumentList @('/i', $InstallerPath, '/qn', '/norestart', '/log', $msiLogPath) `
        -Wait `
        -PassThru

    Write-Log "msiexec terminato con codice $($msiProcess.ExitCode)"

    if ($msiProcess.ExitCode -ne 0) {
        throw "Installazione MSI non riuscita (codice $($msiProcess.ExitCode))."
    }

    Remove-Item -Path $InstallerPath -Force -ErrorAction SilentlyContinue
    Write-Log "Installer rimosso: $InstallerPath"

    if (Test-Path -Path $AppExecutable) {
        Write-Log "Riavvio applicazione: $AppExecutable"
        Start-Process -FilePath $AppExecutable | Out-Null
    } else {
        Write-Log "Launcher non trovato dopo l'installazione: $AppExecutable"
    }

    exit 0
} catch {
    $message = $_.Exception.Message
    Write-Log "Updater fallito: $message"

    if (Test-Path -Path $AppExecutable) {
        Write-Log "Tentativo di riaprire l'app corrente dopo errore"
        Start-Process -FilePath $AppExecutable | Out-Null
    }

    exit 1
}

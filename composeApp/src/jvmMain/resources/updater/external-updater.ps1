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

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function New-ProgressWindow {
    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'Aggiornamento in corso'
    $form.StartPosition = 'CenterScreen'
    $form.Size = New-Object System.Drawing.Size(520, 240)
    $form.MinimumSize = $form.Size
    $form.MaximumSize = $form.Size
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $form.ControlBox = $false
    $form.TopMost = $true
    $form.BackColor = [System.Drawing.Color]::FromArgb(248, 249, 252)

    $titleLabel = New-Object System.Windows.Forms.Label
    $titleLabel.Location = New-Object System.Drawing.Point(24, 22)
    $titleLabel.Size = New-Object System.Drawing.Size(460, 28)
    $titleLabel.Font = New-Object System.Drawing.Font('Segoe UI', 13, [System.Drawing.FontStyle]::Bold)
    $titleLabel.Text = 'Sto preparando l''aggiornamento'

    $detailLabel = New-Object System.Windows.Forms.Label
    $detailLabel.Location = New-Object System.Drawing.Point(24, 58)
    $detailLabel.Size = New-Object System.Drawing.Size(460, 48)
    $detailLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9.75)
    $detailLabel.ForeColor = [System.Drawing.Color]::FromArgb(74, 82, 96)
    $detailLabel.Text = 'Tra pochi secondi l''app verra chiusa e aggiornata automaticamente.'

    $progressBar = New-Object System.Windows.Forms.ProgressBar
    $progressBar.Location = New-Object System.Drawing.Point(24, 118)
    $progressBar.Size = New-Object System.Drawing.Size(460, 14)
    $progressBar.Style = 'Marquee'
    $progressBar.MarqueeAnimationSpeed = 22

    $hintLabel = New-Object System.Windows.Forms.Label
    $hintLabel.Location = New-Object System.Drawing.Point(24, 150)
    $hintLabel.Size = New-Object System.Drawing.Size(460, 42)
    $hintLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $hintLabel.ForeColor = [System.Drawing.Color]::FromArgb(107, 114, 128)
    $hintLabel.Text = 'Non e'' necessario fare altro. Questa finestra si chiudera quando la nuova versione sara pronta.'

    $form.Controls.Add($titleLabel)
    $form.Controls.Add($detailLabel)
    $form.Controls.Add($progressBar)
    $form.Controls.Add($hintLabel)
    $form.Show()
    $form.Refresh()
    [System.Windows.Forms.Application]::DoEvents()

    return @{
        Form = $form
        TitleLabel = $titleLabel
        DetailLabel = $detailLabel
    }
}

function Update-ProgressWindow {
    param(
        [hashtable]$Window,
        [string]$Title,
        [string]$Detail
    )

    if (-not $Window) {
        return
    }

    $Window.TitleLabel.Text = $Title
    $Window.DetailLabel.Text = $Detail
    $Window.Form.Refresh()
    [System.Windows.Forms.Application]::DoEvents()
}

function Close-ProgressWindow {
    param([hashtable]$Window)

    if (-not $Window) {
        return
    }

    if ($Window.Form -and -not $Window.Form.IsDisposed) {
        $Window.Form.Close()
        $Window.Form.Dispose()
    }
}

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
    $progressWindow = New-ProgressWindow
    Write-Log "Updater avviato. PID app: $AppPid"
    Update-ProgressWindow `
        -Window $progressWindow `
        -Title "Attendo la chiusura dell'app" `
        -Detail "Appena l'app termina, avvio l'installazione della nuova versione."

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
    Update-ProgressWindow `
        -Window $progressWindow `
        -Title "Installo l'aggiornamento" `
        -Detail "Sto eseguendo l'installer in background. L'operazione puo richiedere alcuni secondi."

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
        Update-ProgressWindow `
            -Window $progressWindow `
            -Title "Riavvio l'app" `
            -Detail "La nuova versione e stata installata. Sto aprendo l'app aggiornata."
        Start-Process -FilePath $AppExecutable | Out-Null
    } else {
        Write-Log "Launcher non trovato dopo l'installazione: $AppExecutable"
    }

    Start-Sleep -Milliseconds 600
    Close-ProgressWindow -Window $progressWindow
    exit 0
} catch {
    $message = $_.Exception.Message
    Write-Log "Updater fallito: $message"
    Update-ProgressWindow `
        -Window $progressWindow `
        -Title "Aggiornamento non riuscito" `
        -Detail "Si e verificato un errore. Provo a riaprire l'app corrente."

    if (Test-Path -Path $AppExecutable) {
        Write-Log "Tentativo di riaprire l'app corrente dopo errore"
        Start-Process -FilePath $AppExecutable | Out-Null
    }

    Start-Sleep -Milliseconds 1200
    Close-ProgressWindow -Window $progressWindow
    exit 1
}

# Feature Specification: Aggiornamento Applicazione

**Feature Branch**: `007-aggiornamento-applicazione`
**Created**: 2026-03-10
**Status**: Implementato — check manuale, download con progresso, installazione via external updater, release locale

---

## Contesto

L'applicazione distribuisce aggiornamenti tramite **GitHub Releases**. Il processo è interamente on-demand: nessun check automatico schedulato; il controllo parte solo su azione esplicita dell'utente. L'obiettivo finale è un aggiornamento trasparente — l'utente vede solo un banner "riavvia per aggiornare".

---

## Vincoli tecnici

- **Formato installer**: MSI (Windows Installer), prodotto da Compose Desktop via JPackage/WiX.
  - L'EXE generato da JPackage non supporta flag di installazione silenziosa standard.
  - **Solo MSI** viene scaricato e installato dal client.
- **Installazione silenziosa**: `msiexec /i installer.msi /qn /norestart`
  - Richiede UAC elevation se il path di installazione è `C:\Program Files`.
  - Alternativa senza UAC: `INSTALLSCOPE=perUser` installa in `%LOCALAPPDATA%\Programs`.
- **Upgrade code**: JPackage genera un upgrade code stabile derivato da `packageName`. Due MSI con lo stesso `packageName` si riconoscono come versioni dello stesso prodotto — il nuovo disinstalla automaticamente il vecchio.
- **Migrazioni DB**: SQLDelight gestisce le migrazioni in autonomia al primo avvio della nuova versione, confrontando `schemaVersion` nel DB con quella nel JAR. I file `.sqm` in `commonMain/sqldelight/.../migrations/` devono essere aggiunti ad ogni modifica di schema.
- **Canale di release**: il prodotto supporta solo il canale **stable**. Il client interroga `GitHub Releases /releases/latest` del repository configurato in `RemoteConfig.UPDATE_REPO`.
- **Out of scope**: beta, preview, prerelease e workflow separati per canali alternativi non fanno parte della feature.

---

## User Story 1 — Verifica manuale aggiornamenti (P1)

**Stato**: Implementato

L'utente apre la schermata Diagnostica e clicca "Verifica aggiornamenti". Il sistema interroga GitHub Releases, confronta la versione installata con quella disponibile e mostra l'esito.

**Acceptance Scenarios**:

1. **Given** l'app è connessa a internet e c'è una release più recente su GitHub,
   **When** l'utente clicca "Verifica aggiornamenti",
   **Then** il sistema mostra "Aggiornamento disponibile: vX.Y.Z" e abilita il pulsante di installazione.

2. **Given** l'app è alla versione più recente,
   **When** l'utente clicca "Verifica aggiornamenti",
   **Then** il sistema mostra "App aggiornata (vX.Y.Z)".

3. **Given** nessuna connessione internet o GitHub non raggiungibile,
   **When** l'utente clicca "Verifica aggiornamenti",
   **Then** il sistema mostra "Errore verifica: Connessione fallita" (o messaggio HTTP specifico) senza crash.

4. **Given** è in corso una verifica,
   **When** l'utente clicca di nuovo "Verifica aggiornamenti",
   **Then** la richiesta viene ignorata (guard `isCheckingUpdates`).

**Componenti**:
- `VerificaAggiornamenti` — use case, ritorna `Either<DomainError, UpdateCheckResult>`
- `GitHubReleasesClient` — infrastruttura HTTP (Ktor + kotlinx.serialization)
- `UpdateVersionComparator` — confronto versioni semantico (normalizza prefisso `v`, confronto componente per componente)
- `UpdateStatusStore` — `StateFlow<Either<DomainError, UpdateCheckResult>?>` in-memory
- `UpdateSettingsStore` — persistente, salva `lastCheck: Instant`

---

## User Story 2 — Download e installazione aggiornamento (P1)

**Stato**: Implementato

Quando è disponibile un aggiornamento, l'utente clicca "Scarica e prepara". Il sistema scarica il file MSI in background con indicatore di progresso, prepara uno script di installazione esterno (`external-updater.ps1`) e mostra il pulsante "Riavvia per installare". Al click, l'app si chiude, lo script esegue `msiexec /qn` e riapre l'app automaticamente.

**Design: External Updater Pattern**

L'installazione MSI richiede che l'app sia chiusa (il vecchio eseguibile è in uso). Invece di un `ProcessBuilder.waitFor()` in-process, il sistema usa un processo PowerShell esterno che sopravvive alla chiusura dell'app:

1. **Download** — `AggiornaApplicazione.downloadInstaller()` scarica l'MSI in `exports/updates/` con streaming Ktor e progress callback. Supporta cache (riusa installer già scaricato se la size corrisponde), download locale (`file://` URL), e modalità dev con throttling configurabile.
2. **Preparazione** — `AggiornaApplicazione.preparaInstallazione()` copia `external-updater.ps1` nella cartella updates e costruisce il comando PowerShell con parametri: `-InstallerPath`, `-AppExecutable`, `-AppPid`, `-LogPath`.
3. **Lancio e riavvio** — `AggiornaApplicazione.avviaInstallazionePreparata()` lancia lo script PowerShell come processo esterno, poi l'app chiama `exitApplication()`.
4. **External updater** — Lo script PowerShell:
   - Attende la chiusura dell'app (polling sul PID, timeout 2 ore)
   - Mostra una finestra WinForms con 3 fasi di progresso
   - Esegue `msiexec.exe /i installer.msi /qn /norestart /log external-updater.msi.log`
   - Elimina l'installer dopo successo
   - Riapre l'app automaticamente
   - In caso di errore, tenta comunque di rilanciare l'app (versione corrente)

**Acceptance Scenarios**:

1. **Given** è disponibile un aggiornamento con asset MSI,
   **When** l'utente clicca "Scarica e prepara",
   **Then** il sistema mostra un indicatore di progresso con percentuale, dimensione scaricata e velocità di trasferimento.

2. **Given** il download è completato,
   **Then** il sistema prepara lo script di installazione e mostra "Aggiornamento pronto. Premi Riavvia per installare".

3. **Given** il download fallisce (HTTP error, connessione interrotta, timeout),
   **Then** il sistema mostra un errore orientato all'azione ("Controlla la connessione e riprova" / "Controlla spazio disponibile e permessi") e il file `.part` viene eliminato.

4. **Given** il pulsante "Riavvia per installare" è visibile e l'utente clicca,
   **Then** lo script PowerShell viene lanciato, l'app si chiude, l'installer viene eseguito silenziosamente, e l'app riaperta.

5. **Given** l'installer è già stato scaricato in una sessione precedente e la dimensione corrisponde,
   **When** l'utente verifica e scarica nuovamente,
   **Then** l'installer nella cache viene riutilizzato senza download.

**Note implementative**:
- Il download avviene su `Dispatchers.IO`, non blocca la UI.
- File parziali usano estensione `.part` e vengono eliminati al prossimo avvio o in caso di errore.
- Move atomico (con fallback non-atomico) da `.part` al file finale.
- L'MSI scaricato viene eliminato dallo script PowerShell dopo installazione riuscita.
- Modalità dev: variabili `MINISTERO_UPDATE_DEV_*` per throttling download, dimensione chunk, e disabilitazione cache.
- Override locale: `MINISTERO_UPDATE_LOCAL_MSI_PATH` + `MINISTERO_UPDATE_LOCAL_VERSION` per testare con MSI specifico.
- Auto-discovery build locale: `MINISTERO_UPDATE_USE_LOCAL_BUILD` trova l'ultimo MSI in `composeApp/build/compose/binaries/main/msi`.

**Componenti**:
- `AggiornaApplicazione` — download (`downloadInstaller`), preparazione (`preparaInstallazione`), lancio (`avviaInstallazionePreparata`)
- `UpdateCenterViewModel` — orchestrazione stati UI: `isDownloading`, `isInstalling`, `restartRequired`, progress tracking
- `UpdateCenterMenu` / `UpdateHeroCard` — UI nel pannello aggiornamenti con hero card, progress bar, pulsanti azione
- `external-updater.ps1` — script PowerShell bundled in risorse, copiato in `exports/updates/` al momento della preparazione

---

## User Story 3 — Riavvio e migrazione (P2)

**Stato**: Automatico (nessun codice aggiuntivo richiesto se le migration SQLDelight sono mantenute)

Al riavvio dopo un aggiornamento, la nuova versione dell'app rileva automaticamente se lo schema del database è cambiato ed esegue le migration necessarie prima di aprire la UI.

**Acceptance Scenarios**:

1. **Given** il DB è alla versione N e la nuova release porta lo schema alla versione N+1,
   **When** l'app si avvia per la prima volta dopo l'aggiornamento,
   **Then** SQLDelight esegue il file `N.sqm` automaticamente, senza interazione utente.

2. **Given** la migration fallisce (file `.sqm` corrotto o incompatibile),
   **Then** l'app mostra un errore critico con path del DB e istruzioni per il recovery, non entra in loop.

**Vincolo**: ogni PR che modifica `MinisteroDatabase.sq` deve includere il corrispondente file di migration `N.sqm` in `commonMain/sqldelight/.../migrations/` e incrementare `DATABASE_VERSION` (se presente). Il build task `verifyMigrations` fallisce se le migration sono incoerenti con lo schema finale.

---

## Processo di rilascio — Script locale

**Stato**: Implementato

Per fare apparire il file MSI su GitHub Releases (necessario affinché `GitHubReleasesClient` lo trovi come asset scaricabile), si usa lo script PowerShell `scripts/release-local.ps1`, eseguito manualmente dalla macchina Windows dello sviluppatore.

### Prerequisiti

- **Windows** (JPackage/WiX è Windows-only per la generazione MSI)
- **JetBrains Runtime (JBR) 21** — opzionale: passare `-JavaHome` o impostare `JAVA_HOME`; se assente, lo script prova a scaricare automaticamente una JBR 21 ufficiale in `~/.jdks`
- **GitHub CLI (`gh`)** — autenticato con `gh auth login` (`winget install GitHub.cli`)

### Uso rapido

```powershell
# Build locale con scelta guidata della versione proposta dallo script
.\scripts\release-local.ps1

# Solo build locale (verifica MSI senza pubblicare)
.\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\jbr-21"

# Release completa: version bump + test + build + tag + push + release
.\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\jbr-21" -RunTests -PublishRemote
```

### Flusso `-PublishRemote`

Il flusso è **completamente automatico e idempotente** — ogni step verifica se è già stato completato, così un re-run dopo un fallimento riprende da dove si era fermato:

1. Verifica che il working tree git sia pulito.
2. Aggiorna `app.version` in `gradle.properties` e committa (skip se già alla versione target).
3. **(opzionale)** Esegue `:composeApp:jvmTest` se `-RunTests` è presente.
4. Esegue `:composeApp:packageMsi -Papp.version=X.Y.Z` (e `:composeApp:packageExe` se `-IncludeExe`).
5. Crea il tag annotato `vX.Y.Z` (skip se esiste già).
6. Push del tag su `origin` (idempotente).
7. Crea la GitHub Release via `gh` (o la aggiorna se esiste già) e carica il file MSI come asset (`--clobber`).

### Flag principali

| Flag | Descrizione |
|---|---|
| `-Version` | Versione nel formato `MAJOR.MINOR.PATCH` (es. `1.3.0`). Se omessa in una sessione interattiva, lo script propone patch/minor/major a partire da `app.version`. |
| `-JavaHome` | Path a JetBrains Runtime 21; se omesso lo script usa `$env:JAVA_HOME`, una JBR 21 sotto `~/.jdks`, oppure la scarica automaticamente |
| `-RunTests` | Esegue i test JVM prima del packaging |
| `-PublishRemote` | Flusso completo automatico (version bump → release) |
| `-IncludeExe` | Pubblica anche l'EXE oltre all'MSI |
| `-ReleaseNotesFile` | File markdown con le note di release |
| `-ReleaseBody` | Note di release inline (alternativa a `-ReleaseNotesFile`) |
| `-WhatIf` | Dry run — mostra cosa farebbe senza eseguire |
| `-OpenOutput` | Apre la cartella di output in Explorer dopo il build |

### Note

- Il `packageVersion` in Gradle (`numericVersion`) deve essere in formato `MAJOR.MINOR.PATCH` senza suffisso — JPackage rifiuta versioni con trattino (es. `1.3.0-dev`). Il campo `appVersion` può contenere il suffisso per uso interno, ma `numericVersion = appVersion.substringBefore("-")` già lo gestisce.
- Il packaging MSI deve essere eseguito con **JetBrains Runtime 21**. Usare Temurin/OpenJDK produce un installer che include una JVM valida ma non compatibile con `Jewel DecoratedWindow`, causando il crash all'avvio con messaggio finale `Failed to launch JVM`.
- Se non trova una JBR 21 locale e `-WhatIf` non è attivo, lo script scarica automaticamente l'ultima `jbrsdk` 21 Windows x64 dalle release ufficiali di `JetBrains/JetBrainsRuntime` e la riusa nelle esecuzioni successive.
- L'upgrade code MSI viene derivato automaticamente da JPackage in base a `packageName`. Non modificare `packageName` tra release oppure Windows tratterà le versioni come prodotti diversi (doppia installazione).
- `GitHubReleasesClient` cerca asset con estensione `.msi` come prima scelta, `.exe` come secondo tentativo, e infine `firstOrNull()` su qualsiasi asset disponibile come ultimo fallback (resilienza a formati non-standard). Lo script deve uploadare il file `.msi`.
- Lo script supporta `-WhatIf` per verificare il flusso senza effetti collaterali.
- Se `-Version` manca in un contesto non interattivo, lo script non si ferma con un errore generico: mostra la versione corrente e suggerisce i prossimi bump patch/minor/major da passare esplicitamente.
- `gh` rileva il repository automaticamente dal remote git — non serve configurazione aggiuntiva.

---

## Stato implementazione

| Componente | Stato |
|---|---|
| `VerificaAggiornamenti` (check manuale) | ✅ Implementato |
| `GitHubReleasesClient` | ✅ Implementato |
| `UpdateVersionComparator` | ✅ Implementato |
| `UpdateStatusStore` | ✅ Implementato |
| Rimozione check automatico schedulato | ✅ Implementato (UpdateScheduler rimosso) |
| `AggiornaApplicazione` — download + install | ✅ Implementato (external updater pattern) |
| `UpdateCenterViewModel` + UI aggiornamenti | ✅ Implementato |
| `external-updater.ps1` | ✅ Implementato (bundled in risorse) |
| Script release locale (`release-local.ps1`) | ✅ Implementato |
| Documentazione migration SQLDelight | ❌ Da formalizzare |

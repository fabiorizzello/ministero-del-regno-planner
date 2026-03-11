# Feature Specification: Aggiornamento Applicazione

**Feature Branch**: `007-aggiornamento-applicazione`
**Created**: 2026-03-10
**Status**: Parzialmente implementato — check manuale e release locale funzionanti, installazione silenziosa da completare

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
- `UpdateSettingsStore` — persistente, salva `lastCheck: Instant` e `channel: UpdateChannel`

---

## User Story 2 — Installazione silenziosa (P1)

**Stato**: Da implementare

Quando è disponibile un aggiornamento, l'utente clicca "Installa aggiornamento". Il sistema scarica il file MSI in background, lo installa silenziosamente e mostra un banner di riavvio. L'utente non interagisce con wizard o UAC (eccetto l'elevation Windows, se necessaria).

**Acceptance Scenarios**:

1. **Given** è disponibile un aggiornamento con asset MSI,
   **When** l'utente clicca "Installa aggiornamento",
   **Then** il sistema mostra un indicatore di progresso "Download in corso…", scarica il file MSI in `exports/updates/`, e al termine avvia l'installazione silenziosa.

2. **Given** l'installazione silenziosa è completata (exit code `msiexec` = 0),
   **Then** compare un banner persistente "Aggiornamento installato — riavvia l'app per applicare".

3. **Given** il download fallisce (HTTP error, connessione interrotta),
   **Then** il sistema mostra un errore specifico e non avvia l'installazione.

4. **Given** `msiexec` restituisce un exit code diverso da 0,
   **Then** il sistema mostra "Installazione non riuscita (codice X)" e preserva il file MSI scaricato per debug.

5. **Given** il banner di riavvio è visibile e l'utente clicca "Riavvia",
   **Then** l'app termina e la nuova versione è già installata (si apre al prossimo avvio manuale).

**Note implementative**:
- Il download avviene su `Dispatchers.IO`, non blocca la UI.
- `msiexec` viene avviato con `ProcessBuilder("msiexec", "/i", path, "/qn", "/norestart").start()` e si attende la terminazione con `waitFor()` su un thread IO separato.
- L'MSI scaricato viene eliminato dopo installazione riuscita.
- Se l'utente chiude l'app prima del completamento del download, il file parziale viene eliminato al prossimo avvio.

**Componenti da creare/modificare**:
- `AggiornaApplicazione` — aggiungere fase install (`installaSilenzioso(path): Either<DomainError, Unit>`) separata dalla fase download
- `DiagnosticsViewModel` — gestire stati `isDownloading`, `isInstalling`, `updateReadyToApply`
- `DiagnosticsUiState` — aggiungere i nuovi flag di stato

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
- **JDK 21** — passare `-JavaHome` o impostare `JAVA_HOME`
- **GitHub CLI (`gh`)** — autenticato con `gh auth login` (`winget install GitHub.cli`)

### Uso rapido

```powershell
# Solo build locale (verifica MSI senza pubblicare)
.\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1"

# Release completa: version bump + test + build + tag + push + release
.\scripts\release-local.ps1 -Version 1.3.0 -JavaHome "C:\Users\fabio\.jdks\corretto-20.0.2.1" -RunTests -PublishRemote
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
| `-Version` (obbligatorio) | Versione nel formato `MAJOR.MINOR.PATCH` (es. `1.3.0`) |
| `-JavaHome` | Path al JDK; default: `$env:JAVA_HOME` |
| `-RunTests` | Esegue i test JVM prima del packaging |
| `-PublishRemote` | Flusso completo automatico (version bump → release) |
| `-IncludeExe` | Pubblica anche l'EXE oltre all'MSI |
| `-ReleaseNotesFile` | File markdown con le note di release |
| `-ReleaseBody` | Note di release inline (alternativa a `-ReleaseNotesFile`) |
| `-WhatIf` | Dry run — mostra cosa farebbe senza eseguire |
| `-OpenOutput` | Apre la cartella di output in Explorer dopo il build |

### Note

- Il `packageVersion` in Gradle (`numericVersion`) deve essere in formato `MAJOR.MINOR.PATCH` senza suffisso — JPackage rifiuta versioni con trattino (es. `1.3.0-dev`). Il campo `appVersion` può contenere il suffisso per uso interno, ma `numericVersion = appVersion.substringBefore("-")` già lo gestisce.
- L'upgrade code MSI viene derivato automaticamente da JPackage in base a `packageName`. Non modificare `packageName` tra release oppure Windows tratterà le versioni come prodotti diversi (doppia installazione).
- `GitHubReleasesClient` cerca asset con estensione `.msi` come prima scelta, `.exe` come fallback. Lo script deve uploadare il file `.msi`.
- Lo script supporta `-WhatIf` per verificare il flusso senza effetti collaterali.
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
| `AggiornaApplicazione` — download | ✅ Implementato |
| `AggiornaApplicazione` — install silenziosa | ❌ Da implementare |
| Banner riavvio in `DiagnosticsViewModel` | ❌ Da implementare |
| Script release locale (`release-local.ps1`) | ✅ Implementato |
| Documentazione migration SQLDelight | ❌ Da formalizzare |

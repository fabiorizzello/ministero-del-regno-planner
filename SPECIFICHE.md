# Specifiche Progetto - v1.4

## 1. Obiettivo
Realizzare un'app desktop (Kotlin Compose) per pianificare settimanalmente le parti, gestire proclamatori e assegnazioni, con funzionamento locale e aggiornamenti semplici per utenti non tecnici.

## 2. Priorita' prodotto
- Prima fase: funzionalita' core di dominio (proclamatori, parti, assegnazioni, import piano).
- Seconda fase: qualita' operativa (aggiornamenti, diagnostica, packaging, supporto).
- Ogni scelta tecnica deve minimizzare attrito operativo per l'utente finale.

## 3. Vincoli di prodotto
- Piattaforma: desktop Windows (Kotlin Compose JVM).
- Lingua: italiano.
- Inizio settimana: lunedi'.
- Nessuna dipendenza da VPN o server personale sempre acceso.

## 4. Principi architetturali
- Approccio: DDD leggero con enfasi sul dominio e sui use case applicativi.
- Vertical slices: ogni feature e' implementata end-to-end (UI -> use case -> persistenza) in uno slice dedicato.
- Use case espliciti: logica applicativa concentrata in use case nominati.
- Service layer minimo: evitare servizi generici se non necessari; introdurre servizi solo quando risolvono un problema reale.
- Dominio stabile: entita', value object e regole di validazione indipendenti da UI e DB.
- Separazione netta Query/Write:
- Query side dedicato alla lettura (`cerca(termine?)`).
- Write side dedicato agli aggregati (`load(aggregateId)` e `persist(aggregateRoot)`).
- Stato applicazione per slice con `ViewModel + StateFlow`.
- Pattern UI consigliato: MVI leggero (`UiState`, `Intent`, `Effect`) senza framework MVI obbligatorio.
- `Store5` non adottato nel MVP (non necessario per il carico remoto attuale).
- UI separata dalla business logic:
- cartella unica `ui/` per schermate/interfacce.
- nessuna regola business direttamente nei composable.

## 5. Stack tecnico deciso
- UI: Kotlin Compose Desktop.
- Navigazione UI: Voyager (app-level e flow feature-level).
- Database locale: SQLite.
- Accesso DB e migrazioni: SQLDelight.
- Logging: SLF4J + Logback con rolling file.
- Programmazione funzionale: Arrow (`arrow-core`, `arrow-optics`).
- Dependency Injection: Koin.
- Stato/UI orchestration: ViewModel + StateFlow.
- Settings persistenti: Multiplatform Settings.
- Networking: nessun REST/GQL applicativo; per update/schemi uso client HTTP leggero (no Ktor nel MVP).
- Distribuzione aggiornamenti: pacchetto installabile pubblicato su GitHub Releases.
- Storage locale su Windows: `AppData/Local/EfficaciNelMinistero`.
- DB SQLite salvato in `AppData` (cartella `data/`).
- Impostazioni finestra (dimensione/stato) persistite con Multiplatform Settings e ripristinate all'avvio.

## 5.1 Linee guida async e thread UI
- Tutti i use case applicativi chiamati dalla UI sono `suspend`.
- Le operazioni I/O (DB, file, download update/schemi) devono eseguire fuori dal thread UI.
- La UI non blocca mai il thread principale durante query/mutation.
- Ogni azione asincrona espone stato di caricamento (`isLoading`) ed eventuale errore.
- Le query continue da DB sono esposte con `Flow` e raccolte in stato UI.

## 5.2 Standard UI condivisi
- Standard tabellare unico: usare componenti condivisi (`TableColumnSpec`, `StandardTableHeader`, `StandardTableViewport`, `standardTableCell`, `StandardTableEmptyRow`).
- Standard feedback unico: usare `FeedbackBanner` con stato tipizzato (`FeedbackBannerModel`, `FeedbackBannerKind.SUCCESS|ERROR`).
- Feedback dismissibile: il banner espone chiusura (`X`) e puo' essere nascosto manualmente.
- Tema applicativo: light mode Material3 come default.
- Selezione testo: evitare `SelectionContainer` globale su tutto l'albero UI (rischio crash gerarchia in Compose Desktop); usare selezione locale solo dove utile.
- Le nuove schermate devono riusare i componenti standard, evitando varianti locali duplicate.
- Documento operativo: `docs/UI_STANDARD.md`.
- Decisioni consolidate sessione: `docs/PATTERNI_SESSIONE.md`.
- Skill operative disponibili nel repo:
- `skills/compose-table-standard`
- `skills/compose-feedback-banner-standard`

## 6. Modello dati di dominio

### 6.1 Proclamatore
- `id`
- `nome`
- `cognome`
- `sesso` (`M` | `F`)
- `attivo` (`true` | `false`)

### 6.2 Settimana
- `id`
- `dataInizio` (sempre lunedi')

### 6.3 ParteSettimanale
- `id`
- `settimanaId`
- `titolo`
- `numeroPersone` (`1` | `2`)
- `regolaSesso` (`UOMO` | `LIBERO`)
- `ordine`

### 6.4 Assegnazione
- `id`
- `parteSettimanaleId`
- `proclamatoreId`
- `slot` (`1` | `2`)

Nota: per `numeroPersone = 1` esiste solo `slot = 1`.

## 7. Regole funzionali core

### 7.1 Gestione proclamatori
- Creazione e modifica proclamatore.
- Rimozione proclamatore (hard delete fisico su DB).
- Disattivazione/riattivazione proclamatore.
- Proclamatori disattivati esclusi da nuove assegnazioni.
- Storico assegnazioni mantenuto.
- Unicita': combinazione `nome + cognome` univoca (case-insensitive).
- Ricerca unica (termine opzionale) su nome/cognome.

### 7.2 Gestione piano settimanale
- Inserimento e modifica manuale delle parti.
- Import JSON locale con piu' settimane.
- Il JSON import contiene solo parti, non assegnazioni.
- Se settimana gia' presente: prompt `Sovrascrivere settimana esistente?` (`Si`/`No`).

### 7.3 Regole assegnazione
- `UOMO`: assegnati solo con `sesso = M`.
- `LIBERO`: nessun vincolo di sesso.
- Parti da 2 persone: slot 1 e slot 2 obbligatori.
- Una persona non puo' occupare due slot della stessa parte.

### 7.4 Suggerimenti assegnazione
- Suggerimento "fuzzy": mostra tutti i proclamatori assegnabili, ordinati per priorita'.
- Esclusioni hard: regole parte (`UOMO`/`LIBERO`), proclamatore non attivo, vincoli di validazione.
- Ordinamento prioritario: chi non svolge quella parte da piu' tempo.
- Nessuno storico: priorita' alta.

## 8. Use case principali (MVP)
- Tutti i use case elencati sono `suspend`.
- `CreaProclamatore`
- `AggiornaProclamatore`
- `ImpostaStatoProclamatore` (attiva/disattiva)
- `CercaProclamatori` (termine opzionale; se assente restituisce elenco completo)
- `EliminaProclamatore`
- `CreaParteSettimanale`
- `AggiornaParteSettimanale`
- `ImportaPianoDaJson`
- `AssegnaProclamatoreAParte`
- `RimuoviAssegnazione`
- `SuggerisciProclamatoriPerParte`

## 9. Migrazioni DB
- Fase attuale (sviluppo): schema consolidato in migration iniziale unica, senza retrocompatibilita' con dati legacy.
- Nessun file migrazione incrementale (`.sqm`) finche' non si entra in fase di produzione.
- In fase produzione: migrazioni SQLDelight versionate e verificate in build.

## 10. Aggiornamenti applicazione (QOL)
- Nessuna VPN.
- Aggiornamento con pacchetto installabile unico da GitHub Releases.
- Ogni release puo' includere nuova versione app, asset schemi settimanali predefiniti e migrazioni DB SQLDelight.
- Check aggiornamenti:
- all'apertura dell'applicazione
- periodicamente ogni 30 minuti dopo l'apertura.
- Flusso aggiornamento a 2 step con pulsanti distinti:
- `Verifica aggiornamenti`
- `Aggiorna` (abilitato solo se disponibile una nuova versione o nuovi schemi).
- Obiettivo UX: minima interazione utente.

## 10.1 Aggiornamento schemi settimanali (QOL)
- Gli schemi settimanali possono essere aggiornati anche senza nuova versione applicativa.
- Import/aggiornamento schemi con gestione conflitti per settimana.
- In caso di conflitto: chiedere conferma `Sovrascrivere settimana esistente?` (`Si`/`No`).

## 11. Diagnostica e supporto (QOL)
- Log applicativi su file rolling locale.
- Schermata diagnostica con versione app, percorso DB SQLite, percorso cartella log, azione `Apri cartella log`.
- Export diagnostico `.zip` con DB SQLite completo, log recenti (ultimi 14 giorni) e metadati base (versione app, timestamp, OS).
- Le impostazioni UI persistenti (finestra) risiedono nella stessa root `AppData` dell'app.

## 12. Output utenti
- Generazione immagine per ogni proclamatore assegnato.
- Nome file immagine in formato breve: `YYYYMM<giornoLunedi>-<giornoDomenica>_Nome_Cognome.png` (esempio: `20260212-19_Mario_Rossi.png`).
- Nessuna integrazione WhatsApp API.

## 13. PDF e stampa
- PDF dinamico: impaginazione automatica di N foglietti su A4 in base al numero di parti selezionate per la stampa.
- Template grafico con campi dinamici (parte, persona/e, settimana).

## 14. Criteri di accettazione MVP
- Gestione proclamatori con stato attivo/disattivo.
- Elenco proclamatori tabellare sempre visibile (anche vuoto) con paginazione.
- Ricerca visibile a destra nella schermata elenco.
- Flusso creazione/modifica su schermata dedicata con rotte e breadcrumbs.
- Pulsante eliminazione disponibile su riga elenco.
- Creazione e modifica parti settimanali.
- Import JSON multi-settimana con conferma sovrascrittura.
- Assegnazioni valide rispetto a `numeroPersone` e `regolaSesso`.
- Suggerimenti ordinati per "piu' tempo dall'ultima assegnazione".
- Generazione immagini per invio manuale.
- Operazioni I/O senza blocco UI e gestione loading/error in schermata.
- Separazione query/write rispettata con write su aggregate root (`load/persist`).

## 15. Criteri di accettazione QOL
- Export diagnostico con DB + log recenti.
- Processo update tramite pacchetto GitHub Releases.

## 16. Piano implementativo
- L'ordine operativo e la scomposizione in vertical slices sono definiti in `ROADMAP.md`.

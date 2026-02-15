# Specifiche Progetto - v1.2

## 1. Obiettivo
Realizzare un'app desktop (Kotlin Compose) per pianificare settimanalmente le parti, gestire persone e assegnazioni, con funzionamento locale e aggiornamenti semplici per utenti non tecnici.

## 2. Priorita' prodotto
- Prima fase: funzionalita' core di dominio (persone, parti, assegnazioni, import piano).
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

## 5. Stack tecnico deciso
- UI: Kotlin Compose Desktop.
- Database locale: SQLite.
- Accesso DB e migrazioni: SQLDelight.
- Logging: SLF4J + Logback con rolling file.
- Programmazione funzionale: Arrow (`arrow-core`, `arrow-optics`).
- Distribuzione aggiornamenti: pacchetto installabile pubblicato su GitHub Releases.

## 6. Modello dati di dominio

### 6.1 Persona
- `id`
- `nome`
- `cognome`
- `sesso` (`M` | `F`)
- `attivo` (`true` | `false`)
- `destinatarioInvio` (chi riceve l'immagine, opzionale)

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
- `personaId`
- `slot` (`1` | `2`)

Nota: per `numeroPersone = 1` esiste solo `slot = 1`.

## 7. Regole funzionali core

### 7.1 Gestione persone
- Creazione e modifica persona.
- Disattivazione/riattivazione persona.
- Persone disattivate escluse da nuove assegnazioni.
- Storico assegnazioni mantenuto.

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
- Suggerimento "fuzzy": mostra tutte le persone assegnabili, ordinate per priorita'.
- Esclusioni hard: regole parte (`UOMO`/`LIBERO`), persona non attiva, vincoli di validazione.
- Ordinamento prioritario: chi non svolge quella parte da piu' tempo.
- Nessuno storico: priorita' alta.

## 8. Use case principali (MVP)
- `CreaPersona`
- `AggiornaPersona`
- `DisattivaPersona`
- `RiattivaPersona`
- `CreaParteSettimanale`
- `AggiornaParteSettimanale`
- `ImportaPianoDaJson`
- `AssegnaPersonaAParte`
- `RimuoviAssegnazione`
- `SuggerisciPersonePerParte`

## 9. Migrazioni DB
- Migrazioni SQLDelight versionate (`.sqm`) per ogni cambio schema.
- Verifica migrazioni in build (`verify`).
- Backup DB automatico prima di migrazioni strutturali.

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

## 12. Output utenti
- Generazione immagine per ogni persona assegnata.
- Ogni immagine include riferimento a `chi inviare`.
- Nome file immagine in formato breve: `YYYYMM<giornoLunedi>-<giornoDomenica>_Nome_Cognome.png` (esempio: `20260212-19_Mario_Rossi.png`).
- Nessuna integrazione WhatsApp API.

## 13. PDF e stampa
- PDF dinamico: impaginazione automatica di N foglietti su A4 in base al numero di parti selezionate per la stampa.
- Template grafico con campi dinamici (parte, persona/e, settimana).

## 14. Criteri di accettazione MVP
- Gestione persone con stato attivo/disattivo.
- Creazione e modifica parti settimanali.
- Import JSON multi-settimana con conferma sovrascrittura.
- Assegnazioni valide rispetto a `numeroPersone` e `regolaSesso`.
- Suggerimenti ordinati per "piu' tempo dall'ultima assegnazione".
- Generazione immagini per invio manuale.

## 15. Criteri di accettazione QOL
- Export diagnostico con DB + log recenti.
- Processo update tramite pacchetto GitHub Releases.

## 16. Piano implementativo
- L'ordine operativo e la scomposizione in vertical slices sono definiti in `ROADMAP.md`.

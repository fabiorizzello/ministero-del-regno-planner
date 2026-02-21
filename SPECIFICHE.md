# Specifiche Progetto - v1.7 (allineate al codice)

## 1. Obiettivo
Applicazione desktop Windows (Kotlin Compose JVM) per gestire:
- anagrafica proclamatori,
- piano parti settimanali,
- assegnazioni con suggerimenti,
- strumenti diagnostici per supporto remoto.

## 2. Vincoli invarianti
- Lingua UI: italiano.
- Settimana con inizio lunedi'.
- Storage locale in `AppData/Local/EfficaciNelMinistero`.
- Nessuna dipendenza da server personale sempre acceso.

## 3. Architettura e stack
- DDD leggero + vertical slices.
- Use case applicativi espliciti, UI separata dalla business logic.
- Compose Desktop + Voyager.
- SQLite + SQLDelight.
- Koin per DI.
- SLF4J + Logback rolling file.
- ViewModel + StateFlow per stato UI.

## 4. Stato implementazione reale (codice -> specifiche)

### 4.1 Implementato
- Fondazioni: bootstrap path app (`data`, `logs`, `exports`), DB locale, logging rolling con retention automatica 14 giorni all'avvio.
- Proclamatori: CRUD completo, ricerca, stato attivo/disattivo, eliminazione, import JSON iniziale solo con archivio vuoto.
- Parti settimanali: creazione/modifica/rimozione/riordino manuale per settimana, sincronizzazione schemi/tipologie da remoto con gestione conflitti.
- Assegnazioni: assegnazione/rimozione per slot, vincoli dominio (`UOMO`/`LIBERO`, no duplicato nella stessa parte), suggerimenti fuzzy con ranking distinto slot 1/slot 2.
- Diagnostica: schermata info app e percorsi, export `.zip` (DB + log recenti + metadata), indicatori spazio DB/log, pulizia dati storici (`6 mesi`, `1 anno`, `2 anni`).

### 4.2 Parzialmente implementato
- Cruscotto pianificazione: presente navigazione settimana corrente/precedente/successiva e stato completamento slot; manca vista estesa 1-2 mesi con alert dedicati sui buchi.

### 4.3 Non implementato (specifiche da mantenere)
- Output operativo: generazione immagini assegnazioni (`PNG`) e generazione PDF A4 dinamico con foglietti.
- Aggiornamenti applicazione: use case `VerificaAggiornamenti` e `AggiornaApplicazione`, flusso UI di check periodico e update da GitHub Releases.

## 5. Regole funzionali correnti

### 5.1 Proclamatori
- Unicita' logica nome+cognome case-insensitive.
- Proclamatori non attivi esclusi da nuove assegnazioni.
- Import JSON iniziale bloccato se esiste almeno un proclamatore.

### 5.2 Parti e settimane
- Parti con `numeroPersone` in `{1,2}`.
- Regola sesso parte in `{UOMO, LIBERO}`.
- Conflitto dati settimana in sincronizzazione remota risolto con conferma utente.

### 5.3 Assegnazioni e suggerimenti
- Una persona non puo' occupare due slot della stessa parte.
- Suggerimenti ordinabili su distanza globale/per tipo parte.
- "Mai assegnato" prioritario rispetto a chi ha storico recente.

### 5.4 Diagnostica
- Export diagnostico include DB completo, log recenti e metadati ambiente.
- Log retention: massimo 14 giorni con pulizia automatica all'avvio.
- Pulizia dati storici settimanali selezionabile per soglia temporale.

## 6. Backlog specifiche aperte
- Dashboard pianificazione estesa: orizzonte futuro configurabile (default 4-8 settimane), indicatore "pianificato fino al ...", alert esplicito se mancano programmi nelle prossime 4 settimane.
- Output operativo: PNG per persona assegnata con naming standardizzato settimana, PDF A4 dinamico multi-foglietto.
- Aggiornamenti applicazione: verifica update all'avvio e periodica, azioni separate `Verifica aggiornamenti`/`Aggiorna`, canale release GitHub con UX guidata.

## 7. Riferimento pianificazione
Le attivita' residue sono definite in `ROADMAP.md` (solo backlog non completato).

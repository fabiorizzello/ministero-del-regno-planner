# Roadmap Implementazione (Vertical Slices + DDD)

## 1. Regole di sviluppo
- Ogni slice e' end-to-end: UI, use case, repository, persistenza, test.
- Dominio al centro: regole in dominio/use case, non nella UI.
- Service layer minimo: introdurre servizi solo se necessari.
- Prima completezza funzionale core, poi QOL operativo.

## 2. Struttura slice (template)
- `feature/<nome>/domain`: entita', value object, regole.
- `feature/<nome>/application`: use case.
- `feature/<nome>/infrastructure`: repository SQLDelight e mapper.
- `ui/<nome>`: screen, state, intent (presentazione separata dalla business logic).
- `feature/<nome>/test`: test dominio/use case/repository.

## 3. Milestone M0 - Fondazioni tecniche
Obiettivo: baseline pronta per sviluppare slice senza attrito.

Deliverable:
- Setup SQLite + SQLDelight con primo schema.
- Setup Arrow (`core`, `optics`).
- Setup logback rolling file.
- Convenzioni cartelle vertical slice.
- Config base configurazione percorsi (`db`, `logs`, `exports`).

Definition of Done:
- App si avvia e crea DB locale.
- Log file scritto correttamente.
- Build e test base verdi.

## 4. Milestone M1 - Slice Proclamatori
Obiettivo: gestire anagrafica proclamatori e stato attivo/disattivo.

Use case:
- `CreaProclamatore`
- `AggiornaProclamatore`
- `ImpostaStatoProclamatore`
- `CercaProclamatori`

UI:
- Elenco proclamatori tabellare con paginazione.
- Ricerca a destra con filtro unico nome/cognome.
- Form creazione/modifica su schermata dedicata con breadcrumbs/rotte.
- Toggle attivo/disattivo.
- Pulsante eliminazione su riga tabella.

Regole:
- Validazioni base campi obbligatori.
- Persistenza stato `attivo`.

Definition of Done:
- CRUD completo proclamatori.
- Test use case + repository.

## 4.1 Milestone M1B - Slice Import Proclamatori JSON
Obiettivo: importare anagrafica proclamatori da file JSON solo in bootstrap iniziale.

Use case:
- `ImportaProclamatoriDaJson`

UI:
- Azione import file JSON proclamatori.
- Messaggio bloccante se esistono gia' proclamatori.

Regole:
- Precondizione: import consentito solo con tabella proclamatori vuota (`count = 0`).
- Nessun merge/sovrascrittura/cancellazione differenziale.
- Import in transazione unica con validazione completa file prima del commit.

Definition of Done:
- Import JSON proclamatori funzionante solo a DB proclamatori vuoto.
- Blocco import su DB non vuoto con feedback chiaro.
- Test parsing e coerenza transazionale.

## 5. Milestone M2 - Slice Parti Settimanali
Obiettivo: creare/modificare piano parti per settimana.

Use case:
- `CreaParteSettimanale`
- `AggiornaParteSettimanale`

UI:
- Selezione settimana (lunedi').
- Lista parti ordinabile/modificabile.
- Campi: titolo, `numeroPersone`, `regolaSesso`.

Regole:
- `numeroPersone` in {1,2}
- `regolaSesso` in {UOMO, LIBERO}

Definition of Done:
- Parti settimanali persistite e modificabili.
- Test validazioni dominio.

## 6. Milestone M3+M4 - Assegnazioni Core + Suggerimenti Fuzzy [COMPLETATA]
Obiettivo: assegnare persone alle parti con vincoli dominio e ranking fuzzy basato sullo storico.
Implementate insieme in un unico vertical slice.

Use case:
- `AssegnaProclamatoreAParte`
- `RimuoviAssegnazione`
- `CaricaAssegnazioni`
- `SuggerisciProclamatoriPerParte`

UI:
- Tab Assegnazioni con card per ogni parte settimanale.
- Dialog selezione proclamatore con ricerca, ranking a due colonne (globale e per tipo parte), toggle ordinamento.
- Link bidirezionali tra tab Schemi e Assegnazioni (settimana sincronizzata via SharedWeekState).
- Stato completamento visibile ("N/M slot assegnati").

Regole:
- Esclusione proclamatori non attivi.
- Regola `UOMO` e `LIBERO`.
- Niente duplicato proclamatore nella stessa parte/2 slot.
- Ranking slot 1: basato solo su storico slot 1. Ranking slot 2: basato su storico slot 1+2.
- Mai assegnato = priorita' massima.

Design doc: `docs/plans/2026-02-18-assegnazioni-design.md`
Implementation plan: `docs/plans/2026-02-18-assegnazioni-impl.md`

## 8. Milestone M5 - Slice Import Schemi Settimanali
Obiettivo: import JSON multi-settimana con conflitti gestiti.

Use case:
- `ImportaPianoDaJson`

UI:
- Azione import file.
- Dialog conferma per settimana in conflitto.

Regole:
- JSON contiene solo parti.
- Conflitto settimana: `Sovrascrivere settimana esistente?`.
- Schemi aggiornabili anche senza update app.

Definition of Done:
- Import multi-settimana funzionante.
- Conflitti gestiti con prompt utente.
- Test parsing + conflitti.

## 9. Milestone M6 - Slice Output Operativo
Obiettivo: produrre materiali pronti per invio/stampa.

Use case:
- `GeneraImmaginiAssegnazioni`
- `GeneraPdfAssegnazioni`

UI:
- Selezione settimana e parti da includere.
- Azione genera immagini e PDF.

Regole:
- PNG per persona con nome `YYYYMM<lun>-<dom>_Nome_Cognome.png`.
- PDF dinamico A4 con N foglietti in base alle parti selezionate.
- Ogni output include "chi inviare".

Definition of Done:
- Output file creati in cartella export.
- Layout leggibile e stabile su casi reali.

## 10. Milestone M7 - QOL Diagnostica
Obiettivo: ridurre attrito supporto utente.

Use case:
- `EsportaDiagnostica`

UI:
- Schermata diagnostica con versione app, percorso DB, percorso log.
- Pulsante "Apri cartella log".
- Pulsante export diagnostico.

Regole:
- Export `.zip` con DB completo + log ultimi 14 giorni + metadati.

Definition of Done:
- Bundle diagnostico pronto per supporto.
- Test presenza contenuti zip.

## 11. Milestone M8 - QOL Aggiornamenti
Obiettivo: aggiornare con minima interazione utente.

Use case:
- `VerificaAggiornamenti`
- `AggiornaApplicazione`

UI:
- Pulsante `Verifica aggiornamenti`.
- Pulsante `Aggiorna` abilitato solo con update disponibile.

Regole:
- Check all'avvio + ogni 30 minuti.
- Source update: GitHub Releases.
- Aggiornamento app e schemi trattati come canali separabili.

Definition of Done:
- Verifica update stabile.
- Flusso update guidato con minimo intervento utente.

## 12. Ordine di rilascio consigliato
1. M0 Fondazioni.
2. M1 Persone.
3. M1B Import persone JSON.
4. M2 Parti.
5. M3+M4 Assegnazioni + Suggerimenti fuzzy. [COMPLETATA]
6. M5 Import schemi.
8. M6 Output immagini/PDF.
9. M7 Diagnostica.
10. M8 Aggiornamenti.

## 13. Gate qualita' per ogni milestone
- Test use case del dominio obbligatori.
- Nessuna regola business in composable UI.
- Migrazioni SQLDelight versionate e verificate.
- Logging errori significativo sui path diagnostici.

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
- `feature/<nome>/ui`: screen, state, intent.
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

## 4. Milestone M1 - Slice Persone
Obiettivo: gestire anagrafica e stato attivo/disattivo.

Use case:
- `CreaPersona`
- `AggiornaPersona`
- `DisattivaPersona`
- `RiattivaPersona`

UI:
- Elenco persone.
- Form creazione/modifica.
- Toggle attivo/disattivo.

Regole:
- Validazioni base campi obbligatori.
- Persistenza stato `attivo`.

Definition of Done:
- CRUD completo persone.
- Test use case + repository.

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

## 6. Milestone M3 - Slice Assegnazioni Core
Obiettivo: assegnare persone alle parti con vincoli dominio.

Use case:
- `AssegnaPersonaAParte`
- `RimuoviAssegnazione`

UI:
- Screen assegnazioni per settimana.
- Selettore persone per slot.

Regole:
- Esclusione persone non attive.
- Regola `UOMO` e `LIBERO`.
- Niente duplicato persona nello stesso parte/2 slot.

Definition of Done:
- Assegnazioni valide e persistite.
- Errori validazione mostrati in UI.
- Test regole assegnazione.

## 7. Milestone M4 - Slice Suggerimenti Fuzzy
Obiettivo: ranking persone assegnabili per priorita' storica.

Use case:
- `SuggerisciPersonePerParte`

UI:
- Lista suggerimenti "fuzzy": tutte le persone assegnabili ordinate.

Regole ranking:
- Includere tutti gli assegnabili (esclusioni hard applicate).
- Priorita' alta a chi non ha storico.
- Poi ordinamento per maggiore distanza dall'ultima volta sulla stessa parte.

Definition of Done:
- Ranking coerente con specifica.
- Test su scenari con/senza storico.

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
3. M2 Parti.
4. M3 Assegnazioni.
5. M4 Suggerimenti fuzzy.
6. M5 Import schemi.
7. M6 Output immagini/PDF.
8. M7 Diagnostica.
9. M8 Aggiornamenti.

## 13. Gate qualita' per ogni milestone
- Test use case del dominio obbligatori.
- Nessuna regola business in composable UI.
- Migrazioni SQLDelight versionate e verificate.
- Logging errori significativo sui path diagnostici.

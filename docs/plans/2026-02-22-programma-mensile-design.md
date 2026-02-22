# Programma Mensile Unificato — Design

## Obiettivo

Ristrutturare l'applicazione con `Cruscotto` come centro operativo unico del mese, unendo pianificazione e assegnazioni.

L'utente lavora su un `Programma mensile` (corrente o futuro), con settimane precompilate da schemi locali aggiornabili, modifica controllata, assegnazione assistita/automatica e stampa programma.

## Scope

- Nuovo modello `Programma mensile` con settimane collegate.
- Unificazione UX Dashboard + Assegnazioni.
- Gestione settimane `SKIPPED`.
- Import unico `Aggiorna schemi` (part type + schemi settimana) persistito in DB.
- Motore suggerimenti/autoassegnazione con pesi ruolo, cooldown e criteri idoneità proclamatore.
- Stampa programma in PDF (singola pagina A4 verticale).
- Sospensione/rimozione UI output immagini/PDF assegnazioni legacy.

## Non Scope

- Nessuna consultazione storico programmi nell'interfaccia (per ora).
- Nessun undo/versioning modifiche utente su programma/settimana.
- Nessun workflow offline-first avanzato (queue/sync differita).
- Nessun trigger di autoassegnazione automatica: solo avvio manuale.

## Principi Architetturali

- DDD + vertical slice.
- Regole e integrità nel dominio Kotlin (use case), non nel DB.
- Vincoli DB ridotti al minimo tecnico (PK/FK essenziali).
- Operazioni critiche con transazione atomica (import e aggiornamenti strutturali).

## UX Finale

### Navigazione principale

- Tab top-level:
  - `Cruscotto`
  - `Proclamatori`
  - `Diagnostica`

Rimossi tab separati `Schemi` e `Assegnazioni`.

### Cruscotto

- Selettore programmi limitato a `corrente + massimo 1 futuro`.
- Se non esiste il futuro: CTA guidata `Crea prossimo mese`.
- Vista compatta con card settimana.
- Azioni per card:
  - `Modifica settimana`
  - per `SKIPPED`: solo badge + `Riattiva` (se non passata).
- Operazioni programma:
  - `Aggiorna programma da schemi`
  - `Autoassegna programma`
  - `Svuota assegnazioni programma` (corrente+future)
  - `Stampa programma`

### Modifica settimana

- Edit schema settimana (parti/ordine/skip).
- Altre settimane bloccate durante editing.
- Dirty prompt su uscita: `Salva / Scarta / Annulla` (solo schema).
- Assegnazioni restano autosave al click (`Assegna`/`Rimuovi`).
- Warning visivi non bloccanti durante editing.
- Conferma finale bloccante al salvataggio con riepilogo impatti distruttivi.

### Proclamatori

Nuovi criteri:
- `Sospeso` (on/off): esclusione temporanea da assistita/auto.
- `Idoneità conduzione` per `part_type` (default `false`).
- `Idoneità assistenza globale` (default `false`).
- Shortcut: `Abilita tutte compatibili`.

Pannello anomalie:
- Mostra proclamatori affected dopo `Aggiorna schemi` quando idoneità vengono rimosse automaticamente.
- Dismissable.
- Riappare solo al prossimo aggiornamento che genera nuove anomalie.
- CTA per riga: `Apri proclamatore`.

## Regole di dominio

### Programmi

- Periodo mese:
  - `startDate` = primo lunedì del mese.
  - `endDate` = domenica della settimana che contiene l'ultimo giorno del mese.
- Nessuna sovrapposizione programmi.
- Creazione guidata solo del primo mese non programmato.
- Massimo 1 programma futuro.
- Niente chiusura manuale/riapertura: stato temporale derivato da date.

### Settimane

- Ogni settimana del mese viene sempre creata in `week_plan`.
- `SKIPPED` = non assegnabile, ma precompilata comunque.
- Riattivazione `SKIPPED` solo se settimana non passata.
- Settimane passate: sola lettura schema/assegnazioni.
- `SKIPPED` escluse da progress e autoassegnazione.

### Import `Aggiorna schemi`

- JSON unico remoto.
- Persistenza locale in:
  - catalogo `part_type` versionato,
  - tabelle template settimana.
- Strategia:
  - all-or-nothing (rollback totale su errore),
  - sostituzione completa template settimana locali,
  - match schema->settimana solo per `week_start_date` esatta.
- Se un `part_type code` sparisce dal feed: soft deactivate automatico.
- Programmi esistenti non aggiornati automaticamente.
- Badge su programmi futuri: `Template aggiornato, verificare`.

### Aggiorna programma da schemi

- Solo per settimane non passate (incluse `SKIPPED`).
- `SKIPPED` resta `SKIPPED`.
- Conservazione assegnazioni se parte combacia su `partType + sortOrder`.
- Assegnazioni rimosse solo per parti non combacianti.
- Conferma finale con impatti.

### Assegnazione assistita e auto

- Autoassegnazione solo via pulsante manuale.
- Scope autoassegnazione: corrente + future del programma selezionato, solo slot vuoti.
- Settimane `SKIPPED` escluse.
- Assegnazioni esistenti mai toccate dall'autoassegnazione.
- Se non assegnabile tutto: completa il possibile + report persistente + log.
- Concorrenza: lock singola esecuzione in-flight.

Ranking/suggerimenti:
- Basato su carico globale `passato + futuro` su tutti i programmi.
- Peso ruolo:
  - conduzione = 2
  - assistenza = 1
- Cooldown configurabili (default):
  - conduzione = 4 settimane
  - assistenza = 2 settimane
- `Strict cooldown` default ON.
  - OFF: mostra anche candidati in cooldown con warning.
- Tie-break: maggior distanza da ultima assegnazione globale, poi alfabetico.

Info mostrate per candidato:
- ultima conduzione
- ultima assistenza
- carico pesato totale
- assegnazioni future pianificate

Formato recency:
- `< 14 giorni`: `X giorni fa`
- `>= 14 giorni`: `X settimane fa`

### Idoneità e vincoli hard

Filtri hard sempre prioritari:
- sex rule parte
- sospeso
- idoneità conduzione per tipo parte
- idoneità assistenza globale
- cooldown (se strict ON)

Versioning part type:
- idoneità legata a `part_type` radice (non a revisione).
- Se nuova revisione rende incompatibile idoneità hard:
  - pulizia automatica idoneità incompatibili,
  - pannello anomalie in `Proclamatori`.

## Modello dati (alto livello)

Nuove/aggiornate entità principali:

- `program_monthly`
  - `id`, `year`, `month`, `start_date`, `end_date`, `template_applied_at`

- `week_plan`
  - `id`, `program_id`, `week_start_date`, `status` (`ACTIVE|SKIPPED`)

- `weekly_part`
  - `id`, `week_plan_id`, `part_type_id`, `part_type_revision_id`, `sort_order`

- `part_type`
  - `id`, `code`, `active`, `current_revision_id`

- `part_type_revision`
  - `id`, `part_type_id`, `label`, `people_count`, `sex_rule`, `fixed`, `revision_number`, `created_at`

- `schema_week`
  - `id`, `week_start_date`

- `schema_week_part`
  - `id`, `schema_week_id`, `part_type_id`, `sort_order`

- `person` (estesa)
  - `suspended`, `can_assist`

- `person_part_type_eligibility`
  - `person_id`, `part_type_id`, `can_lead`

- `assignment_settings`
  - `strict_cooldown`, `lead_weight`, `assist_weight`, `lead_cooldown_weeks`, `assist_cooldown_weeks`

- `schema_update_anomaly` (snapshot ultimo update)
  - `id`, `person_id`, `part_type_id`, `reason`, `created_at`, `schema_version`

## Error Handling e semplificazioni deliberate

Inibizioni in UI:
- niente creazione mese libero/duplicato
- niente modifica settimane passate
- niente update programma passato
- niente azioni durante import in corso

Errore + log (no flow complesso):
- fallimento stampa PDF
- impossibilità assegnazione completa automatica
- incoerenze runtime rare non recuperabili in UI

Comportamento rete:
- `Aggiorna schemi` con retry manuale.
- niente sync queue/backoff avanzato.

## Stampa Programma

- PDF singola pagina A4 verticale.
- Include tutte le settimane del programma nel flusso, incluse `SKIPPED` con badge evidente.
- Contenuto: schema + assegnazioni correnti.

## Strategia di rollout (big bang in dev)

- Accettato reset/migrazione dati di sviluppo.
- Seed aggiornati al nuovo modello.
- Legacy output rimosso dalla UI.
- Focus su stabilità di dominio e workflow guidato.


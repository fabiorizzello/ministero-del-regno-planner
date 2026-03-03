# Research: Programmi Mensili

## Decision 1: Snapshot attivi con `current + futures` (max 2)

- **Decision**: evolvere il contratto applicativo da `ProgramSelectionSnapshot(current, future?)` a `ProgramSelectionSnapshot(current, futures: List<ProgramMonth>)`, con `futures` ordinati e cardinalità `0..2`.
- **Rationale**: la spec richiede fino a 2 mesi futuri e selezione esplicita mese; il contratto attuale con un solo `future` non rappresenta completamente il dominio.
- **Alternatives considered**:
  - mantenere `future` singolo e scegliere “il più vicino”: scartata perché perde informazione e complica la UI.
  - esporre lista generica non limitata: scartata perché viola vincolo business “max 2 futuri”.

## Decision 2: Creazione mese target con regole dominio centralizzate

- **Decision**: introdurre creazione per `YearMonth` target nel layer `feature/programs/application`, con validazioni centrali: max 2 futuri, contiguità, eccezione iniziale `corrente+1` senza corrente, backfill del corrente finché non passato.
- **Rationale**: le regole sono dominio puro e non devono vivere nella UI; centralizzarle evita divergenze tra schermate e futuri ingressi applicativi.
- **Alternatives considered**:
  - validare solo in UI: scartata (rischio bypass e regressioni).
  - validare solo DB con constraint: scartata (vincoli come contiguità dipendono dal tempo e non da sola struttura tabellare).

## Decision 3: Eliminazione programma corrente/futuro con transazione e conferma impatto

- **Decision**: consentire delete su `CURRENT` e `FUTURE`, vietata su `PAST`; mantenere transazione atomica (rimozione week plan + cascade) e introdurre conteggio impatto (settimane/assegnazioni) per prompt UI.
- **Rationale**: la spec aggiornata richiede cancellazione anche del corrente, con guardrail UX forte prima di operazioni distruttive.
- **Alternatives considered**:
  - continuare con delete solo futuro: scartata perché in conflitto con spec.
  - soft-delete programma: scartata perché il dominio programma usa rimozione reale e cascade già consolidata.

## Decision 4: Refresh schemi senza preview separata e badge solo su delta reale

- **Decision**: mantenere `AggiornaProgrammaDaSchemiUseCase(dryRun)` per uso interno/testing, ma nel workspace usare solo percorso applicativo diretto (`dryRun=false`) e mostrare badge “template modificato” solo quando c’è variazione reale per il mese interessato.
- **Rationale**: riduce rumore UX e allinea la richiesta “no preview separata” + “badge non sempre acceso”.
- **Alternatives considered**:
  - mantenere pulsante preview esplicito: scartata (ridondante per il flusso utente finale).
  - badge globale unico: scartata (non distingue mesi davvero impattati).

## Decision 5: Policy feedback operazioni (toast/banner)

- **Decision**: success notification mostrata solo se non esiste feedback visivo immediato o se apporta informazione aggiuntiva; error notification sempre mostrata.
- **Rationale**: riduce invasività e saturazione visiva, preservando osservabilità utente sugli errori.
- **Alternatives considered**:
  - mostrare sempre success + error: scartata (rumore eccessivo).
  - rimuovere anche error toast: scartata (rischio fallimenti silenziosi).

## Decision 6: Memoria selezione mese in sessione

- **Decision**: memorizzare e ripristinare selezione mese nella sessione UI; su reload mantenere mese selezionato se esiste, altrimenti fallback `current`, poi futuro più vicino; se manca `current` e non c’è selezione valida, selezionare subito il futuro più vicino.
- **Rationale**: evita perdita di contesto durante switch/reload e rende il comportamento prevedibile.
- **Alternatives considered**:
  - persistenza cross-restart: scartata (non richiesta).
  - nessun fallback automatico: scartata (introduce stato vuoto ambiguo in UI).

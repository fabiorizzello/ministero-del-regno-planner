# Quickstart: Programmi Mensili

## Prerequisiti

- Branch corrente: `003-programmi-mensili`
- Database locale inizializzato con schema SQLDelight corrente
- Catalogo schemi disponibile (almeno una settimana template e parte fissa valida)

## Scenario 1: Creazione mese target esplicito

1. Apri workspace Programma con nessun mese corrente.
2. Usa la CTA mese target (es. `Crea marzo`) nella colonna `Mesi`.
3. Verifica:
   - mese creato con range date corretto (primo lunedì -> domenica dopo fine mese),
   - nessuna creazione implicita del mese corrente,
   - settimane generate e visibili.

## Scenario 2: Vincoli futuri e contiguità

1. Con un futuro già presente, crea il mese successivo contiguo.
2. Prova a creare un terzo futuro.
3. Verifica:
   - blocco con errore esplicito su oltre 2 futuri,
   - blocco su salti di contiguità non ammessi.

## Scenario 3: Selezione mese e fallback

1. Seleziona un mese dai chip in `Mesi` (i chip mostrano solo `mese anno`).
2. Esegui un reload dati (es. aggiornamento schemi o creazione/eliminazione mese).
3. Verifica:
   - se il mese esiste ancora, resta selezionato,
   - se è stato eliminato, fallback a `current` o futuro più vicino,
   - se manca `current` e non c'è selezione valida, autoselezione futuro più vicino.

## Scenario 4: Delete programma corrente/futuro

1. Seleziona un mese e premi `Elimina mese`.
2. Conferma nel prompt con riepilogo impatto.
3. Verifica:
   - prompt con impatto (settimane + assegnazioni),
   - rimozione atomica in transazione,
   - nuova selezione automatica coerente,
   - impossibilità di eliminare un programma `PAST`.

## Scenario 5: Aggiorna schemi senza preview separata

1. Premi `Aggiorna schemi` dal workspace.
2. Verifica:
   - nessun pulsante preview separato,
   - aggiornamento programma applicato direttamente,
   - badge "template modificato" visibile solo su mesi futuri realmente impattati.

## Scenario 6: Policy notifiche

1. Esegui un'azione con chiaro riscontro visivo (es. selezione/switch mese).
2. Verifica che non compaia toast success ridondante.
3. Forza un errore (es. creazione mese non valida).
4. Verifica che l'errore sia sempre notificato esplicitamente.

## Comandi verifica rapida

```bash
cd /home/fabio/IdeaProjects/efficaci-nel-ministero
./gradlew test
```

Per test mirati workspace (se presenti):

```bash
./gradlew :composeApp:jvmTest --tests "org.example.project.ui.workspace.*"
```

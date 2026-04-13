# Quickstart: Catalogo Admin Tipi Parte e Schemi

## Goal

Verificare che la feature introduca due schermate admin secondarie di sola consultazione, raggiungibili senza nuovi tab top-level e coerenti con la UX dell'app desktop.

## Preconditions

- Applicazione avviabile in ambiente desktop JVM
- Catalogo con almeno un tipo di parte e almeno uno schema settimanale
- Possibilita' di eseguire `./gradlew :composeApp:jvmTest`

## Scenario 1: Accesso alla navigazione admin secondaria

1. Avviare l'applicazione.
2. Raggiungere l'area amministrativa secondaria dall'ingresso utility esistente.
3. Verificare che siano disponibili almeno:
   - `Diagnostica`
   - `Tipi parte`
   - `Schemi settimanali`
4. Verificare che nessuna delle due nuove schermate compaia come tab top-level accanto a `Programma` e `Studenti`.

**Expected**:
- La sezione admin attiva e' visivamente evidente.
- Il ritorno alla navigazione principale resta prevedibile.

## Scenario 2: Consultazione catalogo tipi parte

1. Aprire `Tipi parte`.
2. Verificare la presenza di un elenco data-dense con codice, etichetta e stato.
3. Selezionare un tipo di parte attivo.
4. Verificare che il pannello di dettaglio mostri almeno:
   - codice
   - etichetta
   - numero persone richieste
   - regola di composizione
   - stato attivo/disattivo
5. Selezionare un tipo di parte disattivo, se presente.

**Expected**:
- L'elemento selezionato cambia il dettaglio senza dialog o navigazione separata.
- Gli elementi disattivi restano leggibili ma distinti dagli attivi.
- Nessuna CTA di modifica o salvataggio e' visibile.

## Scenario 3: Consultazione schemi settimanali

1. Aprire `Schemi settimanali`.
2. Verificare che l'elenco/settore sinistro mostri settimane selezionabili.
3. Selezionare una settimana.
4. Verificare che il dettaglio mostri le parti in ordine stabile.
5. Controllare che ogni riga identifichi chiaramente il tipo di parte corrispondente.

**Expected**:
- Cambiare settimana aggiorna il dettaglio nella stessa esperienza.
- La selezione attiva resta evidente.
- Nessuna azione di editing compare nella schermata.

## Scenario 4: Stati espliciti

1. Simulare catalogo vuoto tipi parte.
2. Simulare catalogo schemi vuoto.
3. Simulare errore di caricamento in ciascuna schermata.

**Expected**:
- Ogni schermata mostra uno stato `loading`, `empty` o `error` esplicito in italiano.
- Nessun contenitore vuoto o ambiguo viene mostrato al posto dello stato.

## Suggested Verification

```powershell
./gradlew :composeApp:jvmTest
```

Per una verifica piu' ampia del modulo:

```powershell
./gradlew :composeApp:build
```

Nota: nel repository corrente non esiste il task root `test`; la verifica equivalente per i test JVM del modulo desktop e' `:composeApp:jvmTest`.

## Test Focus

- Test ViewModel su selezione, empty/error/loading
- Test UI su indicatori di sezione attiva e read-only
- Test UI su assenza di navigazione top-level aggiuntiva

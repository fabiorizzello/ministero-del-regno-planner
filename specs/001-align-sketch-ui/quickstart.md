# Quickstart: Allineamento UI/UX Sketch Applicazione

## Prerequisiti

- JDK 17
- Ambiente desktop JVM operativo
- Repository alla branch `001-align-sketch-ui`

## 1. Preparazione dataset demo

```bash
./gradlew :composeApp:seedHistoricalDemoData
```

## 2. Avvio applicazione

```bash
./gradlew :composeApp:run
```

## 3. Validazione funzionale rapida (smoke)

1. Verifica top bar con sole sezioni: `Programma`, `Proclamatori`, `Diagnostica`.
2. In `Programma`, verifica presenza pannello destro con:
   - autoassegna
   - stampa
   - copertura
   - impostazioni assegnatore
   - feed attivita
3. Esegui azioni critiche Programma senza regressioni:
   - selezione mese
   - aggiornamento schemi
   - autoassegnazione
   - stampa
   - modifica/rimozione assegnazioni
   - eliminazione mese (se abilitata)

## 4. Validazione interazioni finestra (desktop)

1. Drag da area non interattiva top bar: la finestra deve muoversi.
2. Double-click su area non interattiva: toggle maximize/restore.
3. Double-click/click su controlli interattivi top bar: non deve attivare drag/toggle involontario.

## 5. Validazione layout/risoluzioni

Verifica entrambe le risoluzioni:

- `1366x768`
- `1920x1080`

Criteri:

- azioni principali sempre raggiungibili
- nessun elemento primario tagliato/sovrapposto

## 6. Test suite

```bash
./gradlew test
```

## 7. Evidenze richieste per accettazione

- Confronto visuale contro `docs/sketches/workspace-reference-board-modes.html`
- Checklist SC-001..SC-006 compilata
- Esito smoke + esito test automatici

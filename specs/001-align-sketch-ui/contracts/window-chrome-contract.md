# Contract: Window Chrome Interactions

## Scope

Contratto di comportamento della top bar custom per finestra desktop undecorated.

## Actors

- Utente desktop (mouse/touchpad)
- Sistema finestra OS

## Interaction Contract

| Trigger | Preconditions | Expected Result | Must Not Happen |
|---------|---------------|-----------------|-----------------|
| Drag (primary press + move) su area top bar non interattiva | Finestra visibile | La finestra si sposta | Nessun click su controlli; nessun freeze input |
| Double-click (primary) su area top bar non interattiva | Finestra visibile | Toggle `Floating <-> Maximized` | `Minimize` non viene attivato |
| Click/double-click su tab/pulsanti/icone/menu | Elemento interattivo abilitato | Si esegue solo l'azione del controllo | Nessun drag/toggle finestra involontario |
| Drag in stato massimizzato | Finestra massimizzata | Nessuno stato incoerente; comportamento stabile | Nessun blocco input o posizionamento corrotto |

## Acceptance Matrix

| Area top bar | Drag | Double-click toggle | Notes |
|--------------|------|---------------------|-------|
| Brand (non cliccabile) | YES | YES | Area non interattiva |
| Spazio vuoto tra gruppi controlli | YES | YES | Area non interattiva |
| Tab di navigazione | NO | NO | Interattiva |
| Pulsanti azione finestra | NO | NO | Interattiva |
| Pulsanti utility (es. scala testo) | NO | NO | Interattiva |

## Platform Constraint

- Implementazione limitata a `jvmMain` con API Compose Desktop.

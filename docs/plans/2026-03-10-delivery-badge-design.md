# Badge Consegne Pre-caricato — Design

## Goal

Il badge sotto "Biglietti assegnazioni" mostra il riepilogo consegne (pending/blocked/all-sent) appena si seleziona un programma, senza aprire la dialog. Le settimane passate sono escluse sia dal badge sia dalla dialog.

## Architecture

### Filtro settimane passate

Il filtro `weekStart >= today` è applicato nel ViewModel (concern applicativo, non dominio):

- `openAssignmentTickets()`: filtra i ticket ricevuti prima di salvarli nello state
- `ticketBadgeText`: calcolato sui ticket già filtrati
- `buildWeekSections()`: riceve ticket già filtrati → nessun cambio

### Nuovo domain value object

`ProgramDeliverySnapshot` in `output/domain`:

```kotlin
data class ProgramDeliverySnapshot(
    val pending: Int,
    val blocked: Int,
) {
    val allDelivered: Boolean get() = pending == 0 && blocked == 0
}
```

### Estrazione logica `completePartIds()`

La funzione `completePartIds()` (attualmente privata in `GeneraImmaginiAssegnazioni`) viene estratta in una funzione domain-level riusabile, così che il nuovo use case possa determinare quali parti sono "complete" (assegnate con tutti gli slot) senza rigenerare le immagini.

### Nuovo use case: `CaricaRiepilogoConsegneProgrammaUseCase`

- Read-only, niente `TransactionScope`
- Input: `ProgramMonthId`, `referenceDate: LocalDate`
- Output: `Either<DomainError, ProgramDeliverySnapshot>`
- Logica:
  1. Carica week plans del programma con `weekStart >= referenceDate`
  2. Per ogni week plan, determina le parti complete (assigned count >= people count)
  3. Per ogni parte completa, verifica lo stato consegna via `SlipDeliveryStore`
  4. Conta: pending (completi senza consegna o con consegna non-INVIATO), blocked (incompleti)
  5. Restituisce snapshot

### Refresh strategy

Il ViewModel (`ProgramLifecycleViewModel` o `AssignmentManagementViewModel`) chiama il use case:
- On program selection
- After: assign, remove assignment, mark delivered, cancel delivery

### Badge visuale — Material Design 3

Composable `DeliveryBadge` con chip-style pill:

- **Pending chip**: `Surface(tonalElevation)` con `secondaryContainer`, dot `●` in `primary`, testo `onSecondaryContainer`
- **Blocked chip**: `Surface` con `errorContainer`, triangolo `▲` in `error`, testo `onErrorContainer`
- **All-sent chip**: `Surface` con `tertiaryContainer`, checkmark `✓`, testo `onTertiaryContainer`
- Corner radius: `20.dp` (pill shape, come M3 SuggestionChip)
- Padding: `horizontal = 10.dp, vertical = 4.dp`
- Gap tra chip: `8.dp`
- Typography: `labelSmall`
- Layout: `FlowRow`

## Tech Stack

- Kotlin, Compose Multiplatform, Arrow Either, SQLDelight, Koin DI
- Material Design 3 light theme

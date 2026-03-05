# Piano: Arricchimento Domain Models + DomainError + Either Infrastructure

## Contesto

Tre debolezze identificate nella review del codebase:
1. **Domain models anemici**: logica di business pura (calcoli, regole su dati in-memory) vive nei use case invece che nei modelli
2. **DomainError grezzo**: solo `Validation(message)` e `Network(message)` — nessuna granularità per errori di dominio specifici
3. **Either incompleto in infrastruttura**: row mapper silenti su enum sconosciuti; try/catch inconsistente in `AssegnaPersonaUseCase`

**Vincolo fondamentale**: non si spostano operazioni IO nei domain models. Solo calcoli/regole su dati già in-memory.

---

## Task A — Arricchimento Domain Models

### Principio
Solo logica **pura** (no IO, no coroutine, no store access) dai use case ai domain models.

### A1 — `WeekPlan`: calcoli su parti
**File**: `feature/weeklyparts/domain/WeekPlan.kt`

```kotlin
fun nextSortOrder(): Int =
    (parts.maxOfOrNull { it.sortOrder } ?: -1) + 1

fun recompactedSortOrders(): List<Pair<WeeklyPartId, Int>> =
    parts.mapIndexed { i, part -> part.id to i }

fun findPart(partId: WeeklyPartId): WeeklyPart? =
    parts.find { it.id == partId }
```

Semplifica `AggiungiParteUseCase` e `RimuoviParteUseCase`.

### A2 — `PartType`: validazione slot
**File**: `feature/weeklyparts/domain/PartType.kt`

```kotlin
fun isValidSlot(slot: Int): Boolean = slot in 1..peopleCount
```

Semplifica `AssegnaPersonaUseCase`: `if (!part.partType.isValidSlot(slot)) raise(...)`.

### A3 — `SexRule`: logica filtro/mismatch
**File**: `feature/weeklyparts/domain/SexRule.kt`

```kotlin
fun allowsCandidate(candidateSex: Sesso): Boolean = when (this) {
    SexRule.UOMO -> candidateSex == Sesso.M
    SexRule.STESSO_SESSO -> true
}

fun isMismatch(candidateSex: Sesso, requiredSex: Sesso?): Boolean =
    this == SexRule.STESSO_SESSO && requiredSex != null && candidateSex != requiredSex
```

Semplifica `SuggerisciProclamatoriUseCase` e `AutoAssegnaProgrammaUseCase`.

### A4 — `Assignment`: proprietà roleLabel
**File**: `feature/assignments/domain/Assignment.kt`

Sostituire la top-level function `slotToRoleLabel(slot)` con proprietà sulla data class:
```kotlin
val roleLabel: String get() = if (slot == 1) "Studente" else "Assistente"
```

Verificare tutti i chiamanti con `grep -rn "slotToRoleLabel"` e aggiornare.

### A5 — `Proclamatore`: fullName
**File**: `feature/people/domain/Proclamatore.kt`

```kotlin
val fullName: String get() = "${nome.trim()} ${cognome.trim()}"
```

In `AssignmentWithPerson.kt` delegare: `val fullName: String get() = proclamatore.fullName`

---

## Task B — DomainError granulare

### B1 — Nuovi variant
**File**: `core/domain/DomainError.kt`

```kotlin
sealed interface DomainError {
    data class Validation(val message: String) : DomainError   // generico residuo
    data class Network(val message: String) : DomainError

    data class NotFound(val entity: String) : DomainError
    data object PersonaSospesa : DomainError
    data object PersonaGiaAssegnata : DomainError
    data class SlotNonValido(val slot: Int, val max: Int) : DomainError
    data object SettimanaImmutabile : DomainError
    data class ParteFissa(val label: String) : DomainError
}
```

### B2 — `toMessage()` aggiornata

```kotlin
fun DomainError.toMessage(): String = when (this) {
    is Validation -> message
    is Network -> message
    is NotFound -> "$entity non trovato"
    PersonaSospesa -> "Il proclamatore è sospeso"
    PersonaGiaAssegnata -> "Proclamatore già assegnato in questa settimana"
    is SlotNonValido -> "Slot $slot non valido (max: $max)"
    SettimanaImmutabile -> "La settimana non è modificabile (passata o saltata)"
    is ParteFissa -> "La parte '$label' non può essere rimossa"
}
```

### B3 — raise specifici nei use case

| Use case | Prima | Dopo |
|---|---|---|
| `RimuoviParteUseCase` | `Validation("Settimana non trovata")` | `NotFound("Settimana")` |
| `RimuoviParteUseCase` | `Validation("La settimana non è modificabile...")` | `SettimanaImmutabile` |
| `RimuoviParteUseCase` | `Validation("Parte non trovata")` | `NotFound("Parte")` |
| `RimuoviParteUseCase` | `Validation("La parte '...' non può essere rimossa")` | `ParteFissa(part.partType.label)` |
| `AggiungiParteUseCase` | `Validation("Settimana non trovata")` | `NotFound("Settimana")` |
| `AggiungiParteUseCase` | `Validation("La settimana non è modificabile...")` | `SettimanaImmutabile` |
| `AssegnaPersonaUseCase` | `Validation("Proclamatore sospeso")` | `PersonaSospesa` |
| `AssegnaPersonaUseCase` | `Validation("Proclamatore già assegnato...")` | `PersonaGiaAssegnata` |
| `AssegnaPersonaUseCase` | `Validation("Slot non valido")` | `SlotNonValido(slot, part.partType.peopleCount)` |

**Verifica**: `grep -rn "DomainError.Validation" --include="*.kt"` — i residui devono essere solo casi genuinamente generici.

---

## Task C — Either in infrastruttura (fix mirati)

### C1 — Row mapper enum silenti: aggiungere logging
**File**: `feature/assignments/infrastructure/AssignmentRowMapper.kt`
**File**: `feature/people/infrastructure/ProclamatoreRowMapper.kt`
**File**: `feature/weeklyparts/infrastructure/SqlDelightWeekPlanStore.kt` (×2, `WeekPlanStatus`)
**File**: `feature/weeklyparts/infrastructure/PartTypeJsonParser.kt` (`SexRule`)

Pattern attuale: `runCatching { Sesso.valueOf(sex) }.getOrDefault(Sesso.M)` — silenzioso.

Pattern corretto:
```kotlin
private val logger = LoggerFactory.getLogger("AssignmentRowMapper")

sesso = Sesso.entries.find { it.name == sex }
    ?: run { logger.warn("Sesso sconosciuto '{}' → fallback a M", sex); Sesso.M }
```

Il fallback rimane (non-recoverable in desktop app) ma diventa visibile nel log.

### C2 — Rimuovere try-catch in `AssegnaPersonaUseCase` e `RimuoviAssegnazioneUseCase`
**File**: `feature/assignments/application/AssegnaPersonaUseCase.kt`
**File**: `feature/assignments/application/RimuoviAssegnazioneUseCase.kt`

Rimuovere il try-catch attorno alle operazioni store. Un fallimento DB è non-recuperabile → va al global `UncaughtExceptionHandler`. Gli altri use case non wrappano l'IO → consistenza.

### C3 — `RimuoviParteUseCase`: sostituire throw interno con return null
**File**: `feature/weeklyparts/application/RimuoviParteUseCase.kt`

```kotlin
// PRIMA
val updatedPlan = weekPlanStore.findByDate(weekStartDate)
    ?: throw IllegalStateException("Errore nel salvataggio")

// DOPO
val updatedPlan = weekPlanStore.findByDate(weekStartDate)
    ?: return@runInTransaction null
```

L'outer `refreshed ?: raise(DomainError.Validation("Errore nel salvataggio"))` già gestisce il null.

---

## File da modificare

**Domain (Task A)**:
- `feature/weeklyparts/domain/WeekPlan.kt`
- `feature/weeklyparts/domain/PartType.kt`
- `feature/weeklyparts/domain/SexRule.kt`
- `feature/assignments/domain/Assignment.kt`
- `feature/people/domain/Proclamatore.kt`
- `feature/assignments/domain/AssignmentWithPerson.kt`

**Core (Task B)**:
- `core/domain/DomainError.kt`

**Application (Task B3 + C2 + C3)**:
- `feature/weeklyparts/application/RimuoviParteUseCase.kt`
- `feature/weeklyparts/application/AggiungiParteUseCase.kt`
- `feature/assignments/application/AssegnaPersonaUseCase.kt`
- `feature/assignments/application/RimuoviAssegnazioneUseCase.kt`
- `feature/assignments/application/SuggerisciProclamatoriUseCase.kt`
- `feature/assignments/application/AutoAssegnaProgrammaUseCase.kt`
- altri use case con `raise(DomainError.Validation(...))` — verificare con grep

**Infrastructure (Task C1)**:
- `feature/assignments/infrastructure/AssignmentRowMapper.kt`
- `feature/people/infrastructure/ProclamatoreRowMapper.kt`
- `feature/weeklyparts/infrastructure/SqlDelightWeekPlanStore.kt`
- `feature/weeklyparts/infrastructure/PartTypeJsonParser.kt`

**NON da modificare**: SQL `.sq`, Koin modules, test esistenti (salvo compilazione forzata da nuovi tipi)

---

## Verifica

```bash
./gradlew :composeApp:jvmTest
```

Dopo B1, il `when (this)` in `toMessage()` deve coprire tutti i branch (warning Kotlin se manca un sealed subtype — sarà errore di compilazione in futuro).

---

## Valutazione Arrow Optics e altre librerie Arrow

Attualmente il progetto usa solo `arrow-core` (versione 2.1.2).

### Arrow Optics — **NON aggiungere**
Il caso d'uso più rilevante sarebbe `AssignmentManagementViewModel`:
```kotlin
_uiState.update { it.copy(assignmentSettings = it.assignmentSettings.copy(strictCooldown = value)) }
```
Nesting a 1 livello, ripetuto 5 volte. Con optics non è un miglioramento significativo.
Il codebase non ha strutture dati annidate a 3+ livelli. I `WeekPlan.parts` vengono aggiornati attraverso il DB, non in-memory. L'overhead di aggiungere optics (plugin KSP, annotation processing) non è giustificato.

### Arrow `mapOrAccumulate` — **non applicabile**
Nessun caso di validazione accumulativa. Tutti i flussi sono fail-fast (`raise` al primo errore).

### Arrow `parZip`/`parMap` — **NON aggiungere**
`AutoAssegnaProgrammaUseCase` ha loop sequenziale ma non parallelizzabile: usa `alreadyAssignedIds: MutableSet` aggiornato a ogni assegnazione — ogni slot dipende dai precedenti.

### Arrow `Resilience` — **fuori scope**
Utile per `GitHubReleasesClient` ma è feature periferica. Non rilevante per il domain.

### Arrow `Option` — **non aggiungere**
Il codebase usa nullable Kotlin in modo consistente. Mismatch di stile.

**Conclusione**: `arrow-core` con `Either` + `raise` è la scelta giusta. Non aggiungere dipendenze Arrow.

---

## Escluso da questo piano

- **Cooldown come domain service**: richiederebbe `AssignmentSettings` come dep del domain → accoppiamento con application layer
- **Aggregate boundaries formali**: troppo disruptivo rispetto al vantaggio
- **Store methods che restituiscono Either**: DB failures fatali in desktop app
- **Arrow Optics/Resilience/parMap**: ROI insufficiente (analisi sopra)

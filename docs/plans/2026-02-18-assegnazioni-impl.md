# M3+M4 Assegnazioni + Suggerimenti Fuzzy — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Permettere all'utente di assegnare proclamatori alle parti settimanali con dialog di selezione, ranking fuzzy (globale e per tipo parte), e validazione automatica (regola sesso, inattivi, duplicati).

**Architecture:** Vertical slice `feature/assignments/` con domain, application, infrastructure. `SharedWeekState` singleton per sincronizzare settimana tra tab Schemi e Assegnazioni. Ranking calcolato in SQL. Dialog modale con ricerca, due colonne distanza (globale/per parte), toggle ordinamento.

**Tech Stack:** Kotlin Compose Desktop, SQLDelight, Arrow Either, Koin DI, StateFlow.

**Design doc:** `docs/plans/2026-02-18-assegnazioni-design.md`

---

## Task 1: SQL queries e unique index per assignment

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq`

**Step 1:** Aggiungere unique index sulla tabella `assignment` (dopo la CREATE TABLE):

```sql
CREATE UNIQUE INDEX IF NOT EXISTS assignment_unique_part_slot
ON assignment(weekly_part_id, slot);
```

**Step 2:** Aggiungere le query nominate per assignments in fondo al file `.sq`:

```sql
-- Assignment queries
assignmentsForWeek:
SELECT
    a.id,
    a.weekly_part_id,
    a.person_id,
    a.slot,
    p.first_name,
    p.last_name,
    p.sex,
    p.active
FROM assignment a
JOIN person p ON a.person_id = p.id
JOIN weekly_part wp ON a.weekly_part_id = wp.id
WHERE wp.week_plan_id = ?
ORDER BY wp.sort_order, a.slot;

assignmentsForWeeklyPart:
SELECT a.id, a.weekly_part_id, a.person_id, a.slot
FROM assignment a
WHERE a.weekly_part_id = ?
ORDER BY a.slot;

upsertAssignment:
INSERT INTO assignment(id, weekly_part_id, person_id, slot)
VALUES (?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
    person_id = excluded.person_id,
    slot = excluded.slot;

deleteAssignment:
DELETE FROM assignment WHERE id = ?;

personAlreadyAssignedToPart:
SELECT COUNT(*) FROM assignment
WHERE weekly_part_id = ? AND person_id = ?;
```

**Step 3:** Aggiungere query ranking per suggerimenti fuzzy:

```sql
-- Ranking queries (M4)
lastGlobalAssignmentPerPerson:
SELECT
    p.id AS person_id,
    MAX(wpl.week_start_date) AS last_week_date
FROM person p
LEFT JOIN assignment a ON a.person_id = p.id
LEFT JOIN weekly_part wp ON a.weekly_part_id = wp.id
LEFT JOIN week_plan wpl ON wp.week_plan_id = wpl.id
WHERE p.active = 1
GROUP BY p.id;

lastPartTypeAssignmentPerPerson:
SELECT
    p.id AS person_id,
    MAX(wpl.week_start_date) AS last_week_date
FROM person p
LEFT JOIN assignment a ON a.person_id = p.id
LEFT JOIN weekly_part wp ON a.weekly_part_id = wp.id
LEFT JOIN week_plan wpl ON wp.week_plan_id = wpl.id
LEFT JOIN part_type pt ON wp.part_type_id = pt.id
WHERE p.active = 1
  AND (pt.id = ? OR a.id IS NULL)
GROUP BY p.id;

lastSlot1GlobalAssignmentPerPerson:
SELECT
    p.id AS person_id,
    MAX(wpl.week_start_date) AS last_week_date
FROM person p
LEFT JOIN assignment a ON a.person_id = p.id AND a.slot = 1
LEFT JOIN weekly_part wp ON a.weekly_part_id = wp.id
LEFT JOIN week_plan wpl ON wp.week_plan_id = wpl.id
WHERE p.active = 1
GROUP BY p.id;

lastSlot1PartTypeAssignmentPerPerson:
SELECT
    p.id AS person_id,
    MAX(wpl.week_start_date) AS last_week_date
FROM person p
LEFT JOIN assignment a ON a.person_id = p.id AND a.slot = 1
LEFT JOIN weekly_part wp ON a.weekly_part_id = wp.id
LEFT JOIN week_plan wpl ON wp.week_plan_id = wpl.id
LEFT JOIN part_type pt ON wp.part_type_id = pt.id
WHERE p.active = 1
  AND (pt.id = ? OR a.id IS NULL)
GROUP BY p.id;
```

**Step 4:** Verificare build:

```
./gradlew compileKotlinJvm
```

**Step 5:** Commit:

```
git add composeApp/src/commonMain/sqldelight/
git commit -m "feat(db): add assignment queries and ranking queries for M3+M4"
```

---

## Task 2: Domain layer — AssignmentWithPerson e SuggestedProclamatore

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/domain/Assignment.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/domain/AssignmentWithPerson.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/domain/SuggestedProclamatore.kt`

**Step 1:** Aggiornare `Assignment.kt` — i campi usano value class tipizzati:

```kotlin
package org.example.project.feature.assignments.domain

import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.people.domain.ProclamatoreId

@JvmInline
value class AssignmentId(val value: String)

data class Assignment(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
)
```

**Step 2:** Creare `AssignmentWithPerson.kt`:

```kotlin
package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

data class AssignmentWithPerson(
    val id: AssignmentId,
    val weeklyPartId: WeeklyPartId,
    val personId: ProclamatoreId,
    val slot: Int,
    val firstName: String,
    val lastName: String,
    val sex: Sesso,
    val active: Boolean,
) {
    val fullName: String get() = "$firstName $lastName"
}
```

**Step 3:** Creare `SuggestedProclamatore.kt`:

```kotlin
package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.Proclamatore

data class SuggestedProclamatore(
    val proclamatore: Proclamatore,
    val lastGlobalWeeks: Int?,      // null = mai assegnato
    val lastForPartTypeWeeks: Int?, // null = mai su questo tipo di parte
)
```

**Step 4:** Verificare build: `./gradlew compileKotlinJvm`

**Step 5:** Commit:

```
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/domain/
git commit -m "feat(domain): add AssignmentWithPerson and SuggestedProclamatore models"
```

---

## Task 3: SharedWeekState + refactor WeeklyPartsViewModel

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/core/application/SharedWeekState.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsViewModel.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt`

**Step 1:** Creare `SharedWeekState.kt`:

```kotlin
package org.example.project.core.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class SharedWeekState {
    private val _currentMonday = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val currentMonday: StateFlow<LocalDate> = _currentMonday.asStateFlow()

    fun navigateToPreviousWeek() {
        _currentMonday.value = _currentMonday.value.minusWeeks(1)
    }

    fun navigateToNextWeek() {
        _currentMonday.value = _currentMonday.value.plusWeeks(1)
    }
}
```

**Step 2:** Refactorare `WeeklyPartsViewModel`:
- Aggiungere `sharedWeekState: SharedWeekState` come parametro costruttore
- In `init`, raccogliere `sharedWeekState.currentMonday` e sincronizzare lo stato locale
- `navigateToPreviousWeek()` e `navigateToNextWeek()` delegano a `sharedWeekState`
- Rimuovere la gestione locale di `currentMonday` (non piu' calcolato nel default di `WeeklyPartsUiState`, ma ricevuto dal flow condiviso)

**Step 3:** Registrare `SharedWeekState` in `AppModules.kt` come `single`, e passarlo a `WeeklyPartsViewModel`.

**Step 4:** Verificare build: `./gradlew compileKotlinJvm`

**Step 5:** Verificare che la schermata Schemi funzioni come prima (navigazione settimana invariata).

**Step 6:** Commit:

```
git commit -m "refactor: extract SharedWeekState and integrate with WeeklyPartsViewModel"
```

---

## Task 4: Application layer — AssignmentStore e use case

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentsRepository.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/CaricaAssegnazioniUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssegnaPersonaUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/RimuoviAssegnazioneUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/SuggerisciProclamatoriUseCase.kt`

**Step 1:** Aggiornare `AssignmentsRepository` (rinominare o estendere l'interfaccia):

```kotlin
package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.domain.PartTypeId

interface AssignmentStore {
    suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson>
    suspend fun save(assignment: Assignment)
    suspend fun remove(assignmentId: String)
    suspend fun isPersonAssignedToPart(weeklyPartId: WeeklyPartId, personId: ProclamatoreId): Boolean
    suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: java.time.LocalDate,
    ): List<SuggestedProclamatore>
}
```

Nota: rinominare il file da `AssignmentsRepository.kt` a `AssignmentStore.kt` per coerenza con il pattern `WeekPlanStore`. Eliminare il vecchio file.

**Step 2:** Creare `CaricaAssegnazioniUseCase.kt`:

```kotlin
package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import java.time.LocalDate

class CaricaAssegnazioniUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(weekStartDate: LocalDate): List<AssignmentWithPerson> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        return assignmentStore.listByWeek(plan.id)
    }
}
```

**Step 3:** Creare `AssegnaPersonaUseCase.kt`:

```kotlin
package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.util.UUID

class AssegnaPersonaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        personId: ProclamatoreId,
        slot: Int,
    ): Either<DomainError, Unit> = either {
        val plan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Settimana non trovata"))

        val part = plan.parts.find { it.id == weeklyPartId }
            ?: raise(DomainError.Validation("Parte non trovata"))

        if (part.partType.fixed) {
            raise(DomainError.Validation("Non e' possibile assegnare una parte fissa"))
        }

        if (slot < 1 || slot > part.partType.peopleCount) {
            raise(DomainError.Validation("Slot non valido"))
        }

        if (assignmentStore.isPersonAssignedToPart(weeklyPartId, personId)) {
            raise(DomainError.Validation("Proclamatore gia' assegnato a questa parte"))
        }

        assignmentStore.save(
            Assignment(
                id = AssignmentId(UUID.randomUUID().toString()),
                weeklyPartId = weeklyPartId,
                personId = personId,
                slot = slot,
            )
        )
    }
}
```

**Step 4:** Creare `RimuoviAssegnazioneUseCase.kt`:

```kotlin
package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError

class RimuoviAssegnazioneUseCase(
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(assignmentId: String): Either<DomainError, Unit> = either {
        assignmentStore.remove(assignmentId)
    }
}
```

**Step 5:** Creare `SuggerisciProclamatoriUseCase.kt`:

```kotlin
package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

class SuggerisciProclamatoriUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        slot: Int,
    ): List<SuggestedProclamatore> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        val part = plan.parts.find { it.id == weeklyPartId } ?: return emptyList()

        val suggestions = assignmentStore.suggestedProclamatori(
            partTypeId = part.partType.id,
            slot = slot,
            referenceDate = weekStartDate,
        )

        // Filtri hard: regola sesso, gia' assegnato alla stessa parte
        val existingForPart = assignmentStore.listByWeek(plan.id)
            .filter { it.weeklyPartId == weeklyPartId }
            .map { it.personId }
            .toSet()

        return suggestions.filter { s ->
            val p = s.proclamatore
            val passaSesso = when (part.partType.sexRule) {
                SexRule.UOMO -> p.sesso == Sesso.M
                SexRule.LIBERO -> true
            }
            passaSesso && p.id !in existingForPart
        }
    }
}
```

**Step 6:** Verificare build: `./gradlew compileKotlinJvm`

**Step 7:** Commit:

```
git commit -m "feat(application): add assignment use cases and suggestion ranking"
```

---

## Task 5: Infrastructure — SqlDelightAssignmentStore

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/AssignmentRowMapper.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt`

**Step 1:** Creare `AssignmentRowMapper.kt` con funzioni di mapping per le query SQL.

**Step 2:** Creare `SqlDelightAssignmentStore.kt`:
- Implementa `AssignmentStore`
- `listByWeek` → `assignmentsForWeek` query con mapper `AssignmentWithPerson`
- `save` → `upsertAssignment`
- `remove` → `deleteAssignment`
- `isPersonAssignedToPart` → `personAlreadyAssignedToPart`
- `suggestedProclamatori` → esegue le query ranking (4 varianti a seconda dello slot), combina con `searchProclaimers` per i dati proclamatore, calcola `lastGlobalWeeks` e `lastForPartTypeWeeks` come differenza in settimane tra `referenceDate` e `last_week_date`

Per il calcolo settimane:
```kotlin
val weeks = if (lastDate != null) {
    ChronoUnit.WEEKS.between(LocalDate.parse(lastDate), referenceDate).toInt()
} else null
```

**Step 3:** Verificare build: `./gradlew compileKotlinJvm`

**Step 4:** Commit:

```
git commit -m "feat(infrastructure): implement SqlDelightAssignmentStore with ranking"
```

---

## Task 6: Estrarre WeekNavigator come componente condiviso

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/WeekNavigator.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt`

**Step 1:** Estrarre il composable `WeekNavigator` e l'enum `WeekTimeIndicator` da `WeeklyPartsScreen.kt` in un file condiviso `ui/components/WeekNavigator.kt`. Rendere entrambi `internal` (visibili nel modulo).

**Step 2:** Aggiornare `WeeklyPartsScreen.kt` per importare dal nuovo file. Rimuovere le copie locali.

**Step 3:** Verificare build: `./gradlew compileKotlinJvm`

**Step 4:** Commit:

```
git commit -m "refactor: extract WeekNavigator to shared ui/components"
```

---

## Task 7: DI registration

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt`

**Step 1:** Aggiungere registrazioni Koin:

```kotlin
// SharedWeekState
single { SharedWeekState() }

// Assignment infrastructure
single<AssignmentStore> { SqlDelightAssignmentStore(get()) }

// Assignment use cases
single { CaricaAssegnazioniUseCase(get(), get()) }
single { AssegnaPersonaUseCase(get(), get()) }
single { RimuoviAssegnazioneUseCase(get()) }
single { SuggerisciProclamatoriUseCase(get(), get()) }

// AssignmentsViewModel
single {
    AssignmentsViewModel(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        sharedWeekState = get(),
        caricaSettimana = get(),
        caricaAssegnazioni = get(),
        assegnaPersona = get(),
        rimuoviAssegnazione = get(),
        suggerisciProclamatori = get(),
    )
}
```

**Step 2:** Aggiornare la registrazione di `WeeklyPartsViewModel` per iniettare `SharedWeekState`.

**Step 3:** Verificare build: `./gradlew compileKotlinJvm`

**Step 4:** Commit:

```
git commit -m "chore(di): register assignment store, use cases, and ViewModel"
```

---

## Task 8: AssignmentsViewModel

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsViewModel.kt`

**Step 1:** Creare `AssignmentsUiState`:

```kotlin
internal data class AssignmentsUiState(
    val currentMonday: LocalDate = ...,
    val weekPlan: WeekPlan? = null,
    val assignments: List<AssignmentWithPerson> = emptyList(),
    val isLoading: Boolean = true,
    val notice: FeedbackBannerModel? = null,
    // Dialog state
    val pickerWeeklyPartId: WeeklyPartId? = null,
    val pickerSlot: Int? = null,
    val pickerSearchTerm: String = "",
    val pickerSortGlobal: Boolean = true, // true = globale, false = per parte
    val pickerSuggestions: List<SuggestedProclamatore> = emptyList(),
    val isPickerLoading: Boolean = false,
)
```

**Step 2:** Creare `AssignmentsViewModel`:
- `init`: collects `sharedWeekState.currentMonday` → `loadWeekData()`
- `navigateToPreviousWeek/Next()` → delega a `sharedWeekState`
- `openPersonPicker(weeklyPartId, slot)` → imposta stato dialog, chiama `loadSuggestions()`
- `closePersonPicker()`
- `setPickerSearchTerm(term)` → debounce 200ms, ricarica suggerimenti filtrati lato client
- `togglePickerSort()` → inverte `pickerSortGlobal`
- `confirmAssignment(personId)` → chiama `assegnaPersona`, chiude dialog, ricarica
- `removeAssignment(assignmentId)` → chiama `rimuoviAssegnazione`, ricarica
- `dismissNotice()`
- `loadWeekData()` → carica weekPlan + assignments
- `loadSuggestions()` → chiama `suggerisciProclamatori`, salva in stato

**Step 3:** Verificare build: `./gradlew compileKotlinJvm`

**Step 4:** Commit:

```
git commit -m "feat(ui): add AssignmentsViewModel with picker and ranking support"
```

---

## Task 9: UI — AssignmentsScreen e componenti

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsScreen.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt`

**Step 1:** Creare `AssignmentsComponents.kt` con:

- `PartAssignmentCard(part, assignments, onAssignSlot, onRemoveAssignment)` — card per ogni parte:
  - Header: numero + label + chip regola sesso
  - Per `peopleCount = 1`: slot unico senza label ruolo
  - Per `peopleCount = 2`: "Proclamatore" (slot 1) + "Assistente" (slot 2)
  - Parti fixed: solo label "(parte fissa)", nessuno slot
  - Slot assegnato: nome + pulsante "X"
  - Slot vuoto: pulsante "Assegna"

- `PersonPickerDialog(...)` — dialog modale:
  - Titolo: "Assegna — [nome parte]" + ruolo
  - Campo ricerca
  - Toggle "Ordina per: Globale | Per parte"
  - Tabella: Nome | Ultima (globale) | Ultima (questa parte) | [Assegna]
  - Formato distanza: "Mai assegnato", "1 settimana fa", "N settimane fa"
  - Stato vuoto: "Nessun proclamatore disponibile per questa parte"

**Step 2:** Aggiornare `AssignmentsScreen.kt` — sostituire il placeholder:
- Week navigator (dal componente condiviso)
- Barra stato: link "Vai allo schema" + "N/M slot assegnati"
- Lista card parti con `LazyColumn`
- Dialog di selezione
- FeedbackBanner

Usare `MaterialTheme.spacing.*` per tutti i valori di padding/spacing (come nelle altre schermate).

**Step 3:** Verificare build: `./gradlew compileKotlinJvm`

**Step 4:** Commit:

```
git commit -m "feat(ui): implement AssignmentsScreen with card layout and person picker dialog"
```

---

## Task 10: Link bidirezionali Schemi ↔ Assegnazioni

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/weeklyparts/WeeklyPartsScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`

**Step 1:** In `AppScreen.kt`, esporre una callback o meccanismo per cambiare tab programmaticamente. Le opzioni:
- Usare `navigator.replaceAll(section.screen)` dove appropriato
- Passare una callback `onNavigateToSection(AppSection)` ai composable figli

**Step 2:** In `WeeklyPartsScreen`, aggiungere pulsante "Vai alle assegnazioni" che naviga al tab Assegnazioni.

**Step 3:** In `AssignmentsScreen`:
- Aggiungere pulsante "Vai allo schema" che naviga al tab Schemi
- Se settimana non configurata: messaggio con link "Vai allo schema per crearla"

**Step 4:** Verificare build: `./gradlew compileKotlinJvm`

**Step 5:** Commit:

```
git commit -m "feat(ui): add bidirectional navigation links between Schemi and Assegnazioni"
```

---

## Task 11: Aggiornare SPECIFICHE e ROADMAP

**Files:**
- Modify: `SPECIFICHE.md`
- Modify: `ROADMAP.md`
- Modify: `docs/UI_STANDARD.md`

**Step 1:** Aggiornare ROADMAP — segnare M3 e M4 come completate, documentare che sono state implementate insieme.

**Step 2:** Aggiornare SPECIFICHE se necessario (use case nuovi, regole ranking).

**Step 3:** Aggiornare UI_STANDARD se emergono nuovi pattern (card assignment, person picker dialog).

**Step 4:** Commit:

```
git commit -m "docs: update ROADMAP and SPECIFICHE for M3+M4 completion"
```

---

## Riepilogo file

### Nuovi (10):
| File | Scopo |
|------|-------|
| `core/application/SharedWeekState.kt` | Stato settimana condiviso |
| `feature/assignments/domain/AssignmentWithPerson.kt` | Assignment + dati persona |
| `feature/assignments/domain/SuggestedProclamatore.kt` | Modello ranking |
| `feature/assignments/application/AssignmentStore.kt` | Interfaccia store (rinomina da Repository) |
| `feature/assignments/application/CaricaAssegnazioniUseCase.kt` | Carica assegnazioni settimana |
| `feature/assignments/application/AssegnaPersonaUseCase.kt` | Assegna proclamatore a slot |
| `feature/assignments/application/RimuoviAssegnazioneUseCase.kt` | Rimuovi assegnazione |
| `feature/assignments/application/SuggerisciProclamatoriUseCase.kt` | Ranking fuzzy |
| `feature/assignments/infrastructure/AssignmentRowMapper.kt` | Row mapper SQL |
| `feature/assignments/infrastructure/SqlDelightAssignmentStore.kt` | Implementazione store |
| `ui/assignments/AssignmentsComponents.kt` | Componenti UI (card, dialog) |
| `ui/components/WeekNavigator.kt` | Componente condiviso navigazione settimana |

### Modificati (6):
| File | Modifica |
|------|----------|
| `MinisteroDatabase.sq` | Query assignment + ranking + unique index |
| `Assignment.kt` | Value class tipizzati |
| `WeeklyPartsViewModel.kt` | Delega a SharedWeekState |
| `WeeklyPartsScreen.kt` | Import WeekNavigator condiviso + link |
| `AppModules.kt` | Registrazioni DI |
| `AssignmentsScreen.kt` | Implementazione completa |

### Eliminati (1):
| File | Motivo |
|------|--------|
| `AssignmentsRepository.kt` | Rinominato in `AssignmentStore.kt` |

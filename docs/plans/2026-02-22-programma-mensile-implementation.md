# Programma Mensile Unificato Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implementare il nuovo flusso applicativo centrato sul programma mensile unificato (cruscotto + assegnazioni), con import schemi persistito, criteri proclamatore, autoassegnazione manuale, e stampa programma.

**Architecture:** Big-bang in ambiente dev con DDD + vertical slices. Introduzione delle slice `programs`, `schemas`, `assignment-engine`, refactor `assignments` e `planning` in un workspace unico, e rimozione UI output legacy. Integrità applicativa in use case Kotlin, DB con vincoli minimi tecnici.

**Tech Stack:** Kotlin Compose Desktop, SQLDelight, Koin, Voyager, Coroutines/StateFlow, JUnit/Kotlin test.

---

> Nota operativa (decisione utente): in questa fase non vengono creati nuovi test unitari/integration/e2e.  
> Verifica minima richiesta: `./gradlew composeApp:compileKotlinJvm` + smoke manuale UI.

## Task 1: Aggiornare schema SQLDelight per Programmi, Schemi, Versioning e Criteri

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/infrastructure/SchemaMigrationSmokeTest.kt`

**Step 1: Write the failing test**

Scrivi test smoke che apre DB e verifica presenza tabelle/colonne nuove (query semplici su `sqlite_master`).

```kotlin
@Test
fun schema_contains_program_and_schema_tables() {
    val db = TestDatabaseFactory.create()
    val tables = db.ministeroDatabaseQueries.listTables().executeAsList()
    assertTrue(tables.contains("program_monthly"))
    assertTrue(tables.contains("schema_week"))
    assertTrue(tables.contains("part_type_revision"))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*SchemaMigrationSmokeTest*"`
Expected: FAIL, tabelle mancanti.

**Step 3: Write minimal implementation**

Aggiorna `MinisteroDatabase.sq` con:
- `program_monthly`
- `week_plan.status` + `program_id`
- `part_type.current_revision_id`
- `part_type_revision`
- `schema_week`, `schema_week_part`
- `person.suspended`, `person.can_assist`
- `person_part_type_eligibility`
- `assignment_settings`
- `schema_update_anomaly`
- query SQL necessarie per CRUD base delle nuove tabelle.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*SchemaMigrationSmokeTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/org/example/project/db/MinisteroDatabase.sq composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/infrastructure/SchemaMigrationSmokeTest.kt
git commit -m "feat(db): add monthly program, schema templates and eligibility tables"
```

---

## Task 2: Introdurre slice domain/application/infrastructure per Programmi Mensili

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/domain/ProgramMonth.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/ProgramStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/CreaProssimoProgrammaUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/CaricaProgrammiAttiviUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/EliminaProgrammaFuturoUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/infrastructure/SqlDelightProgramStore.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/application/CreaProssimoProgrammaUseCaseTest.kt`

**Step 1: Write the failing test**

Casi minimi:
- crea primo mese non programmato.
- impedisce sovrapposizione.
- impedisce più di 1 programma futuro.
- elimina solo programma futuro.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*CreaProssimoProgrammaUseCaseTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Implementa aggregate e use case:
- calcolo range mese (`firstMonday`, `lastSundayCoveringMonth`).
- stato temporale derivato (past/current/future) da date.
- business rule in Kotlin, non vincoli DB complessi.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*Program*UseCaseTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/programs composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/application/CreaProssimoProgrammaUseCaseTest.kt
git commit -m "feat(programs): add monthly program domain and use cases"
```

---

## Task 3: Introdurre slice Schemi Persistiti (template locali)

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/domain/SchemaWeek.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/SchemaTemplateStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/application/AggiornaSchemiUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas/infrastructure/SqlDelightSchemaTemplateStore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/RemoteDataSource.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/GitHubDataSource.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/config/RemoteConfig.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/application/AggiornaSchemiUseCaseTest.kt`

**Step 1: Write the failing test**

Casi:
- import valido salva part types + templates.
- import invalido -> rollback totale.
- codici mancanti disattivano part types assenti.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*AggiornaSchemiUseCaseTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- JSON unico remoto (`partTypes`, `weeks`).
- transazione unica all-or-nothing.
- replace completa tabelle template locali.
- deactivation automatica part types non più presenti.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*AggiornaSchemiUseCaseTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/schemas composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/RemoteDataSource.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/GitHubDataSource.kt composeApp/src/jvmMain/kotlin/org/example/project/core/config/RemoteConfig.kt composeApp/src/jvmTest/kotlin/org/example/project/feature/schemas/application/AggiornaSchemiUseCaseTest.kt
git commit -m "feat(schemas): persist template weeks and transactional import"
```

---

## Task 4: Versioning part_type + propagazione compatibilità

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/PartTypeRevisionPolicy.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/PartTypeStore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightPartTypeStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application/EligibilityCleanupOnSchemaUpdate.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/weeklyparts/application/PartTypeRevisionPolicyTest.kt`

**Step 1: Write the failing test**

Verifica:
- nuova revisione solo se cambiano `peopleCount|sexRule|fixed`.
- label change aggiorna revisione corrente.
- cleanup idoneità hard incompatibili.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*PartTypeRevisionPolicyTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Aggiungi politica revisione in application.
- Store SQLDelight con `current_revision_id` e `revision_number`.
- Cleanup idoneità incompatibili + snapshot anomalie ultimo aggiornamento.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*PartTypeRevisionPolicyTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/PartTypeRevisionPolicy.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/PartTypeStore.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightPartTypeStore.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application/EligibilityCleanupOnSchemaUpdate.kt composeApp/src/jvmTest/kotlin/org/example/project/feature/weeklyparts/application/PartTypeRevisionPolicyTest.kt
git commit -m "feat(part-types): add revision policy and eligibility cleanup"
```

---

## Task 5: Estendere dominio Proclamatori con sospeso e idoneità

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/domain/Proclamatore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application/EligibilityStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application/ImpostaSospesoUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application/ImpostaIdoneitaConduzioneUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application/ImpostaIdoneitaAssistenzaUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/people/infrastructure/SqlDelightEligibilityStore.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/people/application/EligibilityUseCasesTest.kt`

**Step 1: Write the failing test**

- default idoneità conduzione = false.
- default assistenza = false.
- sospeso esclude da candidati.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*EligibilityUseCasesTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Implementa store/use case + mapping SQLDelight.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*EligibilityUseCasesTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/people/domain/Proclamatore.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/people/application composeApp/src/jvmMain/kotlin/org/example/project/feature/people/infrastructure/SqlDelightEligibilityStore.kt composeApp/src/jvmTest/kotlin/org/example/project/feature/people/application/EligibilityUseCasesTest.kt
git commit -m "feat(people): add suspension and eligibility criteria"
```

---

## Task 6: Nuovo motore suggerimenti/autoassegnazione con pesi e cooldown

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentSettings.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentSettingsStore.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/CalcolaPunteggioCandidato.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AutoAssegnaProgrammaUseCase.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/SuggerisciProclamatoriUseCase.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/assignments/application/AutoAssegnaProgrammaUseCaseTest.kt`

**Step 1: Write the failing test**

Casi:
- strict cooldown ON filtra candidati.
- strict OFF mostra anche cooldown con warning.
- autoassegnazione manuale: solo slot vuoti.
- tie-break: recency globale poi alfabetico.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*AutoAssegnaProgrammaUseCaseTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Introduci settings globali (lead=2, assist=1, cooldown 4/2, strict ON).
- Ranking basato su storico passato + futuro globale tutti i programmi.
- Pipeline autoassegnazione: slot per difficoltà, match hard constraints, report outcome.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*AutoAssegnaProgrammaUseCaseTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/infrastructure/SqlDelightAssignmentStore.kt composeApp/src/jvmTest/kotlin/org/example/project/feature/assignments/application/AutoAssegnaProgrammaUseCaseTest.kt
git commit -m "feat(assignments): add weighted ranking and manual program auto-assignment"
```

---

## Task 7: Generazione settimane programma da schemi locali

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/GeneraSettimaneProgrammaUseCase.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/CreaSettimanaUseCase.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/WeekPlanStore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightWeekPlanStore.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/application/GeneraSettimaneProgrammaUseCaseTest.kt`

**Step 1: Write the failing test**

- settimana con schema: prefill da template.
- settimana senza schema: fallback parte fissa.
- `SKIPPED` comunque precompilata.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*GeneraSettimaneProgrammaUseCaseTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Generazione atomica settimane programma.
- supporto stato `ACTIVE|SKIPPED`.
- mapping `part_type` -> `part_type_revision` corrente in weekly part.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*GeneraSettimaneProgrammaUseCaseTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/GeneraSettimaneProgrammaUseCase.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/CreaSettimanaUseCase.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/application/WeekPlanStore.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/infrastructure/SqlDelightWeekPlanStore.kt composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/application/GeneraSettimaneProgrammaUseCaseTest.kt
git commit -m "feat(programs): generate month weeks from local schema templates"
```

---

## Task 8: Aggiorna programma da schemi con preservazione assegnazioni compatibili

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/AggiornaProgrammaDaSchemiUseCase.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentStore.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/application/AggiornaProgrammaDaSchemiUseCaseTest.kt`

**Step 1: Write the failing test**

Casi:
- aggiorna solo settimane non passate incluse skipped.
- mantiene skipped.
- conserva assegnazioni dove match `partType + sortOrder`.
- rimuove altre assegnazioni con report impatti.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*AggiornaProgrammaDaSchemiUseCaseTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Implementa diffing parti e remap assegnazioni compatibili.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*AggiornaProgrammaDaSchemiUseCaseTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/programs/application/AggiornaProgrammaDaSchemiUseCase.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/assignments/application/AssignmentStore.kt composeApp/src/jvmTest/kotlin/org/example/project/feature/programs/application/AggiornaProgrammaDaSchemiUseCaseTest.kt
git commit -m "feat(programs): apply schema refresh with assignment preservation rules"
```

---

## Task 9: Refactor UI in workspace unico (Cruscotto)

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceScreen.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModel.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceComponents.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModelTest.kt`

**Step 1: Write the failing test**

- carica corrente/futuro.
- default selezione programma corretta.
- lock UI durante `Aggiorna schemi`.
- filtro settimane passate read-only.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*ProgramWorkspaceViewModelTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- integra overview mese + assegnazioni.
- elimina dipendenza tab separati `Schemi`/`Assegnazioni`.
- inserisci azioni programma (`Crea prossimo mese`, `Aggiorna da schemi`, `Autoassegna`, `Svuota`, `Stampa`).

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*ProgramWorkspaceViewModelTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModelTest.kt
git commit -m "feat(ui): add unified program workspace and navigation simplification"
```

---

## Task 10: UI Proclamatori con criteri, sospeso, anomalie

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoreFormViewModel.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriComponents.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreen.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/EligibilityPanelState.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoreEligibilityViewModelTest.kt`

**Step 1: Write the failing test**

- default idoneità false.
- pulsante `Abilita tutte compatibili`.
- sospeso con warning settimane assegnate.
- pannello anomalie dismissable e riapparsa al nuovo update.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*ProclamatoreEligibilityViewModelTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Aggiorna form/modale proclamatore e pannello anomalie.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*ProclamatoreEligibilityViewModelTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/ProclamatoreEligibilityViewModelTest.kt
git commit -m "feat(proclamatori): add eligibility controls, suspended flag, anomalies panel"
```

---

## Task 11: Rimuovere output legacy da assegnazioni

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsViewModel.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/ui/assignments/AssignmentsLegacyOutputRemovedTest.kt`

**Step 1: Write the failing test**

- nessun path UI `Genera output`.
- compile wiring senza use case output nella UI assegnazioni.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*AssignmentsLegacyOutputRemovedTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Rimuovi dialog/stato output wizard e wiring correlato da screen/viewmodel.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*AssignmentsLegacyOutputRemovedTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt composeApp/src/jvmTest/kotlin/org/example/project/ui/assignments/AssignmentsLegacyOutputRemovedTest.kt
git commit -m "refactor(assignments): remove legacy output wizard from UI flow"
```

---

## Task 12: Stampa Programma PDF

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/StampaProgrammaUseCase.kt`
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/feature/output/infrastructure/PdfProgramRenderer.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModel.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/StampaProgrammaUseCaseTest.kt`

**Step 1: Write the failing test**

- PDF creato in A4 verticale singola pagina.
- include settimane skipped nel flusso con marker.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*StampaProgrammaUseCaseTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Renderer PDF programma + call da workspace.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*StampaProgrammaUseCaseTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/feature/output/application/StampaProgrammaUseCase.kt composeApp/src/jvmMain/kotlin/org/example/project/feature/output/infrastructure/PdfProgramRenderer.kt composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/ProgramWorkspaceViewModel.kt composeApp/src/jvmTest/kotlin/org/example/project/feature/output/application/StampaProgrammaUseCaseTest.kt
git commit -m "feat(output): add single-page monthly program PDF printing"
```

---

## Task 13: Impostazioni Assegnazioni (UI + persistenza)

**Files:**
- Create: `composeApp/src/jvmMain/kotlin/org/example/project/ui/settings/AssignmentSettingsScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/config/UpdateSettingsStore.kt`
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/ui/settings/AssignmentSettingsScreenTest.kt`

**Step 1: Write the failing test**

- default strict ON, 4/2 settimane, pesi 2/1.
- persistenza parametri.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*AssignmentSettingsScreenTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Nuova schermata impostazioni assegnazioni e binding con store.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*AssignmentSettingsScreenTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/ui/settings composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt composeApp/src/jvmMain/kotlin/org/example/project/core/config/UpdateSettingsStore.kt composeApp/src/jvmMain/kotlin/org/example/project/core/di/AppModules.kt composeApp/src/jvmTest/kotlin/org/example/project/ui/settings/AssignmentSettingsScreenTest.kt
git commit -m "feat(settings): add assignment engine configuration UI"
```

---

## Task 14: Seed dev e pulizia wiring legacy

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/org/example/project/core/cli/SeedDatabase.kt`
- Modify: `data/part-types.json`
- Modify: `data/weekly-schemas.json`
- Modify: `README.md`
- Modify: `SPECIFICHE.md`
- Test: `composeApp/src/jvmTest/kotlin/org/example/project/core/cli/SeedDatabaseTest.kt`

**Step 1: Write the failing test**

- seed crea programma corrente + futuro (max 1 futuro), settimane, part types versionati, criteri default.

**Step 2: Run test to verify it fails**

Run: `./gradlew composeApp:jvmTest --tests "*SeedDatabaseTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- aggiorna seed al nuovo modello.
- aggiorna docs operative.

**Step 4: Run test to verify it passes**

Run: `./gradlew composeApp:jvmTest --tests "*SeedDatabaseTest*"`
Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/kotlin/org/example/project/core/cli/SeedDatabase.kt data/part-types.json data/weekly-schemas.json README.md SPECIFICHE.md composeApp/src/jvmTest/kotlin/org/example/project/core/cli/SeedDatabaseTest.kt
git commit -m "chore(seed): align development data with monthly program model"
```

---

## Task 15: Verifica end-to-end e hardening finale

**Files:**
- Modify: `docs/plans/2026-02-22-programma-mensile-design.md`
- Modify: `docs/plans/2026-02-22-programma-mensile-implementation.md`

**Step 1: Run full verification**

Run:
- `./gradlew composeApp:compileKotlinJvm`
- `./gradlew composeApp:jvmTest`
- `./gradlew composeApp:run` (smoke manuale)

Expected:
- compile OK
- test suite OK
- flusso UI: creazione mese guidata, update schemi, modifica settimana, autoassegnazione manuale, stampa PDF.

**Step 2: Manual QA checklist**

Verifica manuale:
- blocco creazione se mancano schemi al primo avvio.
- `SKIPPED` precompilate e riattivabili solo se non passate.
- aggiornamento programma da schemi mantiene assegnazioni combacianti.
- strict cooldown ON/OFF nel picker candidati.
- pannello anomalie in proclamatori dopo update con incompatibilità.

**Step 3: Commit**

```bash
git add -A
git commit -m "feat: monthly unified program workflow with schema-backed planning and assignment engine"
```

---

## Note esecuzione

- Ordine obbligatorio: Task 1 -> Task 15.
- Ogni task deve chiudersi con test verdi prima di passare al successivo.
- Se un test fallisce in modo inatteso, applicare `systematic-debugging` prima di modificare implementazione.

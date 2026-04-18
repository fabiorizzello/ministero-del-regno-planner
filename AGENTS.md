# ministero-del-regno-planner Development Guidelines

Auto-generated from feature plans. Last updated: 2026-04-18

## Active Technologies
- Kotlin 2.3.10 su JVM desktop con toolchain Java 21 + Compose Multiplatform 1.10.1, Material3, Coroutines 1.10.2, Arrow 2.2.1.1, SQLDelight 2.2.1, Voyager 1.0.0, Koin 4.1.1 (main)
- SQLite via SQLDelight (`MinisteroDatabase.sq`) con store/query esistenti per persone, assegnazioni, programmi e settimane (main)

- Kotlin 2.3.0 on JVM desktop with Java toolchain 21
- Compose Multiplatform 1.10.0, Material3, Coroutines 1.10.2
- Voyager Navigator 1.0.0, Koin 3.5.6, SQLDelight

## Stabilizzazione UX Programma Feature Notes

- The assignment picker must fully refresh suggestions when the `riposo` filter changes, without losing the active search text or showing stale rows.
- Week-part edits must preserve only assignments whose logical continuity key still exists after add/remove/reorder operations.
- `Salta settimana` stays available for past active weeks, while skipped weeks keep the reactivation flow and explicit success/error feedback.
- The studenti area keeps fuzzy search, explicit Italian empty states, page-driven scroll reset, and a fixed-height card action bar.
- Validation commands for this slice are `./gradlew :composeApp:jvmTest` and `./gradlew :composeApp:build` with JDK 21 / `JAVA_HOME` configured.

## Relevant Paths

- `composeApp/src/jvmMain/kotlin/org/example/project/ui/workspace/`
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/`
- `composeApp/src/jvmMain/kotlin/org/example/project/feature/weeklyparts/`
- `composeApp/src/jvmTest/kotlin/org/example/project/ui/workspace/`
- `composeApp/src/jvmTest/kotlin/org/example/project/ui/proclamatori/`
- `composeApp/src/jvmTest/kotlin/org/example/project/feature/weeklyparts/`
- `specs/009-stabilizza-ux-programma/`

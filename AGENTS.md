# ministero-del-regno-planner Development Guidelines

Auto-generated from feature plans. Last updated: 2026-04-11

## Active Technologies

- Kotlin 2.3.0 on JVM desktop with Java toolchain 21
- Compose Multiplatform 1.10.0, Material3, Coroutines 1.10.2
- Voyager Navigator 1.0.0, Koin 3.5.6, SQLDelight

## Admin Catalog Feature Notes

- The admin area stays secondary to the primary top bar: `Programma` and `Studenti` remain the only top-level operational tabs.
- `Diagnostica`, `Tipi parte`, and `Schemi settimanali` live inside a shared admin shell in `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/`.
- The two new admin destinations are read-only list-detail screens backed by `PartTypeStore` and `SchemaTemplateStore`.
- Validation commands for this slice are `./gradlew :composeApp:jvmTest` and, when the environment supports SQLDelight migration verification, `./gradlew :composeApp:build`.

## Relevant Paths

- `composeApp/src/jvmMain/kotlin/org/example/project/ui/admincatalog/`
- `composeApp/src/jvmTest/kotlin/org/example/project/ui/admincatalog/`
- `specs/008-admin-part-catalog/`

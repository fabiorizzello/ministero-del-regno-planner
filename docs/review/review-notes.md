# Review Notes — Consolidato e Deduplicato (2026-03-09)

## Findings aperti (ordinati per severità)

### High

2. `feature/output`: tutti e tre i use case usano `throw IllegalStateException` invece di `Either<DomainError, T>`.
   - Pattern inconsistente con tutto il resto del codebase; il ViewModel usa `runCatching` come workaround.
   - `GeneraImmaginiAssegnazioni` aggiunge un ulteriore throw in `renderTicketImage()` — espansione del problema con nuovi metodi.
   - Evidenze (righe aggiornate): `StampaProgrammaUseCase.kt:116`, `GeneraImmaginiAssegnazioni.kt:86,107,264`.


### Medium

2. Copertura integration migliorabile sui boundary esterni.
   - HTTP client e PDF rendering ancora poco coperti.
   - `PdfAssignmentsRenderer` ha zero test unitari su `renderWeeklyAssignmentsPdf()` e `renderPersonSheetPdf()`.
   - Evidenze: `GitHubSchemaCatalogDataSource.kt:40`, `GitHubReleasesClient.kt:38`, `PdfAssignmentsRenderer.kt`.

4. `GeneraImmaginiAssegnazioni`: logica PDF→PNG (`renderPdfToPngFile`) nel layer application.
   - `Loader.loadPDF`, `PDFRenderer`, `ImageIO.write` sono infrastruttura; dovrebbero stare in `infrastructure/`.
   - Evidenza: `GeneraImmaginiAssegnazioni.kt:335-341`, `:13-14`.



15. `feature/updates` — zero test coverage.
    - `VerificaAggiornamenti`, `AggiornaApplicazione`, `GitHubReleasesClient`, `UpdateScheduler` non hanno nessun test.
    - Evidenza: `feature/updates/application/*.kt`, `feature/updates/infrastructure/*.kt`.

### Low

- `SqlDelightSchemaUpdateAnomalyStore.append()` non è idempotente: ogni chiamata genera nuovo UUID, retry accumula duplicati.
- **Finding 24**: `WeekPlan` init block lancia `IllegalArgumentException` se `weekStartDate` non è lunedì. Pattern non-funzionale. Alternativa DDD: smart constructor `WeekPlan.of()` → `Either`.
  - Evidenze: `WeekPlan.kt:22-26`, `DomainErrorMappingWeeklyPartsUseCaseTest.kt:92`.
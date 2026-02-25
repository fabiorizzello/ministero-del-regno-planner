<!-- SYNC IMPACT REPORT
Version change: 1.0.0 → 1.0.1
Modified principles:
  - III. UX Consistency: removed i18n/resource-file requirement; app is Italian-only
  - V. Beautiful UI: removed dark theme requirement; single light theme only
Added sections: N/A
Removed sections: N/A
Templates requiring updates: N/A — patch-level changes, no template impact
Follow-up TODOs: None.
-->

# Ministero del Regno Planner Constitution

## Core Principles

### I. Vertical Slices + DDD (NON-NEGOTIABLE)

The codebase is organized as **feature-based vertical slices**. Each feature is
self-contained and owns everything it needs: domain models, use cases, infrastructure,
and DI wiring.

Domain-Driven Design governs where logic lives:

- **Business logic MUST live inside domain models.** Models are rich, not anemic.
  A model that knows a rule enforces it — it does not expose raw data for callers to
  check. Getters and setters without logic are a design smell.
- **Use Cases exist only when logic strictly requires IO.** A Use Case orchestrates
  a domain operation that cannot happen without reading or writing to an external
  system (database, network, file system). If no IO is needed, there is no Use Case —
  the domain model handles it directly.
- **Thin infrastructure adapters.** Infrastructure code (SQLDelight stores, HTTP
  clients, JSON parsers) MUST contain zero business logic. They translate between
  external representations and domain models — nothing more.
- **No cross-feature imports of internal types.** Features MUST NOT import each
  other's internal classes. Shared concepts MUST be promoted to a shared module or
  communicated through explicit interfaces.

**Rationale**: Anemic models scatter business rules across the codebase, making them
impossible to find, test, or trust. Rich models are self-documenting and self-enforcing.
Use Cases scoped strictly to IO keep orchestration logic minimal and honest.

### II. Test-Driven Quality

Testing is a first-class concern and MUST be treated as part of implementation, not
an afterthought:

- Domain model behavior MUST be unit-tested exhaustively — models are pure Kotlin
  and have no excuse for low coverage
- Use Cases MUST have integration tests that exercise real IO paths
  (SQLDelight in-memory DB, fake HTTP clients)
- UI components MUST have tests for all non-trivial rendering and interaction behavior
- Tests MUST be written to fail before implementation begins (Red-Green-Refactor)
- `./gradlew test` MUST pass at all times on the `main` branch
- Domain model test coverage MUST remain above 90%; Use Case coverage above 80%

**Rationale**: Rich domain models with real logic are the most valuable code in the
project — they deserve the most thorough tests. Tests also serve as executable
documentation of business rules.

### III. UX Consistency

User experience MUST be coherent and predictable across the entire application:

- All screens MUST use the shared design system — shared colors, typography scale,
  spacing tokens, and reusable Composables; ad-hoc styling is prohibited
- Navigation patterns MUST be consistent — identical user interactions MUST produce
  identical outcomes regardless of which screen they originate from
- Loading, error, and empty states MUST be explicitly handled and visually represented
  in every screen — silent failures are prohibited
- All interactive elements MUST carry accessibility content descriptions and meet
  minimum touch target sizes (48dp)
- The application is Italian-only; all user-visible strings MUST be hardcoded in
  Italian directly in Composables — no resource file indirection required

**Rationale**: The application serves a specific religious community that relies on it
regularly. Inconsistency erodes trust; clarity and respect in design are non-optional.

### IV. Performance by Design

The application MUST meet these performance targets on the target desktop JVM platform:

- Application cold startup MUST complete in under 3 seconds on reference hardware
- UI MUST render at 60 fps during all user interactions — no frame drops during
  animations, list scrolling, or state transitions
- All SQLDelight database reads MUST complete in under 100 ms; writes under 200 ms
- All network calls MUST be non-blocking using Kotlin Coroutines; the UI MUST remain
  fully responsive during all async operations
- No Coroutine or Flow leaks are permitted — every collector MUST have a defined scope

**Rationale**: Poor performance is a UX failure. Desktop users have higher responsiveness
expectations than web users. Performance MUST be designed in from the start, not
retrofitted.

### V. Beautiful UI

Visual quality is a non-negotiable product requirement, not a stretch goal:

- Every screen MUST follow Material Design 3 guidelines; intentional deviations MUST
  be documented in the feature spec with a rationale
- Color palette, typography scale, and spacing system MUST be defined centrally in the
  design system and applied consistently — magic values are prohibited
- Animations and transitions MUST feel natural and purposeful using Compose animation
  APIs; jarring or purely decorative animations are prohibited
- No placeholder content, lorem ipsum, or stub UI MUST ever exist on `main`; all
  visible states MUST reflect real data or an explicit empty/error state
- The application uses a single light theme; dark theme is explicitly out of scope

**Rationale**: Beauty communicates care. A polished, intentional interface increases
adoption and reflects respect for the community the tool serves.

## Technology Stack

The following technology choices are ratified and MUST be used for their respective
concerns. Alternatives require a formal amendment:

- **Language**: Kotlin (targeting JVM via Kotlin Multiplatform)
- **UI Framework**: Compose Multiplatform — all UI MUST be declared as Composables
- **Database**: SQLDelight — all SQL MUST be declared in `.sq` schema files;
  raw JDBC or direct SQL strings in Kotlin are prohibited
- **DI**: Manual dependency injection via feature-scoped `di` modules;
  annotation-processor-based DI frameworks are prohibited
- **Build**: Gradle with Kotlin DSL — build scripts MUST be deterministic and
  reproducible; version catalog MUST be used for dependency management
- **Testing**: `jvmTest` source set using Kotlin Test and Compose test APIs
- **Async**: Kotlin Coroutines with StateFlow/SharedFlow; callback-based async and
  RxJava are prohibited

## Development Workflow

- All changes MUST be made on a feature branch; direct commits to `main` are prohibited
- Every pull request MUST pass `./gradlew test` before merge; CI failure blocks merge
- Code review MUST explicitly verify: domain model richness (no anemic models), Use
  Case scope (IO only), UX consistency with the design system, and absence of
  performance regressions
- Complexity violations (any deviation from a Core Principle) MUST be justified in the
  PR description and logged in the `Complexity Tracking` table of the relevant `plan.md`
- Git commits MUST follow conventional commit format: `feat:`, `fix:`, `refactor:`,
  `test:`, `docs:`, `chore:`
- A feature is only considered done when: all tasks are complete, all tests pass,
  the UI has been visually verified against the design system, and no open TODOs remain

## Governance

This Constitution supersedes all other coding guidelines and team conventions. It is
the authoritative reference for all technical decisions on this project.

**Amendment procedure**:
1. Propose the change in a pull request with full rationale and a migration plan
2. At least one other contributor MUST review and approve the amendment
3. The version MUST be bumped per the versioning policy below
4. `Last Amended` date MUST be updated to the amendment date

**Versioning policy**:
- MAJOR: Removal or fundamental redefinition of an existing principle
- MINOR: New principle or section added; materially expanded guidance
- PATCH: Clarifications, wording refinements, typo fixes, non-semantic updates

**Compliance**: All PRs and code reviews MUST verify compliance with this Constitution.
Undocumented deviations from any Core Principle are grounds for PR rejection.

**Version**: 1.0.1 | **Ratified**: 2026-02-25 | **Last Amended**: 2026-02-25

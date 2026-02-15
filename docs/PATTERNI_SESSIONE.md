# Pattern Consolidati (Sessione Corrente)

## 1. Navigazione
- Usare Voyager come standard navigazione.
- Livello app: sezione principale con `Navigator` e `replaceAll` tra tab.
- Livello feature: flow interno dedicato con `Navigator` annidato (es. `Elenco -> Nuovo/Modifica -> Elenco`).
- Breadcrumb come proiezione dello stato navigazione corrente.

## 2. Dependency Injection e Config
- Usare Koin come container unico DI.
- Risolvere use case/repository da Koin nelle schermate.
- Evitare container statici custom non necessari.
- Usare Multiplatform Settings per impostazioni persistenti (es. dimensione/stato finestra).

## 3. UI Pattern
- Tabella: componenti condivisi (`TableStandard.kt`) con griglia leggera e allineamento rigoroso header/body.
- Banner: `FeedbackBanner` tipizzato, dismissibile, con supporto `details` per i successi.
- Tema: light mode Material3 come default.
- Evitare `SelectionContainer` globale; usare selezione locale per prevenire crash su cambi gerarchia UI.

## 4. Persistenza e DRY
- Query/Write separati (query side per ricerca, aggregate store per load/persist).
- Mapper SQLDelight condivisi quando usati in piu' classi (es. mapping riga `person` -> `Proclamatore`).

## 5. Convenzioni Operative
- Dopo ogni refactor strutturale: compilazione modulo `:composeApp:compileKotlinJvm`.
- Preferire componenti condivisi e funzioni helper rispetto a duplicazioni locali.

# UI Standard (Compose Desktop)

## 1. Tabella
- Usare `TableColumnSpec` per definire intestazioni e pesi colonna.
- Usare `StandardTableHeader` per la riga header.
- Usare `StandardTableViewport` per il contenitore con bordo e area scroll.
- Usare `Modifier.standardTableCell(lineColor)` per tutte le celle riga.
- Usare `StandardTableEmptyRow` per lo stato vuoto.
- Mostrare sempre la tabella anche senza righe.
- Usare un colore griglia morbido (`outlineVariant` con alpha) per evitare linee troppo pesanti in light mode.
- Mantenere densita' compatta (padding standard celle).
- Header e body devono considerare la stessa larghezza utile (incluso spazio scrollbar) per evitare disallineamenti colonne.

## 2. Banner Successo/Errore
- Usare stato tipizzato `FeedbackBannerModel?` nella screen.
- Popolare `kind` con `FeedbackBannerKind.SUCCESS` o `FeedbackBannerKind.ERROR`.
- Mostrare con `FeedbackBanner(notice)` in posizione stabile nella pagina.
- Usare banner persistente fino a nuovo evento (non snackbar temporaneo).
- Il banner e' dismissibile con azione `X`.
- Per successi, valorizzare anche `details` (es. entita' coinvolta e timestamp) quando utile.
- Evitare palette locali nella feature; usare il componente condiviso.

Riferimento implementazione:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/FeedbackBanner.kt`

## 3. Spacing Centralizzato
- Usare `MaterialTheme.spacing.*` per tutti i valori di padding e spacing.
- Non usare valori `dp` hardcoded per padding/margin/spacing nei composable.
- Token disponibili: `xxs` (2), `xs` (4), `sm` (6), `md` (8), `lg` (12), `xl` (16), `xxl` (32), `cardRadius` (12).
- Valori strutturali (larghezze colonne, dimensioni icone, altezze pulsanti, elevazioni) restano in `dp` diretto.
- Fornito tramite `CompositionLocalProvider` in `AppTheme`; accessibile con `MaterialTheme.spacing`.

Riferimento implementazione:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/theme/AppSpacing.kt`

## 4. Selezione Testo
- Non usare `SelectionContainer` globale nel tema/app root.
- Applicare `SelectionContainer` solo in aree locali realmente utili (messaggi, output diagnostico, ecc.).

## 5. Navigazione Settimana (WeekNavigator)
- Usare il componente condiviso `WeekNavigator` per tutte le schermate che necessitano di navigazione settimanale.
- Parametri: `monday`, `sunday`, `indicator` (WeekTimeIndicator), `enabled`, callbacks `onPrevious`/`onNext`.
- La settimana selezionata e' sincronizzata tra tab tramite `SharedWeekState` (singleton Koin).
- Formato data: italiano ("d MMMM yyyy").

Riferimento implementazione:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/WeekNavigator.kt`

## 6. Card Assegnazione (PartAssignmentCard)
- Ogni parte settimanale e' una card con header (numero, label tipo, chip regola sesso) e righe slot.
- Parti fixed: solo label informativo "(parte fissa)", nessuno slot.
- Parti con 1 persona: slot unico senza label ruolo.
- Parti con 2 persone: slot "Proclamatore" e "Assistente".
- Slot assegnato: nome + pulsante rimozione (X). Slot vuoto: pulsante "Assegna".

Riferimento implementazione:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt`

## 7. Dialog Selezione Proclamatore (PersonPickerDialog)
- Dialog modale con ricerca testuale, toggle ordinamento (Globale/Per parte), tabella suggerimenti.
- Colonne: Nome, Ultima (globale), Ultima (questa parte), pulsante Assegna.
- Distanza in settimane: "Mai assegnato", "Questa settimana", "N settimane fa".
- Ordinamento: null-first (mai assegnato = priorita' massima).
- Filtri hard applicati server-side; ricerca nome/cognome client-side.

Riferimento implementazione:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/assignments/AssignmentsComponents.kt`

## 8. Navigazione tra Sezioni (LocalSectionNavigator)
- Usare `LocalSectionNavigator.current` per navigare tra tab dall'interno di una schermata.
- Fornito tramite `CompositionLocalProvider` in `AppScreen`.
- Utile per link bidirezionali (es. "Vai allo schema" ↔ "Vai alle assegnazioni").

Riferimento implementazione:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`

---
name: compose-voyager-navigation-pattern
description: Applicare il pattern di navigazione Voyager in questo progetto Kotlin Compose Desktop. Usare questa skill quando si crea o rifattorizza navigazione tra sezioni principali o flow di feature (elenco/nuovo/modifica), con stack chiaro e breadcrumb coerenti.
---

# Compose Voyager Navigation Pattern

## Obiettivo

Usare Voyager in modo uniforme su due livelli:
- livello app: navigazione sezioni principali;
- livello feature: navigator annidato per flow locale.

## Workflow

1. Definire screen Voyager tipizzati (`data object`/`data class`) per ogni stato navigabile.
2. Livello app:
3. creare `Navigator(initialScreen)`;
4. usare `navigator.replaceAll(...)` quando si cambia sezione da rail/tab.
5. Livello feature:
6. creare `Navigator` annidato dentro la screen della feature;
7. usare `push(...)` per dettaglio/creazione;
8. usare `replaceAll(listScreen)` su salvataggio, annulla e ritorno breadcrumb.
9. Derivare breadcrumb e titolo dalla screen corrente (non da stato duplicato).

## Regole

- Evitare stato route locale parallelo quando Voyager e' presente.
- Usare `replaceAll` per tornare alla root del flow, evitare stack sporchi.
- Tenere le transizioni coerenti:
- `Elenco -> Nuovo/Modifica` con `push`.
- `Nuovo/Modifica -> Elenco` con `replaceAll`.

## Riferimenti

- App-level: `composeApp/src/jvmMain/kotlin/org/example/project/ui/AppScreen.kt`
- Feature-level (esempio): `composeApp/src/jvmMain/kotlin/org/example/project/ui/proclamatori/ProclamatoriScreen.kt`

## Checklist
- App section navigation su Voyager.
- Feature flow su navigator annidato.
- Breadcrumb calcolato da screen corrente.
- Nessun route state duplicato non necessario.

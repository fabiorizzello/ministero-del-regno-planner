# Backlog Tecnico

Miglioramenti tracciati emersi da code review e sviluppo. Non bloccanti per il rilascio.

## Refactoring

### Ridurre responsabilita' ProclamatoriViewModel
- **Origine:** Code review stabilita' (SOLID-1)
- **Problema:** Il ViewModel gestisce: lista, paginazione, ordinamento, selezione, form (crea+modifica), verifica duplicati, eliminazione singola, eliminazione batch, attivazione/disattivazione batch, import JSON. Ha 10 dipendenze e ~520 righe.
- **Fix:** Considerare split in `ProclamatoriListViewModel` e `ProclamatoreFormViewModel`.
- **File coinvolti:** `ProclamatoriViewModel.kt`, `ProclamatoriScreen.kt`

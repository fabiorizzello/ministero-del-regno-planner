# UI Contract: Students Search, Pagination, and Cards

## Scope

Copre la sezione studenti nelle viste tabella e card e i comportamenti condivisi di ricerca locale.

## Contract 1: Fuzzy search

1. La ricerca studenti MUST tollerare refusi lievi e input incompleto.
2. I risultati MUST essere ordinati per distanza stringa percepita, non per semplice `contains`.
3. Se la ricerca non restituisce risultati, la UI MUST mostrare uno stato vuoto esplicito e utile in italiano.
4. Il comportamento di ranking MUST essere coerente con la ricerca della modale di assegnazione.

## Contract 2: Terminology and labeling

1. La colonna oggi chiamata "capability" MUST usare una dicitura italiana coerente col dominio.
2. Ogni etichetta visibile introdotta o toccata dalla feature MUST essere in italiano.
3. Nessun termine tecnico inglese residuo MUST restare nelle superfici modificate se esiste un equivalente utente chiaro.

## Contract 3: Pagination and scroll reset

1. Ogni cambio pagina MUST riportare la vista all'inizio del contenuto paginato.
2. Il reset scroll MUST funzionare sia in presenza di ricerca sia senza ricerca.
3. Il cambio pagina MUST mantenere ordinamento e selezione coerenti con lo stato corrente.

## Contract 4: Stable card action bar

1. Nella vista card, la barra azioni con "Modifica" e "Rimuovi" MUST mantenere altezza costante.
2. Il numero di capability/chip mostrati MUST NOT comprimere o deformare i pulsanti.
3. I pulsanti MUST restare raggiungibili con mouse e tastiera e usare feedback hover/focus coerenti col design system.

## Test Implications

- Test ranking fuzzy con typo e input parziale.
- Test ViewModel/Composable sul reset scroll al cambio pagina.
- Test di layout/card per garantire toolbar stabile con molte capability.

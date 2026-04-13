# Research: Catalogo Admin Tipi Parte e Schemi

## Decision 1: Posizionare le nuove UI in una navigazione admin secondaria separata dai tab top-level

**Decision**: introdurre un piccolo navigatore admin secondario raggiungibile dall'area utility amministrative gia' esistente, invece di aggiungere nuovi tab principali accanto a `Programma` e `Studenti`.

**Rationale**:
- La spec richiede esplicitamente che queste UI restino strumenti admin e non sezioni top-level.
- `AppScreen.kt` oggi tratta `Diagnostica` come destinazione secondaria richiamata da azione toolbar, non come tab centrale.
- Il pattern UX piu' coerente e' raggruppare strumenti di verifica e governo in una stessa famiglia di navigazione secondaria con sezione attiva evidente.

**Alternatives considered**:
- Aggiungere due nuovi tab top-level: scartato per violazione esplicita di scope e per rumore nella navigazione primaria.
- Inserire entrambe le viste dentro la schermata Diagnostica: scartato perche' mescola tooling tecnico e consultazione di catalogo dominio.

## Decision 2: Usare un pattern data-dense con lista + dettaglio contestuale

**Decision**: per entrambe le schermate adottare un layout desktop ad alta densita' informativa con colonna di selezione e pannello di dettaglio contestuale, mantenendo il dettaglio nella stessa esperienza.

**Rationale**:
- La richiesta utente cita `ui-ux-pro-max` per valutare UX e collocazione; la ricerca ha indicato il pattern `data-dense + drill-down` come il piu' adatto a strumenti admin desktop.
- La consultazione richiede scansione rapida di molti elementi, selezione attiva chiara e assenza di modalita' edit.
- Il codebase usa gia' `WorkspaceStatePane` e componenti coerenti per loading/error; il pattern lista + dettaglio si integra bene senza creare wizard o dialog complessi.

**Alternatives considered**:
- Griglia a card senza dettaglio laterale: scartata per scarsa densita' e peggiore confrontabilita' tra elementi.
- Tabella full-screen con drawer/modal di dettaglio: scartata perche' aggiunge passaggi e riduce la lettura immediata.

## Decision 3: Riutilizzare gli store esistenti invece di introdurre nuovi boundary di dominio

**Decision**: basare le nuove viste sui dati letti da `PartTypeStore.allWithStatus()` e `SchemaTemplateStore.listAll()`, arricchendo lato application/UI solo le projection di consultazione necessarie.

**Rationale**:
- `PartTypeStore` espone gia' `allWithStatus()` e quindi copre il requisito di distinguere attivi/disattivi.
- `SchemaTemplateStore` espone `listAll()` e `findByWeekStartDate()`, sufficienti per popolare indice settimane e dettaglio.
- La costituzione impone thin adapters e no business logic in UI: riusare boundary esistenti evita duplicazioni e mantiene il dominio invariato.

**Alternatives considered**:
- Nuovi store read-only separati: scartati per ridondanza prematura.
- Accesso diretto SQL da ViewModel/UI: scartato per violazione architetturale e scarsa testabilita'.

## Decision 4: Modellare gli stati UI in modo esplicito e immutabile

**Decision**: usare ViewModel con stato immutable orientato a selezione, caricamento, errore, empty e dettaglio corrente, evitando flag sparsi e collezioni mutabili.

**Rationale**:
- Le linee guida `ui-ux-pro-max` per `jetpack-compose` enfatizzano UDF e `UiState` esplicito.
- La costituzione richiede gestione esplicita di loading/error/empty e test UI non banali.
- Il progetto usa gia' `StateFlow` e pattern analoghi in altre schermate.

**Alternatives considered**:
- Stato sparso tra composable e remember locali: scartato perche' fragile, meno testabile e incoerente con il codebase.

## Decision 5: Definire contratti UI espliciti per navigazione admin e consultazione read-only

**Decision**: produrre due contratti di planning: uno per il comportamento della navigazione admin secondaria e uno per il comportamento read-only condiviso tra catalogo tipi parte e schemi settimanali.

**Rationale**:
- La feature non espone API esterne, ma introduce interazioni UI non banali e cross-screen.
- I contratti aiutano a fissare selection state, assenza di CTA mutanti, stati empty/error/loading e semantica di accesso.

**Alternatives considered**:
- Nessun contratto dedicato: scartato perche' perderebbe chiarezza sui comportamenti condivisi e sui criteri di test.

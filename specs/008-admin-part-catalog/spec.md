# Feature Specification: Catalogo Admin Tipi Parte e Schemi

**Feature Branch**: `008-admin-part-catalog`  
**Created**: 2026-04-11  
**Status**: Draft  
**Input**: User description: "dobbiamo creare 2 nuove interfacce per visualizzare i part types e per visualizzare lo schema delle parti per settimana. solo visualizzazione admin. usare $ui-ux-pro-max per valutare dove visualizzare le UI e come creare la ux. sono interfacce come la diagnostica che sono solo admin quindi non tabs principali"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultare il catalogo tipi di parte (Priority: P1)

L'amministratore vuole aprire una schermata dedicata che mostri tutti i tipi di parte
attualmente noti al sistema, con i loro attributi principali, per verificare rapidamente
come e' strutturato il catalogo usato nelle settimane e nelle assegnazioni.

**Why this priority**: Il catalogo tipi parte e' la base del dominio. Senza una
consultazione chiara, il lavoro di verifica amministrativa resta opaco e dipende da
diagnostica tecnica o da form secondari.

**Independent Test**: Aprire l'area amministrativa dedicata ai tipi di parte e verificare
che l'elenco mostri tutti i tipi presenti, con stato e dettagli leggibili senza entrare
in flussi di modifica.

**Acceptance Scenarios**:

1. **Given** esistono tipi di parte attivi e disattivi nel catalogo, **When** l'amministratore
   apre la schermata catalogo tipi parte, **Then** il sistema mostra l'elenco completo con
   indicatori chiari di stato e i principali attributi descrittivi.
2. **Given** l'amministratore seleziona un tipo di parte nell'elenco, **When** la selezione
   cambia, **Then** il sistema mostra un pannello di dettaglio coerente con l'elemento scelto
   senza avviare modalita' di modifica.
3. **Given** il catalogo non contiene alcun tipo di parte, **When** l'amministratore apre la
   schermata, **Then** il sistema mostra uno stato vuoto esplicito con spiegazione in italiano.

---

### User Story 2 - Consultare lo schema settimanale delle parti (Priority: P1)

L'amministratore vuole aprire una schermata dedicata agli schemi settimanali per capire,
settimana per settimana, quali tipi di parte compongono il catalogo operativo usato per la
generazione e l'aggiornamento dei programmi.

**Why this priority**: Gli schemi settimanali sono il secondo livello essenziale del catalogo.
Rendere visibile la loro composizione evita verifiche indirette nei programmi mensili o nei
log diagnostici.

**Independent Test**: Aprire la schermata schemi settimanali, selezionare una settimana del
catalogo e verificare che la composizione delle parti sia visibile in ordine corretto e in
modalita' sola lettura.

**Acceptance Scenarios**:

1. **Given** esistono schemi settimanali nel catalogo, **When** l'amministratore apre la
   schermata schemi, **Then** il sistema mostra una collezione navigabile delle settimane
   disponibili con identificazione chiara della settimana selezionata.
2. **Given** l'amministratore seleziona una settimana del catalogo, **When** visualizza il
   dettaglio, **Then** il sistema mostra le parti previste in ordine, con riferimenti leggibili
   ai relativi tipi di parte.
3. **Given** una settimana non ha parti associate oppure il catalogo schemi e' vuoto,
   **When** l'amministratore apre il dettaglio, **Then** il sistema mostra uno stato vuoto
   esplicito e non un pannello ambiguo o spezzato.

---

### User Story 3 - Accedere a strumenti admin secondari senza appesantire la navigazione principale (Priority: P2)

L'amministratore vuole raggiungere queste due interfacce da un'area amministrativa secondaria,
coerente con la Diagnostica, senza trasformarle in tab principali dell'applicazione.

**Why this priority**: Il requisito di prodotto richiede che questi strumenti restino
amministrativi e non entrino nella navigazione primaria usata nel lavoro quotidiano.

**Independent Test**: Partendo dall'applicazione aperta, raggiungere entrambe le interfacce
tramite la navigazione secondaria admin e tornare al contesto precedente senza confondere le
sezioni principali.

**Acceptance Scenarios**:

1. **Given** l'utente si trova nell'applicazione, **When** raggiunge gli strumenti admin,
   **Then** puo' accedere sia al catalogo tipi parte sia agli schemi settimanali senza usare
   nuovi tab principali top-level.
2. **Given** l'utente si trova in una delle due schermate admin, **When** passa all'altra,
   **Then** il sistema mantiene un'indicazione visiva chiara della sezione attiva.
3. **Given** l'utente non ha bisogno di modificare dati, **When** esplora queste schermate,
   **Then** non incontra CTA di creazione, modifica, eliminazione o salvataggio che suggeriscano
   capability non previste.

### Edge Cases

- Catalogo tipi parte vuoto: la schermata MUST mostrare uno stato vuoto esplicito, non una tabella vuota priva di contesto.
- Catalogo schemi vuoto: la schermata MUST spiegare che non ci sono settimane disponibili da consultare.
- Presenza di tipi di parte disattivi: questi elementi MUST restare consultabili e distinguibili visivamente dagli attivi.
- Settimana con molte parti: il dettaglio MUST restare leggibile anche con elenchi lunghi, preservando ordine e scansione visiva.
- Nomi lunghi o codici tecnici: il layout MUST evitare sovrapposizioni e mantenere accessibile il contenuto completo.
- Errore di caricamento di una sola schermata admin: il sistema MUST mostrare un errore localizzato alla schermata interessata senza compromettere l'intera navigazione principale.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST offrire una schermata amministrativa di sola consultazione per il catalogo dei tipi di parte.
- **FR-002**: La schermata tipi di parte MUST mostrare per ogni elemento almeno codice, nome descrittivo, numero di persone richieste, regola di composizione e stato attivo/disattivo.
- **FR-003**: La schermata tipi di parte MUST consentire di selezionare un elemento e visualizzarne il dettaglio in un'area dedicata della stessa esperienza, senza aprire un flusso di modifica.
- **FR-004**: Il sistema MUST offrire una schermata amministrativa di sola consultazione per gli schemi settimanali del catalogo.
- **FR-005**: La schermata schemi settimanali MUST consentire di selezionare una settimana e visualizzarne la sequenza ordinata delle parti previste.
- **FR-006**: Ogni parte mostrata nel dettaglio di uno schema MUST identificare chiaramente il tipo di parte a cui si riferisce.
- **FR-007**: Entrambe le schermate MUST essere raggiungibili da una navigazione amministrativa secondaria coerente con la natura "solo admin" della funzionalita'.
- **FR-008**: Il sistema MUST NOT introdurre queste schermate come nuovi tab principali dell'applicazione.
- **FR-009**: Entrambe le schermate MUST esporre stati espliciti di caricamento, vuoto ed errore in italiano.
- **FR-010**: Entrambe le schermate MUST usare un'indicazione visiva chiara della sezione attiva e dell'elemento selezionato.
- **FR-011**: Le schermate MUST essere chiaramente in modalita' sola lettura e MUST NOT esporre azioni di creazione, modifica, eliminazione, riordino o salvataggio.
- **FR-012**: La struttura UX MUST privilegiare una consultazione rapida ad alta densita' informativa con pattern lista + dettaglio o drill-down leggero, coerente con gli strumenti amministrativi desktop dell'applicazione.
- **FR-013**: La navigazione secondaria admin MUST permettere di passare tra "Tipi parte", "Schemi settimanali" e le altre utility amministrative senza perdere il contesto.

### Key Entities *(include if feature involves data)*

- **VistaTipoParte**: rappresentazione consultabile di un tipo di parte con identificativo leggibile, etichetta, stato, numero di persone richieste, regola di composizione e altri metadati utili alla verifica amministrativa.
- **VistaSchemaSettimanale**: rappresentazione consultabile di una settimana del catalogo con data di riferimento e lista ordinata delle parti previste.
- **RigaSchemaSettimanale**: singola voce dello schema, collegata a un tipo di parte specifico e mostrata secondo l'ordine ufficiale della settimana.
- **NavigazioneAdminSecondaria**: insieme delle destinazioni amministrative non principali, pensate per utilita' di verifica e governo e separate dalle sezioni top-level operative.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un amministratore puo' raggiungere una delle due nuove schermate in non piu' di 2 passaggi dalla sua area amministrativa di ingresso.
- **SC-002**: Un amministratore puo' identificare il dettaglio di un tipo di parte o di una settimana selezionata in meno di 10 secondi senza dover aprire dialog aggiuntivi.
- **SC-003**: Il 100% delle schermate coinvolte mostra uno stato esplicito per caricamento, vuoto ed errore, senza aree mute o contenitori vuoti non spiegati.
- **SC-004**: Nessuna delle due nuove funzionalita' introduce nuovi tab principali nella navigazione top-level.

## Assumptions

- Le due interfacce sono destinate esclusivamente a consultazione amministrativa e non fanno parte dei flussi operativi quotidiani principali.
- La posizione UX piu' coerente e' un'area secondaria admin, allineata al pattern della Diagnostica, invece di nuove tab top-level.
- La UX deve privilegiare densita' informativa e drill-down leggero: elenco navigabile con dettaglio contestuale, selezione attiva evidente e assenza di CTA distrattive.
- Il catalogo esistente dei tipi di parte e degli schemi settimanali e' la fonte dati da rappresentare; la feature non amplia il perimetro con operazioni di editing.

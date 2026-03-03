# Feature Specification: Allineamento UI/UX Sketch Applicazione

**Feature Branch**: `[001-align-sketch-ui]`  
**Created**: 2026-02-27  
**Status**: Draft  
**Input**: User description: "allineiamo la ui e ux di applicazione a quella dello sketch. se necessario cambiamo altri spec per allinear. stato attuale: [Image #1] . dobbiamo anche rendere la top bar draggabile in modo da draggare la window, e doppio clicc toggla maximize e minimize. se necessario svincoliamoci da MUI e usiamo stili custom. deve essere come sketch in docs"

## Assumptions

- Il reference primario per layout e linguaggio visivo e `docs/sketches/workspace-reference-board-modes.html`; `docs/sketches/workspace-style-options.html` resta un supporto secondario.
- Il doppio click della top bar segue convenzione desktop: alterna finestra massimizzata e finestra ripristinata; la minimizzazione resta disponibile tramite controllo dedicato.
- Le funzionalita attuali devono rimanere invariate; il cambiamento richiesto e principalmente grafico/UX e di interazione finestra.
- Nel Programma, il feed attivita resta integrato nel pannello destro; nelle altre sezioni il feedback contestuale puo restare nel formato attuale.

## Clarifications

### Session 2026-02-27

- Q: Quale sketch e la fonte primaria per l'allineamento UI/UX? → A: `workspace-reference-board-modes.html`.
- Q: Dove devono vivere le impostazioni (attualmente solo assegnazione)? → A: Tutte in Programma; nessuna sezione Impostazioni dedicata.
- Q: Quale area top bar abilita drag e doppio click finestra? → A: Tutta la top bar non interattiva; esclusi controlli cliccabili.
- Q: Quali sezioni deve mostrare la top bar? → A: Solo Programma, Proclamatori, Diagnostica.
- Q: Quale policy tema vale per la feature? → A: Tema chiaro unico (dark mode fuori scope).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Programma Allineato allo Sketch (Priority: P1)

Come coordinatore, voglio che la schermata Programma abbia la stessa impostazione visiva e UX dello sketch (struttura, densita, componenti, colori), mantenendo tutte le azioni gia disponibili.

**Why this priority**: Il Programma e il flusso operativo principale; se non e coerente con lo sketch non viene raggiunto il risultato richiesto.

**Independent Test**: Aprire Programma con dataset completo, confrontare la schermata con lo sketch e completare il flusso base (selezione mese, autoassegna, stampa, gestione parti/assegnazioni) senza regressioni funzionali.

**Acceptance Scenarios**:

1. **Given** l'app aperta su Programma, **When** l'utente visualizza i pannelli principali, **Then** vede una struttura coerente allo sketch: colonna mesi a sinistra, area settimane/timeline al centro, azioni+copertura+feed+impostazioni assegnatore a destra.
2. **Given** un mese con settimane e parti, **When** l'utente naviga e modifica assegnazioni/parti, **Then** le azioni esistenti restano disponibili e producono gli stessi risultati funzionali attesi.
3. **Given** slot assegnati e non assegnati, **When** l'utente osserva card e chip, **Then** le informazioni sono leggibili in formato piu compatto e visivamente allineato al reference.

---

### User Story 2 - Linguaggio Visivo Coerente in Tutta l'Applicazione (Priority: P2)

Come utente, voglio coerenza visiva tra Programma, Proclamatori e Diagnostica, cosi l'intera applicazione appare come un unico prodotto desktop moderno e non come pagine eterogenee.

**Why this priority**: La richiesta esplicita riguarda l'applicazione nel suo insieme, non solo una singola schermata.

**Independent Test**: Navigare tra Programma, Proclamatori e Diagnostica e verificare coerenza di top bar, stile controlli, bordi/raggi, palette e gerarchie tipografiche; verificare che le impostazioni assegnazione siano raggiungibili dal solo Programma.

**Acceptance Scenarios**:

1. **Given** l'utente cambia sezione dalla top bar, **When** passa tra Programma, Proclamatori e Diagnostica, **Then** il linguaggio visivo rimane coerente (componenti, spessori, raggi, stati attivi/inattivi).
2. **Given** tema chiaro attivo, **When** l'utente usa l'app, **Then** contrasto e leggibilita restano adeguati e coerenti con lo stesso stile generale.
3. **Given** l'utente deve modificare impostazioni assegnazione, **When** opera nella schermata Programma, **Then** trova i controlli nel pannello destro senza passare da una sezione Impostazioni dedicata.

---

### User Story 3 - Interazioni Finestra Desktop Naturali (Priority: P3)

Come utente desktop, voglio poter trascinare la finestra dalla top bar e usare il doppio click per cambiare stato finestra rapidamente.

**Why this priority**: E requisito esplicito del task e parte essenziale della UX desktop con finestra custom.

**Independent Test**: Avviare l'app desktop, trascinare la finestra da qualunque area non interattiva della top bar e verificare doppio click su area non interattiva per alternare stato massimizzato/ripristinato.

**Acceptance Scenarios**:

1. **Given** finestra in stato normale, **When** l'utente trascina la top bar in una zona non interattiva, **Then** la finestra si sposta correttamente.
2. **Given** top bar visibile, **When** l'utente fa doppio click su area drag non interattiva, **Then** la finestra alterna tra massimizzata e ripristinata.
3. **Given** controlli o tab cliccabili in top bar, **When** l'utente interagisce con essi, **Then** non si attiva involontariamente il drag o il toggle stato finestra.
4. **Given** top bar con piu aree non interattive, **When** l'utente trascina o fa doppio click fuori dai controlli, **Then** il comportamento di drag/toggle e uniforme su tutta la superficie non interattiva.

---

### Edge Cases

- Doppio click su pulsanti/tab/icone in top bar non deve attivare cambio stato finestra.
- Trascinamento top bar mentre la finestra e massimizzata non deve generare stati incoerenti o blocchi di input.
- Le aree non interattive della top bar devono mantenere comportamento uniforme di drag e doppio click anche quando cambiano contenuti/tab visibili.
- Con dataset molto denso (molte settimane/parti/assegnazioni), il layout deve restare leggibile senza sovrapposizioni.
- In finestre ridotte (es. 1366x768), i controlli principali devono restare raggiungibili tramite scroll o riorganizzazione responsiva desktop.
- Se non ci sono settimane o assegnazioni, la nuova grafica deve mostrare stati vuoti chiari e coerenti con il design.
- La top bar non deve mostrare una sezione Impostazioni dedicata finche esistono solo impostazioni assegnazione integrate in Programma.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST applicare un linguaggio visivo coerente al reference primario `docs/sketches/workspace-reference-board-modes.html` per layout, gerarchia, densita, palette e forma dei componenti principali.
- **FR-002**: Il sistema MUST mantenere nella schermata Programma la struttura a tre aree funzionali (mesi, settimane/parti, azioni/feed/impostazioni assegnatore) con priorita visiva coerente allo sketch.
- **FR-003**: Il sistema MUST integrare nel Programma i controlli di impostazioni assegnatore senza perdere le azioni operative principali del pannello destro.
- **FR-003a**: Il sistema MUST trattare Programma come unico punto di accesso alle impostazioni finche il dominio espone solo impostazioni assegnazione.
- **FR-004**: Il sistema MUST preservare i comportamenti funzionali esistenti di Programma (creazione/selezione mese, aggiornamento schemi, autoassegna, stampa, modifica parti, assegnazione/rimozione, salto/riattivazione settimane, eliminazione mese).
- **FR-005**: Il sistema MUST rendere la top bar area di trascinamento finestra per interazioni desktop.
- **FR-006**: Il sistema MUST alternare lo stato finestra tra massimizzata e ripristinata con doppio click su area drag della top bar.
- **FR-007**: Il sistema MUST impedire che elementi interattivi della top bar attivino drag o toggle finestra in modo involontario.
- **FR-007a**: Il sistema MUST applicare drag e doppio click su tutta la superficie non interattiva della top bar (non limitata al solo brand), escludendo tab, pulsanti, icone e menu.
- **FR-008**: Il sistema MUST ridurre l'aspetto "web legacy" attraverso componenti visuali custom coerenti con lo sketch, senza dipendere dal look predefinito della libreria UI.
- **FR-009**: Il sistema MUST mantenere leggibilita e distinguibilita di card, chip, feed e metriche di copertura con dati pieni e con stati vuoti.
- **FR-010**: Il sistema MUST applicare lo stesso standard stilistico alle sezioni principali dell'applicazione (Programma, Proclamatori, Diagnostica) per evitare drift visivo tra pagine.
- **FR-011**: Il sistema MUST mostrare in top bar solo le sezioni Programma, Proclamatori e Diagnostica finche non emergono nuove aree funzionali autonome.

### Key Entities *(include if feature involves data)*

- **App Chrome Bar**: area superiore con branding, navigazione sezioni e controlli finestra; gestisce interazioni di drag e doppio click.
- **Workspace Panel**: contenitore visuale di ciascuna area principale (sinistra/centro/destra) con regole coerenti di header, bordo, densita e stati.
- **Program Action Block**: insieme di azioni operative e metriche (autoassegna, stampa, copertura, svuota/elimina, feed).
- **Assignment Settings Block**: blocco impostazioni di autoassegnazione integrato nel Programma (strict cooldown, pesi, cooldown) con salvataggio esplicito e unico punto di configurazione.
- **Activity Feed Entry**: evento utente o di sistema mostrato nel feed con severita, messaggio, dettagli e timestamp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In review interna su confronto visivo con `docs/sketches/workspace-reference-board-modes.html` (e verifica secondaria su dettagli da `workspace-style-options.html`), almeno il 90% dei criteri di allineamento (layout, gerarchia, forma componenti, densita, palette) risulta soddisfatto.
- **SC-002**: Il 100% delle azioni operative critiche del Programma completa con esito corretto nei test di fumo funzionali dopo il restyle.
- **SC-003**: Il 100% dei test manuali desktop conferma drag finestra e doppio click di toggle su tutte le aree non interattive della top bar, senza attivazioni accidentali sui controlli interattivi.
- **SC-004**: In almeno due risoluzioni target desktop (1366x768 e 1920x1080), tutte le azioni principali restano accessibili e nessun elemento primario risulta tagliato o sovrapposto.
- **SC-005**: Almeno l'80% dei tester interni valuta la nuova UI "coerente con lo sketch" e "piu moderna del baseline" in una verifica guidata.
- **SC-006**: Nel 100% dei percorsi di navigazione principali, l'utente raggiunge impostazioni assegnazione dal solo Programma senza tab Impostazioni dedicato.

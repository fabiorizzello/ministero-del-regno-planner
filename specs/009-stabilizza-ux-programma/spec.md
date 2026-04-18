# Feature Specification: Stabilizzazione UX Programma e Studenti

**Feature Branch**: `[009-stabilizza-ux-programma]`  
**Created**: 2026-04-18  
**Status**: Draft  
**Input**: User description: "bisogna fare delle sistemazioni all'applicazione tra grafiche e bug logica: switch cooldown in modale assegnazione -> quando cambia non viene ricaricato l'elenco dei proclamatori nella modale - dati stale modifica parti -> modificare una parte di settimana gia' assegnata toglie tutte le assegnazioni anche in realta' sono identiche alla versione precedente (es. 1 sola aggiunta o 1 sola rimozione di parte resetta tutto) salta settimana -> pulsante non disponibile se programma passato -> rendere visibile e usabile ricerca modale e studenti -> deve essere una ricerca fuzzy search con ordinamento per distanza stringa non un semplice contains tabella studenti -> capability traduci in italiano la colonna cooldown label -> traduci in 'riposo' o qualcosa di simile tabella studenti cambio pagina -> deve effettuare lo scroll top e non rimanere lo scroll nello stato attuale cards studenti -> pulsanti modifica ed elimina altezze costanti(altezza toolbar costante), altrimenti rimane schiacciato se troppe capabilities"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Assegnare proclamatori con dati aggiornati (Priority: P1)

Chi pianifica il programma vuole che la modale di assegnazione reagisca subito ai cambi
di filtro legati al riposo, cosi' da vedere un elenco proclamatori coerente con i criteri
correnti e non prendere decisioni su dati obsoleti.

**Why this priority**: La modale di assegnazione e' un flusso operativo centrale. Se mostra
risultati stale, l'utente puo' fare assegnazioni errate o perdere fiducia nel sistema.

**Independent Test**: Aprire la modale di assegnazione, cambiare il controllo relativo al
riposo e verificare che l'elenco dei proclamatori venga ricalcolato e riordinato in base al
nuovo criterio senza chiudere e riaprire la modale.

**Acceptance Scenarios**:

1. **Given** la modale di assegnazione e' aperta con un elenco iniziale di proclamatori,
   **When** l'utente cambia il controllo di riposo, **Then** il sistema aggiorna
   immediatamente l'elenco mostrato usando il nuovo criterio.
2. **Given** l'utente ha una ricerca attiva nella modale di assegnazione, **When** cambia
   il controllo di riposo, **Then** i risultati restano coerenti sia con il filtro di riposo
   sia con la ricerca corrente.
3. **Given** l'interfaccia mostra etichette di stato legate al cooldown, **When** la modale
   o altre viste espongono tale concetto, **Then** il testo mostrato usa una dicitura italiana
   comprensibile come "riposo".

---

### User Story 2 - Modificare settimane assegnate senza perdere lavoro valido (Priority: P1)

Chi gestisce le parti settimanali vuole poter aggiungere o rimuovere singole parti in una
settimana gia' assegnata senza che il sistema cancelli automaticamente tutte le assegnazioni
che restano ancora valide rispetto alla nuova configurazione.

**Why this priority**: La perdita massiva di assegnazioni crea regressioni funzionali gravi,
rilavorazione manuale e rischio di errore nella pianificazione gia' confermata.

**Independent Test**: Partire da una settimana con assegnazioni esistenti, modificare la
composizione aggiungendo o rimuovendo una sola parte, e verificare che solo le assegnazioni
non piu' compatibili vengano rimosse mentre le altre restano intatte.

**Acceptance Scenarios**:

1. **Given** una settimana ha parti assegnate e l'utente aggiunge una nuova parte,
   **When** salva la modifica, **Then** il sistema mantiene tutte le assegnazioni delle parti
   invarianti e richiede nuove assegnazioni solo per la parte aggiunta.
2. **Given** una settimana ha parti assegnate e l'utente rimuove una sola parte,
   **When** salva la modifica, **Then** il sistema elimina solo l'assegnazione collegata alla
   parte rimossa e preserva le altre assegnazioni rimaste identiche.
3. **Given** l'utente aggiorna una settimana senza cambiare una parte gia' presente,
   **When** la modifica viene applicata, **Then** il sistema conserva l'assegnazione della
   parte invariata senza azzerarla.

---

### User Story 3 - Navigare programma e studenti con ricerca e layout piu' solidi (Priority: P2)

Chi usa l'applicazione vuole trovare persone piu' velocemente, poter saltare una settimana
anche se il programma e' passato e consultare l'area studenti con etichette italiane,
paginazione piu' leggibile e card visivamente stabili.

**Why this priority**: Questi affinamenti toccano produttivita' quotidiana, comprensibilita'
dei dati e qualita' percepita dell'interfaccia, riducendo attrito in piu' punti dell'app.

**Independent Test**: Verificare separatamente che la ricerca fuzzy ordini i risultati per
vicinanza, che il pulsante "salta settimana" sia disponibile anche su programmi passati, che
la tabella studenti riporti etichette italiane e scrolli in alto a ogni cambio pagina, e che
le card studenti mantengano pulsanti allineati anche con molte capability.

**Acceptance Scenarios**:

1. **Given** l'utente cerca un proclamatore o uno studente con un nome digitato in modo
   incompleto o con lieve errore, **When** esegue la ricerca, **Then** il sistema mostra i
   risultati ordinati per somiglianza testuale e non solo per semplice contenimento.
2. **Given** l'utente si trova su un programma passato, **When** apre il contesto della
   settimana, **Then** il sistema rende visibile e utilizzabile l'azione per saltare la
   settimana.
3. **Given** l'utente cambia pagina nella tabella studenti dopo aver scrollato verso il
   basso, **When** la nuova pagina viene caricata, **Then** la vista torna all'inizio della
   tabella invece di mantenere la posizione verticale precedente.
4. **Given** una card studente contiene molte capability, **When** viene renderizzata,
   **Then** la toolbar delle azioni mantiene altezza costante e i pulsanti modifica/elimina
   restano leggibili e non schiacciati.

### Edge Cases

- Cambio rapido del controllo di riposo nella modale: l'elenco finale MUST riflettere sempre l'ultimo stato selezionato, senza mostrare un mix di risultati vecchi e nuovi.
- Ricerca fuzzy con piu' corrispondenze simili: i risultati MUST avere un ordinamento stabile e comprensibile a parita' di distanza.
- Nessun risultato coerente con ricerca e riposo: la modale e la schermata studenti MUST mostrare uno stato vuoto esplicito in italiano.
- Modifica di una settimana che rimuove piu' parti insieme: il sistema MUST preservare solo le assegnazioni ancora collegate a parti rimaste effettivamente in programma.
- Settimana passata gia' marcata come saltata: il controllo per saltare la settimana MUST restare coerente con lo stato gia' applicato e non sparire.
- Studente con molte capability o etichette lunghe: la card MUST mantenere leggibilita' delle azioni senza sovrapposizioni o deformazioni evidenti.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST ricalcolare e aggiornare l'elenco dei proclamatori nella modale di assegnazione ogni volta che cambia il controllo relativo al riposo.
- **FR-002**: L'aggiornamento dell'elenco nella modale di assegnazione MUST usare in modo coerente tutti i criteri correnti, inclusi ricerca attiva e stato del riposo.
- **FR-003**: Il sistema MUST sostituire la terminologia tecnica "cooldown" con una dicitura italiana comprensibile all'utente finale, coerente in tutte le viste interessate.
- **FR-004**: Quando una settimana gia' assegnata viene modificata, il sistema MUST preservare tutte le assegnazioni ancora riferite a parti rimaste invariate nella nuova configurazione.
- **FR-005**: Quando una modifica rimuove una o piu' parti dalla settimana, il sistema MUST eliminare solo le assegnazioni relative alle parti effettivamente rimosse.
- **FR-006**: Quando una modifica aggiunge nuove parti alla settimana, il sistema MUST lasciare non assegnate solo le nuove parti introdotte, senza azzerare le assegnazioni gia' valide.
- **FR-007**: L'azione per saltare una settimana MUST restare visibile e utilizzabile anche quando il programma di riferimento e' passato.
- **FR-008**: La ricerca nella modale di assegnazione e nella sezione studenti MUST supportare corrispondenze fuzzy e MUST ordinare i risultati per vicinanza testuale percepita.
- **FR-009**: La colonna capability nella tabella studenti MUST essere rinominata con una dicitura italiana coerente con il dominio applicativo.
- **FR-010**: A ogni cambio pagina della tabella studenti, il sistema MUST riportare la vista all'inizio del contenuto paginato.
- **FR-011**: Le card studenti MUST mantenere una toolbar azioni a altezza costante indipendentemente dal numero di capability mostrate.
- **FR-012**: Gli stati vuoti, le etichette e i controlli toccati da questa feature MUST usare testi in italiano coerenti con il resto dell'applicazione.

### Key Entities *(include if feature involves data)*

- **CriteriAssegnazione**: insieme dei filtri usati nella modale di assegnazione, inclusi ricerca testuale e stato di riposo, che determinano quali proclamatori sono candidati visibili.
- **ParteSettimanaleAssegnata**: parte prevista in una settimana con eventuale assegnazione gia' effettuata, da preservare o rimuovere in base alle modifiche reali della composizione.
- **SettimanaProgramma**: unita' del programma mensile che puo' essere pianificata, modificata o saltata anche quando appartiene a un periodo gia' trascorso.
- **VistaStudente**: rappresentazione elenco/card di uno studente con capability, azioni rapide e metadati consultabili nella schermata studenti.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dopo un cambio del controllo di riposo nella modale di assegnazione, l'utente vede un elenco coerente con il nuovo stato senza dover chiudere e riaprire la modale nel 100% dei casi verificati.
- **SC-002**: In una modifica parziale di una settimana gia' assegnata, almeno il 90% delle assegnazioni ancora valide viene preservato automaticamente e nessuna assegnazione invariata viene rimossa per errore nei casi coperti dalla feature.
- **SC-003**: Gli utenti riescono a trovare una persona tramite ricerca con refusi o input incompleto entro i primi 5 risultati nella maggior parte dei casi comuni di utilizzo.
- **SC-004**: Ogni cambio pagina nella tabella studenti riporta la vista all'inizio del contenuto, eliminando la necessita' di scroll manuale di recupero.
- **SC-005**: Le etichette e i controlli toccati dalla feature risultano interamente in italiano e comprensibili senza termini tecnici inglesi residui nelle schermate interessate.

## Assumptions

- La richiesta copre un unico intervento di stabilizzazione UX/logica su flussi gia' esistenti, non l'introduzione di nuovi moduli di dominio.
- "Cooldown" va interpretato come concetto di periodo di riposo del proclamatore, quindi la terminologia utente deve riflettere quel significato.
- La preservazione delle assegnazioni va valutata sulla continuita' reale della parte all'interno della settimana aggiornata, non sul semplice fatto che l'intera lista sia stata salvata nuovamente.
- La fuzzy search deve migliorare tolleranza ai refusi e ordinamento dei risultati sia nella modale di assegnazione sia nella sezione studenti, mantenendo un comportamento coerente fra i due contesti.

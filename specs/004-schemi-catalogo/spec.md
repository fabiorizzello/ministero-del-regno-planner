# Feature Specification: Schemi e Catalogo

**Feature Branch**: `004-schemi-catalogo`
**Created**: 2026-02-25
**Status**: Draft (reverse-engineered from existing code)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aggiornamento schemi da sorgente remoto (Priority: P1)

L'utente vuole scaricare l'ultimo catalogo dei tipi di parte e i template settimanali
dal sorgente remoto (GitHub), aggiornando il catalogo locale. L'operazione gestisce
anche la pulizia delle idoneità obsolete se alcuni tipi di parte vengono rimossi.

**Why this priority**: Il catalogo schemi è la fonte di verità per i tipi di parte
e per i template settimanali usati nella generazione dei programmi. Senza di esso
la generazione non funziona.

**Independent Test**: Eseguire AggiornaSchemi → verificare che il numero di PartType
nel DB corrisponda a quelli del catalogo remoto e che i template settimanali siano
presenti.

**Acceptance Scenarios**:

1. **Given** connessione disponibile e catalogo remoto valido, **When** si aggiorna,
   **Then** i tipi di parte vengono aggiornati (upsert) e i tipi non più presenti
   vengono disattivati (non eliminati).
2. **Given** il catalogo remoto include template settimanali, **When** si aggiorna,
   **Then** i template vengono sostituiti integralmente (`replaceAll`).
3. **Given** un tipo di parte rimosso dal catalogo remoto aveva idoneità conduzione
   configurate per alcuni proclamatori, **When** si aggiorna, **Then** le idoneità
   obsolete vengono eliminate e viene registrata un'anomalia per ogni proclamatore
   interessato.
4. **Given** il catalogo remoto non è raggiungibile, **When** si aggiorna, **Then**
   viene mostrato un errore di rete; il DB non viene modificato.
5. **Given** un template settimanale nel catalogo referenzia un codice parte non
   presente nel catalogo, **When** si aggiorna, **Then** errore di validazione e
   nessuna modifica al DB.
6. **Given** aggiornamento completato, **Then** il timestamp `last_schema_import_at`
   viene salvato nelle impostazioni locali.

---

### Edge Cases

- Catalog version: il campo `version` del catalogo viene restituito nel result ma
  non viene validato/forzato dal sistema (nessuna constraint su versione minima).
- Idoneità anomalie: vengono registrate con `SchemaUpdateAnomalyDraft` per audit;
  devono essere visibili all'utente dopo l'aggiornamento.
- Tipi di parte disattivati: vengono conservati nel DB con `deactivateMissingCodes`;
  non vengono proposti per nuove settimane ma lo storico rimane intatto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema MUST scaricare il catalogo da sorgente remoto su richiesta
  dell'utente.
- **FR-002**: Il sistema MUST fare upsert dei tipi di parte (aggiornare esistenti,
  inserire nuovi).
- **FR-003**: Il sistema MUST disattivare i tipi di parte non più presenti nel
  catalogo remoto (non eliminare).
- **FR-004**: Il sistema MUST sostituire integralmente i template settimanali con
  quelli del catalogo.
- **FR-005**: Il sistema MUST eliminare le idoneità conduzione che puntano a tipi
  di parte rimossi e registrare un'anomalia per ogni caso.
- **FR-006**: Il sistema MUST validare che ogni template settimanale usi solo codici
  parte presenti nel catalogo; in caso contrario bloccare l'intero import.
- **FR-007**: Il sistema MUST aggiornare `last_schema_import_at` nelle impostazioni
  locali dopo ogni import riuscito.
- **FR-008**: L'intera operazione di import (pulizia idoneità + upsert tipi +
  replace template) MUST avvenire in una singola transazione DB.

### Key Entities

- **SchemaWeekTemplate**: weekStartDate, partTypeCodes (lista). Template locale
  che mappa una settimana specifica ai codici dei tipi di parte da usare.
- **SchemaCatalog** (remoto): version, partTypes[], weeks[]. Fonte di verità esterna.
- **SchemaUpdateAnomaly**: personId, partTypeId, reason, schemaVersion, createdAt.
  Log di audit delle idoneità rimosse durante l'aggiornamento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Il download e import del catalogo completa in meno di 15 secondi in
  condizioni di rete normali.
- **SC-002**: In caso di errore di rete, il DB rimane nello stato precedente (nessuna
  modifica parziale).
- **SC-003**: Le anomalie di idoneità rilevate durante l'aggiornamento sono visibili
  all'utente nel risultato dell'operazione (campo `eligibilityAnomalies` nel result).

## Clarifications

### Session 2026-02-25

- Q: Le spec sono state reverse-engineered dal codice esistente → A: Confermato.
- Q: Il sorgente remoto è GitHub? → A: Sì, da `GitHubDataSource` nella feature
  weeklyparts e dall'import in AggiornaSchemiUseCase.

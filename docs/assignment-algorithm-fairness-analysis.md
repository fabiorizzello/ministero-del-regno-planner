# Analisi Fairness — Algoritmo di Assegnazione Automatica

Data: 2026-03-11

## Stato attuale

### Formula di scoring (per slot)

```
score = lastGlobalWeeks × roleWeight + lastForPartTypeWeeks - cooldownPenalty
```

Dove:
- `lastGlobalWeeks`: settimane dall'ultima assegnazione (qualsiasi ruolo/parte)
- `roleWeight`: `leadWeight` (2) per slot 1, `assistWeight` (1) per slot 2+
- `lastForPartTypeWeeks`: settimane dall'ultima assegnazione a questo tipo di parte
- `cooldownPenalty`: 10.000 se in cooldown, 0 altrimenti

### Sorting candidati

1. Score (desc)
2. lastGlobalWeeks (desc)
3. Cognome (asc)
4. Nome (asc)

### Parametri default

- `strictCooldown = true` (escludi candidati in cooldown)
- `leadWeight = 2`, `assistWeight = 1`
- `leadCooldownWeeks = 4`, `assistCooldownWeeks = 2`

---

## Problemi di fairness identificati

### 1. `lastForPartTypeWeeks` crea specializzazione

Il ranking per parte spinge le persone a ruotare sulle stesse parti. Se Mario ha fatto
"Discorso" 2 settimane fa ma "Studio Biblico" 10 settimane fa, ottiene score diversi
per parti diverse. L'effetto nel tempo: le persone si "specializzano" su certe parti
invece di essere distribuite equamente a livello globale.

**Impatto**: medio — distorce la distribuzione su N assegnazioni.

### 2. Nessuna rotazione slot

L'auto-assign riempie sempre slot 1 prima di slot 2 per ogni parte. Chi ha il cooldown
più lungo vince sistematicamente slot 1. Risultato: le stesse persone fanno sempre
"Studente" (slot 1), altre sempre "Assistente" (slot 2+).

**Impatto**: alto — ruoli cristallizzati.

### 3. Tiebreaker alfabetico

A parità di score e lastGlobalWeeks, vince sempre il cognome che viene prima
nell'ordinamento alfabetico. Questo è un bias sistematico: "Alberti" vince
sempre su "Zanetti" a parità di condizioni.

**Impatto**: basso-medio — rilevante solo a parità, ma su tante assegnazioni si accumula.

### 4. `leadWeight` vs `assistWeight` distorce la distribuzione

Con `leadWeight=2` e `assistWeight=1`, un'assegnazione come conduttore "vale" il doppio
nel ranking. Ma l'obiettivo è che tutti facciano entrambi i ruoli — pesi diversi creano
incentivi distorti che favoriscono chi ha fatto il conduttore più di recente.

**Impatto**: medio — altera l'ordine dei candidati in modo non intuitivo.

---

## Proposte di miglioramento

### A. Rimuovere `lastForPartTypeWeeks` dallo scoring

Ranking puramente globale. La componente per-parte viene rimossa dalla formula.
Il dato può restare disponibile nell'UI come informazione, ma non influenza il ranking.

### B. Slot repeat penalty

Se l'ultimo assignment era nello stesso tipo di slot che stai per ricevere, penalità
moderata. Il dato è già disponibile: `lastConductorWeeks == lastGlobalWeeks` indica
che l'ultimo ruolo era slot 1.

```
slotRepeatPenalty = if (targetSlot matches lastSlot) SLOT_REPEAT_PENALTY else 0
```

Con `SLOT_REPEAT_PENALTY` = 3-5 settimane equivalenti. Non blocca l'assegnazione,
ma crea una rotazione naturale tra ruoli.

### C. Tiebreaker deterministico non-alfabetico

Sostituire il tiebreaker cognome/nome con `hash(personId + weekStartDate)`.
Ogni settimana l'ordine a parità cambia, nessuno è sistematicamente favorito.

### D. Unificare i pesi

`leadWeight = assistWeight = 1`. La differenza tra ruoli va gestita dal cooldown
(che già ha soglie separate per lead e assist), non dal peso nel ranking.

### Formula risultante

```
score = lastGlobalWeeks - slotRepeatPenalty - cooldownPenalty
```

Semplice, trasparente, fair.

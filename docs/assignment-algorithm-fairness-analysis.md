# Analisi Fairness — Algoritmo di Assegnazione Automatica

Data: 2026-03-12

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

### 2. `leadWeight` vs `assistWeight` distorce la distribuzione

Con `leadWeight=2` e `assistWeight=1`, un'assegnazione come conduttore "vale" il doppio
nel ranking. Ma l'obiettivo è che tutti facciano entrambi i ruoli — pesi diversi creano
incentivi distorti che favoriscono chi ha fatto il conduttore più di recente.

**Impatto**: medio — altera l'ordine dei candidati in modo non intuitivo.

### 3. Solo recenza, nessuna fairness cumulativa

L'algoritmo guarda solo `lastGlobalWeeks` (settimane dall'ultima assegnazione).
Non traccia il conteggio totale. Se Mario ha 15 assegnazioni quest'anno e Luigi ne ha 5,
ma Mario ha un gap più lungo in questo momento, l'algoritmo mette Mario davanti a Luigi.
Su un pool di 80 persone con ~20-30 slot/mese, questo squilibrio si accumula nel tempo.

**Impatto**: alto — la distribuzione cumulativa diverge progressivamente.

### 4. Nessuna rotazione slot

L'auto-assign riempie sempre slot 1 prima di slot 2 per ogni parte. Chi ha il cooldown
più lungo vince sistematicamente slot 1. Risultato: le stesse persone fanno sempre
"Studente" (slot 1), altre sempre "Assistente" (slot 2+).

**Impatto**: basso con 80 persone (rotazione naturale con assegnazione ogni 3-4 mesi),
ma presente.

---

## Piano di miglioramento

Ordine di implementazione: A → D → E → B.

### A. Rimuovere `lastForPartTypeWeeks` dallo scoring

Ranking puramente globale. La componente per-parte viene rimossa dalla formula.
Il dato può restare disponibile nell'UI come informazione, ma non influenza il ranking.

### D. Unificare i pesi

`leadWeight = assistWeight = 1`. La differenza tra ruoli va gestita dal cooldown
(che già ha soglie separate per lead e assist), non dal peso nel ranking.

### E. Fairness cumulativa

Aggiungere un fattore basato sul conteggio totale di assegnazioni in una finestra
temporale (es. ultimi 6 mesi, solo all'indietro). Chi ha fatto più assegnazioni viene
penalizzato rispetto a chi ne ha fatte meno.

Costanti nel codice (`COUNT_PENALTY_WEIGHT`, `COUNT_WINDOW_WEEKS`), non esposte
nelle impostazioni utente.

```
countPenalty = totalAssignmentsInWindow × COUNT_PENALTY_WEIGHT
```

La recenza (`lastGlobalWeeks`) resta il fattore primario, ma il conteggio impedisce
che le stesse persone accumulino troppe assegnazioni nel tempo.

### B. Slot repeat penalty

Se l'ultimo assignment era nello stesso tipo di slot che stai per ricevere, penalità
moderata. Il dato è già disponibile: `lastConductorWeeks == lastGlobalWeeks` indica
che l'ultimo ruolo era slot 1.

```
slotRepeatPenalty = if (targetSlot matches lastSlot) SLOT_REPEAT_PENALTY else 0
```

Con `SLOT_REPEAT_PENALTY` = 4 settimane equivalenti (costante nel codice, non esposta
nelle impostazioni utente). Non blocca l'assegnazione, ma crea una rotazione naturale
tra ruoli.

### Formula risultante

```
score = lastGlobalWeeks - countPenalty - slotRepeatPenalty - cooldownPenalty
```

Semplice, trasparente, fair.

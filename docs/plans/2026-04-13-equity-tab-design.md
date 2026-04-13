# Equità & carico — design doc

Data: 2026-04-13
Branch target: `main` (non urgente, niente feature branch dedicato a meno che nasca complicazione)

## Scopo

Uno specchio di fairness a posteriori per il sorvegliante della scuola di ministero:
far vedere se la distribuzione del lavoro nei mesi è equa — chi viene dimenticato e
chi viene sovra-usato — così che il sorvegliante possa correggere a mano quello che
l'algoritmo di auto-assegnazione, decisione per decisione, non riesce a bilanciare.

**Vista puramente informativa, read-only.** Nessuna azione modificatrice, nessun
modal, nessuna navigazione verso altre schermate. L'utente guarda, capisce, chiude
la tab, torna sul programma.

## Non-obiettivi

Queste cose sono esplicitamente **fuori scope** in questa iterazione:

- Drill-down per singolo proclamatore (cronologia completa assegnazioni).
- Export CSV/Excel/stampa.
- Filtro per specifico tipo di parte (solo aggregato totale lead/assist).
- Selettore di finestra personalizzato (usiamo `RANKING_HISTORY_WEEKS` immutabile).
- Azioni contestuali sulla riga (assegna, sospendi, apri scheda).
- Mini-grafici separati: tutta la visualizzazione sta dentro la riga.

## Architettura — vertical slice

Il codice vive nella slice `feature/diagnostics/` per coerenza con gli altri
strumenti admin già presenti (`ContaStoricoUseCase`, `EliminaStoricoUseCase`,
`ImportaSeedApplicazioneDaJsonUseCase`).

```
feature/diagnostics/
  domain/
    EquitaProclamatore.kt        ← DTO per riga (una per persona attiva)
    RiepilogoEquita.kt           ← DTO header (stats aggregate + lista dimenticati)
  application/
    CalcolaEquitaProclamatoriUseCase.kt
  infrastructure/
    SqlDelightEquitaQuery.kt     ← adapter sopra la nuova query SQL

ui/admincatalog/
  EquitaScreen.kt
  EquitaViewModel.kt
  EquitaUiState.kt
  EquitaComponents.kt            ← header card + riga equità + filtri
```

L'integrazione con la UI esistente è minima: un'entry in più nell'enum
`AdminCatalogSection` (4° chip, icona `Balance` o `Equalizer`) e un ramo aggiuntivo
nel `when` di `AdminToolsScreen.kt`.

## Data layer — nuova query SQL (Opzione B confermata)

Una sola query `GROUP BY person_id` restituisce tutto il necessario per una riga,
inclusa la lista delle settimane assegnate nella finestra (serve per la sparkline).

```sql
equityPersonAggregates:
SELECT
    p.id                                                           AS person_id,
    p.first_name,
    p.last_name,
    p.sex,
    p.suspended,
    COUNT(a.id)                                                    AS total_in_window,
    SUM(CASE WHEN a.slot = 1  THEN 1 ELSE 0 END)                   AS lead_count,
    SUM(CASE WHEN a.slot >= 2 THEN 1 ELSE 0 END)                   AS assist_count,
    MAX(CASE WHEN a.slot = 1  THEN wpl.week_start_date END)        AS last_lead_date,
    MAX(CASE WHEN a.slot >= 2 THEN wpl.week_start_date END)        AS last_assist_date,
    MAX(wpl.week_start_date)                                       AS last_any_date,
    GROUP_CONCAT(wpl.week_start_date)                              AS assigned_weeks_csv
FROM person p
LEFT JOIN assignment a ON a.person_id = p.id
LEFT JOIN weekly_part wp ON a.weekly_part_id = wp.id
LEFT JOIN week_plan wpl ON wp.week_plan_id = wpl.id
    AND wpl.week_start_date >= :since_date
GROUP BY p.id;
```

### Dettagli critici della query

- **Filtro finestra nel `ON`, non nel `WHERE`.** Mettendo `AND wpl.week_start_date >= :since_date`
  dentro la clausola `ON` dell'ultimo `LEFT JOIN`, i proclamatori senza alcuna assegnazione
  (o con assegnazioni solo fuori finestra) restano nel result set con `total_in_window = 0`.
  Se il filtro fosse nel `WHERE` sparirebbero, e uno dei tre scopi operativi principali
  ("chi sto dimenticando") morirebbe in culla.
- **`GROUP_CONCAT(wpl.week_start_date)`** è nativo SQLite: restituisce un CSV (default
  separator `,`) con le settimane dove la persona è stata assegnata. Kotlin poi parsa in
  `Set<LocalDate>` e genera la sparkline su 12 bucket settimanali.
- **`LEFT JOIN assignment` senza filtro**: le persone senza nessuna assegnazione
  vivono comunque nella tabella `person`. `total_in_window` diventa `0` perché `COUNT(a.id)`
  conta solo le righe non-null.
- **Ordinamento** fatto in Kotlin dopo il fetch — SQL restituisce per `p.id`, poi il
  ViewModel sorta in base al criterio scelto dall'utente.

### Parametro `:since_date`

La finestra equità è fissa: `today - RANKING_HISTORY_WEEKS` (costante già esistente in
`feature/assignments/application/AssignmentSettings.kt`, attualmente 12 settimane).
`today` = `LocalDate.now()` nel ViewModel, passato come parametro al use case.

## Domain model

```kotlin
data class EquitaProclamatore(
    val proclamatore: Proclamatore,            // id, nome, cognome, sesso, sospeso
    val totaleInFinestra: Int,
    val conduzioniInFinestra: Int,
    val assistenzeInFinestra: Int,
    val ultimaConduzione: LocalDate?,
    val ultimaAssistenza: LocalDate?,
    val ultimaAssegnazione: LocalDate?,
    val settimaneAssegnate: Set<LocalDate>,    // per sparkline
    val cooldownLeadResiduo: Int,              // 0 = libero per conduzione
    val cooldownAssistResiduo: Int,            // 0 = libero per assistenza
) {
    val maiAssegnato: Boolean get() = ultimaAssegnazione == null
    val settimaneDallUltima: Int?              // null se mai assegnato
}

data class RiepilogoEquita(
    val totaleAttivi: Int,
    val maiAssegnati: Int,
    val inCooldownLead: Int,
    val inCooldownAssist: Int,
    val minTotale: Int,                        // distribuzione nella finestra
    val medianaTotale: Int,
    val maxTotale: Int,
    val dimenticatiDaTroppo: List<EquitaProclamatore>,   // soglia 2 × leadCooldownWeeks
)
```

### Calcolo cooldown residuo

Il cooldown residuo per riga è derivato dalle impostazioni assegnatore + `ultimaAssegnazione`
e `lastWasConductor` (= `ultimaConduzione == ultimaAssegnazione`):

- Se l'ultimo ruolo era conduttore → soglia conduzione = `leadCooldownWeeks`, altrimenti `assistCooldownWeeks`
- `leadResiduo = max(0, leadCooldownWeeks - settimaneDallUltima)`
- `assistResiduo = max(0, assistCooldownWeeks - settimaneDallUltima)`

Usiamo la stessa logica di `SuggerisciProclamatoriUseCase` (vedi `SuggestedProclamatore.lastWasConductor`)
per garantire coerenza visiva tra questa vista e il suggeritore.

## UI — layout e componenti

### Composizione verticale dello screen

```
┌─ AdminToolsShell (esistente, con 4° chip "Equità") ─────────┐
│                                                               │
│   AdminContentCard "Riepilogo"                                │
│   ├ Alert dimenticati (se presenti)                          │
│   ├ Stats chip row: Attivi · Mai assegnati · In cooldown × 2 │
│   └ Distribuzione bar: min ─── mediana ─── max               │
│                                                               │
│   Filter bar (search + toggle cooldown + toggle sospesi +    │
│               sort selector)                                  │
│                                                               │
│   LazyColumn di EquitaRow (una per persona)                  │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### Struttura di una `EquitaRow`

```
┌────────────────────────────────────────────────────────────────┐
│ 🟢  AV  Verdi Anna         F   ▓▓▓░░░░░░░  3   ·······●··  3w │
│         Ult. assistenza 2026-03-23                              │
└────────────────────────────────────────────────────────────────┘
```

Elementi da sinistra a destra:

1. **Status dot** (🟢 libero / 🟡 in cooldown assist / 🔴 in cooldown lead) —
   `Box` circolare 10dp con `background` dal sketch palette (`successDot`, `warningDot`,
   `errorDot`). Accanto una piccola icona (`CheckCircle`, `Snooze`, `DoNotDisturb`)
   per la double-encoding a11y.
2. **Avatar iniziali** (riutilizzo componente già esistente in `ProclamatoriSketch.kt`
   `InitialsAvatar`) con colore di sfondo per sesso.
3. **Nome cognome** + badge `M`/`F`.
4. **Barra carico** — `Box` riempito proporzionalmente al `maxTotale` del riepilogo,
   altezza 8dp, angoli arrotondati. Colore gradiente semplice: `surfaceVariant` → `accent`.
5. **Numero totale** (testo titleMedium).
6. **Sparkline 12 settimane** — `Row` di 12 `Box` da 6dp × 6dp, spacing 2dp. Ogni box
   è `accent` se la settimana corrispondente è in `settimaneAssegnate`, altrimenti
   `surfaceVariant`. Le settimane sono computate da `today - 11..today`.
7. **"Nw"** — settimane dall'ultima assegnazione (o `—` se mai).

Sotto, riga di **contesto** (bodySmall, `inkMuted`):
- Se `maiAssegnato`: `"Mai assegnato"`.
- Se in cooldown: `"In cooldown {lead|assist} –Nw · Ult. {conduzione|assistenza} YYYY-MM-DD"`.
- Altrimenti: `"Ult. conduzione ... · Ult. assistenza ..."` (oppure solo quella disponibile).

### Alert dimenticati (header)

`Card` warning con fondo `warningContainer` semi-trasparente, icona `WarningAmber`
in testa, testo:

> **"4 proclamatori non assegnati da oltre 8 settimane"**
> Rossi L., Verdi A., Neri P., Bianchi G.

Soglia default: `2 × leadCooldownWeeks` (= 8 settimane con settings di default).
Se la lista è vuota l'intero card non viene renderizzato.

### Filter bar

- `OutlinedTextField` di ricerca (riusa il pattern di `AssignmentsComponents.kt` con
  `cursorColor` esplicito — footgun documentato in MEMORY).
- Toggle chip `Solo liberi da cooldown` (filtra `cooldownLeadResiduo == 0 || cooldownAssistResiduo == 0`).
- Toggle chip `Include sospesi` (default off — persone con `sospeso=true` nascoste).
- Sort selector (`DropdownMenu`): default `"Meno usati"` (sort per `totaleInFinestra` asc,
  poi `settimaneDallUltima` desc), alternative `"Più usati"` e `"Alfabetico"`.

### Riuso componenti esistenti

- `AdminToolsShell` + `AdminContentCard` → shell e card del riepilogo
- `InitialsAvatar` da `ProclamatoriSketch.kt` → avatar
- `WorkspaceStatePane` → loading / empty / error (non dovrebbe mai essere empty in
  pratica: se ci sono 0 persone attive, lo screen è in empty state)
- `workspaceSketch` palette → tutti i colori

Non serve nessun componente UI nuovo non banale. Le **novità** sono solo:
- `EquitaBarCell` (`Box` proporzionale alla percentuale)
- `EquitaSparkline` (`Row` di 12 box colorati)
- `EquitaStatusDot` (dot + icona)

Tutte pilotabili da parametri, circa 20-30 righe ciascuna.

## ViewModel & UiState

```kotlin
data class EquitaUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val riepilogo: RiepilogoEquita? = null,
    val righe: List<EquitaProclamatore> = emptyList(),
    val filtroRicerca: String = "",
    val soloLiberi: Boolean = false,
    val includiSospesi: Boolean = false,
    val sortMode: EquitaSortMode = EquitaSortMode.MENO_USATI,
)

enum class EquitaSortMode { MENO_USATI, PIU_USATI, ALFABETICO }
```

Il ViewModel:

1. All'entrata dello screen (`onScreenEntered`), chiama il use case con `LocalDate.now()`.
2. Salva risultato grezzo in un field privato; espone `righe` come lista **filtrata + sortata**
   in base allo state corrente (derivato in un `combine` di flow).
3. Gli eventi utente (`onSearchChange`, `onToggleSoloLiberi`, `onToggleIncludiSospesi`,
   `onSortChange`) aggiornano solo lo state; il filtro/sort è applicato in memoria, niente
   nuove query.
4. Niente refresh automatico: la vista è read-only, se l'utente vuole aggiornare torna
   fuori e rientra, o premi un tasto refresh (opzionale, MVP ne fa a meno).

## Error handling

- Query SQL fallita → `error = "Impossibile calcolare l'equità."` + `WorkspaceStatePane.Error`.
- Zero persone attive → `WorkspaceStatePane.Empty` con messaggio "Nessun proclamatore attivo."
- `GROUP_CONCAT` che restituisce stringa malformata → ignoro la singola riga con log (non
  dovrebbe mai succedere: SQLite garantisce il formato).

Nessun error di business: è read-only.

## Testing

### Unit test dominio
- `EquitaProclamatore.settimaneDallUltima` per casi: mai assegnato, oggi, 5 settimane fa.
- `EquitaProclamatore.maiAssegnato` coerente con `ultimaAssegnazione == null`.

### Unit test use case (`CalcolaEquitaProclamatoriUseCaseTest`)
Fixture con 4 persone:
- `Alfa`: mai assegnato.
- `Beta`: 3 conduzioni + 1 assistenza in finestra, ultima = 2 settimane fa.
- `Gamma`: 1 assistenza 10 settimane fa (al bordo finestra).
- `Delta`: sospeso, 5 assegnazioni.

Verifiche:
- `righe.size == 4` (sospesi inclusi, il filtro è nella UI non nel use case)
- `Alfa.maiAssegnato == true`, `totaleInFinestra == 0`
- `Beta.conduzioniInFinestra == 3`, `assistenzeInFinestra == 1`, `ultimaAssegnazione` corretta
- `Gamma.settimaneAssegnate.size == 1`
- `riepilogo.maiAssegnati == 1` (Alfa; Delta è sospeso ma per ora lo usecase non lo esclude
  dalle stats — decisione da rivedere durante implementazione: probabilmente escluderlo).
- `riepilogo.dimenticatiDaTroppo` contiene solo chi non è sospeso e supera la soglia.

### Test SQL (`SqlDelightEquityQueryTest`, stile degli altri `SqlDelightXxxStoreTest`)
- Inserisce persone + weeks + assignments, esegue `equityPersonAggregates`, verifica
  il mapping colonna per colonna.
- **Test critico**: una persona senza assegnazioni deve comparire con `total_in_window = 0`
  (regression test del `LEFT JOIN ... ON`).
- Test della finestra: assegnazione fuori `:since_date` non deve contribuire al count.

### UI test (facoltativo MVP)
Al momento la codebase ha screenshot tests desktop pesanti. Non aggiungo un test UI
dedicato: la logica è tutta nel ViewModel e nel use case, entrambi testabili in JVM
senza rendering Compose.

## Passi implementativi ad alto livello

1. Aggiungere query `equityPersonAggregates` a `MinisteroDatabase.sq` + test.
2. Creare `EquitaProclamatore`, `RiepilogoEquita` in `feature/diagnostics/domain/`.
3. Creare `SqlDelightEquitaQuery` in `feature/diagnostics/infrastructure/` (mapper + fetch).
4. Creare `CalcolaEquitaProclamatoriUseCase` in `feature/diagnostics/application/` + test.
5. Registrare use case in `DiagnosticsModule.kt` (Koin).
6. Creare `EquitaUiState`, `EquitaViewModel`, `EquitaScreen`, `EquitaComponents`.
7. Aggiungere entry `EQUITY` a `AdminCatalogSection` + ramo in `AdminToolsScreen.kt`.
8. Smoke test manuale con uberjar.

## Punti decisionali lasciati all'implementazione

- **Sospesi nelle stats del riepilogo**: li escludo dal `totaleAttivi`, `minTotale`,
  `medianaTotale`, `maxTotale`, `dimenticatiDaTroppo`. La riga del sospeso rimane
  nella lista se l'utente abilita il toggle, ma le aggregate contano solo i non-sospesi.
- **Persone con `ultimaAssegnazione` fuori finestra ma esistente**: non vengono
  taggate come "mai assegnate" — serve un secondo campo `ultimaAssegnazioneGlobale`
  non limitato alla finestra? Per MVP, no: la finestra è l'unica fonte di verità
  visiva, e fuori finestra = dimenticato = è quello che vogliamo mostrare. Se emerge
  necessità di distinguere "mai in vita sua" da "mai negli ultimi 3 mesi" lo aggiungiamo
  dopo.
- **Nome colonna `total_in_window` in snake_case in SQL** — SQLDelight genera automatico
  il mapper Kotlin con nome camel-case.

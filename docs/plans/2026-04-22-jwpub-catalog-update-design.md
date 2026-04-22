# JWPUB Catalog Update — Design

Sostituzione pipeline "Aggiorna catalogo" con datasource basato su file `.jwpub`
ufficiali JW. Rimpiazza l'attuale download JSON da GitHub
(`RemoteConfig.SCHEMAS_CATALOG_URL`) mantenendo invariato il boundary applicativo
`SchemaCatalogRemoteSource`.

## Obiettivo

- Rimuovere dipendenza dal file JSON mantenuto a mano su GitHub.
- Scaricare direttamente i fascicoli `Guida per l'adunanza` da JW CDN.
- Derivare settimane e parti dai dati strutturati presenti nel `.jwpub` senza
  scraping HTML pubblico di WOL.
- Informare l'utente in una modale post-import quando l'algoritmo incontra
  label di parte non riconosciute, forzando l'apertura della modale anche
  quando non ci sono modifiche effettive al programma.

## Scope

In scope:

- Scaricamento fascicoli `mwb` (italiano, `langwritten=I`).
- Decrypt + parsing delle settimane e delle parti dal `.jwpub`.
- Cache locale dei `.jwpub` scaricati.
- Modifica UI per esporre parti ignote e fascicoli scaricati.

Out of scope:

- Resolver editabile per le parti ignote (solo informativo in v1).
- Lingue diverse dall'italiano.
- Rewrite dominio `schemas` / nuovi modelli con `mepsDocumentId`.
- Modifica schema DB / migration SQLDelight.

## Approccio scelto

Boundary-swap dell'implementazione di `SchemaCatalogRemoteSource` piu'
estensione leggera di `RemoteSchemaCatalog` / `AggiornaSchemiResult` per
propagare parti ignote e fascicoli scaricati fino alla UI.

Alternative scartate:

- Boundary-swap puro senza feedback UX: perde trasparenza, l'utente non sa
  che qualcosa e' stato ignorato.
- Rewrite completo della feature schemas: over-engineering rispetto alle
  esigenze attuali.

## Dati verificati sul .jwpub

Empiricamente verificato su `mwb_I_202601.jwpub` (fascicolo gennaio-febbraio
2026).

### Struttura archivio

`.jwpub` = zip con:

- `manifest.json` (metadati pubblicazione)
- `contents` (altro zip) che contiene:
  - SQLite `mwb_I_YYYYMM.db`
  - immagini / asset

### Tabelle rilevanti del DB interno

- `Publication` (1 riga) espone `MepsLanguageIndex`, `Symbol`, `Year`,
  `IssueTagNumber`, `MepsBuildNumber`.
- `Document` contiene 1 riga cover + N righe settimana
  (`Class='106'`). Campi rilevanti: `DocumentId`, `MepsDocumentId`,
  `Title` (es. "5-11 gennaio"), `Subtitle` (es. "ISAIA 17-20"), `Content`
  (BLOB cifrato + compresso).
- `DocumentParagraph`, `Multimedia`, `DocumentMultimedia` non servono.

### Decrypt Content BLOB

Algoritmo (da `sws2apps/meeting-schedules-parser`, MIT):

```
pubCard  = "{MepsLanguageIndex}_{Symbol}_{Year}_{IssueTagNumber}"
xorKey   = "11cbb5587e32846d4c26790c633da289f66fe5842a3a585ce1bc3a294af5ada7"
pubHash  = SHA256(pubCard).hex()
combined = pubHash XOR xorKey                // byte-wise, repeating xorKey
key      = combined[0:32]                    // 16 byte AES key
iv       = combined[32:64]                   // 16 byte IV
raw      = AES-128-CBC(key, iv).decrypt(Content)   // PKCS7 unpadding
html     = zlib.inflate(raw).decode("utf-8")
```

Esempio verificato: `pubCard = "4_mwb26_2026_20260100"` produce HTML valido
con tutte le parti della settimana.

### Struttura HTML risultante

Ogni settimana contiene:

- `<header><h1 id="p1">5-11 GENNAIO</h1><h2>ISAIA 17-20</h2></header>`
- `<div class="bodyTxt">` con parti in ordine DOM.
- Parti: `<h3 id="pN" data-pid="N">...</h3>`.
- Detail line (durata + formato): `<p id="pN+1">(5 min) Dimostrazione. ...</p>`
  subito dopo l'`<h3>`. Necessaria per distinguere il tipo per
  "Spiegare quello in cui si crede".

## Endpoint JW usati

### Discovery fascicolo

```
GET https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS
    ?output=json
    &pub=mwb
    &issue=YYYYMM
    &fileformat=JWPUB
    &alllangs=0
    &langwritten=I
```

Response `files.I.JWPUB[0].file` espone: `url`, `checksum` (MD5),
`modifiedDatetime`, `filesize`. `404` = fascicolo non ancora pubblicato.

### Download

URL del campo `file.url`. Server CDN restituisce il file `.jwpub` completo.

## Pattern pubblicazione

- `pub=mwb`
- `issue=YYYYMM`, mesi ammessi `01`, `03`, `05`, `07`, `09`, `11`
  (bimestrale).
- Esempio 2026: `202601`, `202603`, `202605`, `202607`, `202609`, `202611`.
- Partenza: bimestre corrispondente alla data odierna, poi probe successivi
  finche' non si ottiene `404`.

## Architettura

Vertical slice `feature/schemas` — stesso boundary attuale, implementazione
interna sostituita.

```
[UI]   SchemaManagementViewModel
          |
          v
[App]  AggiornaSchemiUseCase (result arricchito)
          |
          v
[App]  SchemaCatalogRemoteSource  (interface invariata)
          |
          v
[Infra] JwPubSchemaCatalogDataSource (nuovo)
          +-- JwPubMediaClient
          +-- MeetingWorkbookIssueDiscovery
          +-- JwPubCache
          +-- JwPubDownloader
          +-- JwPubArchiveReader
          +-- JwPubContentDecryptor
          +-- JwPubSqliteReader
          +-- JwPubHtmlPartsParser
          +-- JwPubWeekDateResolver
          +-- PartTypeLabelResolver
```

Libs necessarie:

- `org.xerial:sqlite-jdbc:3.53.0.0` — dichiarazione esplicita (upgrade da
  3.51.0.0 transitiva via SQLDelight) per lettura file `.jwpub` interni.
- `org.jsoup:jsoup:1.22.2` — upgrade da 1.18.3 attuale.
- AES / SHA256 / zlib / zip / HTTP: JDK + ktor gia' presenti.

## Cache layout

Nuova sottocartella dentro la root dati esistente:

```
ScuolaDiMinisterData/cache/GuidaAdunanza/
  mwb_I_202601.jwpub
  mwb_I_202601.meta.json
  mwb_I_202603.jwpub
  mwb_I_202603.meta.json
  ...
```

`meta.json` contiene `checksum`, `modifiedDatetime`, `filesize`, `fetchedAt`.
Re-download avviene solo quando il `checksum` restituito da
`GETPUBMEDIALINKS` differisce da quello locale.

`AppPaths` esteso per esporre la nuova directory. `PathsResolver` crea la
directory al bootstrap come avviene gia' per `logs/` e `exports/`.

## Componenti

### Infrastructure (`feature/schemas/infrastructure/jwpub/`)

`JwPubMediaClient` — chiama `GETPUBMEDIALINKS`.
Ritorna `JwPubMediaInfo?` con `url`, `checksum`, `modifiedDatetime`,
`filesize`. `null` indica 404.

`MeetingWorkbookIssueDiscovery` — pure function. Dati anno corrente e
mese di partenza, genera la lista degli `YYYYMM` candidati.

`JwPubCache` — incapsula I/O disco sulla directory cache. Metodi `find`,
`store`, `isUpToDate`.

`JwPubDownloader` — ktor GET bytes dall'URL CDN.

`JwPubArchiveReader` — unzip outer + inner. Restituisce path ad un file
`.db` temporaneo. Espone anche `readManifest` per il JSON esterno.

`JwPubContentDecryptor` — `deriveKeyIv(pubCard)` pure + `decryptAndInflate`
che restituisce HTML. Costante `XOR_KEY_HEX` privata al package.

`JwPubSqliteReader` — apre il file `.db` con `sqlite-jdbc`, legge
`Publication` e `Document WHERE Class='106'`.

`JwPubHtmlPartsParser` — jsoup. Seleziona gli `<h3 id^=p>` in ordine DOM,
legge il titolo pulito e il primo `<p>` successivo come `detailLine`.

`JwPubWeekDateResolver` — parse Italian date range. Gestisce cross-month
(`"26 gennaio – 1º febbraio"`) e cross-year (`"30 dicembre – 5 gennaio"`).
Tabella mesi italiani hardcoded. Fallback = errore esplicito.

`PartTypeLabelResolver` — pure, mapping deterministico label + detailLine
→ codice. Normalizzazione: lowercase, strip diacritici, collapse
whitespace, rimozione numero iniziale `"1. "`.

### Application

`SchemaCatalogRemoteSource` — interface invariata.

`RemoteSchemaCatalog` — aggiunge `skippedUnknownParts: List<SkippedPart>`
e `downloadedIssues: List<String>`.

`JwPubSchemaCatalogDataSource` — orchestratore `fetchCatalog()` (vedi
pipeline nella sezione Data flow).

### UI

`AggiornaSchemiResult` — aggiunge `skippedUnknownParts` e
`downloadedIssues`.

`SchemaManagementUiState` — aggiunge `pendingUnknownPartsReport`,
`pendingDownloadedIssues`, `showResultDialog`.

`SchemaManagementViewModel.refreshSchemasAndProgram()` — apre la modale
quando:

```
hasEffectiveChanges ||
  skippedUnknownParts.isNotEmpty() ||
  downloadedIssues.isNotEmpty()
```

`SchemaRefreshResultDialog` — nuovo componente Compose con tre sezioni:

- Fascicoli scaricati (label dei bimestri).
- Cambiamenti al programma selezionato (presente solo se
  `pendingRefreshPreview != null`).
- Parti ignorate (presente solo se `skippedUnknownParts.nonEmpty`), una
  riga per parte con label, settimana di riferimento, detail line.

Pulsanti:

- `Aggiorna programma con tutte le modifiche`
- `Solo parti non assegnate`
- `Chiudi senza aggiornare`

Light + dark theme via token gia' presenti (`AppTheme`,
`WorkspaceSketchPalette`).

## Mapping label → partTypeCode

Tabella deterministica basata su label normalizzata e, dove serve,
detailLine.

| Label HTML | detailLine contiene | Codice |
|---|---|---|
| "Lettura biblica" | — | `LETTURA_DELLA_BIBBIA` |
| "Iniziare una conversazione" | — | `INIZIARE_CONVERSAZIONE` |
| "Coltivare l'interesse" | — | `COLTIVARE_INTERESSE` |
| "Fare discepoli" | — | `FARE_DISCEPOLI` |
| "Discorso" | — | `DISCORSO` |
| "Spiegare quello in cui si crede" | "Discorso" | `SPIEGARE_CIO_CHE_SI_CREDE_DISCORSO` |
| "Spiegare quello in cui si crede" | "Dimostrazione" | `SPIEGARE_CIO_CHE_SI_CREDE` |
| Cantico / Commenti / Tesori / Gemme / Vita cristiana / Studio congr. | — | `NotEfficaci` (skip silenzioso) |
| Label Efficaci non in tabella | — | `Unknown` → `skippedUnknownParts` |

`partTypes` nel catalogo: lista statica dei 7 codici con
`peopleCount`/`sexRule`/`fixed` allineati alla spec (coerenti con
migration 1 gia' applicata).

## Data flow end-to-end

### Discovery

Anno dalla data odierna, issue candidati da
`MeetingWorkbookIssueDiscovery.candidatesForYear(year, startingFromMonth)`.

### Loop per issue

1. `JwPubMediaClient.fetchMediaLinks`:
   - `200` con payload → continua.
   - `404` → interrompe il loop (fine fascicoli futuri).
   - errore HTTP → abort con `DomainError.Network`.
2. `JwPubCache.find(issue, lang)`:
   - cache hit + `isUpToDate` → usa file locale, non registra in
     `downloadedIssues`.
   - cache miss o `checksum` diverso → `JwPubDownloader.download`,
     `JwPubCache.store`, registra issue in `downloadedIssues`.

### Parsing per ogni `.jwpub`

1. `JwPubArchiveReader.extractInnerDb` → path tmp.
2. `JwPubSqliteReader.readPubCard` + `readWeeks`.
3. `JwPubContentDecryptor.deriveKeyIv(pubCard)`.
4. Per ogni week row:
   1. `decryptAndInflate(content, keyIv)` → HTML.
   2. `JwPubHtmlPartsParser.parseParts(html)` → lista parti con `pid`,
      `title`, `detailLine`.
   3. Per ogni parte `PartTypeLabelResolver.resolve(title, detailLine)`:
      - `Mapped(code)` → aggiunge il codice alla settimana.
      - `NotEfficaci` → skip.
      - `Unknown` → skip + aggiunge a `skippedUnknownParts`.
   4. `JwPubWeekDateResolver.resolve(title, publicationYear)` →
      `weekStartDate`.
   5. Costruisce `RemoteWeekSchemaTemplate`.

### Output

`RemoteSchemaCatalog` con:

- `version` = `manifest.publication.hash` del fascicolo piu' recente.
- `partTypes` = lista statica 7 codici.
- `weeks` = ordinate per `weekStartDate`.
- `skippedUnknownParts`.
- `downloadedIssues`.

### Flusso ViewModel

`AggiornaSchemiResult` propaga i due nuovi campi.
`SchemaManagementViewModel` apre la modale con la nuova regola (anche
quando non ci sono effective changes, se ci sono parti ignote o fascicoli
scaricati da segnalare).

## Error handling

### Policy

- **Fail-fast** per anomalie di formato: abort totale, nessun catalogo
  parziale persistito. Coerente con la singola transazione gia' presente
  in `AggiornaSchemiUseCase`.
- **Fail-soft** per label sconosciute: raccolte in `skippedUnknownParts`,
  import procede.
- **Fail-soft** per errori cache I/O: log warning, import procede,
  worst case = re-download next time.
- **Atomicita' DB**: invariata. `TransactionRunner.runInTransactionEither`.

### Mappa errori → DomainError

| Evento | DomainError |
|---|---|
| HTTP 4xx (non-404) / 5xx su GETPUBMEDIALINKS | `Network("GETPUBMEDIALINKS <issue>: HTTP <code>")` |
| 404 su primo issue candidato | `Network("Nessun fascicolo disponibile")` |
| 404 dopo ≥1 successo | OK, stop loop |
| Timeout / DNS fail | `Network("Connessione non riuscita: <msg>")` |
| Download `.jwpub` fallisce | `Network("Download mwb_I_<issue> fallito: <msg>")` |
| Checksum mismatch | `ImportoNonValido("Checksum mwb_I_<issue> non corrispondente")` |
| Unzip fallisce | `ImportoNonValido("Archivio mwb_I_<issue> corrotto")` |
| SQLite open fallisce | `ImportoNonValido("DB interno mwb_I_<issue> non leggibile")` |
| `Publication` vuota | `ImportoNonValido("Publication metadata mancante")` |
| `Document WHERE Class='106'` = 0 rows | `ImportoNonValido("Nessuna settimana in <file>")` |
| AES BadPadding | `ImportoNonValido("Decrypt fallito per DocId <n>")` |
| zlib inflate fallisce | `ImportoNonValido("Content non decomprimibile per DocId <n>")` |
| HTML parsing 0 h3 | `ImportoNonValido("Nessuna parte trovata settimana <meps>")` |
| Date parse fallisce | `ImportoNonValido("Data non parsabile: <title>")` |

### Edge cases

- Cache stale (meta assente, jwpub presente) → re-download.
- Cache stale (meta presente, jwpub assente) → re-download.
- Italian date edge: `"1º"`, `"30 dicembre – 5 gennaio"`, en-dash, em-dash.
- Fascicoli con meno di 8 settimane (prima/dopo Memoriale).
- Doppio click: guard esistente `isRefreshingSchemas`.
- Offline totale: errore esplicito, nessun fallback "only-cache".
- JW cambia `xorKey`: tutti i decrypt falliscono uniformemente → errore
  esplicito, richiede patch.
- JW cambia struttura HTML: parser trova 0 parti → errore esplicito,
  richiede patch.

### Logging

- INFO per `downloading / cached` per issue, `decrypted <n> weeks`.
- WARN per ogni parte ignota (`Skipped unknown part: '<label>' week <date>`).
- ERROR con stack trace per abort.

## Testing

### Pure units

| Test | Copre |
|---|---|
| `MeetingWorkbookIssueDiscoveryTest` | generazione issue candidati |
| `PartTypeLabelResolverTest` | tutti 7 codici noti + NotEfficaci + Unknown + caso Spiegare Discorso/Dimostrazione |
| `JwPubWeekDateResolverTest` | cross-month, cross-year, separatori, "1º" |
| `JwPubContentDecryptorTest` | vettori noti per `deriveKeyIv` + round-trip su BLOB reale |
| `JwPubHtmlPartsParserTest` | ordine DOM, detailLine, skip non-Efficaci |
| `JwPubCacheTest` | `@TempDir`, find/store/isUpToDate, meta corrotti |

### Infrastructure con fixture reali

| Test | Fixture |
|---|---|
| `JwPubSqliteReaderTest` | `mwb_I_202601.db` reale in `jvmTest/resources/fixtures/jwpub/` |
| `JwPubArchiveReaderTest` | `mwb_I_202601.jwpub` reale |

### Integration con ktor mock

| Test | Approccio |
|---|---|
| `JwPubMediaClientTest` | `MockEngine`, risposta JSON reale + 404 |
| `JwPubDownloaderTest` | `MockEngine`, bytes fixture + fail cases |
| `JwPubSchemaCatalogDataSourceTest` | end-to-end: `MockEngine` + `.jwpub` reale + `@TempDir` cache, verifica `RemoteSchemaCatalog` completo |

### Application / UI

- `AggiornaSchemiUseCaseTest` — aggiornato per verificare propagazione
  nuovi campi nel result.
- `AggiornaSchemiUseCaseTransactionTest` — invariato.
- `SchemaManagementViewModelTest` — scenari: (a) result con skippedUnknownParts
  → dialog forzato aperto; (b) nessuna modifica + nessuna parte ignota
  → no dialog, banner "gia' aggiornato"; (c) preview con modifiche + parti
  ignote → dialog con entrambe le sezioni.

### Golden path manuale

1. `./gradlew :composeApp:run`.
2. Click "Aggiorna catalogo" (prima volta): download `.jwpub` in
   `cache/GuidaAdunanza/`, banner / dialog coerenti.
3. Click di nuovo: cache hit, banner "gia' aggiornato", no dialog.
4. Rimuovi manualmente un `.jwpub`: click → solo quello viene
   ri-scaricato.
5. Screenshot headless light + dark del dialog (tecnica documentata in
   memory project).

### Fixture acquisition

Script locale `scripts/fetch-jwpub-fixture.sh` (non usato in CI, usato
solo per rinfrescare manualmente le fixture binarie).

## Rischi e mitigazioni

| Rischio | Mitigazione |
|---|---|
| JW cambia schema jwpub (chiave AES, DB schema) | Test di regressione su decrypt con vettori pinnati; errore esplicito; patch release. |
| JW cambia struttura HTML interna | Parser minimo basato su `h3[id^=p]` + `<p>` successivo; test su fixture reali; errore esplicito se 0 parti trovate. |
| JW introduce label di parte nuova | Gia' coperto: parte classificata `Unknown`, import procede, utente informato in modale. |
| File `.jwpub` molto grandi / spazio disco limitato | Cache in user dir, fascicolo ~3.5 MB, limite teorico 6 file/anno = ~20 MB. |
| Legal / ToS sul download `.jwpub` | Endpoint pubblico documentato, stesso usato da JW Library; nessun bypass DRM; decrypt algorithm open-source MIT. |

## Decisioni

1. No scraping WOL a runtime — tutti i dati estratti dal `.jwpub`.
2. No uso di `catalog.db` JW (troppo grande, non necessario per `mwb`).
3. Cache checksum-based dentro `ScuolaDiMinisterData/cache/GuidaAdunanza/`.
4. Lista statica `partTypes` nel source — sovrascrive eventuali drift
   manuali.
5. Label ignote non bloccanti — propagate in UI.
6. Modale di risultato forzata aperta anche senza effective changes se
   ci sono fascicoli scaricati o parti ignote.
7. Mapping label → codice pinnato, fail-fast solo sul formato dei file,
   mai sulle label sconosciute.

## Next step

Design approvato → invocare la skill `writing-plans` per produrre un piano
di implementazione step-by-step.

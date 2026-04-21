# JW Meeting Workbook Catalog Design

## Obiettivo

Sostituire l'attuale aggiornamento catalogo basato su JSON remoto GitHub
(`RemoteConfig.SCHEMAS_CATALOG_URL`) con una pipeline che usa:

1. API pubblica JW per scaricare i fascicoli `Guida per l'adunanza`.
2. SQLite interno al file `.jwpub` per enumerare le settimane.
3. Pagine WOL pubbliche per derivare le parti della settimana.

L'obiettivo applicativo non e' scaricare l'intero catalogo JW, ma costruire il
catalogo operativo locale necessario alla generazione del programma.

## Stato attuale dell'app

Ad oggi l'app scarica il catalogo da:

`composeApp/src/jvmMain/kotlin/org/example/project/core/config/RemoteConfig.kt`

```kotlin
const val SCHEMAS_CATALOG_URL =
    "https://raw.githubusercontent.com/fabiorizzello/efficaci-nel-ministero-data/main/schemas-catalog.json"
```

La nuova implementazione dovra' sostituire quel flusso con un datasource JW.

## Endpoint verificati

### 1. Catalogo pubblicazioni JW Library

Manifest pubblico:

```text
https://app.jw-cdn.org/catalogs/publications/v4/manifest.json
```

Esempio reale verificato il 2026-04-18:

```json
{"version":1,"current":"83636c1a-f008-46ab-8e2d-7e85a1de39f3"}
```

Con `current` si possono scaricare:

```text
https://app.jw-cdn.org/catalogs/publications/v4/{version}/catalog.db.gz
https://app.jw-cdn.org/catalogs/publications/v4/{version}/catalog.info.json.gz
```

`catalog.db.gz` e' un SQLite pubblico da circa 50-60 MB compresso.

### 2. API pubblica per i file di una pubblicazione

Endpoint verificato:

```text
https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS
```

Parametri rilevanti:

- `output=json`
- `pub=...`
- `issue=...` per periodici
- `fileformat=PDF,EPUB,JWPUB,MP3,MP4`
- `langwritten=I`
- `alllangs=0`

Esempio reale per la Guida gennaio-febbraio 2026:

```text
https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS?output=json&pub=mwb&issue=202601&fileformat=JWPUB&alllangs=0&langwritten=I
```

La risposta contiene l'URL del file `.jwpub`, per esempio:

```text
https://cfp2.jw-cdn.org/a/afcdf85/2/o/mwb_I_202601.jwpub
```

### 3. Pagine WOL per il contenuto della settimana

Ogni settimana del fascicolo ha un `MepsDocumentId` risolvibile come pagina WOL:

```text
https://wol.jw.org/it/wol/d/r6/lp-i/{mepsDocumentId}
```

Esempio reale:

```text
https://wol.jw.org/it/wol/d/r6/lp-i/202026001
```

Questa pagina contiene i titoli delle parti e gli anchor HTML locali
(`id="p19"`, `data-pid="19"`, ecc.).

## Quando usare `catalog.db`

`catalog.db` e' utile per:

- esplorare il catalogo completo JW Library
- scoprire quali pubblicazioni/anni/fascicoli esistono
- verificare simboli, issue, `DocumentId`, `PublicationId`

Nel nostro caso non e' il metodo da usare a runtime per `AggiornaSchemi`, perche':

- pesa circa 50 MB compresso
- e' molto piu' ampio del bisogno reale dell'app
- ci serve solo `mwb` italiano e solo i fascicoli necessari

Conclusione pratica:

- `catalog.db` va bene come strumento di reverse engineering e fallback
- il runtime dell'app deve usare il metodo "indovina `mwb` + issue"

## Metodo scelto per la Guida per l'adunanza

### Pattern pubblicazione

Per `Guida per l'adunanza Vita e ministero` il pattern verificato e':

- `pub=mwb`
- `issue=YYYYMM`
- mesi usati: `01`, `03`, `05`, `07`, `09`, `11`
- lingua italiana: `langwritten=I`

Esempi 2026:

- `202601` -> gennaio-febbraio
- `202603` -> marzo-aprile
- `202605` -> maggio-giugno
- `202607` -> luglio-agosto
- `202609` -> settembre-ottobre
- `202611` -> novembre-dicembre

Quindi il client puo' "indovinare" gli issue senza scaricare `catalog.db`.

### Algoritmo raccomandato

Input:

- anno, per esempio `2026`
- lingua scritta, inizialmente fissa a `I`

Passi:

1. Generare gli issue candidati: `202601`, `202603`, `202605`, `202607`, `202609`, `202611`.
2. Per ogni issue chiamare `GETPUBMEDIALINKS`.
3. Se la risposta e' `404`, il fascicolo non esiste ancora.
4. Se la risposta contiene `JWPUB`, scaricare il file.
5. Aprire il `.jwpub`, leggere il DB SQLite interno, estrarre le settimane.
6. Per ogni settimana leggere il `MepsDocumentId`.
7. Risolvere la pagina WOL pubblica della settimana.
8. Estrarre le parti dal markup HTML.
9. Convertire il risultato nel catalogo locale dell'app.

Questo approccio evita il download del catalogo globale e scarica solo i fascicoli
davvero necessari.

## File format supportati da `GETPUBMEDIALINKS`

Verificati:

- `PDF`
- `EPUB`
- `JWPUB`
- `MP3`
- `MP4`

Non valido:

- `JSON` come `fileformat`

Importante:

- `output=json` controlla il formato della risposta API
- `fileformat=...` controlla i tipi di file richiesti

## Cosa contiene il file `.jwpub`

Il `.jwpub` e' uno zip con almeno:

- `manifest.json`
- `contents`

`contents` e' a sua volta uno zip con:

- DB SQLite della pubblicazione, per esempio `mwb_I_202601.db`
- immagini e asset locali

### Metadati disponibili in `manifest.json`

Campi rilevanti verificati:

- `publication.title`
- `publication.shortTitle`
- `publication.symbol`
- `publication.issueId`
- `publication.issueNumber`
- `publication.year`
- `publication.issueProperties.title`
- `publication.issueProperties.coverTitle`
- `publication.categories`

Per esempio, per gennaio-febbraio 2026:

- `symbol = mwb26`
- `issueId = 20260100`
- `issueProperties.symbol = mwb26.01`
- `issueProperties.title = Guida per l'adunanza Vita e ministero, gennaio-febbraio 2026`

### Tabelle rilevanti nel DB interno

Tabelle trovate nel DB del fascicolo:

- `Publication`
- `Document`
- `DocumentParagraph`
- `PublicationView`
- `PublicationViewItem`
- `PublicationViewItemField`
- `DocumentExtract`
- `DocumentMultimedia`
- `Multimedia`

### Informazione utile per il catalogo dell'app

Dal DB del `.jwpub` si ottiene bene:

- elenco settimane del fascicolo
- titolo settimana
- riferimento biblico settimana
- `MepsDocumentId` della settimana

Esempio reale da `mwb_I_202601.db`:

- `DocumentId = 1`, `MepsDocumentId = 202026001`, `Title = 5-11 gennaio`, `Subtitle = ISAIA 17-20`
- `DocumentId = 2`, `MepsDocumentId = 202026002`, `Title = 12-18 gennaio`, `Subtitle = ISAIA 21-23`

### Limiti del DB interno

Il DB interno non espone in modo semplice una tabella `Part` gia' pronta con:

- id parte
- sezione
- titolo parte
- durata

Le settimane sono presenti, ma i titoli delle parti sono molto piu' facili da
derivare dalla pagina WOL pubblica del `MepsDocumentId` piuttosto che dal BLOB
compresso del campo `Document.Content`.

Conclusione:

- usare SQLite del `.jwpub` per scoprire le settimane
- usare WOL per estrarre le parti

## Cosa si trova nella pagina WOL della settimana

Pagina tipo:

```text
https://wol.jw.org/it/wol/d/r6/lp-i/202026001
```

Contiene:

- titolo settimana
- riferimento biblico
- intestazioni di sezione:
  - `TESORI DELLA PAROLA DI DIO`
  - `EFFICACI NEL MINISTERO`
  - `VITA CRISTIANA`
- parti come `<h3 ... id="p19" data-pid="19">`

Esempi reali:

- `p3` -> `Cantico 153 e preghiera | Commenti introduttivi`
- `p5` -> `1. "La parte che spetta a chi ci saccheggia"`
- `p10` -> `2. Gemme spirituali`
- `p16` -> `3. Lettura biblica`
- `p19` -> `4. Iniziare una conversazione`
- `p46` -> `9. Studio biblico di congregazione`

### Informazione importante per il mapping delle parti

Il solo titolo della parte non basta sempre per derivare il `partTypeCode`.

Per alcune parti di `EFFICACI NEL MINISTERO`, soprattutto
`Spiegare quello in cui si crede`, il tipo reale e' distinguibile solo leggendo
anche la prima riga descrittiva immediatamente sotto l'`h3`.

Esempi reali verificati:

- titolo: `6. Spiegare quello in cui si crede`
  - dettaglio: `(5 min) Discorso. ...`
- titolo: `6. Spiegare quello in cui si crede`
  - dettaglio: `(4 min) Dimostrazione. ...`

Quindi il parser WOL deve estrarre almeno:

- `title`
- `pid`
- `section`
- `detailLine` o equivalente

Il mapping finale non deve basarsi solo sulla label del titolo.

## Significato di `data-pid`

`data-pid` non e' un ID globale.

E' stabile solo dentro il singolo documento WOL.

Quindi:

- valido: `202026001:p19`
- non valido da solo: `p19`

Abbiamo verificato che lo stesso tipo di parte cambia `data-pid` tra settimane diverse.

## Modello dati raccomandato per l'app

### Identificatore settimana

Usare `MepsDocumentId` come chiave esterna/stabile della settimana JW.

### Identificatore parte

Usare una chiave composta:

```text
{mepsDocumentId}:{pid}
```

Esempi:

- `202026001:p19`
- `202026001:p46`

### Campi da salvare nel catalogo locale

Per il catalogo operativo bastano:

- anno
- issue `YYYYMM`
- file `.jwpub` sorgente
- settimana data/titolo
- riferimento biblico
- `MepsDocumentId`
- sezione parte
- titolo parte
- prima riga descrittiva della parte
- `pid`
- ordinamento nel documento

Facoltativi:

- URL WOL settimana
- URL anchor parte
- titolo fascicolo

### Campi consigliati per il mapping dominio

Per le parti studenti e' consigliato salvare anche un campo derivato tipo:

- `deliveryType`

Valori iniziali osservati:

- `DISCURSO`
- `DIMOSTRAZIONE`
- altri valori eventualmente dedotti dal testo descrittivo

Il valore puo' essere derivato dalla prima riga descrittiva dopo il titolo della parte.

## Proposta di nuova pipeline `AggiornaSchemi`

### Step 1. Discovery fascicoli

Per l'anno target:

- provare `202601`, `202603`, `202605`, `202607`, `202609`, `202611`
- chiamare `GETPUBMEDIALINKS`
- tenere solo gli issue che restituiscono `JWPUB`

### Step 2. Download fascicoli

Scaricare i `.jwpub` in cache locale applicativa.

Cache key minima:

- `langwritten`
- `issue`
- `checksum` o `modifiedDatetime`

### Step 3. Parsing SQLite del `.jwpub`

Dal DB interno estrarre:

- fascicolo
- settimane
- `MepsDocumentId`

### Step 4. Enrichment da WOL

Per ogni `MepsDocumentId`:

- scaricare la pagina WOL
- estrarre in ordine gli `<h3>` delle parti
- leggere anche il primo `<p>` descrittivo associato alla parte
- associare ad ogni parte:
  - `section`
  - `title`
  - `detailLine`
  - `deliveryType`
  - `pid`
  - `sortOrder`

### Step 5. Mapping dominio locale

Convertire il risultato nel catalogo interno dell'app:

- `PartType`/template schema o nuovo modello equivalente
- sostituzione atomica del catalogo remoto, come gia' fa `AggiornaSchemiUseCase`

### Step 6. Transazione locale

Mantenere il comportamento attuale:

- validazione upfront
- singola transazione DB
- nessun aggiornamento parziale

## Decisioni consigliate

### Decisione 1. Non usare `catalog.db` a runtime

Motivo:

- troppo grande
- troppo generale
- non necessario per il solo `mwb`

### Decisione 2. Usare `GETPUBMEDIALINKS` come source of truth per il file

Motivo:

- endpoint pubblico verificato
- evita di indovinare direttamente l'URL CDN finale
- restituisce checksum, file size e metadata utili

### Decisione 3. Usare SQLite del `.jwpub` solo per settimane

Motivo:

- struttura affidabile
- `MepsDocumentId` disponibile
- parsing semplice

### Decisione 4. Usare WOL per le parti

Motivo:

- i titoli delle parti sono gia' presenti in chiaro
- gli anchor `pid` sono disponibili
- evitare reverse engineering del BLOB compresso `Document.Content`

## Rischi e mitigazioni

### Rischio: fascicoli futuri non ancora pubblicati

Mitigazione:

- considerare `404` come "non ancora disponibile"
- importare solo fascicoli trovati

### Rischio: piccole variazioni HTML WOL

Mitigazione:

- parser minimo basato su `h2` e `h3`
- test fixture con pagine reali campione
- fallback esplicito se non vengono trovate parti

### Rischio: parti con numerazione variabile

Esempio:

- alcune settimane hanno `8. Studio biblico di congregazione`
- altre `9. Studio biblico di congregazione`

Mitigazione:

- non usare il numero come chiave
- usare `sortOrder` e titolo normalizzato

### Rischio: stessa label, formato diverso

Esempio reale:

- `Spiegare quello in cui si crede` puo' essere `Discorso.`
- `Spiegare quello in cui si crede` puo' essere `Dimostrazione.`

Mitigazione:

- il mapping verso `partTypeCode` deve usare `title + detailLine`
- se il parser non riesce a derivare il formato reale, l'import deve fallire con
  errore esplicito

## Strategia mapping label -> partTypeCode

Il seed attuale dell'app contiene questi codici principali:

- `LETTURA_DELLA_BIBBIA`
- `INIZIARE_CONVERSAZIONE`
- `COLTIVARE_INTERESSE`
- `FARE_DISCEPOLI`
- `DISCORSO`
- `SPIEGARE_CIO_CHE_SI_CREDE`

La label WOL da sola non e' sempre sufficiente per scegliere il codice corretto.

### Mapping minimo atteso

- `Lettura biblica` -> `LETTURA_DELLA_BIBBIA`
- `Iniziare una conversazione` -> `INIZIARE_CONVERSAZIONE`
- `Coltivare l'interesse` -> `COLTIVARE_INTERESSE`
- `Fare discepoli` -> `FARE_DISCEPOLI`
- `Discorso` -> `DISCORSO`

### Caso speciale: `Spiegare quello in cui si crede`

Usare anche `detailLine`.

Regola proposta:

- `Spiegare quello in cui si crede` + `Discorso.` -> `DISCORSO`
- `Spiegare quello in cui si crede` + `Dimostrazione.` -> nuovo codice distinto
  dell'app oppure mapping esplicito dedicato
- `Spiegare quello in cui si crede` + formato sconosciuto -> errore esplicito

Questo punto e' importante perche' il JSON intermedio ricavato dal solo `h3`
perderebbe una distinzione di dominio rilevante.

### Regola architetturale consigliata

Il runtime non deve generare nuovi `partTypeCode` in modo silenzioso.

Meglio:

1. normalizzare `title`
2. leggere `detailLine`
3. risolvere il codice tramite tabella di mapping esplicita
4. se manca una regola, fallire con errore diagnostico

Questo protegge:

- idoneita' esistenti
- storico assegnazioni
- coerenza del catalogo locale

### Rischio: `data-pid` non globale

Mitigazione:

- usare chiave composta `{mepsDocumentId}:{pid}`

## Implementazione consigliata nel codebase

Nuovi componenti probabili:

- `JwPubMediaClient`
  - chiama `GETPUBMEDIALINKS`
- `MeetingWorkbookIssueDiscovery`
  - genera issue candidati `YYYY01,03,05,07,09,11`
- `JwPubArchiveReader`
  - apre `.jwpub`, estrae SQLite
- `MeetingWorkbookSqliteReader`
  - legge settimane e `MepsDocumentId`
- `WolMeetingWeekClient`
  - scarica pagina WOL settimana
- `WolMeetingWeekParser`
  - estrae sezioni e parti
- `AggiornaSchemiUseCase`
  - orchestration e persistenza finale

## Esempio end-to-end

Per `2026`:

1. Chiamare:

```text
GETPUBMEDIALINKS?output=json&pub=mwb&issue=202601&fileformat=JWPUB&alllangs=0&langwritten=I
```

2. Scaricare `mwb_I_202601.jwpub`.
3. Aprire `mwb_I_202601.db`.
4. Leggere `Document`:
   - `202026001` -> `5-11 gennaio`
   - `202026002` -> `12-18 gennaio`
   - ...
5. Chiamare WOL:

```text
https://wol.jw.org/it/wol/d/r6/lp-i/202026001
```

6. Estrarre le parti:
   - `TESORI DELLA PAROLA DI DIO` -> `1. ...`, `2. Gemme spirituali`, `3. Lettura biblica`
   - `EFFICACI NEL MINISTERO` -> `4. ...`, `5. ...`, `6. ...`
   - `VITA CRISTIANA` -> `Cantico ...`, `7. ...`, `8. ...`, `9. ...`, `Commenti conclusivi ...`

## Nota finale

Per questa app il catalogo da modellare non e' il catalogo pubblicazioni JW completo.

Il catalogo reale da derivare e':

- fascicolo `mwb`
- settimane del fascicolo
- parti di ogni settimana

Questa e' la granularita' corretta per sostituire il file JSON mantenuto a mano su GitHub.

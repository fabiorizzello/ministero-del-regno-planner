# Verifica catalogo JW: `Cosa direste?`

Data della verifica: 2026-08-12.

## Esito

La fonte ufficiale conferma la nuova parte nella **terza settimana del fascicolo settembre-ottobre 2026**, cioè **21-27 settembre 2026**. Non si tratta della settimana 14-20 settembre: il fascicolo parte il 7 settembre e la sua terza settimana è quella del 21.

Valori estratti dal JWPUB:

| Campo | Valore |
|---|---|
| Fascicolo | `mwb_I_202609.jwpub` |
| `DocumentId` / `MepsDocumentId` | `3` / `202026254` |
| Settimana | `21-27 settembre` |
| `pid` | `p24` |
| Titolo grezzo | `6. Cosa direste?` |
| Titolo logico | `Cosa direste?` |
| Sezione | `EFFICACI` (`EFFICACI NEL MINISTERO`) |
| `sortOrder` prodotto dal parser | `6` |
| `detailLine` | `(6 min) Trattazione. DI CASA IN CASA. Esamina brevemente lmd lezione 2 punto 5. Poi chiedi:` |

Il titolo esatto è quindi **`Cosa direste?`**, al plurale e con punto interrogativo. Il resolver può identificarlo dopo la rimozione del numero iniziale mediante la chiave normalizzata `cosa direste`.

## Fonti ufficiali

- L'[API media JW per `mwb`, issue `202609`, lingua italiana](https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS?output=json&pub=mwb&issue=202609&fileformat=JWPUB&alllangs=0&langwritten=I) identifica il fascicolo come “Settembre-ottobre 2026” e pubblica il file JWPUB italiano. Al momento della verifica dichiarava 3.710.226 byte, ultima modifica `2026-02-24 08:05:47` e checksum MD5 `04a837589b04d9a0197e0d32151b4ad6`.
- Il [JWPUB ufficiale italiano](https://cfp2.jw-cdn.org/a/22eaaea/1/o/mwb_I_202609.jwpub) scaricato dall'URL restituito dall'API ha lo stesso MD5 dichiarato; SHA-256 locale: `1d44bf872ad2bae66b687229a63fdcc63b02f8088170d800d20354480b254744`.
- La [pagina ufficiale della settimana 21-27 settembre 2026](https://www.jw.org/it/biblioteca-digitale/guida-attivita-adunanza/mwb-settembre-ottobre-2026/Programma-adunanza-Vita-e-ministero-dal-21-al-27-settembre-2026/) espone lo stesso titolo, nello stesso punto della sezione “Efficaci nel ministero”. Il finder ufficiale equivalente è `https://www.jw.org/finder?wtlocale=I&docid=202026254&srcid=share`.

Queste sono tutte fonti JW di prima parte; non sono stati usati cataloghi o trascrizioni di terzi per determinare i valori.

## Rappresentazione HTML nel JWPUB

Il documento decrittato `DocumentId=3` contiene, immediatamente dopo le parti 4 e 5 della sezione `EFFICACI NEL MINISTERO`:

```html
<h3 class="du-fontSize--base du-color--gold-700 du-margin-top--8 du-margin-bottom--0"
    id="p24" data-pid="24"><strong>6. Cosa direste?</strong></h3>
<div id="tt37" class="du-margin-inlineStart--5 du-margin-inlineStart-desktopOnly--6">
  <div id="tt38" class="du-color--textSubdued du-margin-top--1 du-margin-children-vertical--0">
    <p id="p25" data-pid="25">(6 min) Trattazione. DI CASA IN CASA. Esamina brevemente
      <a class="xt" data-xtid="35" href="jwpub://p/I:1102023302/15-15:251"><em>lmd</em> lezione 2 punto 5</a>. Poi chiedi:</p>
  </div>
</div>
```

Per `JwPubHtmlPartsParser`, l'`h3` fornisce titolo e `pid`; `findDetailLine` visita il `div` fratello successivo e prende il primo `p`, cioè `p25`. `cleanText` trasforma gli spazi non separabili in spazi normali. La sezione resta `EFFICACI`, impostata dall'`h2` precedente.

La pagina HTML pubblica usa la stessa struttura semantica (`h3#p24`, quindi `div#tt37 > div#tt38 > p#p25`), anche se converte il collegamento `jwpub://` nel corrispondente link web.

## Riproduzione

Recupero dei metadati e download, da PowerShell:

```powershell
$api = 'https://b.jw-cdn.org/apis/pub-media/GETPUBMEDIALINKS?output=json&pub=mwb&issue=202609&fileformat=JWPUB&alllangs=0&langwritten=I'
$media = Invoke-RestMethod -Uri $api
$entry = $media.files.I.JWPUB[0]
$entry.file.url
$entry.file.checksum
$entry.filesize
Invoke-WebRequest -UseBasicParsing -Uri $entry.file.url -OutFile .\mwb_I_202609.jwpub
Get-FileHash -Algorithm MD5 .\mwb_I_202609.jwpub
```

Il JWPUB è uno ZIP esterno; l'entry `contents` è a sua volta uno ZIP contenente `mwb_I_202609.db`. Dopo l'estrazione, questa query riproduce l'identificazione della settimana:

```powershell
@'
import sqlite3
db = sqlite3.connect(r'.\mwb_I_202609.db')
print(db.execute("""
  SELECT DocumentId, MepsDocumentId, Title, Subtitle, length(Content)
  FROM Document WHERE Class='106' ORDER BY DocumentId
""").fetchall())
'@ | python -
```

La terza riga è `(3, 202026254, '21-27 settembre', 'GEREMIA 36-37', 3680)`. Per ottenere l'HTML, il blob `Content` va elaborato con le classi già presenti nel repository: `JwPubSqliteReader` legge il record, `JwPubContentDecryptor` deriva chiave e IV dalla pub card `4_mwb26_2026_20260900` e decomprime il contenuto, quindi `JwPubHtmlPartsParser` restituisce il record riportato sopra. La verifica diretta dell'HTML web non richiede decrittazione:

```powershell
$url = 'https://www.jw.org/finder?wtlocale=I&docid=202026254&srcid=share'
$html = (Invoke-WebRequest -UseBasicParsing -Uri $url).Content
$html -split "`n" | Select-String -Pattern 'id="p24"|id="p25"' -Context 0,2
```

## Osservazione aggiuntiva

Nel medesimo fascicolo il titolo ricorre anche nelle settimane **28 settembre-4 ottobre** e **12-18 ottobre**. La presenza del 21-27 settembre non è quindi un'anomalia isolata del documento.

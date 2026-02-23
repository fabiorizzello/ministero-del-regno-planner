# Design: Schemas Catalog Publishing

## Context
`Aggiorna schemi` scarica il catalogo schemi da `RemoteConfig.SCHEMAS_CATALOG_URL`, che punta a un file raw su GitHub. Attualmente l'URL produce 404. Serve ripristinare il file e definire un formato stabile e semplice da mantenere.

## Goals
- Ripristinare il download del catalogo schemi con un file `schemas-catalog.json` pubblicato su GitHub.
- Mantenere il catalogo semplice (un solo file) e aggiornabile manualmente.
- Includere solo le parti attive e solo le settimane che l'utente decide di pubblicare.

## Non-Goals
- Versioning automatico o archiviazione storica completa.
- Download incrementale o multi-file.
- Modifiche al codice client oltre alla disponibilita' del file.

## Approach (Recommended)
Usare un unico file `schemas-catalog.json` nel repo `fabiooo4/efficaci-nel-ministero-data` sul branch `main`. Il file contiene:
- `version`: stringa versione dataset (es. `2026-02-23` o `v1.4`).
- `updated_at`: timestamp ISO 8601.
- `part_types`: elenco delle sole parti attive.
- `weeks`: elenco delle settimane da importare, ciascuna con data di inizio settimana e lista ordinata dei codici parte.

Questa scelta minimizza la complessita' e mantiene un solo endpoint HTTP.

## Data Shape
Esempio di struttura:

```json
{
  "version": "2026-02-23",
  "updated_at": "2026-02-23T12:00:00Z",
  "part_types": [
    { "code": "LET_BIB", "name": "Lettura biblica", "gender": "M" },
    { "code": "INIZIA", "name": "Iniziare una conversazione", "gender": "M" },
    { "code": "COLTIVA", "name": "Coltivare l'interesse", "gender": "M" },
    { "code": "DISCEPOLI", "name": "Fare discepoli", "gender": "M" },
    { "code": "SPIEGA", "name": "Spiegare quello in cui si crede", "gender": "M" },
    { "code": "DISCORSO", "name": "Discorso", "gender": "M" }
  ],
  "weeks": [
    {
      "week_start_date": "2026-02-16",
      "part_type_codes": ["LET_BIB", "INIZIA", "COLTIVA", "DISCEPOLI", "SPIEGA", "DISCORSO"]
    }
  ]
}
```

## Publishing Workflow
- Aggiornare manualmente `schemas-catalog.json` nel repo dati.
- Assicurarsi che `part_types` includa solo parti attive.
- Includere solo le settimane desiderate in `weeks`.
- Incrementare `version` e `updated_at` ad ogni modifica.

## Risks
- Crescita del file nel tempo: mitigata mantenendo solo settimane desiderate.
- Errori manuali: mitigati con validazione locale prima del push (opzionale in futuro).

## Validation
- Verificare che l'URL raw del file risponda con 200.
- Confermare che `Aggiorna schemi` completi il download senza errori 404.

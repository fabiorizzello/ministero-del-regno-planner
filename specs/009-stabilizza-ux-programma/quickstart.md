# Quickstart: Stabilizzazione UX Programma e Studenti

## Goal

Verificare rapidamente i fix di coerenza dati, preservazione assegnazioni, ricerca fuzzy e stabilita' visuale nelle schermate toccate dalla feature.

## Preconditions

1. Repository aperto in `C:\Users\fabio\dev_windows\ministero-del-regno-planner`
2. JDK 21 disponibile e `JAVA_HOME` configurata se `./gradlew` non trova `java`
3. Dataset locale con almeno:
   - proclamatori con nomi simili o digitabili con refusi;
   - almeno una settimana con assegnazioni esistenti;
   - capability differenti tra studenti.

## Recommended Implementation Order

1. Scrivere/aggiornare i test per il refresh suggestions e la fuzzy search.
2. Implementare la preservazione selettiva delle assegnazioni nel path di modifica settimana.
3. Implementare i fix UI di ricerca, etichette, paginazione e card.
4. Eseguire test automatici e verifica manuale mirata.

## Automated Validation

```powershell
./gradlew :composeApp:jvmTest
```

Se l'ambiente supporta la verifica migrazioni SQLDelight e build completa:

```powershell
./gradlew :composeApp:build
```

## Manual Verification Checklist

### Workspace programma

1. Aprire una settimana con una parte assegnabile.
2. Aprire la modale di assegnazione.
3. Cambiare il toggle di riposo:
   - l'elenco si ricarica;
   - nessun dato stale resta visibile;
   - la terminologia utente usa "riposo".
4. Cercare una persona con input imperfetto:
   - la persona corretta compare in alto;
   - l'ordinamento resta stabile.
5. Modificare una settimana gia' assegnata:
   - aggiungendo una sola parte si preservano le assegnazioni esistenti;
   - rimuovendo una sola parte si perde solo l'assegnazione collegata;
   - salvando una parte invariata la sua assegnazione resta.
6. Aprire un programma passato e verificare che "Salta settimana" resti disponibile/usabile.

### Schermata studenti

1. Cercare uno studente con refuso lieve.
2. Verificare che i risultati siano ordinati per vicinanza e non solo per contains.
3. Controllare che la colonna capability sia rinominata in italiano.
4. Scorrere in basso, cambiare pagina e verificare il ritorno automatico in alto.
5. Passare alla vista card con studenti ricchi di capability e verificare:
   - toolbar azioni stabile;
   - pulsanti modifica/rimuovi non schiacciati;
   - hover/focus coerenti.

## Done Criteria

- Tutti i test `:composeApp:jvmTest` passano.
- I casi manuali sopra elencati risultano coerenti con i contratti UI.
- Nessuna etichetta inglese residua nelle superfici modificate.

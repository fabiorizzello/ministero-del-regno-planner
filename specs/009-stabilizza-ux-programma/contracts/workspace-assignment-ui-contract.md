# UI Contract: Workspace Assignment and Week Editing

## Scope

Copre i comportamenti utente di:
- modale di assegnazione proclamatori nel workspace programma;
- modifica parti di una settimana gia' assegnata;
- azione "salta settimana" su settimane di programmi passati o correnti.

## Contract 1: Refresh suggestion list

1. Quando la modale di assegnazione e' aperta, il cambio del controllo di riposo MUST innescare un nuovo caricamento dei suggerimenti.
2. Il nuovo elenco MUST riflettere insieme:
   - settimana e parte correnti;
   - slot corrente;
   - ricerca attiva;
   - impostazione aggiornata di riposo.
3. Durante il refresh la UI MUST mostrare uno stato di caricamento non ambiguo.
4. Se non esistono candidati coerenti, la UI MUST mostrare uno stato vuoto in italiano.
5. Le etichette utente MUST parlare di "riposo" e non di "cooldown".

## Contract 2: Search behavior inside picker

1. La ricerca nella modale MUST essere fuzzy e ordinare i risultati per vicinanza testuale.
2. A parita' di score, l'ordinamento MUST essere stabile e alfabetico.
3. La ricerca MUST continuare a rispettare i filtri di dominio gia' in uso:
   - persona non sospesa;
   - non gia' assegnata nella stessa settimana;
   - idoneita' ruolo;
   - eventuale esclusione per riposo se attiva.

## Contract 3: Preserve valid assignments on week edit

1. Salvare una modifica alla settimana MUST preservare le assegnazioni collegate a parti rimaste logicamente identiche.
2. Aggiungere una parte MUST lasciare non assegnata solo la nuova parte.
3. Rimuovere una parte MUST eliminare solo le assegnazioni collegate alla parte rimossa.
4. Riordinare o risalvare senza cambiare una parte invariata MUST NOT cancellare la sua assegnazione.
5. Se la modifica comporta una perdita reale di assegnazioni, la UI MAY mostrarne l'impatto, ma MUST NOT azzerare assegnazioni non coinvolte.

## Contract 4: Skip week availability

1. L'azione "Salta settimana" MUST restare visibile anche per settimane appartenenti a programmi passati.
2. Se la settimana e' gia' saltata, la UI MUST mostrare uno stato coerente e l'azione di riattivazione, senza nascondere il contesto.
3. Ogni operazione di salto o riattivazione MUST produrre feedback esplicito di successo/errore.

## Test Implications

- Test ViewModel per refresh suggestions al cambio riposo.
- Test use case/domain per preservazione selettiva delle assegnazioni.
- Test UI/interaction per disponibilita' di "Salta settimana" su programma passato.

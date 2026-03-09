# Assegnazioni Fair: Edge Case e Regole

## Obiettivo
Definire regole di fairness per l'assegnazione studenti che considerino sia storico passato sia prenotazioni future, evitando bias e sovra-utilizzo.

## Principio Base
Per il ranking non basta "ultima assegnazione" in senso cronologico.
Serve una distanza temporale effettiva che consideri:
- passato
- futuro
- tipo ruolo (conduttore vs assistente)

## Regola Consigliata per il Ranking
Per ogni persona, calcolare l'impatto da assegnazioni passate e future, pesato per ruolo:

`impatto = pesoRuolo / (abs(settimane) + 1)`

Poi usare:

`impattoEffettivo = max(impattoPassato, impattoFuturo)`

Interpretazione:
- la distanza piu vicina (in futuro o in passato) conta di piu
- il ruolo pesa l'impatto (es. conduttore puo pesare piu di assistente)

## Esempio
Caso:
- 2 settimane fa: conduttore
- tra 3 settimane: assistente

Se sto assegnando un ruolo conduttore:
- evento passato da conduttore tende ad avere impatto maggiore (ruolo piu affine + distanza minore)
- evento futuro da assistente impatta comunque, ma meno

## Edge Case da Gestire
1. Ruoli diversi a distanze diverse
- Non usare solo distanza pura.
- Usare distanza pesata per ruolo target.

2. Parita ripetute e bias alfabetico
- Evitare tie-break fisso su cognome/nome come primo criterio.
- Introdurre rotazione deterministica per settimana.

3. Sovra-uso nella stessa finestra temporale
- Aggiungere cap/penalita su numero assegnazioni in finestra mobile (es. 4-6 settimane).

4. Monopolio di un solo ruolo
- Tracciare fairness separata per conduttore e assistente.

5. Nuovi mai assegnati
- Priorita alta, ma con limiti per non sbilanciare una singola settimana.

6. Prenotazioni future lontane
- Ridurre l'impatto oltre un orizzonte (es. >8 settimane).

7. Ripetizione coppie
- Penalizzare coppie studente-compagno ripetute troppo spesso.

8. Settimane annullate/saltate
- Cooldown e fairness basati su assegnazioni effettive, non su settimane vuote.

9. Override manuali
- Registrare override e prevedere "riassorbimento" automatico del debito fairness.

10. Riattivazione dopo sospensione
- Definire policy esplicita (reset totale, parziale, o mantenimento storico).

## UI: Cosa Mostrare
Mostrare entrambe le direzioni temporali quando utili:
- "2 settimane fa"
- "Tra 3 settimane"

Suggerimento UI compatto:
- `2 settimane fa · tra 3 settimane`

## Decisioni Operative Consigliate
1. Mantenere nel modello sia distanza assoluta sia direzione (passato/futuro).
2. Separare metrica di ranking da etichetta UI.
3. Usare configurazione per i pesi ruolo (lead/assist) e orizzonte futuro.
4. Aggiungere test su scenari misti passato+futuro e ruoli incrociati.

## Note
Queste regole puntano a "fairness percepita" oltre che matematica: chi pianifica deve capire facilmente perche una persona e proposta prima di un'altra.

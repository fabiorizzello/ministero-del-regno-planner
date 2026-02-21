# Roadmap Implementazione - backlog residuo

Nota: questa roadmap contiene solo attivita' nuove/non completate. Le milestone gia' chiuse sono state rimosse.

## 1. Regole di sviluppo
- Ogni slice e' end-to-end: UI, use case, repository, persistenza, test.
- Dominio al centro: regole in dominio/use case, non nella UI.
- Service layer minimo: introdurre servizi solo se necessari.
- Priorita' su valore operativo reale per utente finale.

## 2. Milestone M6 - Output Operativo (non avviata)
Obiettivo: produrre materiali pronti per invio/stampa.

Use case:
- `GeneraImmaginiAssegnazioni`
- `GeneraPdfAssegnazioni`

UI:
- Selezione settimana e parti da includere.
- Azione genera immagini.
- Azione genera PDF.

Regole:
- PNG per persona con nome `YYYYMM<lun>-<dom>_Nome_Cognome.png`.
- PDF dinamico A4 con N foglietti in base alle parti selezionate.
- Output in cartella `exports`.

Definition of Done:
- Generazione PNG stabile su settimane reali.
- Generazione PDF leggibile e consistente.
- Test use case + test integrazione file output.

## 3. Milestone M8 - Aggiornamenti applicazione (non avviata)
Obiettivo: aggiornare con minima interazione utente.

Use case:
- `VerificaAggiornamenti`
- `AggiornaApplicazione`

UI:
- Pulsante `Verifica aggiornamenti`.
- Pulsante `Aggiorna` abilitato solo se update disponibile.
- Feedback stato download/installazione e gestione errore.

Regole:
- Check all'avvio + ogni 30 minuti.
- Source update: GitHub Releases.
- Canali separabili: aggiornamento app e aggiornamento dati remoti.

Definition of Done:
- Verifica update affidabile in ambiente reale.
- Flusso update guidato end-to-end con fallback in caso di errore.
- Logging diagnostico completo del processo update.

## 4. Milestone M9 - Cruscotto pianificazione estesa (nuova da next.md)
Obiettivo: dare visibilita' anticipata sul piano settimane future e sui gap.

Use case:
- `CaricaPanoramicaPianificazioneFutura`
- `CalcolaProgressoPianificazione`
- `GeneraAlertCoperturaSettimane`

UI:
- Cruscotto con orizzonte 1-2 mesi (configurabile in settimane).
- Stato per settimana: `da organizzare`, `parziale`, `pianificata`.
- Indicatore "pianificato fino al <data>".
- Alert esplicito se mancano programmi nelle prossime 4 settimane.

Regole:
- Nessun blocco thread UI su calcolo/stato.
- Alert deduplicati e aggiornati quando cambia la settimana selezionata o i dati remoti.
- Coerenza con lo stato assegnazioni gia' presente.

Definition of Done:
- Dashboard consultabile con range futuro minimo 8 settimane.
- Alert "prossime 4 settimane" affidabile e testato.
- Test use case di calcolo progresso/copertura.

## 5. Ordine di rilascio consigliato (residuo)
1. M9 Cruscotto pianificazione estesa.
2. M6 Output operativo.
3. M8 Aggiornamenti applicazione.

## 6. Gate qualita' per ogni milestone
- Test use case dominio obbligatori.
- Nessuna regola business nei composable UI.
- I/O e rete sempre fuori thread UI.
- Logging diagnostico significativo per troubleshooting utente.

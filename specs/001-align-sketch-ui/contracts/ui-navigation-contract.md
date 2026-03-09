# Contract: UI Navigation & Workspace Layout

## Scope

Contratto UI per navigazione top-level e struttura workspace coerente con reference sketch.

## Navigation Contract

| Item | Requirement |
|------|-------------|
| Top bar sections | Mostrare solo `Programma`, `Proclamatori`, `Diagnostica` |
| Settings access | Impostazioni assegnatore raggiungibili solo da Programma |
| Section switch | Cambio sezione preserva linguaggio visivo e comportamento coerente |

## Workspace Contract (Programma)

| Region | Mandatory Content |
|--------|-------------------|
| Left panel | Mesi + azioni di gestione mese/schemi |
| Center panel | Timeline settimane + board parti/assegnazioni |
| Right panel | Azioni operative, metriche copertura, impostazioni assegnatore, feed attivita |

## Screen State Contract

Ogni schermata (`Programma`, `Proclamatori`, `Diagnostica`) deve esplicitare i seguenti stati:

| State | Rendering Requirement |
|-------|------------------------|
| Loading | Indicatore di caricamento esplicito |
| Error | Messaggio errore esplicito + azione di recupero |
| Empty | Stato vuoto esplicito e comprensibile |
| Content | Dati effettivi con azioni disponibili |

## Visual Consistency Contract

| Area | Requirement |
|------|-------------|
| Tokens | Colori, tipografia, spacing e radius derivati da design system condiviso |
| Components | Wrapper semantici riusati; no pattern ad-hoc per singola schermata |
| Theme | Light-only |
| Language | Testi utente in italiano |

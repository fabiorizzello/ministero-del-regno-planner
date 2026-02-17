---
name: compose-feedback-banner-standard
description: Standardizzare notifiche utente di esito (successo/errore) in Kotlin Compose Desktop per questo progetto. Usare questa skill quando una schermata esegue mutation o operazioni che devono mostrare un feedback persistente e coerente con il tema.
---

# Compose Feedback Banner Standard

## Obiettivo

Rendere uniforme il feedback operativo usando componenti condivisi:
- `composeApp/src/jvmMain/kotlin/org/example/project/ui/components/FeedbackBanner.kt`
- Stato tipizzato (`FeedbackBannerModel`, `FeedbackBannerKind`) nel View State della schermata.

## Workflow

1. Definire stato banner come nullable: `var notice by mutableStateOf<FeedbackBannerModel?>(null)`.
2. Mappare `SUCCESS` su operazioni concluse.
3. Mappare `ERROR` su validazioni o errori dominio.
4. Mostrare banner con `FeedbackBanner(notice)` in posizione costante della schermata (sotto toolbar/sezione azioni).
5. Non usare stringhe non tipizzate o banner locali duplicati.

## Regole

- Usare solo due livelli base: successo ed errore.
- Rendere il testo selezionabile (gia' gestito dal componente standard).
- Usare i colori del componente standard; non duplicare palette nella feature.
- Lasciare il messaggio visibile finche' non viene sovrascritto da una nuova azione o reset esplicito.

## Checklist Finale

- Stato banner tipizzato presente.
- Componente `FeedbackBanner` riusato, non duplicato.
- Messaggi di successo/errore mostrati in modo coerente in dark mode.

package org.example.project.feature.weeklyparts.domain

enum class SexRule {
    /** Solo proclamatori maschi (filtro hard). */
    UOMO,

    /**
     * Intende "stesso sesso degli altri assegnati alla parte".
     * Comportamento definitivo:
     * - Assegnazione manuale: non filtrante (`passaSesso = true`); [SuggestedProclamatore.sexMismatch]
     *   segnala visivamente la discrepanza ma non impedisce la selezione.
     * - Auto-assign: soft-hard — il candidato con `sexMismatch = true` viene escluso se ne esiste uno
     *   senza mismatch, ma non blocca l'intero slot.
     */
    STESSO_SESSO,
}

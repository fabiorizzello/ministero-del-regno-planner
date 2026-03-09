package org.example.project.feature.weeklyparts.domain

import org.example.project.feature.people.domain.Sesso

enum class SexRule {
    /** Solo proclamatori maschi (filtro hard). */
    UOMO,

    /**
     * Intende "stesso sesso degli altri assegnati alla parte".
     * Comportamento definitivo:
     * - Assegnazione manuale: non filtrante (`passaSesso = true`); `sexMismatch` segnala
     *   visivamente la discrepanza ma non impedisce la selezione.
     * - Auto-assign: filtro hard su `sexMismatch` (candidato valido solo se `sexMismatch = false`).
     */
    STESSO_SESSO,
}

fun SexRule.allowsCandidate(candidateSex: Sesso): Boolean = when (this) {
    SexRule.UOMO -> candidateSex == Sesso.M
    SexRule.STESSO_SESSO -> true
}

fun SexRule.isMismatch(candidateSex: Sesso, requiredSex: Sesso?): Boolean =
    this == SexRule.STESSO_SESSO && requiredSex != null && candidateSex != requiredSex

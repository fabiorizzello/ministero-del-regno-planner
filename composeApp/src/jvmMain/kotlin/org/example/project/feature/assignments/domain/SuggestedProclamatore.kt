package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.Proclamatore

data class SuggestedProclamatore(
    val proclamatore: Proclamatore,
    val lastGlobalWeeks: Int?,        // null = mai assegnato (qualsiasi ruolo)
    val lastForPartTypeWeeks: Int?,   // null = mai su questo tipo di parte (qualsiasi ruolo)
    val lastConductorWeeks: Int?,     // null = mai stato conduttore; usato per determinare l'ultimo ruolo
    val lastGlobalBeforeWeeks: Int? = null,
    val lastGlobalAfterWeeks: Int? = null,
    val lastForPartTypeBeforeWeeks: Int? = null,
    val lastForPartTypeAfterWeeks: Int? = null,
    val inCooldown: Boolean = false,
    val cooldownRemainingWeeks: Int = 0,
    val sexMismatch: Boolean = false, // sesso diverso da quello esistente nella parte (regola STESSO_SESSO)
)

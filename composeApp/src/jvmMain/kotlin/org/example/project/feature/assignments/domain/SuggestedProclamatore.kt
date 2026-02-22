package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.Proclamatore

data class SuggestedProclamatore(
    val proclamatore: Proclamatore,
    val lastGlobalWeeks: Int?,      // null = mai assegnato
    val lastForPartTypeWeeks: Int?, // null = mai su questo tipo di parte
    val inCooldown: Boolean = false,
    val cooldownRemainingWeeks: Int = 0,
)

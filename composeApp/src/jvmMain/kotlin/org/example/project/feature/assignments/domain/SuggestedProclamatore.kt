package org.example.project.feature.assignments.domain

import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.weeklyparts.domain.PartTypeId

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
    val totalAssignmentsInWindow: Int = 0,
    /**
     * Conteggio di conduzioni (slot 1) per ciascun tipo di parte nella finestra di equità
     * (RANKING_HISTORY_WEEKS). Usato per bilanciare la rotazione del conduttore tra i diversi
     * tipi di parte. Mappa mancante = nessuna conduzione registrata per quel tipo.
     */
    val leadCountsByPartType: Map<PartTypeId, Int> = emptyMap(),
    /**
     * Conteggio totale di assistenze (slot >= 2) nella finestra di equità (RANKING_HISTORY_WEEKS).
     * Usato per bilanciare il ruolo di assistente con i ruoli di conduzione.
     */
    val assistCountInWindow: Int = 0,
)

/** Regola conservativa di auto-assegnazione: niente mismatch di sesso e niente cooldown attivo. */
fun SuggestedProclamatore.canBeAutoAssigned(): Boolean = !sexMismatch && !inCooldown

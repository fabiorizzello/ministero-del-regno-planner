package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

class SuggerisciProclamatoriUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentRanking,
    private val eligibilityStore: EligibilityStore,
    private val assignmentSettingsStore: AssignmentSettingsStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        slot: Int,
        alreadyAssignedIds: Set<ProclamatoreId> = emptySet(),
    ): List<SuggestedProclamatore> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        val part = plan.parts.find { it.id == weeklyPartId } ?: return emptyList()
        val settings = assignmentSettingsStore.load()
        val cooldownWeeks = if (slot <= 1) settings.leadCooldownWeeks else settings.assistCooldownWeeks
        val roleWeight = if (slot <= 1) settings.leadWeight else settings.assistWeight

        val suggestions = assignmentStore.suggestedProclamatori(
            partTypeId = part.partType.id,
            slot = slot,
            referenceDate = weekStartDate,
        )
        val leadEligibilityByPersonId = suggestions
            .map { it.proclamatore.id }
            .associateWith { personId ->
                eligibilityStore.listLeadEligibility(personId)
                    .any { it.partTypeId == part.partType.id && it.canLead }
            }

        // Filtri hard: regola sesso, idoneita', gia' assegnato nella stessa settimana.
        val eligible = suggestions
            .map { suggestion ->
                val p = suggestion.proclamatore
                val passaSesso = when (part.partType.sexRule) {
                    SexRule.UOMO -> p.sesso == Sesso.M
                    SexRule.LIBERO -> true
                }
                val passaIdoneita = if (slot <= 1) {
                    leadEligibilityByPersonId[p.id] == true
                } else {
                    p.puoAssistere
                }
                val globalWeeks = suggestion.lastGlobalWeeks ?: Int.MAX_VALUE
                val isInCooldown = cooldownWeeks > 0 && globalWeeks < cooldownWeeks
                val remaining = if (isInCooldown) cooldownWeeks - globalWeeks else 0
                val annotated = suggestion.copy(
                    inCooldown = isInCooldown,
                    cooldownRemainingWeeks = remaining.coerceAtLeast(0),
                )
                Triple(annotated, passaSesso && passaIdoneita && p.id !in alreadyAssignedIds, roleWeight)
            }
            .filter { (_, allowed, _) -> allowed }
            .map { (suggestion, _, weight) -> suggestion to weightedScore(suggestion, weight) }
            .filter { (suggestion, _) -> !settings.strictCooldown || !suggestion.inCooldown }

        return eligible
            .sortedWith(
                compareByDescending<Pair<SuggestedProclamatore, Long>> { it.second }
                    .thenByDescending { it.first.lastGlobalWeeks ?: Int.MAX_VALUE }
                    .thenBy { it.first.proclamatore.cognome.lowercase() }
                    .thenBy { it.first.proclamatore.nome.lowercase() },
            )
            .map { (suggestion, _) -> suggestion }
    }

    private fun weightedScore(suggestion: SuggestedProclamatore, roleWeight: Int): Long {
        val safeGlobalWeeks = suggestion.lastGlobalWeeks ?: 999
        val safePartWeeks = suggestion.lastForPartTypeWeeks ?: 999
        val cooldownPenalty = if (suggestion.inCooldown) 10_000 else 0
        return safeGlobalWeeks.toLong() * roleWeight.toLong() + safePartWeeks.toLong() - cooldownPenalty
    }
}

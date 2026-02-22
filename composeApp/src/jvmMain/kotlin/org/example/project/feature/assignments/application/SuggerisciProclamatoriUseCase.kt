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
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        slot: Int,
        alreadyAssignedIds: Set<ProclamatoreId> = emptySet(),
    ): List<SuggestedProclamatore> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        val part = plan.parts.find { it.id == weeklyPartId } ?: return emptyList()

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

        // Filtri hard: regola sesso, gia' assegnato nella stessa settimana
        return suggestions.filter { s ->
            val p = s.proclamatore
            val passaSesso = when (part.partType.sexRule) {
                SexRule.UOMO -> p.sesso == Sesso.M
                SexRule.LIBERO -> true
            }
            val passaIdoneita = if (slot <= 1) {
                leadEligibilityByPersonId[p.id] == true
            } else {
                p.puoAssistere
            }
            passaSesso && passaIdoneita && p.id !in alreadyAssignedIds
        }
    }
}

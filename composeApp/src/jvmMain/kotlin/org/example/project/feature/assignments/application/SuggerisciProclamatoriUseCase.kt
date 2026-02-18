package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

class SuggerisciProclamatoriUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        slot: Int,
    ): List<SuggestedProclamatore> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        val part = plan.parts.find { it.id == weeklyPartId } ?: return emptyList()

        val suggestions = assignmentStore.suggestedProclamatori(
            partTypeId = part.partType.id,
            slot = slot,
            referenceDate = weekStartDate,
        )

        // Filtri hard: regola sesso, gia' assegnato alla stessa parte
        val existingForPart = assignmentStore.listByWeek(plan.id)
            .filter { it.weeklyPartId == weeklyPartId }
            .map { it.personId }
            .toSet()

        return suggestions.filter { s ->
            val p = s.proclamatore
            val passaSesso = when (part.partType.sexRule) {
                SexRule.UOMO -> p.sesso == Sesso.M
                SexRule.LIBERO -> true
            }
            passaSesso && p.id !in existingForPart
        }
    }
}

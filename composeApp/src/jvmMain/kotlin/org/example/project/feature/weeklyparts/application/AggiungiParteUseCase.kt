package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import java.time.LocalDate

class AggiungiParteUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        partTypeId: PartTypeId,
    ): Either<DomainError, WeekPlan> = either {
        val weekPlan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Settimana non trovata"))

        val nextOrder = (weekPlan.parts.maxOfOrNull { it.sortOrder } ?: -1) + 1
        weekPlanStore.addPart(weekPlan.id, partTypeId, nextOrder)

        weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio"))
    }
}

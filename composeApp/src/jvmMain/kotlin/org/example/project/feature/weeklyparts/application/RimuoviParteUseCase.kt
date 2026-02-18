package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

class RimuoviParteUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
    ): Either<DomainError, WeekPlan> = either {
        val weekPlan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Settimana non trovata"))

        val part = weekPlan.parts.find { it.id == weeklyPartId }
            ?: raise(DomainError.Validation("Parte non trovata"))

        if (part.partType.fixed) {
            raise(DomainError.Validation("La parte '${part.partType.label}' non puo' essere rimossa"))
        }

        weekPlanStore.removePart(weeklyPartId)

        // Ricompatta sort_order contigui 0..n-1
        val updatedPlan = weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio"))
        val reorderedIds = updatedPlan.parts.map { it.id }
        weekPlanStore.updateSortOrders(reorderedIds.mapIndexed { i, id -> id to i })

        weekPlanStore.findByDate(weekStartDate)
            ?: raise(DomainError.Validation("Errore nel salvataggio"))
    }
}

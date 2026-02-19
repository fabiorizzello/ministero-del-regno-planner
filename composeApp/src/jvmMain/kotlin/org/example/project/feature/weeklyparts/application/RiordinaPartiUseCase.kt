package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

class RiordinaPartiUseCase(
    private val weekPlanStore: WeekPlanStore,
) {
    suspend operator fun invoke(orderedPartIds: List<WeeklyPartId>): Either<DomainError, Unit> = either {
        try {
            val updates = orderedPartIds.mapIndexed { index, id -> id to index }
            weekPlanStore.updateSortOrders(updates)
        } catch (e: Exception) {
            raise(DomainError.Validation("Errore nel riordinamento: ${e.message}"))
        }
    }
}

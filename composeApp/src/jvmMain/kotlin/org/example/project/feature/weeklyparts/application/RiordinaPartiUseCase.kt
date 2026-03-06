package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

class RiordinaPartiUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        orderedPartIds: List<WeeklyPartId>,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, Unit> = either {
        val aggregate = weekPlanStore.loadAggregateByDate(weekStartDate)
            ?: raise(DomainError.NotFound("Settimana"))
        val reordered = aggregate.reorderParts(orderedPartIds, referenceDate).fold(
            ifLeft = { raise(it) },
            ifRight = { it },
        )
        try {
            transactionRunner.runInTransaction {
                weekPlanStore.saveAggregate(reordered)
            }
        } catch (e: Exception) {
            raise(DomainError.RiordinoPartiFallito(e.message))
        }
    }
}

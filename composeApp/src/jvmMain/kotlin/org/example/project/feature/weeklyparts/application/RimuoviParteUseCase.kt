package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

class RimuoviParteUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, Unit> = either {
        val aggregate = weekPlanStore.loadAggregateByDate(weekStartDate)
            ?: raise(DomainError.NotFound("Settimana"))

        val updated = aggregate.removePart(weeklyPartId, referenceDate).fold(
            ifLeft = { raise(it) },
            ifRight = { it },
        )
        transactionRunner.runInTransactionEither {
            Either.Right(weekPlanStore.saveAggregate(updated))
        }.bind()
    }
}

package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate

class ImpostaStatoSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekPlanId: WeekPlanId,
        status: WeekPlanStatus,
        referenceDate: LocalDate,
    ): Either<DomainError, Unit> = either {
        val aggregate = weekPlanStore.loadAggregateById(weekPlanId)
            ?: raise(DomainError.NotFound("Settimana"))
        transactionRunner.runInTransactionEither {
            Either.Right(weekPlanStore.saveAggregate(aggregate.setStatus(status)))
        }.bind()
    }
}

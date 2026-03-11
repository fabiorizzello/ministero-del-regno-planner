package org.example.project.feature.weeklyparts.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.canBeMutated
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class ImpostaStatoSettimanaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weekPlanId: WeekPlanId,
        status: WeekPlanStatus,
        referenceDate: LocalDate = LocalDate.now(),
    ): Either<DomainError, Unit> = either {
        val aggregate = weekPlanStore.loadAggregateById(weekPlanId)
            ?: raise(DomainError.NotFound("Settimana"))
        if (status == WeekPlanStatus.SKIPPED) {
            val currentMonday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (!aggregate.weekPlan.canBeMutated(currentMonday)) raise(DomainError.SettimanaImmutabile)
        }
        Either.catch {
            transactionRunner.runInTransaction {
                weekPlanStore.saveAggregate(aggregate.setStatus(status))
            }
        }.mapLeft { DomainError.Validation(it.message ?: "Errore salvataggio settimana") }.bind()
    }
}

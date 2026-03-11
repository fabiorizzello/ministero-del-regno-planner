package org.example.project.feature.output.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

class AnnullaConsegnaUseCase(
    private val store: SlipDeliveryStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weeklyPartId: WeeklyPartId,
        weekPlanId: WeekPlanId,
    ): Either<DomainError, Unit> = Either.catch {
        transactionRunner.runInTransaction {
            val active = store.findActiveDelivery(weeklyPartId, weekPlanId)
                ?: return@runInTransaction
            store.cancel(active.id, Instant.now())
        }
    }.mapLeft { DomainError.Validation("Errore annullamento consegna: ${it.message}") }
}

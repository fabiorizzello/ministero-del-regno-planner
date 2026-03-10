package org.example.project.feature.output.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant

class AnnullaConsegnaUseCase(
    private val store: SlipDeliveryStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weeklyPartId: WeeklyPartId,
        weekPlanId: String,
    ): Either<DomainError, Unit> = transactionRunner.runInTransaction {
        either {
            val active = store.findActiveDelivery(weeklyPartId, weekPlanId)
                ?: return@either
            store.cancel(active.id, Instant.now())
        }
    }
}

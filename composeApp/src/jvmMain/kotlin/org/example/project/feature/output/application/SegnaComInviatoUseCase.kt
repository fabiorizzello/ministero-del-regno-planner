package org.example.project.feature.output.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.output.domain.SlipDelivery
import org.example.project.feature.output.domain.SlipDeliveryId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.Instant
import java.util.UUID

class SegnaComInviatoUseCase(
    private val store: SlipDeliveryStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        weeklyPartId: WeeklyPartId,
        weekPlanId: WeekPlanId,
        studentName: String,
        assistantName: String?,
    ): Either<DomainError, Unit> = Either.catch {
        transactionRunner.runInTransaction {
            val existing = store.findActiveDelivery(weeklyPartId, weekPlanId)
            if (existing != null) return@runInTransaction

            store.insert(
                SlipDelivery(
                    id = SlipDeliveryId(UUID.randomUUID().toString()),
                    weeklyPartId = weeklyPartId,
                    weekPlanId = weekPlanId,
                    studentName = studentName,
                    assistantName = assistantName,
                    sentAt = Instant.now(),
                    cancelledAt = null,
                )
            )
        }
    }.mapLeft { DomainError.Validation("Errore registrazione consegna: ${it.message}") }
}

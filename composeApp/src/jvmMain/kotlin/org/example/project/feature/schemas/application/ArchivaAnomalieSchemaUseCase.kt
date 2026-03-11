package org.example.project.feature.schemas.application

import arrow.core.Either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner

class ArchivaAnomalieSchemaUseCase(
    private val store: SchemaUpdateAnomalyStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(): Either<DomainError, Unit> =
        Either.catch {
            transactionRunner.runInTransaction {
                store.dismissAllOpen()
            }
        }.mapLeft { DomainError.Validation("Errore archiviazione anomalie: ${it.message}") }
}

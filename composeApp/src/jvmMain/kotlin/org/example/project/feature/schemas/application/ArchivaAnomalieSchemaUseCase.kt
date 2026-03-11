package org.example.project.feature.schemas.application

import org.example.project.core.persistence.TransactionRunner

class ArchivaAnomalieSchemaUseCase(
    private val store: SchemaUpdateAnomalyStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke() {
        transactionRunner.runInTransaction {
            store.dismissAllOpen()
        }
    }
}

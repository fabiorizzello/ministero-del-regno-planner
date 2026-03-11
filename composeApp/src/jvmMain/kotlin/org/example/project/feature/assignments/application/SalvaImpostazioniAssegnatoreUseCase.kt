package org.example.project.feature.assignments.application

import org.example.project.core.persistence.TransactionRunner

class SalvaImpostazioniAssegnatoreUseCase(
    private val store: AssignmentSettingsStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(settings: AssignmentSettings) {
        transactionRunner.runInTransaction {
            store.save(settings.normalized())
        }
    }
}

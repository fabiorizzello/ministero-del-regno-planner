package org.example.project.feature.assignments.application

import org.example.project.core.persistence.TransactionScope

interface AssignmentSettingsStore {
    suspend fun load(): AssignmentSettings
    context(tx: TransactionScope)
    suspend fun save(settings: AssignmentSettings)
}

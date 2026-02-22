package org.example.project.feature.assignments.application

interface AssignmentSettingsStore {
    suspend fun load(): AssignmentSettings
    suspend fun save(settings: AssignmentSettings)
}

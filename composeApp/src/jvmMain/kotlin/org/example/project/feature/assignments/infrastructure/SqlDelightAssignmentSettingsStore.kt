package org.example.project.feature.assignments.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.application.AssignmentSettings
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import org.example.project.core.persistence.TransactionScope

class SqlDelightAssignmentSettingsStore(
    private val database: MinisteroDatabase,
) : AssignmentSettingsStore {
    override suspend fun load(): AssignmentSettings {
        val row = database.ministeroDatabaseQueries.findAssignmentSettings().executeAsOneOrNull()
        return if (row == null) {
            AssignmentSettings()
        } else {
            AssignmentSettings(
                strictCooldown = row.strict_cooldown == 1L,
                leadCooldownWeeks = row.lead_cooldown_weeks.toInt(),
                assistCooldownWeeks = row.assist_cooldown_weeks.toInt(),
            ).normalized()
        }
    }

    context(tx: TransactionScope)
    override suspend fun save(settings: AssignmentSettings) {
        val normalized = settings.normalized()
        database.ministeroDatabaseQueries.upsertAssignmentSettings(
            id = SINGLETON_ID,
            strict_cooldown = if (normalized.strictCooldown) 1L else 0L,
            lead_cooldown_weeks = normalized.leadCooldownWeeks.toLong(),
            assist_cooldown_weeks = normalized.assistCooldownWeeks.toLong(),
        )
    }

    private companion object {
        const val SINGLETON_ID = "singleton"
    }
}

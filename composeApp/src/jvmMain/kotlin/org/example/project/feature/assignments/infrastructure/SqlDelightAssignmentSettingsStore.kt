package org.example.project.feature.assignments.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.application.AssignmentSettings
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import java.util.UUID

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
                leadWeight = row.lead_weight.toInt(),
                assistWeight = row.assist_weight.toInt(),
                leadCooldownWeeks = row.lead_cooldown_weeks.toInt(),
                assistCooldownWeeks = row.assist_cooldown_weeks.toInt(),
            ).normalized()
        }
    }

    override suspend fun save(settings: AssignmentSettings) {
        val normalized = settings.normalized()
        val existingId = database.ministeroDatabaseQueries
            .findAssignmentSettings()
            .executeAsOneOrNull()
            ?.id

        database.ministeroDatabaseQueries.upsertAssignmentSettings(
            id = existingId ?: UUID.randomUUID().toString(),
            strict_cooldown = if (normalized.strictCooldown) 1L else 0L,
            lead_weight = normalized.leadWeight.toLong(),
            assist_weight = normalized.assistWeight.toLong(),
            lead_cooldown_weeks = normalized.leadCooldownWeeks.toLong(),
            assist_cooldown_weeks = normalized.assistCooldownWeeks.toLong(),
        )
    }
}

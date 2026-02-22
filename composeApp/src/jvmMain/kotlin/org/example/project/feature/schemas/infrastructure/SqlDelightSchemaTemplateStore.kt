package org.example.project.feature.schemas.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate
import java.util.UUID

class SqlDelightSchemaTemplateStore(
    private val database: MinisteroDatabase,
) : SchemaTemplateStore {

    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) {
        database.ministeroDatabaseQueries.transaction {
            database.ministeroDatabaseQueries.deleteAllSchemaWeekParts()
            database.ministeroDatabaseQueries.deleteAllSchemaWeeks()

            templates.forEach { template ->
                val weekId = UUID.randomUUID().toString()
                database.ministeroDatabaseQueries.insertSchemaWeek(
                    id = weekId,
                    week_start_date = template.weekStartDate.toString(),
                )
                template.partTypeIds.forEachIndexed { index, partTypeId ->
                    database.ministeroDatabaseQueries.insertSchemaWeekPart(
                        id = UUID.randomUUID().toString(),
                        schema_week_id = weekId,
                        part_type_id = partTypeId.value,
                        sort_order = index.toLong(),
                    )
                }
            }
        }
    }

    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? {
        val week = database.ministeroDatabaseQueries
            .findSchemaWeekByDate(weekStartDate.toString())
            .executeAsOneOrNull() ?: return null

        val partIds = database.ministeroDatabaseQueries
            .schemaPartsByWeek(week.id) { _, _, part_type_id, _, _, _, _, _, _, _ ->
                PartTypeId(part_type_id)
            }
            .executeAsList()

        return StoredSchemaWeekTemplate(
            weekStartDate = LocalDate.parse(week.week_start_date),
            partTypeIds = partIds,
        )
    }

    override suspend fun isEmpty(): Boolean {
        return database.ministeroDatabaseQueries.countSchemaWeeks().executeAsOne() == 0L
    }
}

package org.example.project.feature.schemas.application

import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate

data class StoredSchemaWeekTemplate(
    val weekStartDate: LocalDate,
    val partTypeIds: List<PartTypeId>,
)

interface SchemaTemplateStore {
    suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>)
    suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate?
    suspend fun isEmpty(): Boolean
}

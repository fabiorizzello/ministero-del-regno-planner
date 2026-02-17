package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartType

data class RemoteWeekSchema(
    val weekStartDate: String,
    val partTypeCodes: List<String>,
)

interface RemoteDataSource {
    suspend fun fetchPartTypes(): List<PartType>
    suspend fun fetchWeeklySchemas(): List<RemoteWeekSchema>
}

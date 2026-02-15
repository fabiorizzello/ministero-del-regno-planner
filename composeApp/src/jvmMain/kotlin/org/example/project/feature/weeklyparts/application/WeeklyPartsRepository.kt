package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId

interface WeeklyPartsRepository {
    suspend fun listByWeek(weekStartDate: String): List<WeeklyPart>
    suspend fun save(part: WeeklyPart)
    suspend fun delete(partId: WeeklyPartId)
}

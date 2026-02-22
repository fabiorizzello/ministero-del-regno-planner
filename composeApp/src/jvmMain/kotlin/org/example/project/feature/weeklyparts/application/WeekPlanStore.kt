package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

interface WeekPlanStore {
    suspend fun findByDate(weekStartDate: LocalDate): WeekPlan?
    suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary>
    suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int>
    suspend fun save(weekPlan: WeekPlan)
    suspend fun delete(weekPlanId: WeekPlanId)
    suspend fun addPart(weekPlanId: WeekPlanId, partTypeId: PartTypeId, sortOrder: Int): WeeklyPartId
    suspend fun removePart(weeklyPartId: WeeklyPartId)
    suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>)
    suspend fun replaceAllParts(weekPlanId: WeekPlanId, partTypeIds: List<PartTypeId>)

    suspend fun saveWithProgram(weekPlan: WeekPlan, programId: String, status: WeekPlanStatus = WeekPlanStatus.ACTIVE) {
        save(weekPlan)
    }

    suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: String): WeekPlan? = null

    suspend fun listByProgram(programId: String): List<WeekPlan> = emptyList()

    suspend fun updateWeekStatus(weekPlanId: WeekPlanId, status: WeekPlanStatus) {}
}

package org.example.project.feature.weeklyparts.application

import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate

/** Read-only access to week plans. Use cases that never mutate inject this instead of [WeekPlanStore]. */
interface WeekPlanQueries {
    suspend fun findByDate(weekStartDate: LocalDate): WeekPlan?
    suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary>
    suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int>
    suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan?
    suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan>
}

interface WeekPlanStore : WeekPlanQueries {
    suspend fun save(weekPlan: WeekPlan)
    suspend fun delete(weekPlanId: WeekPlanId)
    suspend fun addPart(weekPlanId: WeekPlanId, partTypeId: PartTypeId, sortOrder: Int, partTypeRevisionId: String? = null): WeeklyPartId
    suspend fun removePart(weeklyPartId: WeeklyPartId)
    suspend fun updateSortOrders(parts: List<Pair<WeeklyPartId, Int>>)
    suspend fun replaceAllParts(weekPlanId: WeekPlanId, partTypeIds: List<PartTypeId>, revisionIds: List<String?> = emptyList())

    // Defaults preserved so existing implementors aren't broken
    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? = null
    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> = emptyList()

    suspend fun saveWithProgram(weekPlan: WeekPlan, programId: ProgramMonthId, status: WeekPlanStatus = WeekPlanStatus.ACTIVE) {
        save(weekPlan)
    }

    suspend fun deleteByProgram(programId: ProgramMonthId) {
        listByProgram(programId).forEach { week -> delete(week.id) }
    }

    suspend fun updateWeekStatus(weekPlanId: WeekPlanId, status: WeekPlanStatus) {}
}

package org.example.project.feature.assignments.application

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate

interface AssignmentRepository {
    suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson>
    suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>>
    context(tx: TransactionScope) suspend fun save(assignment: Assignment)
    context(tx: TransactionScope) suspend fun remove(assignmentId: AssignmentId)
    context(tx: TransactionScope) suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId)
    suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int
    suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int>
    context(tx: TransactionScope) suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int
    suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int
    suspend fun findWeekPlanIdByAssignmentId(assignmentId: AssignmentId): WeekPlanId?
}

/**
 * Pre-fetched ranking data for all (partTypeId, referenceDate) combinations needed by a single
 * auto-assign run. Avoids repeating 7 full-table-scan queries per slot.
 */
data class SuggestionRankingCache(
    val globalLast: Map<String, String?>,
    val conductorLast: Map<String, String?>,
    val allActive: List<Proclamatore>,
    val globalBeforeByDate: Map<LocalDate, Map<String, String?>>,
    val globalAfterByDate: Map<LocalDate, Map<String, String?>>,
    val partTypeLastByType: Map<PartTypeId, Map<String, String?>>,
    val partTypeBeforeByTypeAndDate: Map<PartTypeId, Map<LocalDate, Map<String, String?>>>,
    val partTypeAfterByTypeAndDate: Map<PartTypeId, Map<LocalDate, Map<String, String?>>>,
    val assignmentCountInWindow: Map<String, Int> = emptyMap(),
)

interface AssignmentRanking {
    suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: LocalDate,
        rankingCache: SuggestionRankingCache? = null,
    ): List<SuggestedProclamatore>

    suspend fun preloadSuggestionRanking(
        referenceDates: Set<LocalDate>,
        partTypeIds: Set<PartTypeId>,
    ): SuggestionRankingCache
}

interface PersonAssignmentLifecycle {
    suspend fun countAssignmentsForPerson(personId: ProclamatoreId): Int
    context(tx: TransactionScope) suspend fun removeAllForPerson(personId: ProclamatoreId)
}

interface PersonAssignmentHistoryQuery {
    suspend fun lastAssignmentDatesByPartType(
        personId: ProclamatoreId,
        partTypeIds: Set<PartTypeId>,
    ): Map<PartTypeId, LocalDate>
}

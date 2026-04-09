package org.example.project.feature.assignments.infrastructure

import org.example.project.core.persistence.TransactionScope
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.PersonAssignmentHistoryQuery
import org.example.project.feature.assignments.application.PersonAssignmentLifecycle
import org.example.project.feature.assignments.application.SuggestionRankingCache
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.infrastructure.mapProclamatoreAssignableRow
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.assignments.application.COUNT_WINDOW_WEEKS
import org.example.project.feature.assignments.application.RANKING_HISTORY_WEEKS
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class SqlDelightAssignmentStore(
    private val database: MinisteroDatabase,
) : AssignmentRepository, AssignmentRanking, PersonAssignmentLifecycle, PersonAssignmentHistoryQuery {

    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> {
        return database.ministeroDatabaseQueries
            .assignmentsForWeek(weekPlanId.value, ::mapAssignmentWithPersonRow)
            .executeAsList()
    }

    override suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>> {
        if (weekPlanIds.isEmpty()) return emptyMap()
        return database.ministeroDatabaseQueries
            .assignmentsForWeekPlanIds(weekPlanIds.map { it.value }) { id, weekly_part_id, person_id, slot, first_name, last_name, sex, week_plan_id ->
                week_plan_id to mapAssignmentWithPersonRow(id, weekly_part_id, person_id, slot, first_name, last_name, sex)
            }
            .executeAsList()
            .groupBy({ WeekPlanId(it.first) }, { it.second })
    }

    context(tx: TransactionScope)
    override suspend fun save(assignment: Assignment) {
        database.ministeroDatabaseQueries.upsertAssignment(
            id = assignment.id.value,
            weekly_part_id = assignment.weeklyPartId.value,
            person_id = assignment.personId.value,
            slot = assignment.slot.toLong(),
        )
    }

    context(tx: TransactionScope)
    override suspend fun remove(assignmentId: AssignmentId) {
        database.ministeroDatabaseQueries.deleteAssignment(assignmentId.value)
    }

    context(tx: TransactionScope)
    override suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId) {
        database.ministeroDatabaseQueries.deleteAssignmentsForWeek(weekPlanId.value)
    }

    override suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: LocalDate,
        rankingCache: SuggestionRankingCache?,
    ): List<SuggestedProclamatore> {
        val cache = rankingCache ?: fetchRankingFromDb(setOf(referenceDate), setOf(partTypeId))
        return buildSuggestions(cache, partTypeId, referenceDate)
    }

    override suspend fun preloadSuggestionRanking(
        referenceDates: Set<LocalDate>,
        partTypeIds: Set<PartTypeId>,
    ): SuggestionRankingCache = fetchRankingFromDb(referenceDates, partTypeIds)

    private fun fetchRankingFromDb(
        referenceDates: Set<LocalDate>,
        partTypeIds: Set<PartTypeId>,
    ): SuggestionRankingCache {
        // Limit the ranking query to a generous time window: the earliest reference date
        // minus RANKING_HISTORY_WEEKS. This covers COUNT_WINDOW_WEEKS for counting plus
        // ample margin for ranking differentiation, while avoiding unbounded full-table scans.
        val sinceDate = referenceDates.min().minusWeeks(RANKING_HISTORY_WEEKS)
        val rawRows = database.ministeroDatabaseQueries
            .allAssignmentRankingData(sinceDate.toString())
            .executeAsList()

        val allActive = database.ministeroDatabaseQueries
            .allAssignableProclaimers(::mapProclamatoreAssignableRow)
            .executeAsList()

        // Index raw data by person_id for efficient computation
        data class RankRow(val weekDate: String, val partTypeId: String, val slot: Long)

        val byPerson: Map<String, List<RankRow>> = rawRows
            .groupBy(
                keySelector = { it.person_id },
                valueTransform = { RankRow(it.week_start_date, it.part_type_id, it.slot) },
            )

        // assignmentCountInWindow: count assignments per person in [referenceDate - COUNT_WINDOW_WEEKS, referenceDate)
        val windowReferenceDate = referenceDates.min()
        val windowStart = windowReferenceDate.minusWeeks(COUNT_WINDOW_WEEKS.toLong()).toString()
        val windowEnd = windowReferenceDate.toString()
        val assignmentCountInWindow: Map<String, Int> = byPerson.mapValues { (_, rows) ->
            rows.count { it.weekDate >= windowStart && it.weekDate < windowEnd }
        }

        // globalLast: MAX(week_start_date) per person
        val globalLast: Map<String, String?> = byPerson.mapValues { (_, rows) ->
            rows.maxOf { it.weekDate }
        }

        // conductorLast: MAX(week_start_date) per person WHERE slot == 1
        val conductorLast: Map<String, String?> = byPerson.mapValues { (_, rows) ->
            rows.filter { it.slot == 1L }.maxOfOrNull { it.weekDate }
        }

        // globalBeforeByDate: for each referenceDate, MAX(week_start_date) WHERE weekDate <= dateStr per person
        val globalBeforeByDate: Map<LocalDate, Map<String, String?>> = referenceDates.associateWith { date ->
            val dateStr = date.toString()
            byPerson.mapValues { (_, rows) ->
                rows.filter { it.weekDate <= dateStr }.maxOfOrNull { it.weekDate }
            }.filterValues { it != null }
        }

        // globalAfterByDate: for each referenceDate, MIN(week_start_date) WHERE weekDate > dateStr per person
        val globalAfterByDate: Map<LocalDate, Map<String, String?>> = referenceDates.associateWith { date ->
            val dateStr = date.toString()
            byPerson.mapValues { (_, rows) ->
                rows.filter { it.weekDate > dateStr }.minOfOrNull { it.weekDate }
            }.filterValues { it != null }
        }

        // partTypeLastByType: for each partTypeId, MAX(week_start_date) WHERE part_type_id matches per person
        val partTypeLastByType: Map<PartTypeId, Map<String, String?>> = partTypeIds.associateWith { ptId ->
            val ptValue = ptId.value
            byPerson.mapValues { (_, rows) ->
                rows.filter { it.partTypeId == ptValue }.maxOfOrNull { it.weekDate }
            }.filterValues { it != null }
        }

        // partTypeBeforeByTypeAndDate: for each (partTypeId, date), MAX WHERE part_type_id matches AND weekDate <= dateStr
        val partTypeBeforeByTypeAndDate: Map<PartTypeId, Map<LocalDate, Map<String, String?>>> =
            partTypeIds.associateWith { ptId ->
                val ptValue = ptId.value
                referenceDates.associateWith { date ->
                    val dateStr = date.toString()
                    byPerson.mapValues { (_, rows) ->
                        rows.filter { it.partTypeId == ptValue && it.weekDate <= dateStr }
                            .maxOfOrNull { it.weekDate }
                    }.filterValues { it != null }
                }
            }

        // partTypeAfterByTypeAndDate: for each (partTypeId, date), MIN WHERE part_type_id matches AND weekDate > dateStr
        val partTypeAfterByTypeAndDate: Map<PartTypeId, Map<LocalDate, Map<String, String?>>> =
            partTypeIds.associateWith { ptId ->
                val ptValue = ptId.value
                referenceDates.associateWith { date ->
                    val dateStr = date.toString()
                    byPerson.mapValues { (_, rows) ->
                        rows.filter { it.partTypeId == ptValue && it.weekDate > dateStr }
                            .minOfOrNull { it.weekDate }
                    }.filterValues { it != null }
                }
            }

        return SuggestionRankingCache(
            globalLast = globalLast,
            conductorLast = conductorLast,
            allActive = allActive,
            globalBeforeByDate = globalBeforeByDate,
            globalAfterByDate = globalAfterByDate,
            partTypeLastByType = partTypeLastByType,
            partTypeBeforeByTypeAndDate = partTypeBeforeByTypeAndDate,
            partTypeAfterByTypeAndDate = partTypeAfterByTypeAndDate,
            assignmentCountInWindow = assignmentCountInWindow,
        )
    }

    private fun buildSuggestions(
        cache: SuggestionRankingCache,
        partTypeId: PartTypeId,
        referenceDate: LocalDate,
    ): List<SuggestedProclamatore> {
        val globalRanking = cache.globalLast
        val conductorRanking = cache.conductorLast
        val globalBeforeRanking = cache.globalBeforeByDate[referenceDate] ?: emptyMap()
        val globalAfterRanking = cache.globalAfterByDate[referenceDate] ?: emptyMap()
        val partTypeRanking = cache.partTypeLastByType[partTypeId] ?: emptyMap()
        val partTypeBeforeRanking = cache.partTypeBeforeByTypeAndDate[partTypeId]?.get(referenceDate) ?: emptyMap()
        val partTypeAfterRanking = cache.partTypeAfterByTypeAndDate[partTypeId]?.get(referenceDate) ?: emptyMap()
        val countInWindow = cache.assignmentCountInWindow

        return cache.allActive.map { p ->
            val lastGlobalDate = globalRanking[p.id.value]
            val lastPartDate = partTypeRanking[p.id.value]
            val lastConductorDate = conductorRanking[p.id.value]
            val lastGlobalBeforeDate = globalBeforeRanking[p.id.value]
            val nextGlobalAfterDate = globalAfterRanking[p.id.value]
            val lastPartBeforeDate = partTypeBeforeRanking[p.id.value]
            val nextPartAfterDate = partTypeAfterRanking[p.id.value]
            val signedGlobalWeeks = lastGlobalDate?.let {
                ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt()
            }
            val signedPartWeeks = lastPartDate?.let {
                ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt()
            }
            val signedConductorWeeks = lastConductorDate?.let {
                ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt()
            }
            val globalBeforeWeeks = lastGlobalBeforeDate?.let {
                ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt()
            }
            val globalAfterWeeks = nextGlobalAfterDate?.let {
                ChronoUnit.WEEKS.between(referenceDate, LocalDate.parse(it)).toInt()
            }
            val partBeforeWeeks = lastPartBeforeDate?.let {
                ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt()
            }
            val partAfterWeeks = nextPartAfterDate?.let {
                ChronoUnit.WEEKS.between(referenceDate, LocalDate.parse(it)).toInt()
            }

            SuggestedProclamatore(
                proclamatore = p,
                lastGlobalWeeks = signedGlobalWeeks?.let(::abs),
                lastForPartTypeWeeks = signedPartWeeks?.let(::abs),
                lastConductorWeeks = signedConductorWeeks?.let(::abs),
                lastGlobalBeforeWeeks = globalBeforeWeeks,
                lastGlobalAfterWeeks = globalAfterWeeks,
                lastForPartTypeBeforeWeeks = partBeforeWeeks,
                lastForPartTypeAfterWeeks = partAfterWeeks,
                totalAssignmentsInWindow = countInWindow[p.id.value] ?: 0,
            )
        }
    }

    override suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int {
        return database.ministeroDatabaseQueries
            .countAssignmentsForWeek(weekPlanId.value)
            .executeAsOne()
            .toInt()
    }

    override suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> {
        return database.ministeroDatabaseQueries
            .assignmentCountsByWeekInRange(startDate.toString(), endDate.toString())
            .executeAsList()
            .associate { row ->
                WeekPlanId(row.week_plan_id) to row.assignment_count.toInt()
            }
    }

    override suspend fun countAssignmentsForPerson(personId: ProclamatoreId): Int {
        return database.ministeroDatabaseQueries
            .countAssignmentsForPerson(personId.value)
            .executeAsOne()
            .toInt()
    }

    override suspend fun lastAssignmentDatesByPartType(
        personId: ProclamatoreId,
        partTypeIds: Set<PartTypeId>,
    ): Map<PartTypeId, LocalDate> {
        if (partTypeIds.isEmpty()) return emptyMap()
        return database.ministeroDatabaseQueries
            .lastAssignmentDateByPartTypesForPerson(personId.value, partTypeIds.map { it.value })
            .executeAsList()
            .associate { row ->
                PartTypeId(row.part_type_id) to LocalDate.parse(row.last_week_start_date)
            }
    }

    context(tx: TransactionScope)
    override suspend fun removeAllForPerson(personId: ProclamatoreId) {
        database.ministeroDatabaseQueries.deleteAssignmentsForPerson(personId.value)
    }

    context(tx: TransactionScope)
    override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int {
        val count = database.ministeroDatabaseQueries
            .countAssignmentsByProgramFromDate(programId.value, fromDate.toString())
            .executeAsOne().toInt()
        database.ministeroDatabaseQueries
            .deleteAssignmentsByProgramFromDate(programId.value, fromDate.toString())
        return count
    }

    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int {
        return database.ministeroDatabaseQueries
            .countAssignmentsByProgramFromDate(programId.value, fromDate.toString())
            .executeAsOne().toInt()
    }

}

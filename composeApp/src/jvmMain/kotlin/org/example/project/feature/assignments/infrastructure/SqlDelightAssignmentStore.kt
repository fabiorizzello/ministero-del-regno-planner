package org.example.project.feature.assignments.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.PersonAssignmentLifecycle
import org.example.project.feature.assignments.application.SuggestionRankingCache
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.infrastructure.mapProclamatoreAssignableRow
import org.example.project.feature.people.infrastructure.mapProclamatoreRow
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class SqlDelightAssignmentStore(
    private val database: MinisteroDatabase,
) : AssignmentRepository, AssignmentRanking, PersonAssignmentLifecycle {

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

    override suspend fun save(assignment: Assignment) {
        database.ministeroDatabaseQueries.upsertAssignment(
            id = assignment.id.value,
            weekly_part_id = assignment.weeklyPartId.value,
            person_id = assignment.personId.value,
            slot = assignment.slot.toLong(),
        )
    }

    override suspend fun remove(assignmentId: AssignmentId) {
        database.ministeroDatabaseQueries.deleteAssignment(assignmentId.value)
    }

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
    ): SuggestionRankingCache = database.ministeroDatabaseQueries.transactionWithResult {
        val globalLast = database.ministeroDatabaseQueries
            .lastGlobalAssignmentPerPerson()
            .executeAsList()
            .associate { it.person_id to it.last_week_date }

        val conductorLast = database.ministeroDatabaseQueries
            .lastSlot1GlobalAssignmentPerPerson()
            .executeAsList()
            .associate { it.person_id to it.last_week_date }

        val allActive = database.ministeroDatabaseQueries
            .allAssignableProclaimers(::mapProclamatoreAssignableRow)
            .executeAsList()

        val globalBeforeByDate = referenceDates.associateWith { date ->
            database.ministeroDatabaseQueries
                .lastGlobalAssignmentBeforePerPerson(date.toString())
                .executeAsList()
                .associate { it.person_id to it.week_date }
        }

        val globalAfterByDate = referenceDates.associateWith { date ->
            database.ministeroDatabaseQueries
                .firstGlobalAssignmentAfterPerPerson(date.toString())
                .executeAsList()
                .associate { it.person_id to it.week_date }
        }

        val partTypeLastByType = partTypeIds.associateWith { ptId ->
            database.ministeroDatabaseQueries
                .lastPartTypeAssignmentPerPerson(ptId.value)
                .executeAsList()
                .associate { it.person_id to it.last_week_date }
        }

        val partTypeBeforeByTypeAndDate = partTypeIds.associateWith { ptId ->
            referenceDates.associateWith { date ->
                database.ministeroDatabaseQueries
                    .lastPartTypeAssignmentBeforePerPerson(ptId.value, date.toString())
                    .executeAsList()
                    .associate { it.person_id to it.week_date }
            }
        }

        val partTypeAfterByTypeAndDate = partTypeIds.associateWith { ptId ->
            referenceDates.associateWith { date ->
                database.ministeroDatabaseQueries
                    .firstPartTypeAssignmentAfterPerPerson(ptId.value, date.toString())
                    .executeAsList()
                    .associate { it.person_id to it.week_date }
            }
        }

        SuggestionRankingCache(
            globalLast = globalLast,
            conductorLast = conductorLast,
            allActive = allActive,
            globalBeforeByDate = globalBeforeByDate,
            globalAfterByDate = globalAfterByDate,
            partTypeLastByType = partTypeLastByType,
            partTypeBeforeByTypeAndDate = partTypeBeforeByTypeAndDate,
            partTypeAfterByTypeAndDate = partTypeAfterByTypeAndDate,
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

    override suspend fun removeAllForPerson(personId: ProclamatoreId) {
        database.ministeroDatabaseQueries.deleteAssignmentsForPerson(personId.value)
    }

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

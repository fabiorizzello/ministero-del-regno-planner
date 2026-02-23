package org.example.project.feature.assignments.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.PersonAssignmentLifecycle
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.infrastructure.mapProclamatoreAssignableRow
import org.example.project.feature.people.infrastructure.mapProclamatoreRow
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SqlDelightAssignmentStore(
    private val database: MinisteroDatabase,
) : AssignmentRepository, AssignmentRanking, PersonAssignmentLifecycle {

    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> {
        return database.ministeroDatabaseQueries
            .assignmentsForWeek(weekPlanId.value, ::mapAssignmentWithPersonRow)
            .executeAsList()
    }

    override suspend fun save(assignment: Assignment) {
        try {
            database.ministeroDatabaseQueries.upsertAssignment(
                id = assignment.id.value,
                weekly_part_id = assignment.weeklyPartId.value,
                person_id = assignment.personId.value,
                slot = assignment.slot.toLong(),
            )
        } catch (e: Exception) {
            throw IllegalStateException("Errore nel salvataggio dell'assegnazione: ${e.message}", e)
        }
    }

    override suspend fun remove(assignmentId: AssignmentId) {
        database.ministeroDatabaseQueries.deleteAssignment(assignmentId.value)
    }

    override suspend fun isPersonAssignedInWeek(
        weekPlanId: WeekPlanId,
        personId: ProclamatoreId,
    ): Boolean {
        val count = database.ministeroDatabaseQueries
            .personAlreadyAssignedInWeek(weekPlanId.value, personId.value)
            .executeAsOne()
        return count > 0L
    }

    override suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: LocalDate,
    ): List<SuggestedProclamatore> {
        return database.ministeroDatabaseQueries.transactionWithResult {
            // Choose ranking queries based on slot:
            // Slot 1: only slot-1 history
            // Slot 2+: all slots (1+2) history
            val globalRanking: Map<String, String?> = if (slot == 1) {
                database.ministeroDatabaseQueries
                    .lastSlot1GlobalAssignmentPerPerson()
                    .executeAsList()
                    .associate { it.person_id to it.last_week_date }
            } else {
                database.ministeroDatabaseQueries
                    .lastGlobalAssignmentPerPerson()
                    .executeAsList()
                    .associate { it.person_id to it.last_week_date }
            }

            val partTypeRanking: Map<String, String?> = if (slot == 1) {
                database.ministeroDatabaseQueries
                    .lastSlot1PartTypeAssignmentPerPerson(partTypeId.value)
                    .executeAsList()
                    .associate { it.person_id to it.last_week_date }
            } else {
                database.ministeroDatabaseQueries
                    .lastPartTypeAssignmentPerPerson(partTypeId.value)
                    .executeAsList()
                    .associate { it.person_id to it.last_week_date }
            }

            val allActive = database.ministeroDatabaseQueries
                .allAssignableProclaimers(::mapProclamatoreAssignableRow)
                .executeAsList()

            allActive.map { p ->
                val lastGlobalDate = globalRanking[p.id.value]
                val lastPartDate = partTypeRanking[p.id.value]

                SuggestedProclamatore(
                    proclamatore = p,
                    lastGlobalWeeks = lastGlobalDate?.let {
                        ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
                    },
                    lastForPartTypeWeeks = lastPartDate?.let {
                        ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
                    },
                    lastGlobalDays = lastGlobalDate?.let {
                        ChronoUnit.DAYS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
                    },
                    lastForPartTypeDays = lastPartDate?.let {
                        ChronoUnit.DAYS.between(LocalDate.parse(it), referenceDate).toInt().coerceAtLeast(0)
                    },
                )
            }
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

    override suspend fun deleteByProgramFromDate(programId: String, fromDate: LocalDate): Int {
        val count = database.ministeroDatabaseQueries
            .countAssignmentsByProgramFromDate(programId, fromDate.toString())
            .executeAsOne().toInt()
        database.ministeroDatabaseQueries
            .deleteAssignmentsByProgramFromDate(programId, fromDate.toString())
        return count
    }

    override suspend fun countByProgramFromDate(programId: String, fromDate: LocalDate): Int {
        return database.ministeroDatabaseQueries
            .countAssignmentsByProgramFromDate(programId, fromDate.toString())
            .executeAsOne().toInt()
    }
}

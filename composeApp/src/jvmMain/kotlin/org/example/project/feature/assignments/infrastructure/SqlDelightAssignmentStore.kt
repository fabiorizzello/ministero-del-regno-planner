package org.example.project.feature.assignments.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.assignments.application.AssignmentStore
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SqlDelightAssignmentStore(
    private val database: MinisteroDatabase,
) : AssignmentStore {

    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> {
        return database.ministeroDatabaseQueries
            .assignmentsForWeek(weekPlanId.value, ::mapAssignmentWithPersonRow)
            .executeAsList()
    }

    override suspend fun save(assignment: Assignment) {
        database.ministeroDatabaseQueries.upsertAssignment(
            id = assignment.id.value,
            weekly_part_id = assignment.weeklyPartId.value,
            person_id = assignment.personId.value,
            slot = assignment.slot.toLong(),
        )
    }

    override suspend fun remove(assignmentId: String) {
        database.ministeroDatabaseQueries.deleteAssignment(assignmentId)
    }

    override suspend fun isPersonAssignedToPart(
        weeklyPartId: WeeklyPartId,
        personId: ProclamatoreId,
    ): Boolean {
        val count = database.ministeroDatabaseQueries
            .personAlreadyAssignedToPart(weeklyPartId.value, personId.value)
            .executeAsOne()
        return count > 0L
    }

    override suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: LocalDate,
    ): List<SuggestedProclamatore> {
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

        // Get all active proclamatori using searchProclaimers with includeAll=1 and empty terms
        val allActive = database.ministeroDatabaseQueries
            .searchProclaimers(1L, "", "", "") { id, firstName, lastName, sex, active ->
                Proclamatore(
                    id = ProclamatoreId(id),
                    nome = firstName,
                    cognome = lastName,
                    sesso = Sesso.valueOf(sex),
                    attivo = active == 1L,
                )
            }
            .executeAsList()
            .filter { it.attivo }

        return allActive.map { p ->
            val lastGlobalDate = globalRanking[p.id.value]
            val lastPartDate = partTypeRanking[p.id.value]

            SuggestedProclamatore(
                proclamatore = p,
                lastGlobalWeeks = lastGlobalDate?.let {
                    ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt()
                },
                lastForPartTypeWeeks = lastPartDate?.let {
                    ChronoUnit.WEEKS.between(LocalDate.parse(it), referenceDate).toInt()
                },
            )
        }
    }
}

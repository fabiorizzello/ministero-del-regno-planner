package org.example.project.feature.people.infrastructure

import org.example.project.db.MinisteroDatabase
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate
import java.util.UUID

class SqlDelightEligibilityStore(
    private val database: MinisteroDatabase,
) : EligibilityStore {
    override suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean) {
        database.ministeroDatabaseQueries.updatePersonSuspended(
            suspended = if (suspended) 1L else 0L,
            id = personId.value,
        )
    }

    override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {
        database.ministeroDatabaseQueries.updatePersonCanAssist(
            can_assist = if (canAssist) 1L else 0L,
            id = personId.value,
        )
    }

    override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {
        database.ministeroDatabaseQueries.upsertPersonPartTypeEligibility(
            id = UUID.randomUUID().toString(),
            person_id = personId.value,
            part_type_id = partTypeId.value,
            can_lead = if (canLead) 1L else 0L,
        )
    }

    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> {
        return database.ministeroDatabaseQueries
            .eligibilityByPerson(personId.value)
            .executeAsList()
            .map { row ->
                LeadEligibility(
                    partTypeId = PartTypeId(row.part_type_id),
                    canLead = row.can_lead == 1L,
                )
            }
    }

    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> {
        return database.ministeroDatabaseQueries
            .futureAssignmentWeeksForPersonFromDate(personId.value, fromDate.toString())
            .executeAsList()
            .map(LocalDate::parse)
    }
}

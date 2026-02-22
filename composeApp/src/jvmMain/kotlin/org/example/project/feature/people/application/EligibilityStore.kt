package org.example.project.feature.people.application

import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate

data class LeadEligibility(
    val partTypeId: PartTypeId,
    val canLead: Boolean,
)

interface EligibilityStore {
    suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean)
    suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean)
    suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean)
    suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility>
    suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate>
}

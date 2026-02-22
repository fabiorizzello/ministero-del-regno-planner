package org.example.project.feature.people.application

import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate

data class LeadEligibility(
    val partTypeId: PartTypeId,
    val canLead: Boolean,
)

data class EligibilityCleanupCandidate(
    val personId: ProclamatoreId,
    val partTypeId: PartTypeId,
)

interface EligibilityStore {
    suspend fun setSuspended(personId: ProclamatoreId, suspended: Boolean)
    suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean)
    suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean)
    suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility>
    suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate>
    suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>)
    suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate>
}

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

    /**
     * Pre-load lead eligibility for all [partTypeIds] in one query.
     * Safe to cache for the duration of a single auto-assign run: no write path
     * modifies person_part_type_eligibility during auto-assign.
     */
    suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>>

    suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>)
    suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate>
}

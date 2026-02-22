package org.example.project.feature.people.application

import org.example.project.feature.people.domain.ProclamatoreId

class CaricaIdoneitaProclamatoreUseCase(
    private val eligibilityStore: EligibilityStore,
) {
    suspend operator fun invoke(personId: ProclamatoreId): List<LeadEligibility> {
        return eligibilityStore.listLeadEligibility(personId)
    }
}

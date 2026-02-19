package org.example.project.feature.assignments.application

import org.example.project.feature.people.domain.ProclamatoreId

class ContaAssegnazioniPersonaUseCase(
    private val assignmentStore: AssignmentStore,
) {
    suspend operator fun invoke(personId: ProclamatoreId): Int {
        return assignmentStore.countAssignmentsForPerson(personId)
    }
}

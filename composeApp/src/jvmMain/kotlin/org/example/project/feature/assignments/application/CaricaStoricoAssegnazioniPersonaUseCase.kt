package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.PersonAssignmentHistory
import org.example.project.feature.people.domain.ProclamatoreId

class CaricaStoricoAssegnazioniPersonaUseCase(
    private val assignmentStore: PersonAssignmentLifecycle,
) {
    suspend operator fun invoke(personId: ProclamatoreId): PersonAssignmentHistory {
        return assignmentStore.getAssignmentHistoryForPerson(personId)
    }
}

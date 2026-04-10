package org.example.project.feature.assignments.application

import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import java.time.LocalDate

class CaricaUltimeAssegnazioniPerParteProclamatoreUseCase(
    private val historyQuery: PersonAssignmentHistoryQuery,
) {
    suspend operator fun invoke(
        personId: ProclamatoreId,
        partTypeIds: Set<PartTypeId>,
    ): Map<PartTypeId, LocalDate> {
        if (partTypeIds.isEmpty()) return emptyMap()
        return historyQuery.lastAssignmentDatesByPartType(personId, partTypeIds)
    }

    suspend fun lastAssistantDate(personId: ProclamatoreId): LocalDate? {
        return historyQuery.lastAssistantAssignmentDate(personId)
    }
}

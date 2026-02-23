package org.example.project.feature.assignments.application

import kotlinx.coroutines.sync.Mutex
import org.example.project.core.domain.toMessage
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate

data class AutoAssignUnresolvedSlot(
    val weekStartDate: LocalDate,
    val partLabel: String,
    val slot: Int,
    val reason: String,
)

data class AutoAssignProgramResult(
    val assignedCount: Int,
    val unresolved: List<AutoAssignUnresolvedSlot>,
)

class AutoAssegnaProgrammaUseCase(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentRepository: AssignmentRepository,
    private val suggerisciProclamatori: SuggerisciProclamatoriUseCase,
    private val assegnaPersona: AssegnaPersonaUseCase,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(
        programId: String,
        referenceDate: LocalDate,
    ): AutoAssignProgramResult {
        if (!mutex.tryLock()) {
            return AutoAssignProgramResult(assignedCount = 0, unresolved = emptyList())
        }
        try {
            return doAssign(programId, referenceDate)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doAssign(
        programId: String,
        referenceDate: LocalDate,
    ): AutoAssignProgramResult {
        val weeks = weekPlanStore.listByProgram(programId)
            .filter { it.weekStartDate >= referenceDate }
            .filter { it.status == WeekPlanStatus.ACTIVE }

        var assignedCount = 0
        val unresolved = mutableListOf<AutoAssignUnresolvedSlot>()

        for (week in weeks) {
            val assignments = assignmentRepository.listByWeek(week.id)
            val existingByPartAndSlot = assignments.associateBy { it.weeklyPartId.value to it.slot }
            val alreadyAssignedIds = assignments.map { it.personId }.toMutableSet()

            for (part in week.parts) {
                for (slot in 1..part.partType.peopleCount) {
                    if (existingByPartAndSlot.containsKey(part.id.value to slot)) continue

                    val suggestions = suggerisciProclamatori(
                        weekStartDate = week.weekStartDate,
                        weeklyPartId = part.id,
                        slot = slot,
                        alreadyAssignedIds = alreadyAssignedIds,
                    )

                    val selected = suggestions.firstOrNull()
                    if (selected == null) {
                        unresolved += AutoAssignUnresolvedSlot(
                            weekStartDate = week.weekStartDate,
                            partLabel = part.partType.label,
                            slot = slot,
                            reason = "Nessun candidato idoneo",
                        )
                        continue
                    }

                    val result = assegnaPersona(
                        weekStartDate = week.weekStartDate,
                        weeklyPartId = part.id,
                        personId = selected.proclamatore.id,
                        slot = slot,
                    )

                    result.fold(
                        ifLeft = { error ->
                            unresolved += AutoAssignUnresolvedSlot(
                                weekStartDate = week.weekStartDate,
                                partLabel = part.partType.label,
                                slot = slot,
                                reason = error.toMessage(),
                            )
                        },
                        ifRight = {
                            assignedCount += 1
                            alreadyAssignedIds += selected.proclamatore.id
                        },
                    )
                }
            }
        }

        return AutoAssignProgramResult(
            assignedCount = assignedCount,
            unresolved = unresolved,
        )
    }
}

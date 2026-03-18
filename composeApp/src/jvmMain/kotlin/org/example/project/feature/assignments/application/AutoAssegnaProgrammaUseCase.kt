package org.example.project.feature.assignments.application

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.toMessage
import org.example.project.core.persistence.TransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.assignments.domain.canBeAutoAssigned
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.PartTypeId
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
    private val weekPlanStore: WeekPlanQueries,
    private val assignmentRepository: AssignmentRepository,
    private val suggerisciProclamatori: SuggerisciProclamatoriUseCase,
    private val assegnaPersona: AssegnaPersonaUseCase,
    private val transactionRunner: TransactionRunner,
    private val assignmentRanking: AssignmentRanking,
    private val eligibilityStore: EligibilityStore,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate,
    ): Either<DomainError, AutoAssignProgramResult> = mutex.withLock {
        transactionRunner.runInTransactionEither {
            either { doAssign(programId, referenceDate) }
        }
    }

    context(tx: TransactionScope)
    private suspend fun doAssign(
        programId: ProgramMonthId,
        referenceDate: LocalDate,
    ): AutoAssignProgramResult {
        val weeks = weekPlanStore.listByProgram(programId)
            .filter { it.weekStartDate >= referenceDate }
            .filter { it.status == WeekPlanStatus.ACTIVE }

        val partTypeIds: Set<PartTypeId> = weeks.flatMap { w -> w.parts.map { it.partType.id } }.toSet()
        val assignmentsByWeek = assignmentRepository.listByWeekPlanIds(weeks.map { it.id }.toSet())
        // Eligibility is static for the duration of the run: no write path touches it during auto-assign.
        val eligibilityCache = eligibilityStore.preloadLeadEligibilityByPartType(partTypeIds)

        var assignedCount = 0
        val unresolved = mutableListOf<AutoAssignUnresolvedSlot>()

        for (week in weeks) {
            // Reload ranking per week so assignments made in earlier weeks are reflected.
            val rankingCache = assignmentRanking.preloadSuggestionRanking(
                referenceDates = setOf(week.weekStartDate),
                partTypeIds = partTypeIds,
            )
            val assignments = assignmentsByWeek[week.id] ?: emptyList()
            val existingByPartAndSlot = assignments.associateBy { it.weeklyPartId.value to it.slot }
            // Tracks only in-progress assignments (not yet persisted) made within this loop.
            // Pre-existing DB assignments are loaded internally by SuggerisciProclamatoriUseCase.
            val inProgressAssignedIds = mutableSetOf<ProclamatoreId>()

            for (part in week.parts) {
                for (slot in 1..part.partType.peopleCount) {
                    if (existingByPartAndSlot.containsKey(part.id.value to slot)) continue

                    val suggestions = suggerisciProclamatori(
                        weekStartDate = week.weekStartDate,
                        weeklyPartId = part.id,
                        slot = slot,
                        additionalExcludedIds = inProgressAssignedIds,
                        rankingCache = rankingCache,
                        eligibilityCache = eligibilityCache,
                    )

                    val selected = suggestions.firstOrNull { it.canBeAutoAssigned() }
                    if (selected == null) {
                        unresolved += AutoAssignUnresolvedSlot(
                            weekStartDate = week.weekStartDate,
                            partLabel = part.partType.label,
                            slot = slot,
                            reason = "Nessun candidato idoneo",
                        )
                        continue
                    }

                    val result = assegnaPersona.assignWithoutTransaction(
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
                            inProgressAssignedIds += selected.proclamatore.id
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

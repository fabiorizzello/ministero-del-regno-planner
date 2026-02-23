package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate
import java.util.UUID

class GeneraSettimaneProgrammaUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        programId: String,
        skippedWeeks: Set<LocalDate> = emptySet(),
    ): Either<DomainError, Unit> = either {
        val program = programStore.findById(org.example.project.feature.programs.domain.ProgramMonthId(programId))
            ?: raise(DomainError.Validation("Programma non trovato"))

        val fixedPart = partTypeStore.findFixed()

        // Pre-compute part type IDs per week BEFORE the transaction
        // so we can raise() in the either context if validation fails
        data class WeekSpec(val weekStartDate: LocalDate, val partTypeIds: List<PartTypeId>, val status: WeekPlanStatus)

        val weekSpecs = buildList {
            var currentWeek = program.startDate
            while (!currentWeek.isAfter(program.endDate)) {
                val template = schemaTemplateStore.findByWeekStartDate(currentWeek)
                val partTypeIds = template?.partTypeIds ?: listOfNotNull(fixedPart?.id)
                if (partTypeIds.isEmpty()) {
                    raise(DomainError.Validation("Nessun template e nessuna parte fissa per $currentWeek"))
                }
                add(WeekSpec(
                    weekStartDate = currentWeek,
                    partTypeIds = partTypeIds,
                    status = if (currentWeek in skippedWeeks) WeekPlanStatus.SKIPPED else WeekPlanStatus.ACTIVE,
                ))
                currentWeek = currentWeek.plusWeeks(1)
            }
        }

        transactionRunner.runInTransaction {
            weekPlanStore.listByProgram(programId).forEach { existing ->
                weekPlanStore.delete(existing.id)
            }

            for (spec in weekSpecs) {
                val weekPlan = WeekPlan(
                    id = WeekPlanId(UUID.randomUUID().toString()),
                    weekStartDate = spec.weekStartDate,
                    parts = emptyList(),
                    programId = programId,
                    status = spec.status,
                )

                weekPlanStore.saveWithProgram(
                    weekPlan = weekPlan,
                    programId = programId,
                    status = weekPlan.status,
                )
                weekPlanStore.replaceAllParts(weekPlan.id, spec.partTypeIds)
            }
        }
    }
}

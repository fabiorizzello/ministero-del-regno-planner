package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
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

        transactionRunner.runInTransaction {
            weekPlanStore.listByProgram(programId).forEach { existing ->
                weekPlanStore.delete(existing.id)
            }

            var currentWeek = program.startDate
            while (!currentWeek.isAfter(program.endDate)) {
                val template = schemaTemplateStore.findByWeekStartDate(currentWeek)
                val partTypeIds = template?.partTypeIds ?: listOfNotNull(fixedPart?.id)
                if (partTypeIds.isEmpty()) {
                    throw IllegalStateException(
                        "Nessun template e nessuna parte fissa per ${currentWeek}",
                    )
                }

                val weekPlan = WeekPlan(
                    id = WeekPlanId(UUID.randomUUID().toString()),
                    weekStartDate = currentWeek,
                    parts = emptyList(),
                    programId = programId,
                    status = if (currentWeek in skippedWeeks) WeekPlanStatus.SKIPPED else WeekPlanStatus.ACTIVE,
                )

                weekPlanStore.saveWithProgram(
                    weekPlan = weekPlan,
                    programId = programId,
                    status = weekPlan.status,
                )
                weekPlanStore.replaceAllParts(weekPlan.id, partTypeIds)

                currentWeek = currentWeek.plusWeeks(1)
            }
        }
    }
}

package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class GeneraSettimaneProgrammaUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        skippedWeeks: Set<LocalDate> = emptySet(),
    ): Either<DomainError, Unit> = either {
        val program = programStore.findById(programId)
            ?: raise(DomainError.NotFound("Programma"))

        val fixedPart = partTypeStore.findFixed()
        val partTypesById = partTypeStore.all().associateBy { partType -> partType.id }

        data class WeekSpec(
            val weekStartDate: LocalDate,
            val orderedPartTypes: List<Pair<PartType, String?>>,
            val status: WeekPlanStatus,
        )

        val weekSpecs = buildList {
            var currentWeek = program.startDate
            while (!currentWeek.isAfter(program.endDate)) {
                val template = schemaTemplateStore.findByWeekStartDate(currentWeek)
                val partTypeIds = template?.partTypeIds ?: listOfNotNull(fixedPart?.id)
                if (partTypeIds.isEmpty()) {
                    raise(DomainError.SettimanaSenzaTemplateENessunaParteFissa(currentWeek))
                }

                val orderedPartTypes = partTypeIds.map { partTypeId ->
                    val partType = partTypesById[partTypeId]
                        ?: raise(DomainError.NotFound("Tipo parte"))
                    partType to partTypeStore.getLatestRevisionId(partTypeId)
                }

                add(
                    WeekSpec(
                        weekStartDate = currentWeek,
                        orderedPartTypes = orderedPartTypes,
                        status = if (currentWeek in skippedWeeks) WeekPlanStatus.SKIPPED else WeekPlanStatus.ACTIVE,
                    ),
                )
                currentWeek = currentWeek.plusWeeks(1)
            }
        }

        val aggregates = weekSpecs.map { spec ->
            val week = WeekPlan(
                id = WeekPlanId(UUID.randomUUID().toString()),
                weekStartDate = spec.weekStartDate,
                parts = spec.orderedPartTypes.mapIndexed { index, (partType, revisionId) ->
                    WeeklyPart(
                        id = WeeklyPartId(UUID.randomUUID().toString()),
                        partType = partType,
                        partTypeRevisionId = revisionId,
                        sortOrder = index,
                    )
                },
                programId = programId,
                status = spec.status,
            )
            WeekPlanAggregate(
                weekPlan = week,
                assignments = emptyList(),
            )
        }

        transactionRunner.runInTransaction {
            weekPlanStore.replaceProgramAggregates(programId, aggregates)
            programStore.updateTemplateAppliedAt(programId, LocalDateTime.now())
        }
    }
}

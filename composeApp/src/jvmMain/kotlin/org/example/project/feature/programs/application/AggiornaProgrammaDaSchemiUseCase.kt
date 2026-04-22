package org.example.project.feature.programs.application

import arrow.core.Either
import arrow.core.raise.either
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class SchemaRefreshMode {
    ALL,
    ONLY_UNASSIGNED,
}

data class WeekRefreshDetail(
    val weekStartDate: LocalDate,
    val partsAdded: Int,
    val partsRemoved: Int,
    val partsKept: Int,
    val assignmentsPreserved: Int,
    val assignmentsRemoved: Int,
)

data class SchemaRefreshReport(
    val weeksUpdated: Int,
    val assignmentsPreserved: Int,
    val assignmentsRemoved: Int,
    val weekDetails: List<WeekRefreshDetail> = emptyList(),
)

data class SchemaRefreshPreview(
    val allChanges: SchemaRefreshReport,
    val onlyUnassignedChanges: SchemaRefreshReport,
)

fun WeekRefreshDetail.hasEffectiveChanges(): Boolean =
    partsAdded > 0 || partsRemoved > 0 || assignmentsRemoved > 0

fun SchemaRefreshReport.hasEffectiveChanges(): Boolean =
    weekDetails.any { detail -> detail.hasEffectiveChanges() }

fun SchemaRefreshPreview.hasEffectiveChanges(): Boolean =
    allChanges.hasEffectiveChanges() || onlyUnassignedChanges.hasEffectiveChanges()

private data class WeekRefreshCandidate(
    val aggregate: WeekPlanAggregate,
    val orderedPartTypes: List<Pair<PartType, String?>>, // ordered by sort
)

private data class WeekRefreshComputation(
    val refreshedAggregate: WeekPlanAggregate,
    val detail: WeekRefreshDetail,
)

fun interface AggiornaProgrammaDaSchemiOperation {
    suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate,
        dryRun: Boolean,
        mode: SchemaRefreshMode,
    ): Either<DomainError, SchemaRefreshReport>
}

class AggiornaProgrammaDaSchemiUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val schemaTemplateStore: SchemaTemplateStore,
    private val partTypeStore: PartTypeStore,
    private val transactionRunner: TransactionRunner,
) : AggiornaProgrammaDaSchemiOperation {
    override suspend operator fun invoke(
        programId: ProgramMonthId,
        referenceDate: LocalDate,
        dryRun: Boolean,
        mode: SchemaRefreshMode,
    ): Either<DomainError, SchemaRefreshReport> = either {
        val program = programStore.findById(programId)
            ?: raise(DomainError.NotFound("Programma"))

        val partTypesById = partTypeStore.all().associateBy { partType -> partType.id }
        val refreshCandidates = mutableListOf<WeekRefreshCandidate>()

        for (weekAggregate in weekPlanStore.listAggregatesByProgram(programId)) {
            val week = weekAggregate.weekPlan
            if (week.weekStartDate < referenceDate) continue
            if (week.status != WeekPlanStatus.ACTIVE) continue

            val template = schemaTemplateStore.findByWeekStartDate(week.weekStartDate)
                ?: continue
            if (template.partTypeIds.isEmpty()) continue

            val orderedPartTypes = template.partTypeIds.map { partTypeId ->
                val partType = partTypesById[partTypeId]
                    ?: raise(DomainError.NotFound("Tipo parte"))
                partType to partTypeStore.getLatestRevisionId(partTypeId)
            }

            refreshCandidates += WeekRefreshCandidate(
                aggregate = weekAggregate,
                orderedPartTypes = orderedPartTypes,
            )
        }

        val computations = refreshCandidates.map { candidate ->
            val refreshedAggregate = when (mode) {
                SchemaRefreshMode.ALL -> candidate.aggregate.replaceParts(candidate.orderedPartTypes) {
                    WeeklyPartId(UUID.randomUUID().toString())
                }
                SchemaRefreshMode.ONLY_UNASSIGNED -> candidate.aggregate.replaceOnlyUnassignedParts(candidate.orderedPartTypes) {
                    WeeklyPartId(UUID.randomUUID().toString())
                }
            }.bind()

            val oldKeys = candidate.aggregate.weekPlan.parts
                .map { it.partType.id to it.sortOrder }
                .toSet()
            val newKeys = refreshedAggregate.weekPlan.parts
                .map { it.partType.id to it.sortOrder }
                .toSet()
            val assignmentsPreserved = refreshedAggregate.assignments.size
            val assignmentsRemoved = candidate.aggregate.assignments.size - assignmentsPreserved

            WeekRefreshComputation(
                refreshedAggregate = refreshedAggregate,
                detail = WeekRefreshDetail(
                    weekStartDate = candidate.aggregate.weekPlan.weekStartDate,
                    partsAdded = (newKeys - oldKeys).size,
                    partsRemoved = (oldKeys - newKeys).size,
                    partsKept = (oldKeys intersect newKeys).size,
                    assignmentsPreserved = assignmentsPreserved,
                    assignmentsRemoved = assignmentsRemoved,
                ),
            )
        }

        val assignmentsPreserved = computations.sumOf { it.detail.assignmentsPreserved }
        val assignmentsRemoved = computations.sumOf { it.detail.assignmentsRemoved }
        val weekDetails = computations.map { it.detail }

        if (!dryRun) {
            transactionRunner.runInTransactionEither {
                either {
                    for (computation in computations) {
                        applyRefreshCandidate(computation).bind()
                    }
                    programStore.updateTemplateAppliedAt(program.id, LocalDateTime.now())
                }
            }.bind()
        }

        SchemaRefreshReport(
            weeksUpdated = refreshCandidates.size,
            assignmentsPreserved = assignmentsPreserved,
            assignmentsRemoved = assignmentsRemoved,
            weekDetails = weekDetails,
        )
    }

    context(tx: TransactionScope)
    private suspend fun applyRefreshCandidate(
        computation: WeekRefreshComputation,
    ): Either<DomainError, Unit> = either {
        weekPlanStore.saveAggregate(computation.refreshedAggregate)
    }
}

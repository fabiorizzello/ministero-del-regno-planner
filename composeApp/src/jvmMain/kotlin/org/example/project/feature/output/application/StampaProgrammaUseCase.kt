package org.example.project.feature.output.application

import arrow.core.Either
import arrow.core.raise.either
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.project.core.config.AppRuntime
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.core.formatting.formatMonthYearLabel

internal fun weekPlanStatusLabel(status: WeekPlanStatus): String = when (status) {
    WeekPlanStatus.ACTIVE -> "Attiva"
    WeekPlanStatus.SKIPPED -> "Saltata"
}

internal fun partSlotRoleLabel(
    peopleCount: Int,
    slot: Int,
): String? = when {
    peopleCount <= 1 -> "Studente"
    slot == 1 -> "Studente"
    else -> "Assistente"
}

internal fun partCardStatusLabel(status: ProgramWeekPrintCardStatus): String = when (status) {
    ProgramWeekPrintCardStatus.EMPTY -> "Vuota"
    ProgramWeekPrintCardStatus.PARTIAL -> "Parziale"
    ProgramWeekPrintCardStatus.ASSIGNED -> "Assegnata"
}

internal fun buildProgramWeekPrintSection(
    week: WeekPlan,
    assignments: List<AssignmentWithPerson>,
): ProgramWeekPrintSection {
    if (week.status == WeekPlanStatus.SKIPPED) {
        return ProgramWeekPrintSection(
            weekStartDate = week.weekStartDate,
            weekEndDate = week.weekStartDate.plusDays(6),
            statusLabel = weekPlanStatusLabel(week.status),
            cards = emptyList(),
            emptyStateLabel = "Settimana saltata",
        )
    }

    val assignmentByPartAndSlot = assignments.associateBy { it.weeklyPartId.value to it.slot }
    val cards = week.parts
        .sortedBy { it.sortOrder }
        .map { part ->
            val slots = (1..part.partType.peopleCount).map { slot ->
                val assignment = assignmentByPartAndSlot[part.id.value to slot]
                ProgramWeekPrintSlot(
                    roleLabel = partSlotRoleLabel(part.partType.peopleCount, slot),
                    assignedTo = assignment?.fullName ?: "Non assegnato",
                    isAssigned = assignment != null,
                )
            }
            val filledSlots = slots.count { it.isAssigned }
            val status = when {
                filledSlots == 0 -> ProgramWeekPrintCardStatus.EMPTY
                filledSlots < slots.size -> ProgramWeekPrintCardStatus.PARTIAL
                else -> ProgramWeekPrintCardStatus.ASSIGNED
            }

            ProgramWeekPrintCard(
                displayNumber = part.sortOrder + PART_DISPLAY_NUMBER_OFFSET,
                partLabel = part.snapshot?.label ?: part.partType.label,
                status = status,
                statusLabel = partCardStatusLabel(status),
                slots = slots,
            )
        }

    return ProgramWeekPrintSection(
        weekStartDate = week.weekStartDate,
        weekEndDate = week.weekStartDate.plusDays(6),
        statusLabel = weekPlanStatusLabel(week.status),
        cards = cards,
        emptyStateLabel = if (cards.isEmpty()) {
            "Nessuna parte configurata"
        } else {
            null
        },
    )
}

class StampaProgrammaUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanQueries,
    private val assignmentRepository: AssignmentRepository,
    private val renderer: ProgramRenderer,
    private val fileOpener: FileOpener,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val programExportDirProvider: () -> Path = { AppRuntime.paths().exportsDir.resolve("programmi") },
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(programId: ProgramMonthId): Either<DomainError, Path> = withContext(dispatcher) {
        val result = either {
            val program = programStore.findById(programId)
                ?: raise(DomainError.NotFound("Programma"))

            val weeks = weekPlanStore.listByProgram(programId).sortedBy { it.weekStartDate }
            val assignmentsByWeek = assignmentRepository.listByWeekPlanIds(weeks.map { it.id }.toSet())
            val sections = weeks.map { week ->
                buildProgramWeekPrintSection(
                    week = week,
                    assignments = assignmentsByWeek[week.id] ?: emptyList(),
                )
            }

            val outputFileName = buildMonthlyProgramFileName(program.year, program.month)
            val outputPath = prepareMonthlyProgramOutputPath(
                outputDir = programExportDirProvider(),
                outputFileName = outputFileName,
            ).bind()

            renderer.renderMonthlyProgramPdf(
                title = "Programma ${formatMonthYearLabel(program.month, program.year)}",
                sections = sections,
                outputPath = outputPath,
            )

            outputPath
        }
        result.onRight { path -> fileOpener.open(path) }
        result
    }

    private fun prepareMonthlyProgramOutputPath(
        outputDir: Path,
        outputFileName: String,
    ): Either<DomainError, Path> {
        return Either.catch { Files.createDirectories(outputDir) }
            .mapLeft { DomainError.Validation("Impossibile creare directory output: ${it.message}") }
            .map {
                cleanupMonthlyProgramExports(
                    outputDir = outputDir,
                    keepFileName = outputFileName,
                ) { path, error ->
                    logger.warn { "Cleanup PDF programma non riuscito (${path.fileName}): ${error.message}" }
                }
                outputDir.resolve(outputFileName)
            }
    }
}

internal fun buildMonthlyProgramFileName(
    year: Int,
    month: Int,
): String = "programma-$year-${month.toString().padStart(2, '0')}.pdf"

internal fun cleanupMonthlyProgramExports(
    outputDir: Path,
    keepFileName: String,
    onFailure: ((Path, Throwable) -> Unit)? = null,
) {
    Files.newDirectoryStream(outputDir, "programma-*.pdf").use { paths ->
        for (path in paths) {
            if (path.fileName.toString() == keepFileName) continue
            runCatching { Files.deleteIfExists(path) }
                .onFailure { error -> onFailure?.invoke(path, error) }
        }
    }
}

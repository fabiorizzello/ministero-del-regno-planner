package org.example.project.feature.output.application

import java.nio.file.Path
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.output.infrastructure.PdfProgramRenderer
import org.example.project.feature.output.infrastructure.ProgramWeekPrintSection
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanStore

class StampaProgrammaUseCase(
    private val programStore: ProgramStore,
    private val weekPlanStore: WeekPlanStore,
    private val assignmentRepository: AssignmentRepository,
    private val renderer: PdfProgramRenderer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend operator fun invoke(programId: String): Path = withContext(dispatcher) {
        val program = programStore.findById(ProgramMonthId(programId))
            ?: throw IllegalStateException("Programma non trovato")

        val weeks = weekPlanStore.listByProgram(programId)
        val sections = weeks.map { week ->
            val assignments = assignmentRepository.listByWeek(week.id)
            val assignmentByPartAndSlot = assignments.associateBy { it.weeklyPartId.value to it.slot }
            val lines = week.parts.flatMap { part ->
                (1..part.partType.peopleCount).map { slot ->
                    val assigned = assignmentByPartAndSlot[part.id.value to slot]?.fullName ?: "Non assegnato"
                    val role = if (part.partType.peopleCount > 1) {
                        if (slot == 1) "Conducente" else "Assistente"
                    } else {
                        ""
                    }
                    val rolePrefix = if (role.isBlank()) "" else "[$role] "
                    "- ${part.partType.label}: ${rolePrefix}${assigned}"
                }
            }
            ProgramWeekPrintSection(
                weekStartDate = week.weekStartDate,
                statusLabel = week.status.name,
                lines = lines,
            )
        }

        val outputPath = AppRuntime.paths().exportsDir
            .resolve("programmi")
            .resolve("programma-${program.year}-${program.month.toString().padStart(2, '0')}.pdf")

        renderer.renderMonthlyProgramPdf(
            title = "Programma ${program.month}/${program.year}",
            sections = sections,
            outputPath = outputPath,
        )

        outputPath
    }
}

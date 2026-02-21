package org.example.project.feature.output.application

import java.nio.file.Path
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.output.infrastructure.PdfAssignmentsRenderer
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.sundayOf
import org.slf4j.LoggerFactory

class GeneraPdfAssegnazioni(
    private val caricaSettimana: CaricaSettimanaUseCase,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val renderer: PdfAssignmentsRenderer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(GeneraPdfAssegnazioni::class.java)

    suspend operator fun invoke(
        weekStartDate: LocalDate,
        selectedPartIds: Set<WeeklyPartId>,
    ): Path = withContext(dispatcher) {
        val weekPlan = caricaSettimana(weekStartDate)
            ?: throw IllegalStateException("Settimana non trovata per $weekStartDate")
        val assignments = caricaAssegnazioni(weekStartDate)

        val parts = weekPlan.parts
            .filter { selectedPartIds.isEmpty() || selectedPartIds.contains(it.id) }
            .map { part ->
                val partAssignments = assignments.filter { it.weeklyPartId == part.id }
                val entries = (1..part.partType.peopleCount).map { slot ->
                    val assignment = partAssignments.firstOrNull { it.slot == slot }
                    val roleLabel = when {
                        part.partType.peopleCount == 1 -> null
                        slot == 1 -> "Proclamatore"
                        else -> "Assistente"
                    }
                    val base = assignment?.fullName ?: "Non assegnato"
                    if (roleLabel == null) base else "$roleLabel: $base"
                }
                PdfAssignmentsRenderer.RenderedPart(
                    label = part.partType.label,
                    assignments = entries,
                )
            }

        val weekEnd = sundayOf(weekStartDate)
        val baseName = "assegnazioni-${weekStartDate.toString()}-${weekEnd.toString()}"
        val outputPath = AppRuntime.paths().exportsDir.resolve("assegnazioni").resolve("$baseName.pdf")

        renderer.renderWeeklyAssignmentsPdf(weekStartDate, weekEnd, parts, outputPath)
        logger.info("PDF assegnazioni creato: {}", outputPath.toAbsolutePath())
        outputPath
    }
}

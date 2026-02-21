package org.example.project.feature.output.application

import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.example.project.core.config.AppRuntime
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.output.infrastructure.PdfAssignmentsRenderer
import org.example.project.feature.weeklyparts.application.CaricaSettimanaUseCase
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.sundayOf
import org.slf4j.LoggerFactory

class GeneraImmaginiAssegnazioni(
    private val caricaSettimana: CaricaSettimanaUseCase,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val renderer: PdfAssignmentsRenderer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(GeneraImmaginiAssegnazioni::class.java)

    suspend operator fun invoke(
        weekStartDate: LocalDate,
        selectedPartIds: Set<WeeklyPartId>,
    ): List<Path> = withContext(dispatcher) {
        val weekPlan = caricaSettimana(weekStartDate)
            ?: throw IllegalStateException("Settimana non trovata per $weekStartDate")
        val assignments = caricaAssegnazioni(weekStartDate)
        val weekEnd = sundayOf(weekStartDate)

        val selectedParts = weekPlan.parts
            .filter { selectedPartIds.isEmpty() || selectedPartIds.contains(it.id) }
            .sortedBy { it.sortOrder }
        val partOrderMap = selectedParts.associateBy({ it.id }, { it.sortOrder })

        val assignmentsByPerson = assignments
            .filter { assignment -> selectedParts.any { it.id == assignment.weeklyPartId } }
            .groupBy { it.personId }

        val outputs = mutableListOf<Path>()
        val outputDir = AppRuntime.paths().exportsDir.resolve("assegnazioni")
        outputDir.toFile().mkdirs()

        assignmentsByPerson.forEach { (_, personAssignments) ->
            val orderedAssignments = personAssignments.sortedWith(
                compareBy({ partOrderMap[it.weeklyPartId] ?: Int.MAX_VALUE }, { it.slot }),
            )
            val personName = orderedAssignments.first().fullName
            val assignmentsLabels = orderedAssignments.map { assignment ->
                val part = selectedParts.first { it.id == assignment.weeklyPartId }
                val roleLabel = when {
                    part.partType.peopleCount == 1 -> null
                    assignment.slot == 1 -> "Proclamatore"
                    else -> "Assistente"
                }
                val role = roleLabel?.let { " ($it)" } ?: ""
                "${part.partType.label}$role"
            }

            val baseName = buildImageBaseName(weekStartDate, weekEnd, personName)
            val pdfPath = outputDir.resolve("$baseName-tmp.pdf")
            val pngPath = outputDir.resolve("$baseName.png")

            try {
                renderer.renderPersonSheetPdf(
                    PdfAssignmentsRenderer.PersonSheet(
                        fullName = personName,
                        weekStart = weekStartDate,
                        weekEnd = weekEnd,
                        assignments = assignmentsLabels,
                    ),
                    pdfPath,
                )
                renderPdfToPng(pdfPath, pngPath)
                outputs.add(pngPath)
                logger.info("Immagine creata: {}", pngPath.toAbsolutePath())
            } catch (error: Exception) {
                logger.error(
                    "Generazione immagine fallita per {} (pdf={}, png={}): {}",
                    personName,
                    pdfPath.toAbsolutePath(),
                    pngPath.toAbsolutePath(),
                    error.message,
                    error,
                )
                throw IllegalStateException(
                    "Errore generando immagine per $personName (pdf=$pdfPath, png=$pngPath): ${error.message}",
                    error,
                )
            } finally {
                runCatching { java.nio.file.Files.deleteIfExists(pdfPath) }
                    .onFailure { cleanupError ->
                        logger.warn(
                            "Cleanup PDF temporaneo non riuscito ({}): {}",
                            pdfPath.toAbsolutePath(),
                            cleanupError.message,
                        )
                    }
            }
        }

        outputs
    }

    private fun renderPdfToPng(pdfPath: Path, pngPath: Path) {
        PDDocument.load(pdfPath.toFile()).use { document ->
            val renderer = PDFRenderer(document)
            val image: BufferedImage = renderer.renderImageWithDPI(0, 200f)
            ImageIO.write(image, "png", pngPath.toFile())
        }
    }

    private fun buildImageBaseName(weekStart: LocalDate, weekEnd: LocalDate, fullName: String): String {
        val yearMonth = weekStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"))
        val startDay = weekStart.dayOfMonth.toString().padStart(2, '0')
        val endDay = weekEnd.dayOfMonth.toString().padStart(2, '0')
        val safeName = fullName
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^A-Za-z0-9_]+"), "")
        return "${yearMonth}${startDay}-${endDay}_$safeName"
    }
}

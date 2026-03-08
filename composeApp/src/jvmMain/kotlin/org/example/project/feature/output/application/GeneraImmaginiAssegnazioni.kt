package org.example.project.feature.output.application

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.example.project.core.config.AppRuntime
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.output.infrastructure.PdfAssignmentsRenderer
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.components.sundayOf

private val weekImagePrefixFormatter = DateTimeFormatter.ofPattern("yyyyMM")
private val monthImagePrefixFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

data class AssignmentTicketLine(
    val partLabel: String,
    val roleLabel: String,
)

data class AssignmentTicketImage(
    val fullName: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val imagePath: Path,
    val assignments: List<AssignmentTicketLine>,
)

private data class PersonTicketSheet(
    val fullName: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val assignments: List<AssignmentTicketLine>,
)

class GeneraImmaginiAssegnazioni(
    private val programStore: ProgramStore,
    private val weekPlanQueries: WeekPlanQueries,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val renderer: PdfAssignmentsRenderer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val outputDirProvider: () -> Path = { AppRuntime.paths().exportsDir.resolve("assegnazioni") },
    private val pdfToPngRenderer: (Path, Path) -> Unit = ::renderPdfToPngFile,
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(
        weekStartDate: LocalDate,
        selectedPartIds: Set<WeeklyPartId>,
    ): List<Path> = withContext(dispatcher) {
        val weekPlan = weekPlanQueries.findByDate(weekStartDate)
            ?: throw IllegalStateException("Settimana non trovata per $weekStartDate")
        val assignments = caricaAssegnazioni(weekStartDate)
        val sheets = buildPersonTicketSheets(
            weekPlan = weekPlan,
            assignments = assignments,
            selectedPartIds = selectedPartIds,
        )
        val outputDir = ensureOutputDir()

        sheets.map { sheet ->
            val baseName = buildWeeklyImageBaseName(sheet.weekStart, sheet.weekEnd, sheet.fullName)
            renderTicketImage(
                outputDir = outputDir,
                baseName = baseName,
                sheet = sheet,
            )
        }
    }

    suspend fun generateProgramTickets(programId: ProgramMonthId): List<AssignmentTicketImage> = withContext(dispatcher) {
        val program = programStore.findById(programId)
            ?: throw IllegalStateException("Programma non trovato")
        val weeks = weekPlanQueries.listByProgram(programId)
            .sortedBy { it.weekStartDate }
        val outputDir = ensureOutputDir()
        cleanupProgramTicketExports(
            outputDir = outputDir,
            year = program.year,
            month = program.month,
        ) { path, error ->
            logger.warn { "Cleanup biglietto non riuscito (${path.fileName}): ${error.message}" }
        }

        weeks
            .filter { it.status == WeekPlanStatus.ACTIVE }
            .flatMap { week ->
                val weekAssignments = caricaAssegnazioni(week.weekStartDate)
                buildPersonTicketSheets(
                    weekPlan = week,
                    assignments = weekAssignments,
                    selectedPartIds = emptySet(),
                )
            }
            .sortedWith(compareBy({ it.weekStart }, { it.fullName.lowercase() }))
            .map { sheet ->
                val baseName = buildProgramImageBaseName(
                    year = program.year,
                    month = program.month,
                    weekStart = sheet.weekStart,
                    weekEnd = sheet.weekEnd,
                    fullName = sheet.fullName,
                )
                val imagePath = renderTicketImage(
                    outputDir = outputDir,
                    baseName = baseName,
                    sheet = sheet,
                )
                AssignmentTicketImage(
                    fullName = sheet.fullName,
                    weekStart = sheet.weekStart,
                    weekEnd = sheet.weekEnd,
                    imagePath = imagePath,
                    assignments = sheet.assignments,
                )
            }
    }

    private fun buildPersonTicketSheets(
        weekPlan: WeekPlan,
        assignments: List<AssignmentWithPerson>,
        selectedPartIds: Set<WeeklyPartId>,
    ): List<PersonTicketSheet> {
        if (weekPlan.status == WeekPlanStatus.SKIPPED) return emptyList()
        val selectedParts = weekPlan.parts
            .filter { selectedPartIds.isEmpty() || selectedPartIds.contains(it.id) }
            .sortedBy { it.sortOrder }
        if (selectedParts.isEmpty()) return emptyList()

        val selectedPartIdsSet = selectedParts.mapTo(mutableSetOf()) { it.id }
        val partsById = selectedParts.associateBy { it.id }
        val partOrderMap = selectedParts.associate { it.id to it.sortOrder }

        return assignments
            .filter { it.weeklyPartId in selectedPartIdsSet }
            .groupBy { it.personId }
            .values
            .map { personAssignments ->
                val orderedAssignments = personAssignments.sortedWith(
                    compareBy({ partOrderMap[it.weeklyPartId] ?: Int.MAX_VALUE }, { it.slot }),
                )
                PersonTicketSheet(
                    fullName = orderedAssignments.first().fullName,
                    weekStart = weekPlan.weekStartDate,
                    weekEnd = sundayOf(weekPlan.weekStartDate),
                    assignments = orderedAssignments.map { assignment ->
                        val part = checkNotNull(partsById[assignment.weeklyPartId]) {
                            "Parte non trovata per assegnazione ${assignment.id.value}"
                        }
                        AssignmentTicketLine(
                            partLabel = partDisplayLabel(part),
                            roleLabel = slotRoleLabel(part, assignment.slot),
                        )
                    },
                )
            }
            .sortedBy { it.fullName.lowercase() }
    }

    private fun renderTicketImage(
        outputDir: Path,
        baseName: String,
        sheet: PersonTicketSheet,
    ): Path {
        val pdfPath = outputDir.resolve("$baseName-tmp.pdf")
        val pngPath = outputDir.resolve("$baseName.png")
        try {
            renderer.renderPersonSheetPdf(
                PdfAssignmentsRenderer.PersonSheet(
                    fullName = sheet.fullName,
                    weekStart = sheet.weekStart,
                    weekEnd = sheet.weekEnd,
                    assignments = sheet.assignments.map(::buildSheetAssignmentLabel),
                ),
                pdfPath,
            )
            pdfToPngRenderer(pdfPath, pngPath)
            logger.info("Immagine creata: {}", pngPath.toAbsolutePath())
            return pngPath
        } catch (error: Exception) {
            logger.error(
                "Generazione immagine fallita per {} (pdf={}, png={}): {}",
                sheet.fullName,
                pdfPath.toAbsolutePath(),
                pngPath.toAbsolutePath(),
                error.message,
                error,
            )
            throw IllegalStateException(
                "Errore generando immagine per ${sheet.fullName} (pdf=$pdfPath, png=$pngPath): ${error.message}",
                error,
            )
        } finally {
            runCatching { Files.deleteIfExists(pdfPath) }
                .onFailure { cleanupError ->
                    logger.warn(
                        "Cleanup PDF temporaneo non riuscito ({}): {}",
                        pdfPath.toAbsolutePath(),
                        cleanupError.message,
                    )
                }
        }
    }

    private fun ensureOutputDir(): Path {
        val outputDir = outputDirProvider()
        Files.createDirectories(outputDir)
        return outputDir
    }
}

internal fun cleanupProgramTicketExports(
    outputDir: Path,
    year: Int,
    month: Int,
    onFailure: ((Path, Throwable) -> Unit)? = null,
) {
    val prefix = "biglietto-${LocalDate.of(year, month, 1).format(monthImagePrefixFormatter)}-"
    Files.newDirectoryStream(outputDir, "$prefix*.png").use { paths ->
        for (path in paths) {
            runCatching { Files.deleteIfExists(path) }
                .onFailure { error -> onFailure?.invoke(path, error) }
        }
    }
}

internal fun buildWeeklyImageBaseName(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    fullName: String,
): String {
    val yearMonth = weekStart.format(weekImagePrefixFormatter)
    val startDay = weekStart.dayOfMonth.toString().padStart(2, '0')
    val endDay = weekEnd.dayOfMonth.toString().padStart(2, '0')
    return "${yearMonth}${startDay}-${endDay}_${sanitizeFileName(fullName)}"
}

internal fun buildProgramImageBaseName(
    year: Int,
    month: Int,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    fullName: String,
): String {
    val monthPrefix = LocalDate.of(year, month, 1).format(monthImagePrefixFormatter)
    return "biglietto-$monthPrefix-${weekStart.format(DateTimeFormatter.BASIC_ISO_DATE)}-${weekEnd.dayOfMonth.toString().padStart(2, '0')}-${sanitizeFileName(fullName)}"
}

private fun partDisplayLabel(part: WeeklyPart): String = part.snapshot?.label ?: part.partType.label

private fun slotRoleLabel(
    part: WeeklyPart,
    slot: Int,
): String = if (part.partType.peopleCount <= 1 || slot == 1) {
    "Studente"
} else {
    "Assistente"
}

private fun buildSheetAssignmentLabel(line: AssignmentTicketLine): String = "${line.partLabel} (${line.roleLabel})"

private fun sanitizeFileName(fullName: String): String = fullName
    .trim()
    .replace(Regex("\\s+"), "_")
    .replace(Regex("[^A-Za-z0-9_]+"), "")

private fun renderPdfToPngFile(pdfPath: Path, pngPath: Path) {
    Loader.loadPDF(pdfPath.toFile()).use { document ->
        val renderer = PDFRenderer(document)
        val image: BufferedImage = renderer.renderImageWithDPI(0, 200f)
        ImageIO.write(image, "png", pngPath.toFile())
    }
}

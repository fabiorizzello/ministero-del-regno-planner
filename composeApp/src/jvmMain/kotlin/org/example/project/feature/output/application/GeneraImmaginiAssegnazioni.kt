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
import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.example.project.feature.weeklyparts.domain.sundayOf

private val weekImagePrefixFormatter = DateTimeFormatter.ofPattern("yyyyMM")
private val monthImagePrefixFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

data class AssignmentTicketLine(
    val partLabel: String,
    val roleLabel: String?,
    val partNumber: Int,
)

data class AssignmentTicketImage(
    val fullName: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val imagePath: Path,
    val assignments: List<AssignmentTicketLine>,
)

data class PartAssignmentWarning(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val partLabel: String,
    val assignedCount: Int,
    val expectedCount: Int,
) {
    val isEmpty: Boolean get() = assignedCount == 0
    val isPartial: Boolean get() = assignedCount in 1 until expectedCount
}

data class TicketGenerationResult(
    val tickets: List<AssignmentTicketImage>,
    val warnings: List<PartAssignmentWarning>,
)

private data class PersonTicketSheet(
    val fullName: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val assignments: List<AssignmentTicketLine>,
    val primaryPartSortOrder: Int,
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

    suspend fun generateProgramTickets(programId: ProgramMonthId): TicketGenerationResult = withContext(dispatcher) {
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

        val activeWeeks = weeks.filter { it.status == WeekPlanStatus.ACTIVE }
        val weekAssignmentsByWeek = activeWeeks.map { week ->
            week to caricaAssegnazioni(week.weekStartDate)
        }

        val tickets = weekAssignmentsByWeek
            .flatMap { (week, weekAssignments) ->
                val completePartIds = completePartIds(week, weekAssignments)
                buildPersonTicketSheets(
                    weekPlan = week,
                    assignments = weekAssignments,
                    selectedPartIds = completePartIds,
                )
            }
            .sortedWith(compareBy({ it.weekStart }, { it.primaryPartSortOrder }, { it.fullName.lowercase() }))
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

        val warnings = weekAssignmentsByWeek.flatMap { (week, weekAssignments) ->
            buildPartWarnings(week, weekAssignments)
        }

        TicketGenerationResult(tickets = tickets, warnings = warnings)
    }

    private fun completePartIds(
        weekPlan: WeekPlan,
        assignments: List<AssignmentWithPerson>,
    ): Set<WeeklyPartId> {
        val assignedCountByPart = assignments.groupBy { it.weeklyPartId }.mapValues { it.value.size }
        return weekPlan.parts
            .filter { part -> (assignedCountByPart[part.id] ?: 0) >= part.partType.peopleCount }
            .mapTo(mutableSetOf()) { it.id }
    }

    private fun buildPartWarnings(
        weekPlan: WeekPlan,
        assignments: List<AssignmentWithPerson>,
    ): List<PartAssignmentWarning> {
        val assignedCountByPart = assignments.groupBy { it.weeklyPartId }.mapValues { it.value.size }
        return weekPlan.parts
            .sortedBy { it.sortOrder }
            .mapNotNull { part ->
                val assignedCount = assignedCountByPart[part.id] ?: 0
                val expectedCount = part.partType.peopleCount
                if (assignedCount < expectedCount) {
                    PartAssignmentWarning(
                        weekStart = weekPlan.weekStartDate,
                        weekEnd = sundayOf(weekPlan.weekStartDate),
                        partLabel = partDisplayLabel(part),
                        assignedCount = assignedCount,
                        expectedCount = expectedCount,
                    )
                } else null
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
            .filter { personAssignments -> personAssignments.any { it.slot == 1 } }
            .map { personAssignments ->
                val orderedAssignments = personAssignments.sortedWith(
                    compareBy({ partOrderMap[it.weeklyPartId] ?: Int.MAX_VALUE }, { it.slot }),
                )
                val firstPartSortOrder = partOrderMap[orderedAssignments.first().weeklyPartId] ?: Int.MAX_VALUE
                PersonTicketSheet(
                    fullName = orderedAssignments.first().fullName,
                    weekStart = weekPlan.weekStartDate,
                    weekEnd = sundayOf(weekPlan.weekStartDate),
                    primaryPartSortOrder = firstPartSortOrder,
                    assignments = orderedAssignments.map { assignment ->
                        val part = checkNotNull(partsById[assignment.weeklyPartId]) {
                            "Parte non trovata per assegnazione ${assignment.id.value}"
                        }
                        AssignmentTicketLine(
                            partLabel = partDisplayLabel(part),
                            roleLabel = slotRoleLabel(part, assignment.slot),
                            partNumber = part.sortOrder + PART_DISPLAY_NUMBER_OFFSET,
                        )
                    },
                )
            }
            .sortedWith(compareBy({ it.primaryPartSortOrder }, { it.fullName.lowercase() }))
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
            logger.info { "Immagine creata: ${pngPath.toAbsolutePath()}" }
            return pngPath
        } catch (error: Exception) {
            logger.error(error) {
                "Generazione immagine fallita per ${sheet.fullName} (pdf=${pdfPath.toAbsolutePath()}, png=${pngPath.toAbsolutePath()}): ${error.message}"
            }
            throw IllegalStateException(
                "Errore generando immagine per ${sheet.fullName} (pdf=$pdfPath, png=$pngPath): ${error.message}",
                error,
            )
        } finally {
            runCatching { Files.deleteIfExists(pdfPath) }
                .onFailure { cleanupError ->
                    logger.warn { "Cleanup PDF temporaneo non riuscito (${pdfPath.toAbsolutePath()}): ${cleanupError.message}" }
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
): String? = if (part.partType.peopleCount > 1 && slot > 1) "Assistente" else null

private fun buildSheetAssignmentLabel(line: AssignmentTicketLine): String =
    "${line.partNumber}. ${line.partLabel}${line.roleLabel?.let { " ($it)" } ?: ""}"

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

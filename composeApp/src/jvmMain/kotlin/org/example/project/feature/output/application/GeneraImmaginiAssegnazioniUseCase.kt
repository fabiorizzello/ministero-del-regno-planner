package org.example.project.feature.output.application

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.either
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.project.core.config.AppRuntime
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.output.domain.completePartIds
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.domain.sundayOf

private val weekImagePrefixFormatter = DateTimeFormatter.ofPattern("yyyyMM")
private val invalidTicketNameChars = Regex("""[<>:"/\\|?*\x00-\x1F]""")
private val reservedTicketNamePrefix = Regex("""(?i)^(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]) *\.""")
private const val MAX_TICKET_PERSON_NAME_LENGTH = 120

data class AssignmentTicketLine(
    val partLabel: String,
    val roleLabel: String?,
    val partNumber: Int,
)

data class AssignmentTicketImage(
    val fullName: String,
    val assistantName: String?,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val imagePath: Path,
    val assignments: List<AssignmentTicketLine>,
    val weeklyPartId: WeeklyPartId,
    val weekPlanId: WeekPlanId,
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
    val exportFolder: Path? = null,
)

private data class AssignmentSlipWithOrder(
    val slip: AssignmentSlipData,
    val sortOrder: Int,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val weeklyPartId: WeeklyPartId,
    val weekPlanId: WeekPlanId,
)

class GeneraImmaginiAssegnazioniUseCase(
    private val programStore: ProgramStore,
    private val weekPlanQueries: WeekPlanQueries,
    private val caricaAssegnazioni: CaricaAssegnazioniUseCase,
    private val assignmentRepository: AssignmentRepository,
    private val renderer: AssignmentsRenderer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val outputDirProvider: () -> Path = { AppRuntime.paths().exportsDir.resolve("assegnazioni") },
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(
        weekStartDate: LocalDate,
        selectedPartIds: Set<WeeklyPartId>,
    ): Either<DomainError, List<Path>> = withContext(dispatcher) {
        either {
            val weekPlan = weekPlanQueries.findByDate(weekStartDate)
                ?: raise(DomainError.NotFound("Settimana per $weekStartDate"))
            val assignments = caricaAssegnazioni(weekStartDate)
            val slips = buildAssignmentSlips(
                weekPlan = weekPlan,
                assignments = assignments,
                selectedPartIds = selectedPartIds,
            )
            val outputDir = ensureOutputDir().bind()

            slips.map { slipWithOrder ->
                val baseName = buildWeeklySlipBaseName(
                    weekStart = slipWithOrder.weekStart,
                    weekEnd = slipWithOrder.weekEnd,
                    partNumber = slipWithOrder.slip.partNumber,
                    studentName = slipWithOrder.slip.studentName,
                )
                renderSlipImage(
                    outputDir = outputDir,
                    baseName = baseName,
                    slip = slipWithOrder.slip,
                ).bind()
            }
        }
    }

    suspend fun generateProgramTickets(
        programId: ProgramMonthId,
        currentWeekStart: LocalDate? = null,
    ): Either<DomainError, TicketGenerationResult> = withContext(dispatcher) {
        either {
            val program = programStore.findById(programId)
                ?: raise(DomainError.NotFound("Programma"))
            val weeks = weekPlanQueries.listByProgram(programId)
                .sortedBy { it.weekStartDate }
            val outputDir = ensureOutputDir().bind()

            val activeWeeks = weeks.filter { it.status == WeekPlanStatus.ACTIVE }
            val assignmentsByWeekPlan = assignmentRepository.listByWeekPlanIds(
                activeWeeks.map { it.id }.toSet()
            )
            val weekAssignmentsByWeek = activeWeeks.map { week ->
                week to (assignmentsByWeekPlan[week.id] ?: emptyList())
            }

            val slips = weekAssignmentsByWeek
                .flatMap { (week, weekAssignments) ->
                    val completeIds = completePartIds(week.parts, weekAssignments)
                    if (completeIds.isEmpty()) return@flatMap emptyList()
                    buildAssignmentSlips(
                        weekPlan = week,
                        assignments = weekAssignments,
                        selectedPartIds = completeIds,
                    )
                }
                .sortedWith(compareBy({ it.weekStart }, { it.sortOrder }, { it.slip.studentName.lowercase() }, { it.weeklyPartId.value }))

            val warnings = weekAssignmentsByWeek.flatMap { (week, weekAssignments) ->
                buildPartWarnings(week, weekAssignments)
            }

            if (slips.isEmpty()) return@either TicketGenerationResult(emptyList(), warnings)

            val ticketDir = Either.catch {
                val monthDir = Files.createDirectories(outputDir.resolve("biglietti-${program.yearMonth}"))
                Files.createTempDirectory(monthDir, "invio-")
            }.mapLeft { DomainError.Validation("Impossibile creare la cartella biglietti: ${it.message}") }.bind()
            TicketGenerationResult(
                tickets = renderProgramTickets(ticketDir, slips, currentWeekStart).bind(),
                warnings = warnings,
                exportFolder = ticketDir,
            )
        }
    }

    private fun renderProgramTickets(
        outputDir: Path,
        slips: List<AssignmentSlipWithOrder>,
        currentWeekStart: LocalDate?,
    ): Either<DomainError, List<AssignmentTicketImage>> = either {
        val usedNames = mutableSetOf<String>()
        val generatedPaths = mutableListOf<Path>()
        val historicalDir = outputDir.resolve("Settimane passate")
        var completed = false
        try {
            if (currentWeekStart != null && slips.any { it.weekStart < currentWeekStart }) {
                Either.catch { Files.createDirectory(historicalDir) }
                    .mapLeft { DomainError.Validation("Impossibile creare la cartella delle settimane passate: ${it.message}") }
                    .bind()
            }
            val tickets = slips.map { entry ->
                val slip = entry.slip
                val imageDir = if (currentWeekStart != null && entry.weekStart < currentWeekStart) historicalDir else outputDir
                val baseName = "${ticketFileNameFor(slip.studentName)} - ${entry.weekStart}"
                var uniqueName = baseName
                var suffix = 2
                while (!usedNames.add(uniqueName.uppercase())) {
                    uniqueName = "$baseName ($suffix)"
                    suffix++
                }
                generatedPaths.add(imageDir.resolve("$uniqueName.png"))
                val imagePath = renderSlipImage(imageDir, uniqueName, slip).bind()
                AssignmentTicketImage(
                    fullName = slip.studentName,
                    assistantName = slip.assistantName,
                    weekStart = entry.weekStart,
                    weekEnd = entry.weekEnd,
                    imagePath = imagePath,
                    assignments = listOf(AssignmentTicketLine(slip.partLabel, null, slip.partNumber)),
                    weeklyPartId = entry.weeklyPartId,
                    weekPlanId = entry.weekPlanId,
                )
            }
            completed = true
            tickets
        } finally {
            if (!completed) {
                (generatedPaths + listOf(historicalDir, outputDir)).forEach { path ->
                    runCatching { Files.deleteIfExists(path) }
                        .onFailure { error ->
                            logger.warn { "Cleanup esportazione non riuscito (${path.fileName}): ${error.message}" }
                        }
                }
            }
        }
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

    private fun buildAssignmentSlips(
        weekPlan: WeekPlan,
        assignments: List<AssignmentWithPerson>,
        selectedPartIds: Set<WeeklyPartId>,
    ): List<AssignmentSlipWithOrder> {
        if (weekPlan.status == WeekPlanStatus.SKIPPED) return emptyList()
        val selectedParts = weekPlan.parts
            .filter { selectedPartIds.isEmpty() || selectedPartIds.contains(it.id) }
            .sortedBy { it.sortOrder }
        if (selectedParts.isEmpty()) return emptyList()

        val selectedPartIdsSet = selectedParts.mapTo(mutableSetOf()) { it.id }
        val assignmentsByPart = assignments
            .filter { it.weeklyPartId in selectedPartIdsSet }
            .groupBy { it.weeklyPartId }

        val weekEnd = sundayOf(weekPlan.weekStartDate)
        return selectedParts.mapNotNull { part ->
            val partAssignments = assignmentsByPart[part.id] ?: return@mapNotNull null
            val student = partAssignments.firstOrNull { it.slot == 1 } ?: return@mapNotNull null
            val assistant = partAssignments.firstOrNull { it.slot == 2 }
            AssignmentSlipWithOrder(
                slip = AssignmentSlipData(
                    studentName = student.fullName,
                    assistantName = assistant?.fullName,
                    weekStart = weekPlan.weekStartDate,
                    partNumber = part.sortOrder + PART_DISPLAY_NUMBER_OFFSET,
                    partLabel = partDisplayLabel(part),
                ),
                sortOrder = part.sortOrder,
                weekStart = weekPlan.weekStartDate,
                weekEnd = weekEnd,
                weeklyPartId = part.id,
                weekPlanId = weekPlan.id,
            )
        }
    }

    private fun renderSlipImage(
        outputDir: Path,
        baseName: String,
        slip: AssignmentSlipData,
    ): Either<DomainError, Path> {
        val pdfPath = outputDir.resolve("$baseName-tmp.pdf")
        val pngPath = outputDir.resolve("$baseName.png")
        return try {
            renderer.renderAssignmentSlipPdf(slip, pdfPath)
            renderer.renderPdfToImage(pdfPath, pngPath)
            check(Files.isRegularFile(pngPath)) { "Il file PNG non è stato creato" }
            logger.info { "Immagine creata: ${pngPath.toAbsolutePath()}" }
            pngPath.right()
        } catch (error: Exception) {
            logger.error(error) {
                "Generazione immagine fallita per ${slip.studentName} (pdf=${pdfPath.toAbsolutePath()}, png=${pngPath.toAbsolutePath()}): ${error.message}"
            }
            DomainError.Validation("Errore generando immagine per ${slip.studentName}: ${error.message}").left()
        } finally {
            runCatching { Files.deleteIfExists(pdfPath) }
                .onFailure { cleanupError ->
                    logger.warn { "Cleanup PDF temporaneo non riuscito (${pdfPath.toAbsolutePath()}): ${cleanupError.message}" }
                }
        }
    }

    private fun ensureOutputDir(): Either<DomainError, Path> {
        val outputDir = outputDirProvider()
        return Either.catch { Files.createDirectories(outputDir) }
            .mapLeft { DomainError.Validation("Impossibile creare directory output: ${it.message}") }
            .map { outputDir }
    }
}

internal fun buildWeeklySlipBaseName(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    partNumber: Int,
    studentName: String,
): String {
    val yearMonth = weekStart.format(weekImagePrefixFormatter)
    val startDay = weekStart.dayOfMonth.toString().padStart(2, '0')
    val endDay = weekEnd.dayOfMonth.toString().padStart(2, '0')
    return "${yearMonth}${startDay}-${endDay}_p${partNumber}_${sanitizeFileName(studentName)}"
}

private fun ticketFileNameFor(fullName: String): String {
    val name = Normalizer.normalize(fullName, Normalizer.Form.NFC)
        .replace(invalidTicketNameChars, "-")
        .take(MAX_TICKET_PERSON_NAME_LENGTH)
        .trimEnd { it.isHighSurrogate() }
        .trim { it == '.' || it.isWhitespace() }
        .ifBlank { "Studente" }
    return if (reservedTicketNamePrefix.containsMatchIn(name)) "_$name" else name
}

private fun partDisplayLabel(part: WeeklyPart): String = part.snapshot?.label ?: part.partType.label

private fun sanitizeFileName(fullName: String): String = fullName
    .trim()
    .replace(Regex("\\s+"), "_")
    .replace(Regex("[^A-Za-z0-9_]+"), "")

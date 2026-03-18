package org.example.project.feature.output.application

import io.mockk.coEvery
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.example.project.core.domain.DomainError
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.person
import org.example.project.feature.output.infrastructure.PdfProgramRenderer
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StampaProgrammaUseCaseTest {

    @Test
    fun `active week status is mapped to user friendly label`() {
        assertEquals("Attiva", weekPlanStatusLabel(WeekPlanStatus.ACTIVE))
    }

    @Test
    fun `skipped week status is mapped to user friendly label`() {
        assertEquals("Saltata", weekPlanStatusLabel(WeekPlanStatus.SKIPPED))
    }

    @Test
    fun `section builder creates program cards with assigned and unassigned slots`() {
        val partWithTwoSlots = WeeklyPart(
            id = WeeklyPartId("part-1"),
            partType = partType(id = "pt-1", label = "Visita iniziale", peopleCount = 2),
            sortOrder = 0,
        )
        val partWithOneSlot = WeeklyPart(
            id = WeeklyPartId("part-2"),
            partType = partType(id = "pt-2", label = "Discorso", peopleCount = 1),
            sortOrder = 1,
        )
        val week = WeekPlan(
            id = WeekPlanId("week-1"),
            weekStartDate = LocalDate.of(2026, 3, 2),
            parts = listOf(partWithTwoSlots, partWithOneSlot),
        )
        val assignment = AssignmentWithPerson(
            id = AssignmentId("assignment-1"),
            weeklyPartId = partWithTwoSlots.id,
            personId = ProclamatoreId("p-1"),
            slot = 1,
            proclamatore = person(
                id = "p-1",
                nome = "Mario",
                cognome = "Rossi",
                sesso = Sesso.M,
            ),
        )

        val section = buildProgramWeekPrintSection(
            week = week,
            assignments = listOf(assignment),
        )

        assertEquals(LocalDate.of(2026, 3, 8), section.weekEndDate)
        assertEquals("Attiva", section.statusLabel)
        assertNull(section.emptyStateLabel)
        assertEquals(
            listOf(
                ProgramWeekPrintCard(
                    displayNumber = 3,
                    partLabel = "Visita iniziale",
                    status = ProgramWeekPrintCardStatus.PARTIAL,
                    statusLabel = "Parziale",
                    slots = listOf(
                        ProgramWeekPrintSlot(
                            roleLabel = "Studente",
                            assignedTo = "Mario Rossi",
                            isAssigned = true,
                        ),
                        ProgramWeekPrintSlot(
                            roleLabel = "Assistente",
                            assignedTo = "Non assegnato",
                            isAssigned = false,
                        ),
                    ),
                ),
                ProgramWeekPrintCard(
                    displayNumber = 4,
                    partLabel = "Discorso",
                    status = ProgramWeekPrintCardStatus.EMPTY,
                    statusLabel = "Vuota",
                    slots = listOf(
                        ProgramWeekPrintSlot(
                            roleLabel = "Studente",
                            assignedTo = "Non assegnato",
                            isAssigned = false,
                        ),
                    ),
                ),
            ),
            section.cards,
        )
    }

    @Test
    fun `section builder marks skipped empty week with dedicated placeholder`() {
        val week = WeekPlan(
            id = WeekPlanId("week-skipped"),
            weekStartDate = LocalDate.of(2026, 3, 9),
            parts = emptyList(),
            status = WeekPlanStatus.SKIPPED,
        )

        val section = buildProgramWeekPrintSection(
            week = week,
            assignments = emptyList(),
        )

        assertEquals(emptyList(), section.cards)
        assertEquals("Settimana saltata", section.emptyStateLabel)
    }

    @Test
    fun `monthly program file name uses padded month`() {
        assertEquals("programma-2026-03.pdf", buildMonthlyProgramFileName(2026, 3))
    }

    @Test
    fun `monthly program output path points to export folder`() {
        val outputDir = java.nio.file.Path.of("C:/app/exports/programmi")
        val outputPath = outputDir.resolve(buildMonthlyProgramFileName(2026, 3))

        assertEquals(java.nio.file.Path.of("C:/app/exports/programmi/programma-2026-03.pdf"), outputPath)
        assertEquals("programma-2026-03.pdf", outputPath.fileName.toString())
    }

    @Test
    fun `cleanup removes old program pdf files and keeps current export`() {
        val outputDir = Files.createTempDirectory("program-export-cleanup-test")
        val keepFileName = buildMonthlyProgramFileName(2026, 3)
        val oldPdf = Files.createFile(outputDir.resolve("programma-2026-02.pdf"))
        val keepPdf = Files.createFile(outputDir.resolve(keepFileName))
        val otherFile = Files.createFile(outputDir.resolve("note.txt"))

        try {
            cleanupMonthlyProgramExports(outputDir, keepFileName)

            assertFalse(Files.exists(oldPdf))
            assertTrue(Files.exists(keepPdf))
            assertTrue(Files.exists(otherFile))
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `invoke ritorna Validation se directory output non puo essere creata`() = runTest {
        val blockerFile = Files.createTempFile("blocker", ".txt")
        val impossibleDir = blockerFile.resolve("subdir")
        val programId = ProgramMonthId("program-2026-03")
        val programStore = mockk<ProgramStore>()
        val weekPlanQueries = mockk<WeekPlanQueries>()
        val assignmentRepository = mockk<AssignmentRepository>()
        coEvery { programStore.findById(programId) } returns ProgramMonth(
            id = programId, year = 2026, month = 3,
            startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2026, 3, 31),
            templateAppliedAt = null, createdAt = LocalDateTime.of(2026, 2, 20, 9, 0),
        )
        coEvery { weekPlanQueries.listByProgram(programId) } returns emptyList()
        coEvery { assignmentRepository.listByWeekPlanIds(emptySet()) } returns emptyMap()

        val useCase = StampaProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = weekPlanQueries,
            assignmentRepository = assignmentRepository,
            renderer = mockk(),
            fileOpener = mockk(),
            programExportDirProvider = { impossibleDir },
        )

        val result = useCase(programId)

        assertIs<arrow.core.Either.Left<DomainError.Validation>>(result)
        Unit
    }

    private fun partType(
        id: String,
        label: String,
        peopleCount: Int,
    ): PartType = PartType(
        id = PartTypeId(id),
        code = id.uppercase(),
        label = label,
        peopleCount = peopleCount,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )
}

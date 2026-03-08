package org.example.project.feature.output.application

import io.mockk.coEvery
import io.mockk.mockk
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.example.project.feature.assignments.application.CaricaAssegnazioniUseCase
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.output.infrastructure.PdfAssignmentsRenderer
import org.example.project.feature.people.domain.Proclamatore
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
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneraImmaginiAssegnazioniTest {

    @Test
    fun `generateProgramTickets crea png del mese, pulisce i vecchi e mantiene i metadati ordinati`() = runTest {
        val tempDir = Files.createTempDirectory("ticket-exports")
        val programId = ProgramMonthId("program-2026-03")
        val programStore = mockk<ProgramStore>()
        val weekPlanQueries = mockk<WeekPlanQueries>()
        val caricaAssegnazioni = mockk<CaricaAssegnazioniUseCase>()
        val weekOne = weekPlan(
            id = "week-1",
            monday = LocalDate.of(2026, 3, 2),
            status = WeekPlanStatus.ACTIVE,
            parts = listOf(singleSlotPart("p1", "Lettura"), dualSlotPart("p2", "Visita")),
        )
        val skippedWeek = weekPlan(
            id = "week-2",
            monday = LocalDate.of(2026, 3, 9),
            status = WeekPlanStatus.SKIPPED,
            parts = listOf(singleSlotPart("p3", "Commento")),
        )
        val weekThree = weekPlan(
            id = "week-3",
            monday = LocalDate.of(2026, 3, 16),
            status = WeekPlanStatus.ACTIVE,
            parts = listOf(dualSlotPart("p4", "Studio biblico")),
        )

        Files.writeString(tempDir.resolve("biglietto-2026-03-legacy.png"), "old")
        Files.writeString(tempDir.resolve("biglietto-2026-04-legacy.png"), "keep")

        coEvery { programStore.findById(programId) } returns ProgramMonth(
            id = programId,
            year = 2026,
            month = 3,
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 3, 31),
            templateAppliedAt = null,
            createdAt = LocalDateTime.of(2026, 2, 20, 9, 0),
        )
        coEvery { weekPlanQueries.listByProgram(programId) } returns listOf(skippedWeek, weekThree, weekOne)
        coEvery { caricaAssegnazioni(weekOne.weekStartDate) } returns listOf(
            assignment("a1", weekOne.parts[0].id, "p-z", "Zeno", "Alfa", slot = 1),
        )
        coEvery { caricaAssegnazioni(skippedWeek.weekStartDate) } returns emptyList()
        coEvery { caricaAssegnazioni(weekThree.weekStartDate) } returns listOf(
            assignment("a2", weekThree.parts[0].id, "p-a", "Anna", "Bianchi", slot = 1),
            assignment("a3", weekThree.parts[0].id, "p-m", "Mario", "Rossi", slot = 2),
        )

        val useCase = GeneraImmaginiAssegnazioni(
            programStore = programStore,
            weekPlanQueries = weekPlanQueries,
            caricaAssegnazioni = caricaAssegnazioni,
            renderer = PdfAssignmentsRenderer(),
            outputDirProvider = { tempDir },
            pdfToPngRenderer = { _, pngPath ->
                ImageIO.write(BufferedImage(12, 18, BufferedImage.TYPE_INT_RGB), "png", pngPath.toFile())
            },
        )

        val result = useCase.generateProgramTickets(programId)

        assertEquals(listOf("Zeno Alfa", "Anna Bianchi", "Mario Rossi"), result.map { it.fullName })
        assertEquals("Studente", result.first().assignments.single().roleLabel)
        assertEquals("Assistente", result.last().assignments.single().roleLabel)
        assertTrue(result.all { Files.exists(it.imagePath) })
        assertTrue(result.all { it.imagePath.fileName.toString().startsWith("biglietto-2026-03-") })
        assertFalse(Files.exists(tempDir.resolve("biglietto-2026-03-legacy.png")))
        assertTrue(Files.exists(tempDir.resolve("biglietto-2026-04-legacy.png")))
    }

    private fun weekPlan(
        id: String,
        monday: LocalDate,
        status: WeekPlanStatus,
        parts: List<WeeklyPart>,
    ) = WeekPlan(
        id = WeekPlanId(id),
        weekStartDate = monday,
        parts = parts,
        programId = ProgramMonthId("program-2026-03"),
        status = status,
    )

    private fun singleSlotPart(
        id: String,
        label: String,
    ) = WeeklyPart(
        id = WeeklyPartId(id),
        partType = PartType(
            id = PartTypeId("pt-$id"),
            code = "PT-$id",
            label = label,
            peopleCount = 1,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        ),
        sortOrder = 0,
    )

    private fun dualSlotPart(
        id: String,
        label: String,
    ) = WeeklyPart(
        id = WeeklyPartId(id),
        partType = PartType(
            id = PartTypeId("pt-$id"),
            code = "PT-$id",
            label = label,
            peopleCount = 2,
            sexRule = SexRule.STESSO_SESSO,
            fixed = false,
            sortOrder = 0,
        ),
        sortOrder = 0,
    )

    private fun assignment(
        assignmentId: String,
        partId: WeeklyPartId,
        personId: String,
        nome: String,
        cognome: String,
        slot: Int,
    ) = AssignmentWithPerson(
        id = AssignmentId(assignmentId),
        weeklyPartId = partId,
        personId = ProclamatoreId(personId),
        slot = slot,
        proclamatore = Proclamatore(
            id = ProclamatoreId(personId),
            nome = nome,
            cognome = cognome,
            sesso = Sesso.M,
        ),
    )
}

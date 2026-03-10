package org.example.project.feature.output.infrastructure

import java.nio.file.Files
import java.time.LocalDate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfProgramRendererTest {

    @Test
    fun `monthly program pdf is rendered as a single page grid of cards`() {
        val renderer = PdfProgramRenderer()
        val tempDir = Files.createTempDirectory("monthly-program-pdf-test")
        val outputPath = tempDir.resolve("programma-2026-03.pdf")

        try {
            renderer.renderMonthlyProgramPdf(
                title = "Programma marzo 2026",
                sections = buildSections(),
                outputPath = outputPath,
            )

            Loader.loadPDF(outputPath.toFile()).use { document ->
                val text = PDFTextStripper().getText(document)

                assertEquals(1, document.numberOfPages)
                assertTrue(text.contains("Programma marzo 2026"))
                assertTrue(text.contains("Settimana"))
                assertTrue(text.contains("marzo"))
                assertTrue(text.contains("Visita iniziale"))
                assertTrue(text.contains("Mario Rossi"))
                assertTrue(text.contains("Studente"))
                assertTrue(text.contains("Non assegnato"))
                assertTrue(text.contains("Settimana saltata"))
            }
        } finally {
            outputPath.toFile().delete()
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun buildSections(): List<ProgramWeekPrintSection> =
        listOf(
            section(
                weekStart = LocalDate.of(2026, 3, 2),
                cards = listOf(
                    card(
                        displayNumber = 3,
                        partLabel = "Visita iniziale",
                        status = ProgramWeekPrintCardStatus.PARTIAL,
                        slots = listOf(
                            slot("Studente", "Mario Rossi", true),
                            slot("Assistente", "Non assegnato", false),
                        ),
                    ),
                    card(
                        displayNumber = 4,
                        partLabel = "Dimostrazione",
                        status = ProgramWeekPrintCardStatus.ASSIGNED,
                        slots = listOf(slot("Studente", "Luca Bianchi", true)),
                    ),
                    card(
                        displayNumber = 5,
                        partLabel = "Ritorno",
                        status = ProgramWeekPrintCardStatus.EMPTY,
                        slots = listOf(slot("Studente", "Non assegnato", false)),
                    ),
                    card(
                        displayNumber = 6,
                        partLabel = "Corso biblico",
                        status = ProgramWeekPrintCardStatus.ASSIGNED,
                        slots = listOf(slot("Studente", "Sara Neri", true)),
                    ),
                ),
            ),
            section(
                weekStart = LocalDate.of(2026, 3, 9),
                cards = listOf(
                    card(
                        displayNumber = 3,
                        partLabel = "Prima visita",
                        status = ProgramWeekPrintCardStatus.PARTIAL,
                        slots = listOf(
                            slot("Studente", "Anna Costa", true),
                            slot("Assistente", "Non assegnato", false),
                        ),
                    ),
                    card(
                        displayNumber = 4,
                        partLabel = "Discorso",
                        status = ProgramWeekPrintCardStatus.ASSIGNED,
                        slots = listOf(slot("Studente", "Marco Gallo", true)),
                    ),
                    card(
                        displayNumber = 5,
                        partLabel = "Studio",
                        status = ProgramWeekPrintCardStatus.ASSIGNED,
                        slots = listOf(slot("Studente", "Giulia Serra", true)),
                    ),
                ),
            ),
            ProgramWeekPrintSection(
                weekStartDate = LocalDate.of(2026, 3, 23),
                weekEndDate = LocalDate.of(2026, 3, 29),
                statusLabel = "Saltata",
                cards = emptyList(),
                emptyStateLabel = "Settimana saltata",
            ),
            section(
                weekStart = LocalDate.of(2026, 3, 30),
                cards = listOf(
                    card(
                        displayNumber = 3,
                        partLabel = "Prima visita",
                        status = ProgramWeekPrintCardStatus.ASSIGNED,
                        slots = listOf(
                            slot("Studente", "Davide Neri", true),
                            slot("Assistente", "Silvia Moro", true),
                        ),
                    ),
                    card(
                        displayNumber = 4,
                        partLabel = "Dimostrazione",
                        status = ProgramWeekPrintCardStatus.ASSIGNED,
                        slots = listOf(slot("Studente", "Matteo Lodi", true)),
                    ),
                ),
            ),
        )

    private fun section(
        weekStart: LocalDate,
        cards: List<ProgramWeekPrintCard>,
    ): ProgramWeekPrintSection = ProgramWeekPrintSection(
        weekStartDate = weekStart,
        weekEndDate = weekStart.plusDays(6),
        statusLabel = "Attiva",
        cards = cards,
    )

    private fun card(
        displayNumber: Int,
        partLabel: String,
        status: ProgramWeekPrintCardStatus,
        slots: List<ProgramWeekPrintSlot>,
    ): ProgramWeekPrintCard = ProgramWeekPrintCard(
        displayNumber = displayNumber,
        partLabel = partLabel,
        status = status,
        statusLabel = when (status) {
            ProgramWeekPrintCardStatus.EMPTY -> "Vuota"
            ProgramWeekPrintCardStatus.PARTIAL -> "Parziale"
            ProgramWeekPrintCardStatus.ASSIGNED -> "Assegnata"
        },
        slots = slots,
    )

    private fun slot(
        roleLabel: String?,
        assignedTo: String,
        isAssigned: Boolean,
    ): ProgramWeekPrintSlot = ProgramWeekPrintSlot(
        roleLabel = roleLabel,
        assignedTo = assignedTo,
        isAssigned = isAssigned,
    )
}

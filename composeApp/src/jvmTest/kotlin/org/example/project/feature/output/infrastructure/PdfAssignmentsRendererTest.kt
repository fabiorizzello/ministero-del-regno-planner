package org.example.project.feature.output.infrastructure

import java.nio.file.Files
import java.time.LocalDate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfAssignmentsRendererTest {

    private val renderer = PdfAssignmentsRenderer()
    private val weekStart = LocalDate.of(2026, 3, 2)
    private val weekEnd = LocalDate.of(2026, 3, 8)

    @Test
    fun `renderPersonSheetPdf crea pdf con nome persona date e assegnazioni`() {
        val tempDir = Files.createTempDirectory("person-sheet-pdf-test")
        val outputPath = tempDir.resolve("biglietto.pdf")

        try {
            renderer.renderPersonSheetPdf(
                sheet = PdfAssignmentsRenderer.PersonSheet(
                    fullName = "Mario Rossi",
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    assignments = listOf(
                        "3. Visita iniziale",
                        "4. Dimostrazione (Assistente)",
                    ),
                ),
                outputPath = outputPath,
            )

            Loader.loadPDF(outputPath.toFile()).use { document ->
                val text = PDFTextStripper().getText(document)

                assertEquals(1, document.numberOfPages)
                assertTrue(text.contains("Mario Rossi"))
                assertTrue(text.contains("marzo 2026"))
                assertTrue(text.contains("Visita iniziale"))
                assertTrue(text.contains("Dimostrazione"))
                assertTrue(text.contains("Assistente"))
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `renderPersonSheetPdf crea le directory di output se non esistono`() {
        val tempDir = Files.createTempDirectory("person-sheet-mkdir-test")
        val outputPath = tempDir.resolve("sub/nested/biglietto.pdf")

        try {
            renderer.renderPersonSheetPdf(
                sheet = PdfAssignmentsRenderer.PersonSheet(
                    fullName = "Anna Bianchi",
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    assignments = listOf("3. Studio biblico"),
                ),
                outputPath = outputPath,
            )

            assertTrue(Files.exists(outputPath))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `renderPersonSheetPdf gestisce lista assegnazioni vuota senza eccezione`() {
        val tempDir = Files.createTempDirectory("person-sheet-empty-test")
        val outputPath = tempDir.resolve("biglietto-vuoto.pdf")

        try {
            renderer.renderPersonSheetPdf(
                sheet = PdfAssignmentsRenderer.PersonSheet(
                    fullName = "Zeno Alfa",
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    assignments = emptyList(),
                ),
                outputPath = outputPath,
            )

            Loader.loadPDF(outputPath.toFile()).use { document ->
                val text = PDFTextStripper().getText(document)
                assertEquals(1, document.numberOfPages)
                assertTrue(text.contains("Zeno Alfa"))
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `renderWeeklyAssignmentsPdf crea pdf con label parti e assegnati`() {
        val tempDir = Files.createTempDirectory("weekly-pdf-test")
        val outputPath = tempDir.resolve("assegnazioni.pdf")

        try {
            renderer.renderWeeklyAssignmentsPdf(
                weekStart = weekStart,
                weekEnd = weekEnd,
                parts = listOf(
                    PdfAssignmentsRenderer.RenderedPart(
                        label = "Visita iniziale",
                        assignments = listOf("Studente: Mario Rossi", "Assistente: Anna Bianchi"),
                    ),
                    PdfAssignmentsRenderer.RenderedPart(
                        label = "Studio biblico",
                        assignments = listOf("Studente: Luca Verdi"),
                    ),
                ),
                outputPath = outputPath,
            )

            Loader.loadPDF(outputPath.toFile()).use { document ->
                val text = PDFTextStripper().getText(document)

                assertTrue(document.numberOfPages >= 1)
                assertTrue(text.contains("Visita iniziale"))
                assertTrue(text.contains("Mario Rossi"))
                assertTrue(text.contains("Studio biblico"))
                assertTrue(text.contains("Luca Verdi"))
                assertTrue(text.contains("marzo 2026"))
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `renderWeeklyAssignmentsPdf produce piu pagine quando le parti superano la capacita`() {
        val tempDir = Files.createTempDirectory("weekly-pdf-multipage-test")
        val outputPath = tempDir.resolve("assegnazioni-multipage.pdf")

        // perPage ≈ (841.89 - 80) / 120 = 6 → 8 parti generano 2 pagine
        val parts = (1..8).map { i ->
            PdfAssignmentsRenderer.RenderedPart(
                label = "Parte $i",
                assignments = listOf("Studente: Persona $i"),
            )
        }

        try {
            renderer.renderWeeklyAssignmentsPdf(
                weekStart = weekStart,
                weekEnd = weekEnd,
                parts = parts,
                outputPath = outputPath,
            )

            Loader.loadPDF(outputPath.toFile()).use { document ->
                assertTrue(document.numberOfPages >= 2)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `renderWeeklyAssignmentsPdf crea le directory di output se non esistono`() {
        val tempDir = Files.createTempDirectory("weekly-pdf-mkdir-test")
        val outputPath = tempDir.resolve("sub/assegnazioni.pdf")

        try {
            renderer.renderWeeklyAssignmentsPdf(
                weekStart = weekStart,
                weekEnd = weekEnd,
                parts = listOf(
                    PdfAssignmentsRenderer.RenderedPart(
                        label = "Lettura",
                        assignments = listOf("Mario Rossi"),
                    ),
                ),
                outputPath = outputPath,
            )

            assertTrue(Files.exists(outputPath))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}

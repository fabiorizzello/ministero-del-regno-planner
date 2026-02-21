package org.example.project.feature.output.infrastructure

import java.awt.Color
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.slf4j.LoggerFactory

class PdfAssignmentsRenderer {
    private val logger = LoggerFactory.getLogger(PdfAssignmentsRenderer::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)

    data class RenderedPart(
        val label: String,
        val assignments: List<String>,
    )

    data class PersonSheet(
        val fullName: String,
        val weekStart: LocalDate,
        val weekEnd: LocalDate,
        val assignments: List<String>,
    )

    fun renderWeeklyAssignmentsPdf(
        weekStart: LocalDate,
        weekEnd: LocalDate,
        parts: List<RenderedPart>,
        outputPath: Path,
    ) {
        logger.info("Generazione PDF assegnazioni: {}", outputPath.toAbsolutePath())
        PDDocument().use { document ->
            val pageSize = PDRectangle.A4
            val margin = 40f
            val fogliettoHeight = 120f
            val usableHeight = pageSize.height - (margin * 2)
            val perPage = (usableHeight / fogliettoHeight).toInt().coerceAtLeast(1)

            var index = 0
            while (index < parts.size) {
                val page = PDPage(pageSize)
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.setNonStrokingColor(Color.BLACK)
                    content.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                    content.beginText()
                    content.newLineAtOffset(margin, pageSize.height - margin)
                    content.showText("Assegnazioni ${weekStart.format(dateFormatter)} - ${weekEnd.format(dateFormatter)}")
                    content.endText()

                    var y = pageSize.height - margin - 30f
                    for (i in 0 until perPage) {
                        if (index >= parts.size) break
                        val part = parts[index]
                        drawPartBox(content, margin, y, pageSize.width - margin * 2, fogliettoHeight - 10f, part)
                        y -= fogliettoHeight
                        index += 1
                    }
                }
            }

            outputPath.toFile().parentFile?.mkdirs()
            document.save(outputPath.toFile())
        }
    }

    fun renderPersonSheetPdf(sheet: PersonSheet, outputPath: Path) {
        logger.info("Generazione PDF persona: {}", outputPath.toAbsolutePath())
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                val margin = 50f
                var y = page.mediaBox.height - margin

                content.setNonStrokingColor(Color.BLACK)
                content.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                content.beginText()
                content.newLineAtOffset(margin, y)
                content.showText(sheet.fullName)
                content.endText()

                y -= 26f
                content.setFont(PDType1Font.HELVETICA, 12f)
                content.beginText()
                content.newLineAtOffset(margin, y)
                content.showText("Settimana ${sheet.weekStart.format(dateFormatter)} - ${sheet.weekEnd.format(dateFormatter)}")
                content.endText()

                y -= 24f
                content.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                content.beginText()
                content.newLineAtOffset(margin, y)
                content.showText("Assegnazioni")
                content.endText()

                y -= 18f
                content.setFont(PDType1Font.HELVETICA, 12f)
                sheet.assignments.forEach { assignment ->
                    if (y < margin + 20f) return@forEach
                    content.beginText()
                    content.newLineAtOffset(margin, y)
                    content.showText("• $assignment")
                    content.endText()
                    y -= 16f
                }
            }

            outputPath.toFile().parentFile?.mkdirs()
            document.save(outputPath.toFile())
        }
    }

    private fun drawPartBox(
        content: PDPageContentStream,
        x: Float,
        yTop: Float,
        width: Float,
        height: Float,
        part: RenderedPart,
    ) {
        val yBottom = yTop - height
        content.setStrokingColor(Color.DARK_GRAY)
        content.addRect(x, yBottom, width, height)
        content.stroke()

        var y = yTop - 18f
        content.setNonStrokingColor(Color.BLACK)
        content.setFont(PDType1Font.HELVETICA_BOLD, 13f)
        content.beginText()
        content.newLineAtOffset(x + 10f, y)
        content.showText(part.label)
        content.endText()

        y -= 16f
        content.setFont(PDType1Font.HELVETICA, 11f)
        part.assignments.forEach { assignment ->
            if (y < yBottom + 12f) return@forEach
            content.beginText()
            content.newLineAtOffset(x + 10f, y)
            content.showText(assignment)
            content.endText()
            y -= 14f
        }
    }
}

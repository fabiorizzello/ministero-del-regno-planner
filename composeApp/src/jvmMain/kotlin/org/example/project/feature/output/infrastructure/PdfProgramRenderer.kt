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
import org.apache.pdfbox.pdmodel.font.Standard14Fonts

data class ProgramWeekPrintSection(
    val weekStartDate: LocalDate,
    val statusLabel: String,
    val lines: List<String>,
)

class PdfProgramRenderer {
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    private val helvetica = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val helveticaBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    fun renderMonthlyProgramPdf(
        title: String,
        sections: List<ProgramWeekPrintSection>,
        outputPath: Path,
    ) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            PDPageContentStream(doc, page).use { content ->
                val margin = 38f
                var y = page.mediaBox.height - margin
                val lineHeight = 12f

                content.setNonStrokingColor(Color.BLACK)
                content.setFont(helveticaBold, 16f)
                content.beginText()
                content.newLineAtOffset(margin, y)
                content.showText(title)
                content.endText()

                y -= 24f
                sections.forEach { section ->
                    if (y < margin + 20f) return@forEach
                    content.setFont(helveticaBold, 11f)
                    content.beginText()
                    content.newLineAtOffset(margin, y)
                    val weekHeader = "Settimana ${section.weekStartDate.format(dateFormatter)} - ${section.statusLabel}"
                    content.showText(weekHeader)
                    content.endText()

                    y -= lineHeight
                    section.lines.forEach { line ->
                        if (y < margin + 8f) return@forEach
                        content.setFont(helvetica, 10f)
                        content.beginText()
                        content.newLineAtOffset(margin + 10f, y)
                        content.showText(line.take(120))
                        content.endText()
                        y -= lineHeight
                    }
                    y -= 4f
                }
            }

            outputPath.toFile().parentFile?.mkdirs()
            doc.save(outputPath.toFile())
        }
    }
}

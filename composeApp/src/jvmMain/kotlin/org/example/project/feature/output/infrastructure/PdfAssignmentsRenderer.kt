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
import org.apache.pdfbox.pdmodel.font.PDFont
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
                    drawTextLine(
                        content = content,
                        font = PDType1Font.HELVETICA_BOLD,
                        fontSize = 14f,
                        x = margin,
                        y = pageSize.height - margin,
                        text = "Assegnazioni ${weekStart.format(dateFormatter)} - ${weekEnd.format(dateFormatter)}",
                    )

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
                val contentWidth = page.mediaBox.width - (margin * 2)
                var y = page.mediaBox.height - margin

                content.setNonStrokingColor(Color.BLACK)

                drawTextLine(
                    content = content,
                    font = PDType1Font.HELVETICA_BOLD,
                    fontSize = 18f,
                    x = margin,
                    y = y,
                    text = sheet.fullName,
                )

                y -= 26f
                drawTextLine(
                    content = content,
                    font = PDType1Font.HELVETICA,
                    fontSize = 12f,
                    x = margin,
                    y = y,
                    text = "Settimana ${sheet.weekStart.format(dateFormatter)} - ${sheet.weekEnd.format(dateFormatter)}",
                )

                y -= 24f
                drawTextLine(
                    content = content,
                    font = PDType1Font.HELVETICA_BOLD,
                    fontSize = 13f,
                    x = margin,
                    y = y,
                    text = "Assegnazioni",
                )

                y -= 18f
                val lineHeight = 16f
                val maxLines = ((y - (margin + 20f)) / lineHeight).toInt().coerceAtLeast(0)
                val assignmentLines = buildList {
                    for (assignment in sheet.assignments) {
                        val remaining = maxLines - size
                        if (remaining <= 0) break
                        addAll(
                            wrapText(
                                font = PDType1Font.HELVETICA,
                                fontSize = 12f,
                                text = "- $assignment",
                                maxWidth = contentWidth,
                                maxLines = remaining,
                            ),
                        )
                    }
                }

                assignmentLines.forEach { line ->
                    drawTextLine(
                        content = content,
                        font = PDType1Font.HELVETICA,
                        fontSize = 12f,
                        x = margin,
                        y = y,
                        text = line,
                    )
                    y -= lineHeight
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
        val innerX = x + 10f
        val innerWidth = width - 20f
        val bottomPadding = yBottom + 12f

        content.setStrokingColor(Color.DARK_GRAY)
        content.addRect(x, yBottom, width, height)
        content.stroke()

        var y = yTop - 18f
        content.setNonStrokingColor(Color.BLACK)

        val titleLineHeight = 14f
        val titleLines = wrapText(
            font = PDType1Font.HELVETICA_BOLD,
            fontSize = 13f,
            text = part.label,
            maxWidth = innerWidth,
            maxLines = 2,
        )
        titleLines.forEach { line ->
            drawTextLine(
                content = content,
                font = PDType1Font.HELVETICA_BOLD,
                fontSize = 13f,
                x = innerX,
                y = y,
                text = line,
            )
            y -= titleLineHeight
        }

        y -= 2f

        val assignmentLineHeight = 14f
        val maxAssignmentLines = ((y - bottomPadding) / assignmentLineHeight).toInt().coerceAtLeast(0)
        val assignmentLines = buildList {
            for (assignment in part.assignments) {
                val remaining = maxAssignmentLines - size
                if (remaining <= 0) break
                addAll(
                    wrapText(
                        font = PDType1Font.HELVETICA,
                        fontSize = 11f,
                        text = assignment,
                        maxWidth = innerWidth,
                        maxLines = remaining,
                    ),
                )
            }
        }

        assignmentLines.forEach { line ->
            drawTextLine(
                content = content,
                font = PDType1Font.HELVETICA,
                fontSize = 11f,
                x = innerX,
                y = y,
                text = line,
            )
            y -= assignmentLineHeight
        }
    }

    private fun drawTextLine(
        content: PDPageContentStream,
        font: PDFont,
        fontSize: Float,
        x: Float,
        y: Float,
        text: String,
    ) {
        val safeText = sanitizeForPdf(font, text)
        if (safeText.isBlank()) return
        content.setFont(font, fontSize)
        content.beginText()
        content.newLineAtOffset(x, y)
        content.showText(safeText)
        content.endText()
    }

    private fun wrapText(
        font: PDFont,
        fontSize: Float,
        text: String,
        maxWidth: Float,
        maxLines: Int,
    ): List<String> {
        if (maxLines <= 0 || maxWidth <= 0f) return emptyList()
        val normalized = sanitizeForPdf(font, text)
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var remaining = normalized

        while (remaining.isNotEmpty()) {
            val fitted = fitToWidth(font, fontSize, remaining, maxWidth)
            if (fitted.isEmpty()) break

            var splitIndex = fitted.length
            if (splitIndex < remaining.length) {
                val lastSpace = fitted.lastIndexOf(' ')
                if (lastSpace > 0) {
                    splitIndex = lastSpace
                }
            }

            var line = remaining.substring(0, splitIndex).trimEnd()
            if (line.isEmpty()) {
                line = fitted.trimEnd()
                splitIndex = line.length.coerceAtLeast(1)
            }

            lines += line
            remaining = remaining.substring(splitIndex).trimStart()
        }

        if (lines.isEmpty()) return emptyList()
        if (lines.size <= maxLines) return lines

        val truncated = lines.take(maxLines).toMutableList()
        truncated[truncated.lastIndex] = ellipsize(font, fontSize, truncated.last(), maxWidth)
        return truncated
    }

    private fun fitToWidth(
        font: PDFont,
        fontSize: Float,
        text: String,
        maxWidth: Float,
    ): String {
        if (text.isEmpty()) return ""
        if (textWidth(font, fontSize, text) <= maxWidth) return text

        var low = 1
        var high = text.length
        var best = 1

        while (low <= high) {
            val mid = (low + high) / 2
            val candidate = text.substring(0, mid)
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return text.substring(0, best.coerceAtLeast(1))
    }

    private fun ellipsize(
        font: PDFont,
        fontSize: Float,
        text: String,
        maxWidth: Float,
    ): String {
        val suffix = "..."
        if (text.isBlank()) return suffix

        var base = text.trimEnd()
        while (base.isNotEmpty() && textWidth(font, fontSize, "$base$suffix") > maxWidth) {
            base = base.dropLast(1)
        }

        if (base.isEmpty()) {
            return fitToWidth(font, fontSize, suffix, maxWidth)
        }

        return "$base$suffix"
    }

    private fun textWidth(
        font: PDFont,
        fontSize: Float,
        text: String,
    ): Float = (font.getStringWidth(text) / 1000f) * fontSize

    private fun sanitizeForPdf(
        font: PDFont,
        text: String,
    ): String {
        if (text.isEmpty()) return ""
        val collapsed = text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')

        return buildString(collapsed.length) {
            collapsed.forEach { ch ->
                val safeChar = if (ch.code in 32..255) ch else '?'
                append(safeChar)
            }
        }.replace(Regex("\\s+"), " ").trim().ifEmpty {
            fitToWidth(font, 12f, "?", 100f)
        }
    }
}

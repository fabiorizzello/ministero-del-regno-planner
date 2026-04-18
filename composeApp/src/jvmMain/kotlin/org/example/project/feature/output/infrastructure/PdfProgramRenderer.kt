package org.example.project.feature.output.infrastructure

import java.awt.Color
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.example.project.feature.output.application.ProgramRenderer
import org.example.project.feature.output.application.ProgramWeekPrintCard
import org.example.project.feature.output.application.ProgramWeekPrintSection
import org.example.project.feature.output.application.ProgramWeekPrintSlot

class PdfProgramRenderer : ProgramRenderer {
    private val weekDateFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)
    private val fallbackRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val fallbackBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    private val primaryInk = Color(20, 20, 20)
    private val mutedInk = Color(85, 85, 85)

    override fun renderMonthlyProgramPdf(
        title: String,
        sections: List<ProgramWeekPrintSection>,
        outputPath: Path,
    ) {
        PDDocument().use { document ->
            val fonts = loadFonts(document)
            val pageSize = PDRectangle.A4
            val layout = LayoutMetrics.compactFor(
                pageSize = pageSize,
                sections = sections,
            )
            var page = PDPage(pageSize)
            document.addPage(page)
            var content = PDPageContentStream(document, page)
            var y = layout.pageTopY

            try {
                fun ensureSpace(requiredHeight: Float) {
                    if (y - requiredHeight >= layout.bottomMargin) return
                    content.close()
                    page = PDPage(pageSize)
                    document.addPage(page)
                    content = PDPageContentStream(document, page)
                    y = layout.pageTopY
                }

                fun drawWrappedParagraph(
                    text: String,
                    font: PDFont,
                    fontSize: Float,
                    color: Color,
                    indentX: Float = layout.contentX,
                    extraSpacingAfter: Float = 0f,
                ) {
                    val lines = wrapText(
                        font = font,
                        fontSize = fontSize,
                        text = text,
                        maxWidth = layout.contentWidth - (indentX - layout.contentX),
                    )
                    if (lines.isEmpty()) {
                        if (extraSpacingAfter > 0f) {
                            ensureSpace(extraSpacingAfter)
                            y -= extraSpacingAfter
                        }
                        return
                    }
                    val paragraphHeight = lines.size * layout.lineHeight(fontSize) + extraSpacingAfter
                    ensureSpace(paragraphHeight)
                    lines.forEach { line ->
                        drawTextLine(
                            content = content,
                            font = font,
                            fontSize = fontSize,
                            x = indentX,
                            y = y - fontSize,
                            text = line,
                            color = color,
                        )
                        y -= layout.lineHeight(fontSize)
                    }
                    y -= extraSpacingAfter
                }

                drawWrappedParagraph(
                    text = title,
                    font = fonts.bold,
                    fontSize = layout.titleFontSize,
                    color = primaryInk,
                    extraSpacingAfter = layout.sectionGap,
                )

                sections.forEachIndexed { index, section ->
                    drawWeekSection(
                        section = section,
                        fonts = fonts,
                        layout = layout,
                        drawParagraph = { text, font, fontSize, color, indentX, extraSpacingAfter ->
                            drawWrappedParagraph(text, font, fontSize, color, indentX, extraSpacingAfter)
                        },
                    )
                    if (index < sections.lastIndex) {
                        ensureSpace(layout.sectionGap)
                        y -= layout.sectionGap
                    }
                }
            } finally {
                content.close()
            }

            val parentDir = outputPath.toFile().parentFile
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                throw java.io.IOException("Impossibile creare la directory di output: $parentDir")
            }
            document.save(outputPath.toFile())
        }
    }

    private fun drawWeekSection(
        section: ProgramWeekPrintSection,
        fonts: FontSet,
        layout: LayoutMetrics,
        drawParagraph: (
            text: String,
            font: PDFont,
            fontSize: Float,
            color: Color,
            indentX: Float,
            extraSpacingAfter: Float,
        ) -> Unit,
    ) {
        val weekTitle = buildString {
            append("Settimana ")
            append(formatWeekRange(section.weekStartDate, section.weekEndDate))
            if (section.statusLabel.isNotBlank() && section.statusLabel != "Attiva") {
                append(" - ")
                append(section.statusLabel)
            }
        }
        drawParagraph(
            weekTitle,
            fonts.bold,
            layout.weekFontSize,
            primaryInk,
            layout.contentX,
            layout.weekGap,
        )

        if (section.cards.isEmpty()) {
            drawParagraph(
                section.emptyStateLabel ?: "Nessuna parte configurata",
                fonts.regular,
                layout.bodyFontSize,
                primaryInk,
                layout.itemIndentX,
                0f,
            )
            return
        }

        section.cards.forEachIndexed { index, card ->
            drawPartBlock(
                card = card,
                fonts = fonts,
                layout = layout,
                drawParagraph = drawParagraph,
            )
            if (index < section.cards.lastIndex) {
                drawParagraph("", fonts.regular, layout.bodyFontSize, primaryInk, layout.contentX, layout.partGap)
            }
        }
    }

    private fun drawPartBlock(
        card: ProgramWeekPrintCard,
        fonts: FontSet,
        layout: LayoutMetrics,
        drawParagraph: (
            text: String,
            font: PDFont,
            fontSize: Float,
            color: Color,
            indentX: Float,
            extraSpacingAfter: Float,
        ) -> Unit,
    ) {
        drawParagraph(
            "${card.displayNumber}. ${card.partLabel}",
            fonts.bold,
            layout.partFontSize,
            primaryInk,
            layout.itemIndentX,
            layout.slotGap,
        )
        card.slots.forEach { slot ->
            drawSlotLine(
                slot = slot,
                fonts = fonts,
                layout = layout,
                drawParagraph = drawParagraph,
            )
        }
    }

    private fun drawSlotLine(
        slot: ProgramWeekPrintSlot,
        fonts: FontSet,
        layout: LayoutMetrics,
        drawParagraph: (
            text: String,
            font: PDFont,
            fontSize: Float,
            color: Color,
            indentX: Float,
            extraSpacingAfter: Float,
        ) -> Unit,
    ) {
        val label = slot.roleLabel?.takeIf { it.isNotBlank() } ?: "Assegnazione"
        drawParagraph(
            "$label: ${slot.assignedTo}",
            fonts.regular,
            layout.bodyFontSize,
            if (slot.isAssigned) primaryInk else mutedInk,
            layout.slotIndentX,
            0f,
        )
    }

    private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
        val startLabel = start.format(weekDateFormatter)
        val endLabel = end.format(weekDateFormatter)
        return if (startLabel == endLabel) startLabel else "$startLabel - $endLabel"
    }

    private fun drawTextLine(
        content: PDPageContentStream,
        font: PDFont,
        fontSize: Float,
        x: Float,
        y: Float,
        text: String,
        color: Color,
    ) {
        val safeText = sanitizeForPdf(font, text)
        if (safeText.isBlank()) return
        content.setNonStrokingColor(color)
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
    ): List<String> {
        val safeText = sanitizeForPdf(font, text)
        if (safeText.isBlank() || maxWidth <= 0f) return emptyList()

        val words = safeText.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = ""
        words.forEach { word ->
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) {
                    lines += currentLine
                    currentLine = ""
                    if (textWidth(font, fontSize, word) <= maxWidth) {
                        currentLine = word
                    } else {
                        lines += splitLongWord(font, fontSize, word, maxWidth)
                    }
                } else {
                    lines += splitLongWord(font, fontSize, word, maxWidth)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines += currentLine
        }
        return lines
    }

    private fun splitLongWord(
        font: PDFont,
        fontSize: Float,
        word: String,
        maxWidth: Float,
    ): List<String> {
        if (word.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var current = ""
        word.forEach { ch ->
            val candidate = current + ch
            if (textWidth(font, fontSize, candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                chunks += current
                current = ch.toString()
            }
        }
        if (current.isNotEmpty()) {
            chunks += current
        }
        return chunks
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
        if (text.isBlank()) return ""
        val collapsed = text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')

        val sanitized = buildString(collapsed.length) {
            collapsed.forEach { ch ->
                when {
                    ch == ' ' -> append(ch)
                    ch.code < 32 -> Unit
                    canRender(font, ch) -> append(ch)
                    else -> append('?')
                }
            }
        }.replace(Regex("\\s+"), " ").trim()

        return if (sanitized.isBlank()) "?" else sanitized
    }

    private fun canRender(
        font: PDFont,
        ch: Char,
    ): Boolean = runCatching {
        font.getStringWidth(ch.toString())
        true
    }.getOrDefault(false)

    private fun loadFonts(document: PDDocument): FontSet = FontSet(
        regular = loadPreferredFont(document, regularFontCandidates()) ?: fallbackRegular,
        bold = loadPreferredFont(document, boldFontCandidates()) ?: fallbackBold,
    )

    private fun loadPreferredFont(
        document: PDDocument,
        candidates: List<String>,
    ): PDFont? = candidates.firstNotNullOfOrNull { candidate ->
        val file = File(candidate)
        if (!file.exists()) {
            null
        } else {
            runCatching { PDType0Font.load(document, file) }.getOrNull()
        }
    }

    private fun regularFontCandidates(): List<String> = listOf(
        "C:\\Windows\\Fonts\\segoeui.ttf",
        "C:\\Windows\\Fonts\\arial.ttf",
        "C:\\Windows\\Fonts\\verdana.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/Library/Fonts/Arial.ttf",
        "/Library/Fonts/Verdana.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Verdana.ttf",
    )

    private fun boldFontCandidates(): List<String> = listOf(
        "C:\\Windows\\Fonts\\segoeuib.ttf",
        "C:\\Windows\\Fonts\\arialbd.ttf",
        "C:\\Windows\\Fonts\\verdanab.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "/Library/Fonts/Verdana Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/System/Library/Fonts/Supplemental/Verdana Bold.ttf",
    )

    private data class LayoutMetrics(
        val pageSize: PDRectangle,
        val topMargin: Float,
        val bottomMargin: Float,
        val horizontalMargin: Float,
        val titleFontSize: Float,
        val weekFontSize: Float,
        val partFontSize: Float,
        val bodyFontSize: Float,
        val sectionGap: Float,
        val weekGap: Float,
        val partGap: Float,
        val slotGap: Float,
        val itemIndent: Float,
        val slotIndent: Float,
        val lineSpacing: Float,
    ) {
        val pageTopY: Float = pageSize.height - topMargin
        val contentX: Float = horizontalMargin
        val contentWidth: Float = pageSize.width - (horizontalMargin * 2)
        val itemIndentX: Float = contentX + itemIndent
        val slotIndentX: Float = contentX + slotIndent

        fun lineHeight(fontSize: Float): Float = fontSize + lineSpacing

        companion object {
            fun compactFor(
                pageSize: PDRectangle,
                sections: List<ProgramWeekPrintSection>,
            ): LayoutMetrics {
                val base = LayoutMetrics(
                    pageSize = pageSize,
                    topMargin = 22f,
                    bottomMargin = 20f,
                    horizontalMargin = 24f,
                    titleFontSize = 13f,
                    weekFontSize = 10f,
                    partFontSize = 8.6f,
                    bodyFontSize = 7.8f,
                    sectionGap = 6f,
                    weekGap = 2f,
                    partGap = 2f,
                    slotGap = 0.5f,
                    itemIndent = 8f,
                    slotIndent = 16f,
                    lineSpacing = 2f,
                )

                val baseHeight = estimatedContentHeight(base, sections)
                val availableHeight = pageSize.height - base.topMargin - base.bottomMargin
                if (baseHeight <= availableHeight) return base

                val rawScale = (availableHeight / baseHeight).coerceIn(0.74f, 1f)
                return LayoutMetrics(
                    pageSize = pageSize,
                    topMargin = (base.topMargin * rawScale).coerceAtLeast(16f),
                    bottomMargin = (base.bottomMargin * rawScale).coerceAtLeast(14f),
                    horizontalMargin = (base.horizontalMargin * rawScale).coerceAtLeast(18f),
                    titleFontSize = (base.titleFontSize * rawScale).coerceAtLeast(11f),
                    weekFontSize = (base.weekFontSize * rawScale).coerceAtLeast(8.4f),
                    partFontSize = (base.partFontSize * rawScale).coerceAtLeast(7.3f),
                    bodyFontSize = (base.bodyFontSize * rawScale).coerceAtLeast(6.9f),
                    sectionGap = (base.sectionGap * rawScale).coerceAtLeast(3f),
                    weekGap = (base.weekGap * rawScale).coerceAtLeast(1f),
                    partGap = (base.partGap * rawScale).coerceAtLeast(1f),
                    slotGap = (base.slotGap * rawScale).coerceAtLeast(0f),
                    itemIndent = (base.itemIndent * rawScale).coerceAtLeast(6f),
                    slotIndent = (base.slotIndent * rawScale).coerceAtLeast(12f),
                    lineSpacing = (base.lineSpacing * rawScale).coerceAtLeast(1f),
                )
            }

            private fun estimatedContentHeight(
                layout: LayoutMetrics,
                sections: List<ProgramWeekPrintSection>,
            ): Float {
                var total = layout.lineHeight(layout.titleFontSize) + layout.sectionGap
                sections.forEachIndexed { index, section ->
                    total += layout.lineHeight(layout.weekFontSize) + layout.weekGap
                    if (section.cards.isEmpty()) {
                        total += layout.lineHeight(layout.bodyFontSize)
                    } else {
                        section.cards.forEachIndexed { cardIndex, card ->
                            total += layout.lineHeight(layout.partFontSize) + layout.slotGap
                            total += card.slots.size * layout.lineHeight(layout.bodyFontSize)
                            if (cardIndex < section.cards.lastIndex) {
                                total += layout.partGap
                            }
                        }
                    }
                    if (index < sections.lastIndex) {
                        total += layout.sectionGap
                    }
                }
                return total
            }
        }
    }

    private data class FontSet(
        val regular: PDFont,
        val bold: PDFont,
    )
}

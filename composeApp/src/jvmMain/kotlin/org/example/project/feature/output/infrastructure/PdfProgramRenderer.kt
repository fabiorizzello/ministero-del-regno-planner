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

data class ProgramWeekPrintSlot(
    val roleLabel: String?,
    val assignedTo: String,
    val isAssigned: Boolean,
)

enum class ProgramWeekPrintCardStatus {
    EMPTY,
    PARTIAL,
    ASSIGNED,
}

data class ProgramWeekPrintCard(
    val displayNumber: Int,
    val partLabel: String,
    val status: ProgramWeekPrintCardStatus,
    val statusLabel: String,
    val slots: List<ProgramWeekPrintSlot>,
)

data class ProgramWeekPrintSection(
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val statusLabel: String,
    val cards: List<ProgramWeekPrintCard>,
    val emptyStateLabel: String? = null,
)

class PdfProgramRenderer {
    private val columnCount = 3
    private val weekDateFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)
    private val fallbackRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val fallbackBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    private val primaryInk = Color(20, 20, 20)
    private val mutedInk = Color(85, 85, 85)

    fun renderMonthlyProgramPdf(
        title: String,
        sections: List<ProgramWeekPrintSection>,
        outputPath: Path,
    ) {
        PDDocument().use { document ->
            val fonts = loadFonts(document)
            val pageSize = PDRectangle.A4
            val page = PDPage(pageSize)
            document.addPage(page)

            PDPageContentStream(document, page).use { content ->
                val margin = 16f
                val contentWidth = pageSize.width - (margin * 2)
                var y = pageSize.height - margin

                drawTextLine(
                    content = content,
                    font = fonts.bold,
                    fontSize = 16f,
                    x = margin,
                    y = y,
                    text = title,
                    color = primaryInk,
                )

                y -= 24f

                if (sections.isNotEmpty()) {
                    val sectionGap = 8f
                    val layout = createLayout(
                        sections = sections,
                        availableHeight = y - margin - sectionGap * (sections.size - 1).coerceAtLeast(0),
                        contentWidth = contentWidth,
                    )

                    sections.forEachIndexed { index, section ->
                        val sectionHeight = sectionHeight(section, layout)
                        drawWeekSection(
                            content = content,
                            section = section,
                            x = margin,
                            yTop = y,
                            width = contentWidth,
                            layout = layout,
                            fonts = fonts,
                        )
                        y -= sectionHeight
                        if (index < sections.lastIndex) {
                            y -= sectionGap
                        }
                    }
                }
            }

            outputPath.toFile().parentFile?.mkdirs()
            document.save(outputPath.toFile())
        }
    }

    private fun createLayout(
        sections: List<ProgramWeekPrintSection>,
        availableHeight: Float,
        contentWidth: Float,
    ): LayoutMetrics {
        val totalUnits = sections.sumOf { sectionLayoutUnits(it).toDouble() }.toFloat().coerceAtLeast(1f)
        val unit = (availableHeight / totalUnits).coerceAtLeast(5.8f)
        val cardGap = (unit * 0.22f).coerceIn(5f, 8f)
        val cardWidth = (contentWidth - cardGap * (columnCount - 1)) / columnCount
        return LayoutMetrics(
            columnCount = columnCount,
            weekHeaderHeight = unit * 1.02f,
            headerGap = unit * 0.34f,
            rowGap = unit * 0.22f,
            cardGap = cardGap,
            cardWidth = cardWidth,
            cardHeaderHeight = unit * 0.8f,
            slotRowHeight = unit * 0.72f,
            cardPaddingY = unit * 0.12f,
            weekFontSize = (unit * 0.86f).coerceIn(9.8f, 11.6f),
            titleFontSize = (unit * 0.72f).coerceIn(7.4f, 9.2f),
            bodyFontSize = (unit * 0.66f).coerceIn(6.8f, 8.2f),
            roleFontSize = (unit * 0.6f).coerceIn(6.2f, 7.2f),
            roleColumnWidth = (cardWidth * 0.24f).coerceIn(30f, 42f),
            roleGap = (unit * 0.18f).coerceIn(3f, 5f),
        )
    }

    private fun drawWeekSection(
        content: PDPageContentStream,
        section: ProgramWeekPrintSection,
        x: Float,
        yTop: Float,
        width: Float,
        layout: LayoutMetrics,
        fonts: FontSet,
    ) {
        val weekTitle = "Settimana ${formatWeekRange(section.weekStartDate, section.weekEndDate)}"
        drawTextLine(
            content = content,
            font = fonts.bold,
            fontSize = layout.weekFontSize,
            x = x,
            y = yTop - layout.weekFontSize,
            text = weekTitle,
            color = primaryInk,
        )

        var cursorY = yTop - layout.weekHeaderHeight - layout.headerGap
        if (section.cards.isEmpty()) {
            drawTextLine(
                content = content,
                font = fonts.bold,
                fontSize = layout.titleFontSize,
                x = x,
                y = cursorY - layout.titleFontSize,
                text = section.emptyStateLabel ?: "Nessuna parte configurata",
                color = primaryInk,
            )
            return
        }

        val rows = sectionRows(section, layout)
        rows.forEachIndexed { rowIndex, rowCards ->
            val rowHeight = rowHeight(rowCards, layout)
            rowCards.forEachIndexed { cardIndex, card ->
                drawPartBlock(
                    content = content,
                    card = card,
                    x = x + cardIndex * (layout.cardWidth + layout.cardGap),
                    yTop = cursorY,
                    width = layout.cardWidth,
                    layout = layout,
                    fonts = fonts,
                )
            }
            cursorY -= rowHeight
            if (rowIndex < rows.lastIndex) {
                cursorY -= layout.rowGap
            }
        }
    }

    private fun drawPartBlock(
        content: PDPageContentStream,
        card: ProgramWeekPrintCard,
        x: Float,
        yTop: Float,
        width: Float,
        layout: LayoutMetrics,
        fonts: FontSet,
    ) {
        val title = "${card.displayNumber}. ${card.partLabel}"
        drawTextLine(
            content = content,
            font = fonts.bold,
            fontSize = layout.titleFontSize,
            x = x,
            y = yTop - layout.titleFontSize,
            text = ellipsize(fonts.bold, layout.titleFontSize, title, width),
            color = primaryInk,
        )

        card.slots.forEachIndexed { slotIndex, slot ->
            val rowTop = yTop - layout.cardHeaderHeight - slotIndex * layout.slotRowHeight - layout.cardPaddingY
            drawSlotRow(
                content = content,
                slot = slot,
                x = x,
                yTop = rowTop,
                width = width,
                layout = layout,
                fonts = fonts,
            )
        }
    }

    private fun drawSlotRow(
        content: PDPageContentStream,
        slot: ProgramWeekPrintSlot,
        x: Float,
        yTop: Float,
        width: Float,
        layout: LayoutMetrics,
        fonts: FontSet,
    ) {
        val roleX = x
        val textX = roleX + layout.roleColumnWidth + layout.roleGap
        val textWidth = width - layout.roleColumnWidth - layout.roleGap
        val baselineY = yTop - (layout.slotRowHeight - layout.bodyFontSize) / 2f - 2f

        if (slot.roleLabel != null) {
            drawTextLine(
                content = content,
                font = fonts.bold,
                fontSize = layout.roleFontSize,
                x = roleX,
                y = baselineY,
                text = ellipsize(fonts.bold, layout.roleFontSize, "${slot.roleLabel}:", layout.roleColumnWidth),
                color = mutedInk,
            )
        }

        drawTextLine(
            content = content,
            font = fonts.regular,
            fontSize = layout.bodyFontSize,
            x = textX,
            y = baselineY,
            text = ellipsize(fonts.regular, layout.bodyFontSize, slot.assignedTo, textWidth),
            color = primaryInk,
        )
    }

    private fun rowHeight(
        cards: List<ProgramWeekPrintCard>,
        layout: LayoutMetrics,
    ): Float {
        val maxSlots = cards.maxOfOrNull { maxOf(it.slots.size, 1) } ?: 1
        return layout.cardHeaderHeight + maxSlots * layout.slotRowHeight + layout.cardPaddingY
    }

    private fun sectionHeight(
        section: ProgramWeekPrintSection,
        layout: LayoutMetrics,
    ): Float {
        if (section.cards.isEmpty()) {
            return layout.weekHeaderHeight + layout.headerGap + layout.slotRowHeight * 0.95f
        }
        val rows = sectionRows(section, layout)
        return layout.weekHeaderHeight +
            layout.headerGap +
            rows.sumOf { rowHeight(it, layout).toDouble() }.toFloat() +
            layout.rowGap * (rows.size - 1).coerceAtLeast(0)
    }

    private fun sectionLayoutUnits(section: ProgramWeekPrintSection): Float {
        val weekHeaderUnits = 1.02f
        val headerGapUnits = 0.24f
        val cardHeaderUnits = 0.8f
        val slotUnits = 0.72f
        val paddingUnits = 0.12f
        val rowGapUnits = 0.22f
        if (section.cards.isEmpty()) {
            return weekHeaderUnits + headerGapUnits + slotUnits * 0.95f
        }
        val rowCards = section.cards.chunked(columnCount)
        return weekHeaderUnits +
            headerGapUnits +
            rowCards.sumOf { row ->
                val maxSlots = row.maxOfOrNull { maxOf(it.slots.size, 1) } ?: 1
                (cardHeaderUnits + slotUnits * maxSlots + paddingUnits).toDouble()
            }.toFloat() +
            rowGapUnits * (rowCards.size - 1).coerceAtLeast(0)
    }

    private fun sectionRows(
        section: ProgramWeekPrintSection,
        layout: LayoutMetrics,
    ): List<List<ProgramWeekPrintCard>> = section.cards.chunked(layout.columnCount)

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

    private fun ellipsize(
        font: PDFont,
        fontSize: Float,
        text: String,
        maxWidth: Float,
    ): String {
        val safeText = sanitizeForPdf(font, text)
        if (safeText.isBlank() || maxWidth <= 0f) return ""
        if (textWidth(font, fontSize, safeText) <= maxWidth) return safeText

        val suffix = "..."
        var candidate = safeText
        while (candidate.isNotEmpty() && textWidth(font, fontSize, "$candidate$suffix") > maxWidth) {
            candidate = candidate.dropLast(1).trimEnd()
        }
        return if (candidate.isBlank()) suffix else "$candidate$suffix"
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
        val columnCount: Int,
        val weekHeaderHeight: Float,
        val headerGap: Float,
        val rowGap: Float,
        val cardGap: Float,
        val cardWidth: Float,
        val cardHeaderHeight: Float,
        val slotRowHeight: Float,
        val cardPaddingY: Float,
        val weekFontSize: Float,
        val titleFontSize: Float,
        val bodyFontSize: Float,
        val roleFontSize: Float,
        val roleColumnWidth: Float,
        val roleGap: Float,
    )

    private data class FontSet(
        val regular: PDFont,
        val bold: PDFont,
    )
}

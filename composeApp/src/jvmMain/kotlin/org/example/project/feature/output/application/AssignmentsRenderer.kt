package org.example.project.feature.output.application

import java.nio.file.Path
import java.time.LocalDate

/**
 * Data needed to render a single assignment slip (S-89 card).
 */
data class AssignmentSlipData(
    val studentName: String,
    val assistantName: String?,
    val weekStart: LocalDate,
    val partNumber: Int,
    val partLabel: String,
)

/**
 * Renders assignment slips as PDF documents and converts them to images.
 */
interface AssignmentsRenderer {
    fun renderAssignmentSlipPdf(slip: AssignmentSlipData, outputPath: Path)
    fun renderPdfToImage(pdfPath: Path, pngPath: Path)
}

package org.example.project.feature.output.application

import java.nio.file.Path

/**
 * Renders a monthly program document (PDF) from application-layer print models.
 */
interface ProgramRenderer {
    fun renderMonthlyProgramPdf(
        title: String,
        sections: List<ProgramWeekPrintSection>,
        outputPath: Path,
    )
}

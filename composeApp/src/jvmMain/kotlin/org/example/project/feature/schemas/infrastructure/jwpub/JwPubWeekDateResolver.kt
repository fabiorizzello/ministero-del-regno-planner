package org.example.project.feature.schemas.infrastructure.jwpub

import java.time.LocalDate
import java.util.Locale

object JwPubWeekDateResolver {

    class UnparseableDateException(message: String) : RuntimeException(message)

    private val MONTHS = mapOf(
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4,
        "maggio" to 5, "giugno" to 6, "luglio" to 7, "agosto" to 8,
        "settembre" to 9, "ottobre" to 10, "novembre" to 11, "dicembre" to 12,
    )

    // Accept `-`, en-dash, em-dash as range separator, with optional
    // surrounding whitespace. "º" ordinal is ignored.
    private val RANGE_REGEX = Regex(
        "^\\s*(\\d{1,2})º?\\s*(?:([a-zA-Zàèéìòù]+)\\s*)?[\\-–—]\\s*(\\d{1,2})º?\\s*([a-zA-Zàèéìòù]+)\\s*$",
    )

    fun resolve(title: String, publicationYear: Int): LocalDate {
        val normalized = title.lowercase(Locale.ROOT).trim()
        val match = RANGE_REGEX.matchEntire(normalized)
            ?: throw UnparseableDateException("Title not in expected range format: $title")
        val startDay = match.groupValues[1].toInt()
        val rawStartMonth = match.groupValues[2]
        val endMonth = MONTHS[match.groupValues[4]]
            ?: throw UnparseableDateException("Unknown end month: ${match.groupValues[4]}")
        val startMonth = if (rawStartMonth.isBlank()) {
            endMonth  // same month range
        } else {
            MONTHS[rawStartMonth]
                ?: throw UnparseableDateException("Unknown start month: $rawStartMonth")
        }
        val startYear = if (startMonth == 12 && endMonth == 1) {
            publicationYear
        } else {
            publicationYear
        }
        return LocalDate.of(startYear, startMonth, startDay)
    }
}

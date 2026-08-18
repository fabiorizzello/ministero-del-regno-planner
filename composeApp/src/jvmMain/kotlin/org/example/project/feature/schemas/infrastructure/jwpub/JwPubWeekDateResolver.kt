package org.example.project.feature.schemas.infrastructure.jwpub

import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

object JwPubWeekDateResolver {

    class UnparseableDateException(message: String) : RuntimeException(message)

    private val MONTHS = mapOf(
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4,
        "maggio" to 5, "giugno" to 6, "luglio" to 7, "agosto" to 8,
        "settembre" to 9, "ottobre" to 10, "novembre" to 11, "dicembre" to 12,
    )

    // JW may use a dash or plain whitespace between two complete dates.
    // Both dates may include their year; the ordinal mark is ignored.
    private val RANGE_REGEX =
        """^\s*(\d{1,2})(?:\u00BA)?\s*(?:(\p{L}+)(?:\s+(\d{4}))?)?\s*(?:[-\u2013\u2014]|\s+)\s*(\d{1,2})(?:\u00BA)?\s*(\p{L}+)(?:\s+(\d{4}))?\s*$"""
            .toRegex()

    fun resolve(title: String, publicationYear: Int): LocalDate {
        val normalized = title.lowercase(Locale.ROOT).trim()
        val match = RANGE_REGEX.matchEntire(normalized)
            ?: throw UnparseableDateException(
                "Titolo della settimana in un formato non riconosciuto: $title",
            )
        val startDay = match.groupValues[1].toInt()
        val rawStartMonth = match.groupValues[2]
        val explicitStartYear = match.groupValues[3].toIntOrNull()
        val endMonth = MONTHS[match.groupValues[5]]
            ?: throw UnparseableDateException(
                "Mese finale non riconosciuto: ${match.groupValues[5]}",
            )
        val endYear = match.groupValues[6].toIntOrNull()
            ?: explicitStartYear
            ?: publicationYear
        val startMonth = if (rawStartMonth.isBlank()) {
            endMonth
        } else {
            MONTHS[rawStartMonth]
                ?: throw UnparseableDateException("Mese iniziale non riconosciuto: $rawStartMonth")
        }
        val startYear = explicitStartYear
            ?: if (startMonth == 12 && endMonth == 1) endYear - 1 else endYear

        return try {
            LocalDate.of(startYear, startMonth, startDay)
        } catch (_: DateTimeException) {
            throw UnparseableDateException("Data della settimana non valida: $title")
        }
    }
}

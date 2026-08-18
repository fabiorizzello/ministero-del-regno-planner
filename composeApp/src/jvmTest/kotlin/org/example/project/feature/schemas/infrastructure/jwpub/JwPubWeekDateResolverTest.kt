package org.example.project.feature.schemas.infrastructure.jwpub

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwPubWeekDateResolverTest {

    @Test fun `same month range`() {
        assertEquals(
            LocalDate.of(2026, 1, 5),
            JwPubWeekDateResolver.resolve("5-11 gennaio", 2026),
        )
    }

    @Test fun `cross-month range en dash`() {
        assertEquals(
            LocalDate.of(2026, 1, 26),
            JwPubWeekDateResolver.resolve("26 gennaio – 1º febbraio", 2026),
        )
    }

    @Test fun `cross-month range em dash`() {
        assertEquals(
            LocalDate.of(2026, 2, 23),
            JwPubWeekDateResolver.resolve("23 febbraio — 1º marzo", 2026),
        )
    }

    @Test fun `same month with ordinal mark in start`() {
        assertEquals(
            LocalDate.of(2026, 3, 1),
            JwPubWeekDateResolver.resolve("1º-7 marzo", 2026),
        )
    }

    @Test fun `cross year range returns start date in previous calendar year`() {
        assertEquals(
            LocalDate.of(2026, 12, 28),
            JwPubWeekDateResolver.resolve("28 dicembre – 3 gennaio", 2027),
        )
    }

    @Test fun `same publication year for non cross year range`() {
        assertEquals(
            LocalDate.of(2027, 2, 1),
            JwPubWeekDateResolver.resolve("1-7 febbraio", 2027),
        )
    }

    @Test fun `same month range with explicit year`() {
        assertEquals(
            LocalDate.of(2026, 12, 21),
            JwPubWeekDateResolver.resolve("21-27 dicembre 2026", 2026),
        )
    }

    @Test fun `cross year range with explicit end year`() {
        assertEquals(
            LocalDate.of(2026, 12, 28),
            JwPubWeekDateResolver.resolve("28 dicembre - 3 gennaio 2027", 2027),
        )
    }

    @Test fun `cross year range without separator as emitted by catalog title`() {
        assertEquals(
            LocalDate.of(2026, 12, 28),
            JwPubWeekDateResolver.resolve("28 dicembre 2026  3 gennaio 2027", 2027),
        )
    }

    @Test fun `uppercase title is normalized`() {
        assertEquals(
            LocalDate.of(2026, 1, 5),
            JwPubWeekDateResolver.resolve("5-11 GENNAIO", 2026),
        )
    }

    @Test fun `garbled input reports the error in Italian`() {
        val error = assertFailsWith<JwPubWeekDateResolver.UnparseableDateException> {
            JwPubWeekDateResolver.resolve("nonsense", 2026)
        }

        assertEquals(
            "Titolo della settimana in un formato non riconosciuto: nonsense",
            error.message,
        )
    }
}

package org.example.project.core.cli

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class SeedHistoricalDemoDataTargetMonthsTest {
    @Test
    fun `buildTargetMonths includes past current and future in chronological order`() {
        val result = buildTargetMonths(
            referenceMonth = YearMonth.of(2026, 2),
            pastMonths = 2,
            includeCurrent = true,
            futureMonths = 2,
        )

        assertEquals(
            listOf(
                YearMonth.of(2025, 12),
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 2),
                YearMonth.of(2026, 3),
                YearMonth.of(2026, 4),
            ),
            result,
        )
    }

    @Test
    fun `buildTargetMonths can exclude current month`() {
        val result = buildTargetMonths(
            referenceMonth = YearMonth.of(2026, 2),
            pastMonths = 1,
            includeCurrent = false,
            futureMonths = 1,
        )

        assertEquals(
            listOf(
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 3),
            ),
            result,
        )
    }
}

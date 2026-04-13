package org.example.project.feature.programs.domain

import org.example.project.core.domain.DomainError
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgramMonthAggregateTest {

    @Test
    fun `validate deletion blocks past program`() {
        val program = ProgramMonth(
            id = ProgramMonthId("p1"),
            year = 2026,
            month = 1,
            startDate = LocalDate.of(2026, 1, 5),
            endDate = LocalDate.of(2026, 2, 1),
            templateAppliedAt = null,
            createdAt = LocalDateTime.now(),
        )
        val aggregate = ProgramMonthAggregate(program)

        val error = aggregate.validateDeletion(LocalDate.of(2026, 3, 1))

        assertEquals(DomainError.ProgrammaPassatoNonEliminabile, error)
    }

    @Test
    fun `validate creation target returns MeseFuoriFinestra for out of window`() {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val target = YearMonth.of(2026, 5)

        val error = ProgramMonthAggregate.validateCreationTarget(
            target = target,
            referenceDate = referenceDate,
            existingByMonth = emptySet(),
            futureMonths = emptySet(),
        )

        assertEquals(DomainError.MeseFuoriFinestraCreazione, error)
    }

    @Test
    fun `validate creation target returns null when target is allowed`() {
        val referenceDate = LocalDate.of(2026, 2, 10)
        val target = YearMonth.of(2026, 3)

        val error = ProgramMonthAggregate.validateCreationTarget(
            target = target,
            referenceDate = referenceDate,
            existingByMonth = emptySet(),
            futureMonths = emptySet(),
        )

        assertNull(error)
    }

    @Test
    fun `validate creation target permette il mese precedente`() {
        val referenceDate = LocalDate.of(2026, 4, 13)
        val target = YearMonth.of(2026, 3)

        val error = ProgramMonthAggregate.validateCreationTarget(
            target = target,
            referenceDate = referenceDate,
            existingByMonth = emptySet(),
            futureMonths = emptySet(),
        )

        assertNull(error)
    }

    @Test
    fun `validate creation target blocca il mese precedente se gia esistente`() {
        val referenceDate = LocalDate.of(2026, 4, 13)
        val target = YearMonth.of(2026, 3)

        val error = ProgramMonthAggregate.validateCreationTarget(
            target = target,
            referenceDate = referenceDate,
            existingByMonth = setOf(target),
            futureMonths = emptySet(),
        )

        assertEquals(DomainError.ProgrammaGiaEsistenteNelMese(month = 3, year = 2026), error)
    }

    @Test
    fun `validate creation target rifiuta due mesi indietro come fuori finestra`() {
        val referenceDate = LocalDate.of(2026, 4, 13)
        val target = YearMonth.of(2026, 2)

        val error = ProgramMonthAggregate.validateCreationTarget(
            target = target,
            referenceDate = referenceDate,
            existingByMonth = emptySet(),
            futureMonths = emptySet(),
        )

        assertEquals(DomainError.MeseFuoriFinestraCreazione, error)
    }

    @Test
    fun `validate creation target permette mese precedente anche se quota futuri piena`() {
        val referenceDate = LocalDate.of(2026, 4, 13)
        val target = YearMonth.of(2026, 3)
        val futures = setOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6))

        val error = ProgramMonthAggregate.validateCreationTarget(
            target = target,
            referenceDate = referenceDate,
            existingByMonth = setOf(YearMonth.of(2026, 4)) + futures,
            futureMonths = futures,
        )

        assertNull(error)
    }
}

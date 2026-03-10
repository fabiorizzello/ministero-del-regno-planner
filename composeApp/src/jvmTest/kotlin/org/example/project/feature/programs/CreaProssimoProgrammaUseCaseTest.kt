package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.application.CreaProssimoProgrammaUseCase
import org.example.project.feature.programs.domain.ProgramMonth
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreaProssimoProgrammaUseCaseTest {

    // referenceDate = 2026-03-04 (Wednesday) → current month = March 2026
    private val referenceDate = LocalDate.of(2026, 3, 4)

    // 1. Mese target non valido (mese < 1 o > 12) → MeseTargetNonValido
    @Test
    fun `invalid month number returns MeseTargetNonValido`() = runTest {
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        val result = useCase(2026, 13, referenceDate)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.MeseTargetNonValido, left)
        Unit
    }

    // 2. Mese corrente (marzo 2026) non ancora esistente → creazione consentita
    @Test
    fun `current month can be created when no programs exist`() = runTest {
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        val result = useCase(2026, 3, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(2026, created.year)
        assertEquals(3, created.month)
        assertEquals(1, store.programs.size)
        Unit
    }

    // 3. Mese corrente+1 (aprile 2026) non ancora esistente e nessun programma futuro → consentito
    @Test
    fun `next month can be created when no future programs exist`() = runTest {
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        val result = useCase(2026, 4, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(4, created.month)
        Unit
    }

    // 4. Mese corrente+2 (maggio 2026) senza corrente+1 (aprile) → MeseNonCreabile
    @Test
    fun `month plus two without plus one returns MeseNonCreabile`() = runTest {
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        val result = useCase(2026, 5, referenceDate)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.MeseNonCreabile, left)
        Unit
    }

    // 5. Mese corrente+2 con corrente+1 già esistente → consentito
    @Test
    fun `month plus two allowed when plus one already exists`() = runTest {
        val store = InMemoryProgramStore(
            programs = mutableListOf(fixtureProgramMonth(YearMonth.of(2026, 4))),
        )
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        val result = useCase(2026, 5, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        assertEquals(5, created.month)
        Unit
    }

    // 6. Mese già esistente → ProgrammaGiaEsistenteNelMese
    @Test
    fun `duplicate month returns ProgrammaGiaEsistenteNelMese`() = runTest {
        val store = InMemoryProgramStore(
            programs = mutableListOf(fixtureProgramMonth(YearMonth.of(2026, 3))),
        )
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        val result = useCase(2026, 3, referenceDate)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertIs<DomainError.ProgrammaGiaEsistenteNelMese>(left)
        Unit
    }

    // 7. Mese fuori finestra (più di 2 mesi avanti) → MeseFuoriFinestraCreazione
    @Test
    fun `month beyond window returns MeseFuoriFinestraCreazione`() = runTest {
        val store = InMemoryProgramStore(
            programs = mutableListOf(
                fixtureProgramMonth(YearMonth.of(2026, 4)),
                fixtureProgramMonth(YearMonth.of(2026, 5)),
            ),
        )
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        val result = useCase(2026, 6, referenceDate)

        val left = assertIs<Either.Left<DomainError>>(result).value
        assertEquals(DomainError.MeseFuoriFinestraCreazione, left)
        Unit
    }

    // 8. Data inizio e fine programma calcolata correttamente (primo lunedì del mese → ultima domenica)
    @Test
    fun `created program has correct start and end dates`() = runTest {
        val store = InMemoryProgramStore()
        val useCase = CreaProssimoProgrammaUseCase(store, PassthroughTransactionRunner)

        // March 2026: first day is Sunday → first Monday is March 2
        val result = useCase(2026, 3, referenceDate)

        val created = assertIs<Either.Right<ProgramMonth>>(result).value
        // First Monday of March 2026 = March 2
        assertEquals(LocalDate.of(2026, 3, 2), created.startDate)
        // Last day of March 2026 is Tuesday March 31 → next or same Sunday = April 5
        assertEquals(LocalDate.of(2026, 4, 5), created.endDate)
        Unit
    }
}

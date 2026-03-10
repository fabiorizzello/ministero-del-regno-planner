package org.example.project.feature.diagnostics

import kotlinx.coroutines.test.runTest
import org.example.project.feature.diagnostics.application.ContaStoricoUseCase
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ContaStoricoUseCaseTest {

    @Test
    fun `returns counts from store`() = runTest {
        val store = FakeDiagnosticsStore(
            weekPlansCount = 5L,
            weeklyPartsCount = 12L,
            assignmentsCount = 30L,
        )
        val useCase = ContaStoricoUseCase(store)

        val result = useCase(LocalDate.of(2026, 1, 1))

        assertEquals(5, result.weekPlans)
        assertEquals(12, result.weeklyParts)
        assertEquals(30, result.assignments)
    }

    @Test
    fun `passes correct cutoff date to store`() = runTest {
        val store = FakeDiagnosticsStore()
        val useCase = ContaStoricoUseCase(store)
        val cutoff = LocalDate.of(2026, 3, 15)

        useCase(cutoff)

        assertEquals(listOf(cutoff), store.countCalls)
    }

    @Test
    fun `hasData is false when all counts are zero`() = runTest {
        val store = FakeDiagnosticsStore(
            weekPlansCount = 0L,
            weeklyPartsCount = 0L,
            assignmentsCount = 0L,
        )
        val useCase = ContaStoricoUseCase(store)

        val result = useCase(LocalDate.of(2026, 1, 1))

        assertEquals(false, result.hasData)
    }

    @Test
    fun `hasData is true when any count is positive`() = runTest {
        val store = FakeDiagnosticsStore(
            weekPlansCount = 1L,
            weeklyPartsCount = 0L,
            assignmentsCount = 0L,
        )
        val useCase = ContaStoricoUseCase(store)

        val result = useCase(LocalDate.of(2026, 1, 1))

        assertEquals(true, result.hasData)
    }
}

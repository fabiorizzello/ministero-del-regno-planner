package org.example.project.feature.diagnostics

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.diagnostics.application.EliminaStoricoUseCase
import org.example.project.feature.diagnostics.application.EliminazioneStoricoResult
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EliminaStoricoUseCaseTest {

    private val cutoff = LocalDate.of(2026, 1, 1)

    @Test
    fun `happy path - deletes week plans and returns Right with vacuum result`() = runBlocking {
        val store = FakeDiagnosticsStore()
        var vacuumCalled = false
        val useCase = EliminaStoricoUseCase(
            store = store,
            transactionRunner = PassthroughTransactionRunner,
            vacuumDatabase = { vacuumCalled = true; true },
        )

        val result = useCase(cutoff)

        assertIs<Either.Right<EliminazioneStoricoResult>>(result)
        assertEquals(true, result.value.vacuumExecuted)
        assertTrue(vacuumCalled)
        assertEquals(listOf(cutoff), store.deleteCalls)
    }

    @Test
    fun `passes correct cutoff date to store delete`() = runBlocking {
        val store = FakeDiagnosticsStore()
        val useCase = EliminaStoricoUseCase(
            store = store,
            transactionRunner = PassthroughTransactionRunner,
            vacuumDatabase = { true },
        )
        val specificDate = LocalDate.of(2025, 6, 15)

        useCase(specificDate)

        assertEquals(listOf(specificDate), store.deleteCalls)
    }

    @Test
    fun `vacuum failure returns Right with vacuumExecuted false`() = runBlocking {
        val store = FakeDiagnosticsStore()
        val useCase = EliminaStoricoUseCase(
            store = store,
            transactionRunner = PassthroughTransactionRunner,
            vacuumDatabase = { false },
        )

        val result = useCase(cutoff)

        assertIs<Either.Right<EliminazioneStoricoResult>>(result)
        assertEquals(false, result.value.vacuumExecuted)
    }

    @Test
    fun `vacuum is called after transaction completes`() = runBlocking {
        val callOrder = mutableListOf<String>()
        val store = object : FakeDiagnosticsStore() {
            context(tx: TransactionScope)
            override suspend fun deleteWeekPlansBeforeDate(cutoffDate: LocalDate) {
                callOrder += "delete"
                super.deleteWeekPlansBeforeDate(cutoffDate)
            }
        }
        val useCase = EliminaStoricoUseCase(
            store = store,
            transactionRunner = PassthroughTransactionRunner,
            vacuumDatabase = { callOrder += "vacuum"; true },
        )

        useCase(cutoff)

        assertEquals(listOf("delete", "vacuum"), callOrder)
    }

    @Test
    fun `default vacuumDatabase lambda returns false without real DB`() = runBlocking {
        val store = FakeDiagnosticsStore()
        val useCase = EliminaStoricoUseCase(
            store = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase(cutoff)

        assertIs<Either.Right<EliminazioneStoricoResult>>(result)
        assertEquals(false, result.value.vacuumExecuted)
    }
}

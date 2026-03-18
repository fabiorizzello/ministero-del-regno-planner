package org.example.project.feature.schemas

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.schemas.application.ArchivaAnomalieSchemaUseCase
import org.example.project.feature.schemas.application.SchemaUpdateAnomaly
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyDraft
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArchivaAnomalieSchemaUseCaseTest {

    @Test
    fun `happy path dismisses all open anomalies and returns Right`() = runTest {
        var dismissed = false
        val store = object : SchemaUpdateAnomalyStore {
            context(tx: TransactionScope)
            override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {}
            override suspend fun listOpen(): List<SchemaUpdateAnomaly> = emptyList()
            context(tx: TransactionScope)
            override suspend fun dismissAllOpen() { dismissed = true }
        }
        val useCase = ArchivaAnomalieSchemaUseCase(
            store = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase()

        assertIs<Either.Right<Unit>>(result)
        assertTrue(dismissed)
        Unit
    }

    @Test
    fun `store exception is mapped to Validation domain error`() = runTest {
        val store = object : SchemaUpdateAnomalyStore {
            context(tx: TransactionScope)
            override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) {}
            override suspend fun listOpen(): List<SchemaUpdateAnomaly> = emptyList()
            context(tx: TransactionScope)
            override suspend fun dismissAllOpen() { error("connection lost") }
        }
        val useCase = ArchivaAnomalieSchemaUseCase(
            store = store,
            transactionRunner = PassthroughTransactionRunner,
        )

        val result = useCase()

        val left = assertIs<Either.Left<DomainError>>(result).value
        val validation = assertIs<DomainError.Validation>(left)
        assertEquals("connection lost", validation.message)
        Unit
    }
}

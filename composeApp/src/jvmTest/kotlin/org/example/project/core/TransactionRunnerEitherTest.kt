package org.example.project.core

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import org.example.project.core.domain.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionRunnerEitherTest {

    private val runner = PassthroughTransactionRunner

    @Test
    fun `runInTransactionEither returns Right on success`() = runBlocking {
        val result = runner.runInTransactionEither { Either.Right(42) }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `runInTransactionEither returns Left on domain error`() = runBlocking {
        val result = runner.runInTransactionEither<Int> {
            Either.Left(DomainError.NotFound("test"))
        }
        assertEquals(Either.Left(DomainError.NotFound("test")), result)
    }

    @Test
    fun `runInTransactionEither wraps exception as Validation`() = runBlocking {
        val result = runner.runInTransactionEither<Int> {
            error("boom")
        }
        assertEquals(Either.Left(DomainError.Validation("boom")), result)
    }

    @Test
    fun `runInTransactionEither preserves Left error type`() = runBlocking {
        val result = runner.runInTransactionEither<Int> {
            Either.Left(DomainError.ProclamatoreDuplicato)
        }
        assertEquals(Either.Left(DomainError.ProclamatoreDuplicato), result)
    }

    @Test
    fun `runInTransactionEither with either block and bind`() = runBlocking {
        val result = runner.runInTransactionEither {
            either {
                val value = Either.Right("hello").bind()
                "$value world"
            }
        }
        assertEquals(Either.Right("hello world"), result)
    }

    @Test
    fun `runInTransactionEither with either block propagates bind failure`() = runBlocking {
        val result = runner.runInTransactionEither<String> {
            either {
                Either.Left(DomainError.ProclamatoreDuplicato).bind()
                "unreachable"
            }
        }
        assertEquals(Either.Left(DomainError.ProclamatoreDuplicato), result)
    }

    @Test
    fun `runInTransactionEither with either block propagates raise`() = runBlocking {
        val result = runner.runInTransactionEither<String> {
            either {
                raise(DomainError.NotFound("test"))
            }
        }
        assertEquals(Either.Left(DomainError.NotFound("test")), result)
    }

    @Test
    fun `runInTransactionEither delegates to runInTransaction`() = runBlocking {
        val counting = CountingTransactionRunner()
        val result = counting.runInTransactionEither { Either.Right("done") }
        assertEquals(Either.Right("done"), result)
        assertEquals(1, counting.calls)
    }
}

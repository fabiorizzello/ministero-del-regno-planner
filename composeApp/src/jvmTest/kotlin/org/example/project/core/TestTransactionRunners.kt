package org.example.project.core

import org.example.project.core.persistence.DefaultTransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope

/**
 * Executes the block immediately without real transaction management.
 * Use for unit tests that don't need transaction verification.
 */
internal object PassthroughTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T =
        with(DefaultTransactionScope) { block() }
}

/**
 * Tracks how many times runInTransaction is called.
 * Use for tests that verify transaction boundaries.
 */
internal class CountingTransactionRunner : TransactionRunner {
    var calls: Int = 0
        private set

    override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T {
        calls += 1
        return with(DefaultTransactionScope) { block() }
    }
}

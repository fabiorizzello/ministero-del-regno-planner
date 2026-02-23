package org.example.project.core.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.db.MinisteroDatabase

interface TransactionRunner {
    /**
     * Runs the given [block] within a database transaction.
     *
     * [block] should only call synchronous JDBC-backed store methods.
     * Although it is declared `suspend`, the body is executed inside
     * [kotlinx.coroutines.runBlocking] so that SQLDelight's
     * `TransactionWithoutReturn` callback (a non-suspend lambda) can
     * invoke it.  Calling real suspending I/O (network, delay, channel)
     * inside [block] will block the thread and may deadlock.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

class SqlDelightTransactionRunner(
    private val database: MinisteroDatabase,
) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            var result: T? = null
            var thrown: Throwable? = null
            database.ministeroDatabaseQueries.transaction {
                try {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    result = kotlinx.coroutines.runBlocking { block() }
                } catch (e: Throwable) {
                    thrown = e
                    rollback()
                }
            }
            thrown?.let { throw it }
            @Suppress("UNCHECKED_CAST")
            result as T
        }
    }
}

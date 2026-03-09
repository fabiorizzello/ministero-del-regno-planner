package org.example.project.core.persistence

import org.example.project.db.MinisteroDatabase

/** Marker scope disponibile solo durante [TransactionRunner.runInTransaction]. */
interface TransactionScope

/** Default transaction scope implementation. */
object DefaultTransactionScope : TransactionScope

interface TransactionRunner {
    /**
     * Runs the given block within a database transaction.
     * The block can call suspend store methods — they're synchronous JDBC
     * calls under the hood with the same shared SQLite connection.
     */
    suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T
}

class SqlDelightTransactionRunner(
    private val database: MinisteroDatabase,
) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T {
        var result: T? = null
        var thrown: Throwable? = null
        database.ministeroDatabaseQueries.transaction {
            try {
                // Safe: store methods declared suspend but are synchronous JDBC calls
                @Suppress("BlockingMethodInNonBlockingContext")
                result = kotlinx.coroutines.runBlocking {
                    with(DefaultTransactionScope) { block() }
                }
            } catch (e: Throwable) {
                thrown = e
                rollback()
            }
        }
        thrown?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

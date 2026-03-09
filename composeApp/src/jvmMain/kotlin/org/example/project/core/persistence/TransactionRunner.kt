package org.example.project.core.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T =
        withContext(Dispatchers.IO) {
            var result: T? = null
            var thrown: Throwable? = null
            database.ministeroDatabaseQueries.transaction {
                try {
                    // SQLDelight's transaction{} callback is a non-suspend lambda, so we must
                    // bridge back to coroutines with runBlocking. This is safe here because
                    // withContext(Dispatchers.IO) guarantees we are already on an IO thread —
                    // blocking an IO thread is the intended use of that dispatcher.
                    result = runBlocking(coroutineContext) {
                        with(DefaultTransactionScope) { block() }
                    }
                } catch (e: Throwable) {
                    thrown = e
                    rollback()
                }
            }
            thrown?.let { throw it }
            // result is always non-null here: if block() returned null (T = Nothing? / nullable),
            // result holds that null; if block() threw, we rethrew above. The T? declaration is
            // a Kotlin limitation — we cannot initialise a generic `var result: T` without a value.
            @Suppress("UNCHECKED_CAST")
            result as T
        }
}

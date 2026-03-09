package org.example.project.feature.people

import org.example.project.core.persistence.DefaultTransactionScope
import org.example.project.core.persistence.TransactionRunner
import org.example.project.core.persistence.TransactionScope

internal object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend TransactionScope.() -> T): T =
        with(DefaultTransactionScope) { block() }
}

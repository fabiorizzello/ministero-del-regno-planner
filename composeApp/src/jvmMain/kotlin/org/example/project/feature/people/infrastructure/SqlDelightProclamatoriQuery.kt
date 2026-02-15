package org.example.project.feature.people.infrastructure

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.example.project.db.MinisteroDatabase
import org.example.project.feature.people.application.ProclamatoriQuery
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId

class SqlDelightProclamatoriQuery(
    private val database: MinisteroDatabase,
) : ProclamatoriQuery {

    override suspend fun cerca(termine: String?): List<Proclamatore> {
        val clean = termine?.trim().orEmpty()
        val includeAll = if (clean.isBlank()) 1L else 0L
        val query = database.ministeroDatabaseQueries.searchProclaimers(
            includeAll,
            clean,
            clean,
            clean,
            ::mapProclamatoreRow,
        )
        return query.asFlow().mapToList(Dispatchers.IO).first()
    }

    override suspend fun esisteConNomeCognome(nome: String, cognome: String, esclusoId: ProclamatoreId?): Boolean {
        val count = if (esclusoId == null) {
            database.ministeroDatabaseQueries.countProclaimersByFullName(
                first_name = nome.trim(),
                last_name = cognome.trim(),
            ).executeAsOne()
        } else {
            database.ministeroDatabaseQueries.countProclaimersByFullNameExcludingId(
                first_name = nome.trim(),
                last_name = cognome.trim(),
                id = esclusoId.value,
            ).executeAsOne()
        }
        return count > 0L
    }
}

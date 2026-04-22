package org.example.project.core.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.example.project.core.config.AppRuntime
import org.example.project.db.MinisteroDatabase

object DatabaseProvider {
    @Volatile
    private var instance: MinisteroDatabase? = null

    fun database(): MinisteroDatabase {
        return instance ?: synchronized(this) {
            instance ?: createDatabase().also { instance = it }
        }
    }

    private fun createDatabase(): MinisteroDatabase {
        val dbPath = AppRuntime.paths().dbFile.toAbsolutePath().toString()
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$dbPath",
            schema = MinisteroDatabase.Schema,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        val db = MinisteroDatabase(driver)
        // Seed canonical partTypes on every boot. On a fresh install Schema.create()
        // runs CREATE TABLE only (no migrations), so migration 1.sqm's upserts never
        // execute. These upserts are idempotent (INSERT ... ON CONFLICT DO UPDATE),
        // safe to re-run, and self-heal any drift.
        seedCanonicalPartTypes(db)
        return db
    }

    private fun seedCanonicalPartTypes(db: MinisteroDatabase) {
        with(db.ministeroDatabaseQueries) {
            seedPartTypeLetturaDellaBibbia()
            seedPartTypeIniziareConversazione()
            seedPartTypeColtivareInteresse()
            seedPartTypeFareDiscepoli()
            seedPartTypeDiscorso()
            seedPartTypeSpiegareCioCheSiCrede()
            seedPartTypeSpiegareCioCheSiCredeDiscorso()
        }
    }
}

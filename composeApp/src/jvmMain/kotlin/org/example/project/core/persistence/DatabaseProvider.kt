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
        return MinisteroDatabase(driver)
    }
}

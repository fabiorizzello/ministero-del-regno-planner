package org.example.project.core.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UserConfigStoreTest {
    @Test
    fun `load returns default config when file is missing`() {
        val rootDir = Files.createTempDirectory("user-config-store-test")
        val store = UserConfigStore(
            file = rootDir.resolve("user-config.json"),
            defaultDatabaseFile = rootDir.resolve("data").resolve("ministero.sqlite"),
        )

        assertEquals(null, store.load().databaseFilePath)
    }

    @Test
    fun `save and load custom database path`() {
        val rootDir = Files.createTempDirectory("user-config-store-test")
        val store = UserConfigStore(
            file = rootDir.resolve("user-config.json"),
            defaultDatabaseFile = rootDir.resolve("data").resolve("ministero.sqlite"),
        )
        val customDbFile = rootDir.resolve("custom").resolve("shared.sqlite")

        store.saveDatabaseFile(customDbFile)

        assertEquals(
            customDbFile.toAbsolutePath().normalize().toString(),
            store.load().databaseFilePath,
        )
    }

    @Test
    fun `save default database path clears override`() {
        val rootDir = Files.createTempDirectory("user-config-store-test")
        val defaultDbFile = rootDir.resolve("data").resolve("ministero.sqlite")
        val store = UserConfigStore(
            file = rootDir.resolve("user-config.json"),
            defaultDatabaseFile = defaultDbFile,
        )

        store.saveDatabaseFile(defaultDbFile)

        assertEquals(null, store.load().databaseFilePath)
        assertFalse(Files.readString(rootDir.resolve("user-config.json")).contains(defaultDbFile.toString()))
    }
}

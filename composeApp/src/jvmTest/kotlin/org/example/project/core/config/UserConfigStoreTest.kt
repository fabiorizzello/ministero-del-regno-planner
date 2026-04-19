package org.example.project.core.config

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `save stores pending cleanup path when provided`() {
        val rootDir = Files.createTempDirectory("user-config-store-test")
        val defaultDbFile = rootDir.resolve("data").resolve("ministero.sqlite")
        val store = UserConfigStore(
            file = rootDir.resolve("user-config.json"),
            defaultDatabaseFile = defaultDbFile,
        )
        val customDbFile = rootDir.resolve("custom").resolve("shared.sqlite")
        val previousDbFile = rootDir.resolve("old").resolve("previous.sqlite")

        store.saveDatabaseFile(customDbFile, pendingCleanupPath = previousDbFile)

        val config = store.load()
        assertEquals(customDbFile.toAbsolutePath().normalize().toString(), config.databaseFilePath)
        assertEquals(previousDbFile.toAbsolutePath().normalize().toString(), config.pendingDatabaseCleanupPath)
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

    @Test
    fun `cleanup pending database files deletes db set and clears config field`() {
        val rootDir = Files.createTempDirectory("user-config-store-test")
        val defaultDbFile = rootDir.resolve("data").resolve("ministero.sqlite")
        val previousDbFile = rootDir.resolve("old").resolve("previous.sqlite")
        Files.createDirectories(previousDbFile.parent)
        Files.writeString(previousDbFile, "db")
        Files.writeString(previousDbFile.resolveSibling("${previousDbFile.fileName}-wal"), "wal")
        Files.writeString(previousDbFile.resolveSibling("${previousDbFile.fileName}-shm"), "shm")

        val store = UserConfigStore(
            file = rootDir.resolve("user-config.json"),
            defaultDatabaseFile = defaultDbFile,
        )
        store.saveDatabaseFile(rootDir.resolve("custom").resolve("active.sqlite"), pendingCleanupPath = previousDbFile)

        store.cleanupPendingDatabaseFiles()

        assertFalse(previousDbFile.exists())
        assertFalse(previousDbFile.resolveSibling("${previousDbFile.fileName}-wal").exists())
        assertFalse(previousDbFile.resolveSibling("${previousDbFile.fileName}-shm").exists())
        assertEquals(null, store.load().pendingDatabaseCleanupPath)
        assertTrue(store.load().databaseFilePath?.contains("active.sqlite") == true)
    }
}

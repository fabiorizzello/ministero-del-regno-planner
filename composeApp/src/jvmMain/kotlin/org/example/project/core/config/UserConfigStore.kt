package org.example.project.core.config

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UserConfig(
    val databaseFilePath: String? = null,
    val pendingDatabaseCleanupPath: String? = null,
)

class UserConfigStore(
    private val file: Path,
    private val defaultDatabaseFile: Path,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): UserConfig {
        if (!Files.exists(file) || !Files.isRegularFile(file)) return UserConfig()
        return runCatching {
            json.decodeFromString<UserConfig>(Files.readString(file))
        }.getOrElse {
            throw IOException("Impossibile leggere la configurazione utente: $file", it)
        }
    }

    fun saveDatabaseFile(path: Path?, pendingCleanupPath: Path? = null) {
        val normalizedDefault = defaultDatabaseFile.toAbsolutePath().normalize()
        val normalizedPath = path?.toAbsolutePath()?.normalize()
        val normalizedCleanupPath = pendingCleanupPath?.toAbsolutePath()?.normalize()
        val config = UserConfig(
            databaseFilePath = normalizedPath
                ?.takeUnless { it == normalizedDefault }
                ?.toString(),
            pendingDatabaseCleanupPath = normalizedCleanupPath
                ?.takeUnless { it == normalizedPath }
                ?.toString(),
        )
        save(config)
    }

    fun cleanupPendingDatabaseFiles() {
        val config = load()
        val pendingCleanupPath = config.pendingDatabaseCleanupPath
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: return

        deleteDatabaseFileSet(pendingCleanupPath)
        save(config.copy(pendingDatabaseCleanupPath = null))
    }

    private fun save(config: UserConfig) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(config))
    }

    private fun deleteDatabaseFileSet(dbFile: Path) {
        deleteIfExists(dbFile)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            deleteIfExists(Path.of("$dbFile$suffix"))
        }
    }

    private fun deleteIfExists(path: Path) {
        runCatching { Files.deleteIfExists(path) }
            .getOrElse { throw IOException("Impossibile eliminare il database precedente: $path", it) }
    }
}

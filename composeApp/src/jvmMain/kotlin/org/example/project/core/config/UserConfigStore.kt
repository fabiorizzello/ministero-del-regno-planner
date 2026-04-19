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

    fun saveDatabaseFile(path: Path?) {
        val normalizedDefault = defaultDatabaseFile.toAbsolutePath().normalize()
        val normalizedPath = path?.toAbsolutePath()?.normalize()
        val config = UserConfig(
            databaseFilePath = normalizedPath
                ?.takeUnless { it == normalizedDefault }
                ?.toString(),
        )
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(config))
    }
}

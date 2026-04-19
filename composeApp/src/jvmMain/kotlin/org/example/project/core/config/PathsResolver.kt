package org.example.project.core.config

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object PathsResolver {
    private const val APP_DIR_NAME = "EfficaciNelMinistero"
    private const val USER_CONFIG_FILE_NAME = "user-config.json"
    private val logger = KotlinLogging.logger {}
    private const val DB_FILE_NAME = "ministero.sqlite"

    fun resolve(): AppPaths {
        val rootDir = resolveRootDir()
        createDir(rootDir)
        val logsDir = rootDir.resolve("logs")
        val exportsDir = rootDir.resolve("exports")
        val defaultDbFile = defaultDatabaseFile(rootDir)
        val configStore = UserConfigStore(userConfigFile(rootDir), defaultDbFile)
        val configuredDbFile = runCatching {
            configStore.load().databaseFilePath
                ?.takeIf { it.isNotBlank() }
                ?.let(Paths::get)
                ?.toAbsolutePath()
                ?.normalize()
        }.onFailure { error ->
            logger.warn(error) { "Configurazione utente non valida, uso il database predefinito" }
        }.getOrNull()
        val dbFile = configuredDbFile ?: defaultDbFile

        createDir(logsDir)
        createDir(exportsDir)
        createDir(dbFile.parent)

        return AppPaths(
            rootDir = rootDir,
            dbFile = dbFile,
            logsDir = logsDir,
            exportsDir = exportsDir,
        )
    }

    private fun createDir(path: Path) {
        try {
            Files.createDirectories(path)
        } catch (e: IOException) {
            logger.error(e) { "Impossibile creare la directory: $path" }
            throw IOException("Impossibile creare la directory richiesta dall'applicazione: $path", e)
        }
    }

    private fun resolveRootDir(): Path {
        val userHome = Paths.get(System.getProperty("user.home")).normalize()
        val localAppData = System.getenv("LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            val candidate = Paths.get(localAppData, APP_DIR_NAME).normalize()
            if (candidate.startsWith(userHome)) {
                return candidate
            }
        }
        return userHome.resolve(".$APP_DIR_NAME")
    }

    fun resolveRootDirPath(): Path = resolveRootDir()

    fun defaultDatabaseFile(rootDir: Path = resolveRootDir()): Path =
        rootDir.resolve("data").resolve(DB_FILE_NAME)

    fun userConfigFile(rootDir: Path = resolveRootDir()): Path =
        rootDir.resolve(USER_CONFIG_FILE_NAME)
}

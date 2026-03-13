package org.example.project.feature.updates.application

import arrow.core.Either
import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.example.project.core.domain.DomainError

class AggiornaApplicazione(
    private val httpClient: HttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val externalUpdaterLauncher: (List<String>) -> Unit = { command ->
        ProcessBuilder(command).start()
    },
    private val currentProcessIdProvider: () -> Long = { ProcessHandle.current().pid() },
    private val bundledResourcesDirProvider: () -> Path? = {
        System.getProperty("compose.application.resources.dir")
            ?.takeIf(String::isNotBlank)
            ?.let(Paths::get)
    },
    private val appExecutableProvider: () -> Path = {
        resolveInstalledAppExecutable(
            bundledResourcesDir = System.getProperty("compose.application.resources.dir")
                ?.takeIf(String::isNotBlank)
                ?.let(Paths::get),
        )
    },
    private val updaterScriptBytesProvider: () -> ByteArray = {
        requireNotNull(AggiornaApplicazione::class.java.getResourceAsStream(UPDATER_SCRIPT_RESOURCE_PATH)) {
            "Risorsa updater non trovata: $UPDATER_SCRIPT_RESOURCE_PATH"
        }.use { input -> input.readBytes() }
    },
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(asset: UpdateAsset): Either<DomainError, UpdateInstallResult> = withContext(dispatcher) {
        either {
            val installerPath = downloadInstallerInternal(asset).bind()
            prepareInstallInternal(installerPath).bind()
        }
    }

    suspend fun downloadInstaller(asset: UpdateAsset): Either<DomainError, Path> = withContext(dispatcher) {
        either { downloadInstallerInternal(asset).bind() }
    }

    suspend fun preparaInstallazione(installerPath: Path): Either<DomainError, UpdateInstallResult> = withContext(dispatcher) {
        either { prepareInstallInternal(installerPath).bind() }
    }

    fun avviaInstallazionePreparata(installResult: UpdateInstallResult): Either<DomainError, Unit> = either {
        logger.info { "Avvio updater esterno: ${installResult.installerPath.toAbsolutePath()}" }
        Either.catch { externalUpdaterLauncher(installResult.updaterCommand) }
            .mapLeft { error ->
                DomainError.Validation("Avvio updater non riuscito: ${error.message ?: "errore sconosciuto"}")
            }
            .bind()
    }

    private suspend fun downloadInstallerInternal(asset: UpdateAsset): Either<DomainError, Path> = either {
        val updatesDir = prepareUpdatesDir().bind()
        cleanupStalePartialDownloads(updatesDir)
        val outputPath = updatesDir.resolve(asset.name)
        val partialPath = updatesDir.resolve("${asset.name}.part")

        if (isInstallerAlreadyCached(outputPath, asset)) {
            logger.info { "Riutilizzo installer gia scaricato: ${outputPath.toAbsolutePath()}" }
            outputPath
        } else {
            val localAssetPath = resolveLocalAssetPath(asset.downloadUrl)
            if (localAssetPath != null) {
                logger.info { "Copia aggiornamento da sorgente locale: ${localAssetPath.toAbsolutePath()}" }
                Either.catch {
                    if (!Files.isRegularFile(localAssetPath)) {
                        error("File locale non trovato: $localAssetPath")
                    }
                    Files.copy(
                        localAssetPath,
                        partialPath,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    try {
                        Files.move(
                            partialPath,
                            outputPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            partialPath,
                            outputPath,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                }.mapLeft { error ->
                    runCatching { Files.deleteIfExists(partialPath) }
                    DomainError.Validation("Errore copia aggiornamento locale: ${error.message ?: "errore sconosciuto"}")
                }.bind()
            } else {
                logger.info { "Download aggiornamento: ${asset.downloadUrl}" }
                val response = Either.catch { httpClient.get(asset.downloadUrl) }
                    .mapLeft { error ->
                        DomainError.Network("Download aggiornamento fallito: ${error.message ?: "Connessione fallita"}")
                    }
                    .bind()
                if (!response.status.isSuccess()) {
                    raise(DomainError.Network("Download aggiornamento fallito: HTTP ${response.status.value}"))
                }
                val body = Either.catch { response.bodyAsBytes() }
                    .mapLeft { error ->
                        DomainError.Network("Download aggiornamento fallito: ${error.message ?: "Connessione fallita"}")
                    }
                    .bind()

                Either.catch {
                    Files.write(
                        partialPath,
                        body,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE,
                    )
                    try {
                        Files.move(
                            partialPath,
                            outputPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            partialPath,
                            outputPath,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                }.mapLeft { error ->
                    runCatching { Files.deleteIfExists(partialPath) }
                    DomainError.Validation("Errore salvataggio aggiornamento: ${error.message ?: "errore sconosciuto"}")
                }.bind()
            }

            logger.info { "Aggiornamento scaricato in ${outputPath.toAbsolutePath()}" }
            outputPath
        }
    }

    private fun prepareInstallInternal(installerPath: Path): Either<DomainError, UpdateInstallResult> = either {
        val updatesDir = installerPath.parent ?: prepareUpdatesDir().bind()
        val updaterScriptPath = prepareExternalUpdaterScript(updatesDir).bind()
        val appExecutable = Either.catch { appExecutableProvider() }
            .mapLeft { error ->
                DomainError.Validation("Launcher applicazione non trovato: ${error.message ?: "errore sconosciuto"}")
            }
            .bind()
        val updaterLogPath = updatesDir.resolve(UPDATER_LOG_FILE_NAME)
        val command = listOf(
            "powershell.exe",
            "-NoProfile",
            "-STA",
            "-ExecutionPolicy",
            "Bypass",
            "-WindowStyle",
            "Hidden",
            "-File",
            updaterScriptPath.toAbsolutePath().toString(),
            "-InstallerPath",
            installerPath.toAbsolutePath().toString(),
            "-AppExecutable",
            appExecutable.toAbsolutePath().toString(),
            "-AppPid",
            currentProcessIdProvider().toString(),
            "-LogPath",
            updaterLogPath.toAbsolutePath().toString(),
        )

        logger.info { "Updater esterno preparato: ${updaterScriptPath.toAbsolutePath()}" }
        logger.info { "Riavvio richiesto per completare l'aggiornamento: ${installerPath.toAbsolutePath()}" }

        UpdateInstallResult(
            installerPath = installerPath,
            restartRequired = true,
            updaterCommand = command,
        )
    }

    private fun prepareExternalUpdaterScript(updatesDir: Path): Either<DomainError, Path> =
        Either.catch {
            val targetPath = updatesDir.resolve(UPDATER_SCRIPT_FILE_NAME)
            val installedCopy = bundledResourcesDirProvider()?.resolve(UPDATER_SCRIPT_FILE_NAME)
            if (installedCopy != null && Files.exists(installedCopy)) {
                Files.copy(installedCopy, targetPath, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.write(
                    targetPath,
                    updaterScriptBytesProvider(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }
            targetPath
        }.mapLeft { error ->
            DomainError.Validation("Preparazione updater non riuscita: ${error.message ?: "errore sconosciuto"}")
        }

    private fun isInstallerAlreadyCached(outputPath: Path, asset: UpdateAsset): Boolean {
        if (!Files.isRegularFile(outputPath)) return false
        return runCatching {
            val currentSize = Files.size(outputPath)
            asset.sizeBytes <= 0L || currentSize == asset.sizeBytes
        }.getOrDefault(false)
    }

    private fun resolveLocalAssetPath(downloadUrl: String): Path? =
        runCatching {
            when {
                downloadUrl.startsWith("file:/", ignoreCase = true) -> Paths.get(java.net.URI(downloadUrl))
                Regex("^[a-zA-Z]:\\\\").containsMatchIn(downloadUrl) -> Paths.get(downloadUrl)
                else -> null
            }
        }.getOrNull()

    private fun prepareUpdatesDir(): Either<DomainError, Path> =
        Either.catch {
            AppRuntime.paths().exportsDir.resolve("updates").also { Files.createDirectories(it) }
        }.mapLeft { error ->
            DomainError.Validation("Impossibile preparare la cartella aggiornamenti: ${error.message ?: "errore sconosciuto"}")
        }

    private fun cleanupStalePartialDownloads(updatesDir: Path) {
        runCatching {
            Files.list(updatesDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".part") }
                    .forEach { Files.deleteIfExists(it) }
            }
        }.onFailure { error ->
            logger.warn { "Cleanup download parziali non riuscito: ${error.message}" }
        }
    }

    private companion object {
        private const val APP_EXECUTABLE_NAME = "scuola-di-ministero.exe"
        private const val UPDATER_LOG_FILE_NAME = "external-updater.log"
        private const val UPDATER_SCRIPT_FILE_NAME = "external-updater.ps1"
        private const val UPDATER_SCRIPT_RESOURCE_PATH = "/updater/external-updater.ps1"

        private fun resolveInstalledAppExecutable(bundledResourcesDir: Path?): Path {
            val appDir = when {
                bundledResourcesDir != null -> bundledResourcesDir.parent
                else -> {
                    val jarLocation = AggiornaApplicazione::class.java.protectionDomain.codeSource.location.toURI()
                    Paths.get(jarLocation).parent
                }
            } ?: error("Cartella app non determinabile.")

            val installRoot = appDir.parent ?: error("Root installazione non determinabile.")
            return installRoot.resolve(APP_EXECUTABLE_NAME)
        }
    }
}

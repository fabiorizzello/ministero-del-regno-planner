package org.example.project.feature.updates.application

import arrow.core.Either
import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
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

    suspend fun downloadInstaller(
        asset: UpdateAsset,
        onProgress: (UpdateDownloadProgress) -> Unit = {},
    ): Either<DomainError, Path> = withContext(dispatcher) {
        either { downloadInstallerInternal(asset, onProgress).bind() }
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

    private suspend fun downloadInstallerInternal(
        asset: UpdateAsset,
        onProgress: (UpdateDownloadProgress) -> Unit = {},
    ): Either<DomainError, Path> = either {
        val updatesDir = prepareUpdatesDir().bind()
        cleanupStalePartialDownloads(updatesDir)
        val outputPath = updatesDir.resolve(asset.name)
        val partialPath = updatesDir.resolve("${asset.name}.part")

        if (isInstallerAlreadyCached(outputPath, asset)) {
            logger.info { "Riutilizzo installer gia scaricato: ${outputPath.toAbsolutePath()}" }
            val cachedSize = runCatching { Files.size(outputPath) }.getOrDefault(asset.sizeBytes)
            onProgress(
                UpdateDownloadProgress(
                    downloadedBytes = cachedSize,
                    totalBytes = asset.sizeBytes.takeIf { it > 0L } ?: cachedSize,
                ),
            )
            outputPath
        } else {
            val localAssetPath = resolveLocalAssetPath(asset.downloadUrl)
            if (localAssetPath != null) {
                logger.info { "Copia aggiornamento da sorgente locale: ${localAssetPath.toAbsolutePath()}" }
                val localSize = runCatching { Files.size(localAssetPath) }.getOrDefault(asset.sizeBytes)
                onProgress(UpdateDownloadProgress(0L, localSize.takeIf { it > 0L }))
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
                    onProgress(UpdateDownloadProgress(localSize, localSize.takeIf { it > 0L }))
                }.mapLeft { error ->
                    runCatching { Files.deleteIfExists(partialPath) }
                    DomainError.Validation("Errore copia aggiornamento locale: ${error.message ?: "errore sconosciuto"}")
                }.bind()
            } else {
                logger.info { "Download aggiornamento: ${asset.downloadUrl}" }
                val response = Either.catch {
                    httpClient.get(asset.downloadUrl) {
                        timeout {
                            requestTimeoutMillis = DOWNLOAD_REQUEST_TIMEOUT_MILLIS
                        }
                    }
                }
                    .mapLeft { error ->
                        DomainError.Network("Download aggiornamento fallito: ${error.message ?: "Connessione fallita"}")
                    }
                    .bind()
                if (!response.status.isSuccess()) {
                    raise(DomainError.Network("Download aggiornamento fallito: HTTP ${response.status.value}"))
                }
                val declaredSize = response.headers["Content-Length"]?.toLongOrNull()
                    ?: asset.sizeBytes.takeIf { it > 0L }
                onProgress(UpdateDownloadProgress(0L, declaredSize))

                Either.catch {
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(max(DEFAULT_BUFFER_SIZE, 16 * 1024))
                    var downloadedBytes = 0L
                    Files.newOutputStream(
                        partialPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE,
                    ).use { output ->
                        while (true) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress(UpdateDownloadProgress(downloadedBytes, declaredSize))
                        }
                    }
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
                    val finalSize = runCatching { Files.size(outputPath) }.getOrDefault(declaredSize ?: 0L)
                    onProgress(UpdateDownloadProgress(finalSize, declaredSize ?: finalSize))
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
        private const val DOWNLOAD_REQUEST_TIMEOUT_MILLIS = 30 * 60 * 1_000L
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

package org.example.project.feature.updates.application

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.example.project.core.domain.DomainError
import io.github.oshai.kotlinlogging.KotlinLogging

class AggiornaApplicazione(
    private val httpClient: HttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val installCommandRunner: (List<String>) -> Int = { command ->
        ProcessBuilder(command).start().waitFor()
    },
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(asset: UpdateAsset): Either<DomainError, UpdateInstallResult> = withContext(dispatcher) {
        either {
            val installerPath = downloadInstallerInternal(asset).bind()
            installSilenziosoInternal(installerPath).bind()
        }
    }

    suspend fun downloadInstaller(asset: UpdateAsset): Either<DomainError, Path> = withContext(dispatcher) {
        either { downloadInstallerInternal(asset).bind() }
    }

    suspend fun installaSilenzioso(installerPath: Path): Either<DomainError, UpdateInstallResult> = withContext(dispatcher) {
        either { installSilenziosoInternal(installerPath).bind() }
    }

    private suspend fun downloadInstallerInternal(asset: UpdateAsset): Either<DomainError, Path> = either {
        val updatesDir = prepareUpdatesDir().bind()
        cleanupStalePartialDownloads(updatesDir)
        val outputPath = updatesDir.resolve(asset.name)
        val partialPath = updatesDir.resolve("${asset.name}.part")

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

        logger.info { "Aggiornamento scaricato in ${outputPath.toAbsolutePath()}" }
        outputPath
    }

    private fun installSilenziosoInternal(installerPath: Path): Either<DomainError, UpdateInstallResult> = either {
        logger.info { "Installazione silenziosa aggiornamento: ${installerPath.toAbsolutePath()}" }
        val command = listOf(
            "msiexec",
            "/i",
            installerPath.toAbsolutePath().toString(),
            "/qn",
            "/norestart",
        )
        val exitCode = Either.catch { installCommandRunner(command) }
            .mapLeft { error ->
                DomainError.Validation("Avvio installazione non riuscito: ${error.message ?: "errore sconosciuto"}")
            }
            .bind()
        if (exitCode != 0) {
            raise(DomainError.Validation("Installazione non riuscita (codice $exitCode)"))
        }

        runCatching { Files.deleteIfExists(installerPath) }
            .onFailure { error ->
                logger.warn { "Cleanup installer non riuscito (${installerPath.toAbsolutePath()}): ${error.message}" }
            }

        UpdateInstallResult(
            installerPath = installerPath,
            restartRequired = true,
        )
    }

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
}

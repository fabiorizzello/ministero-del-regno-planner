package org.example.project.feature.updates.application

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
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
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(asset: UpdateAsset): Either<DomainError, Path> = withContext(dispatcher) {
        either {
            val updatesDir = Either.catch {
                AppRuntime.paths().exportsDir.resolve("updates").also { Files.createDirectories(it) }
            }.mapLeft { error ->
                DomainError.Validation("Impossibile preparare la cartella aggiornamenti: ${error.message ?: "errore sconosciuto"}")
            }.bind()
            val outputPath = updatesDir.resolve(asset.name)

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
                    outputPath,
                    body,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }.mapLeft { error ->
                DomainError.Validation("Errore salvataggio aggiornamento: ${error.message ?: "errore sconosciuto"}")
            }.bind()

            logger.info { "Aggiornamento scaricato in ${outputPath.toAbsolutePath()}" }
            openFile(outputPath)
            outputPath
        }
    }

    private fun openFile(path: Path) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile())
            }
        }.onFailure { error ->
            logger.warn { "Apertura installer non riuscita: ${error.message}" }
        }
    }
}

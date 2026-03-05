package org.example.project.feature.updates.application

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
import mu.KotlinLogging

class AggiornaApplicazione(
    private val httpClient: HttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = KotlinLogging.logger {}

    suspend operator fun invoke(asset: UpdateAsset): Path = withContext(dispatcher) {
        val updatesDir = AppRuntime.paths().exportsDir.resolve("updates")
        Files.createDirectories(updatesDir)
        val outputPath = updatesDir.resolve(asset.name)

        logger.info("Download aggiornamento: {}", asset.downloadUrl)
        val response = httpClient.get(asset.downloadUrl)
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Download aggiornamento fallito: HTTP ${response.status.value}")
        }

        Files.write(
            outputPath,
            response.bodyAsBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        logger.info("Aggiornamento scaricato in {}", outputPath.toAbsolutePath())
        openFile(outputPath)
        outputPath
    }

    private fun openFile(path: Path) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile())
            } else {
                ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start()
            }
        }.onFailure { error ->
            logger.warn("Apertura installer non riuscita: {}", error.message)
        }
    }
}

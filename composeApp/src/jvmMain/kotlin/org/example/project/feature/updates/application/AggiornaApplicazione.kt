package org.example.project.feature.updates.application

import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.core.config.AppRuntime
import org.slf4j.LoggerFactory

class AggiornaApplicazione(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(AggiornaApplicazione::class.java)
    private val httpClient = HttpClient.newBuilder().build()

    suspend operator fun invoke(asset: UpdateAsset): Path = withContext(dispatcher) {
        val updatesDir = AppRuntime.paths().exportsDir.resolve("updates")
        Files.createDirectories(updatesDir)
        val outputPath = updatesDir.resolve(asset.name)

        logger.info("Download aggiornamento: {}", asset.downloadUrl)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(asset.downloadUrl))
            .header("User-Agent", "EfficaciNelMinistero")
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Download aggiornamento fallito: HTTP ${response.statusCode()}")
        }

        Files.write(
            outputPath,
            response.body(),
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

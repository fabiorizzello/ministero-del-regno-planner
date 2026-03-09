package org.example.project.feature.output.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.project.feature.output.application.FileOpener
import java.awt.Desktop
import java.nio.file.Path

class DesktopFileOpener : FileOpener {
    private val logger = KotlinLogging.logger {}

    override fun open(path: Path) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile())
            } else {
                ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start()
            }
        }.onFailure { error ->
            logger.warn { "Apertura file non riuscita (${path.fileName}): ${error.message}" }
        }
    }
}

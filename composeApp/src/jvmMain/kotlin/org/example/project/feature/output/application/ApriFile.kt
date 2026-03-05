package org.example.project.feature.output.application

import mu.KotlinLogging
import java.awt.Desktop
import java.nio.file.Path

private val logger = KotlinLogging.logger("ApriFile")

internal fun apriFile(path: Path) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile())
        } else {
            // Fallback Linux senza Desktop support (es. alcune distro minimali)
            ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start()
        }
    }.onFailure { error ->
        logger.warn { "Apertura file non riuscita (${path.fileName}): ${error.message}" }
    }
}

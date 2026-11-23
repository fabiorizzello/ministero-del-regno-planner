package org.example.project.feature.output.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.project.feature.output.application.FileOpener
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.NotDirectoryException
import java.nio.file.Path

class DesktopFileOpener : FileOpener {
    private val logger = KotlinLogging.logger {}

    override fun open(path: Path) {
        runCatching { openPath(path) }.onFailure { error ->
            logger.warn { "Apertura file non riuscita (${path.fileName}): ${error.message}" }
        }
    }

    fun openDirectory(path: Path) {
        if (!Files.isDirectory(path)) throw NotDirectoryException(path.toString())
        openPath(path)
    }

    private fun openPath(path: Path) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop != null && desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(path.toFile())
        } else {
            val osName = System.getProperty("os.name", "")
            val command = when {
                osName.startsWith("Windows", ignoreCase = true) -> "explorer.exe"
                osName.startsWith("Mac", ignoreCase = true) -> "open"
                else -> "xdg-open"
            }
            ProcessBuilder(command, path.toAbsolutePath().toString()).start()
        }
    }
}

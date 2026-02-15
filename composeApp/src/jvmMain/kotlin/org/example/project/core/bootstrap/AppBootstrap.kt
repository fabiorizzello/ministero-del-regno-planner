package org.example.project.core.bootstrap

import org.example.project.core.config.AppRuntime
import org.example.project.core.config.AppVersion
import org.example.project.core.config.PathsResolver
import org.slf4j.LoggerFactory

object AppBootstrap {
    private var initialized = false

    fun initialize() {
        if (initialized) return

        val paths = PathsResolver.resolve()
        System.setProperty("app.log.dir", paths.logsDir.toAbsolutePath().toString())
        AppRuntime.initialize(paths)
        val logger = LoggerFactory.getLogger(AppBootstrap::class.java)

        logger.info("Avvio applicazione versione {}", AppVersion.current)
        logger.info("Percorso database {}", paths.dbFile.toAbsolutePath())
        logger.info("Percorso log {}", paths.logsDir.toAbsolutePath())
        initialized = true
    }
}

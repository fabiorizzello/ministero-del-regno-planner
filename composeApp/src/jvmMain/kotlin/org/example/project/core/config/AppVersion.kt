package org.example.project.core.config

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Properties

private val logger = KotlinLogging.logger {}

object AppVersion {
    val current: String by lazy {
        val stream = AppVersion::class.java.getResourceAsStream("/version.properties")
        if (stream == null) {
            logger.warn { "version.properties non trovato nel classpath — versione impostata a 'unknown'" }
            return@lazy "unknown"
        }
        val props = Properties()
        stream.use { props.load(it) }
        val version = props.getProperty("app.version")
        if (version == null) {
            logger.warn { "Key 'app.version' assente in version.properties — versione impostata a 'unknown'" }
            "unknown"
        } else {
            version
        }
    }
}

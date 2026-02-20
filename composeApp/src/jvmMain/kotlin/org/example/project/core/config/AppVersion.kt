package org.example.project.core.config

import java.util.Properties

object AppVersion {
    val current: String by lazy {
        val stream = AppVersion::class.java.getResourceAsStream("/version.properties")
        if (stream != null) {
            val props = Properties()
            stream.use { props.load(it) }
            props.getProperty("app.version", "unknown")
        } else {
            "unknown"
        }
    }
}

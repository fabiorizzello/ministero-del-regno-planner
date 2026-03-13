package org.example.project.core.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.github.oshai.kotlinlogging.KotlinLogging

private val httpLog = KotlinLogging.logger("AppHttpClient")

fun createAppHttpClient(): HttpClient = HttpClient(Java) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
    }
    install(Logging) {
        level = LogLevel.INFO
        logger = object : Logger {
            override fun log(message: String) = httpLog.debug { message }
        }
    }
    defaultRequest {
        headers.append("User-Agent", "EfficaciNelMinistero")
    }
}

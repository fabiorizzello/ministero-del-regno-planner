package org.example.project.feature.updates.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.oshai.kotlinlogging.KotlinLogging

private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L

class UpdateScheduler(
    private val scope: CoroutineScope,
    private val verificaAggiornamenti: VerificaAggiornamenti,
) {
    private val logger = KotlinLogging.logger {}
    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        if (job != null) return
        job = scope.launch {
            logger.info { "Avvio scheduler aggiornamenti" }
            while (isActive) {
                runCatching { verificaAggiornamenti() }
                    .onFailure { error -> logger.warn { "Scheduler update fallito: ${error.message}" } }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }
}

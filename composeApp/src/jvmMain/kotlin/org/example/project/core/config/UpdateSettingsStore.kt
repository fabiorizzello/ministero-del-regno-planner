package org.example.project.core.config

import com.russhwolf.settings.Settings
import java.time.Instant

class UpdateSettingsStore(
    private val settings: Settings,
) {
    fun loadLastCheck(): Instant? {
        val epoch = settings.getLong(KEY_LAST_CHECK, -1L)
        return if (epoch > 0L) Instant.ofEpochMilli(epoch) else null
    }

    fun saveLastCheck(instant: Instant) {
        settings.putLong(KEY_LAST_CHECK, instant.toEpochMilli())
    }

    companion object {
        private const val KEY_LAST_CHECK = "update.lastCheck"
    }
}

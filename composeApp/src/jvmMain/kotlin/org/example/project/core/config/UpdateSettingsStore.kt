package org.example.project.core.config

import com.russhwolf.settings.Settings
import java.time.Instant
import org.example.project.core.util.enumByName
import org.example.project.feature.updates.application.UpdateChannel

class UpdateSettingsStore(
    private val settings: Settings,
) {
    fun loadChannel(): UpdateChannel {
        val raw = settings.getString(KEY_CHANNEL, UpdateChannel.STABLE.name)
        return enumByName(raw, UpdateChannel.STABLE)
    }

    fun saveChannel(channel: UpdateChannel) {
        settings.putString(KEY_CHANNEL, channel.name)
    }

    fun loadLastCheck(): Instant? {
        val epoch = settings.getLong(KEY_LAST_CHECK, -1L)
        return if (epoch > 0L) Instant.ofEpochMilli(epoch) else null
    }

    fun saveLastCheck(instant: Instant) {
        settings.putLong(KEY_LAST_CHECK, instant.toEpochMilli())
    }

    companion object {
        private const val KEY_CHANNEL = "update.channel"
        private const val KEY_LAST_CHECK = "update.lastCheck"
    }
}

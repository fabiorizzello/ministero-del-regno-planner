package org.example.project.core.config

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.russhwolf.settings.Settings

data class WindowSettings(
    val widthDp: Int = 1400,
    val heightDp: Int = 900,
    val placement: WindowPlacement = WindowPlacement.Floating,
)

class WindowSettingsStore(
    private val settings: Settings,
) {
    fun load(): WindowSettings {
        val width = settings.getInt(KEY_WIDTH_DP, 1400).coerceIn(800, 4000)
        val height = settings.getInt(KEY_HEIGHT_DP, 900).coerceIn(600, 2400)
        val placement = settings.getString(KEY_PLACEMENT, WindowPlacement.Floating.name)
            .let { runCatching { WindowPlacement.valueOf(it) }.getOrNull() }
            ?: WindowPlacement.Floating

        return WindowSettings(
            widthDp = width,
            heightDp = height,
            placement = placement,
        )
    }

    fun save(windowSettings: WindowSettings) {
        settings.putInt(KEY_WIDTH_DP, windowSettings.widthDp)
        settings.putInt(KEY_HEIGHT_DP, windowSettings.heightDp)
        settings.putString(KEY_PLACEMENT, windowSettings.placement.name)
    }

    private companion object {
        const val KEY_WIDTH_DP = "window.widthDp"
        const val KEY_HEIGHT_DP = "window.heightDp"
        const val KEY_PLACEMENT = "window.placement"
    }
}

fun WindowState.toSettingsSnapshot(): WindowSettings {
    return WindowSettings(
        widthDp = size.width.value.toInt().coerceIn(800, 4000),
        heightDp = size.height.value.toInt().coerceIn(600, 2400),
        placement = placement,
    )
}

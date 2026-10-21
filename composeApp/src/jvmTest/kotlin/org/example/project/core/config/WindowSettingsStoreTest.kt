package org.example.project.core.config

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSettingsStoreTest {
    @Test
    fun `narrow window widths survive snapshot save and reload`() {
        val node = Preferences.userRoot().node("window-settings-test-${UUID.randomUUID()}")
        try {
            val store = WindowSettingsStore(PreferencesSettings(node))
            for (width in listOf(375, 600, 900, 1400)) {
                val snapshot = WindowState(size = DpSize(width.dp, 900.dp)).toSettingsSnapshot()
                store.save(snapshot)
                assertEquals(width, store.load().widthDp)
            }
            store.save(WindowSettings(widthDp = -10))
            assertEquals(1, store.load().widthDp)
        } finally {
            node.removeNode()
        }
    }
}

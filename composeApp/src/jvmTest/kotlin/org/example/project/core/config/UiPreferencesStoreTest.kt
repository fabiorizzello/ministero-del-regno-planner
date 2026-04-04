package org.example.project.core.config

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class UiPreferencesStoreTest {
    private fun store(): UiPreferencesStore {
        val node = Preferences.userRoot().node("ui-preferences-store-test-${UUID.randomUUID()}")
        return UiPreferencesStore(PreferencesSettings(node))
    }

    @Test
    fun `salva e ricarica ultima sezione`() {
        val store = store()

        store.saveLastSection("PROCLAMATORI")

        assertEquals("PROCLAMATORI", store.loadLastSection("PLANNING"))
    }

    @Test
    fun `salva e ricarica view mode studenti`() {
        val store = store()

        store.saveStudentsViewMode("CARDS")

        assertEquals("CARDS", store.loadStudentsViewMode("TABLE"))
    }

    @Test
    fun `salva e ricarica theme mode`() {
        val store = store()

        store.saveThemeMode("DARK")

        assertEquals("DARK", store.loadThemeMode("LIGHT"))
    }
}

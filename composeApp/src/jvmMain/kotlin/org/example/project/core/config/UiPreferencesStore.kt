package org.example.project.core.config

import com.russhwolf.settings.Settings

class UiPreferencesStore(
    private val settings: Settings,
) {
    fun loadLastSection(defaultSection: String): String =
        settings.getString(KEY_LAST_SECTION, defaultSection)

    fun saveLastSection(section: String) {
        settings.putString(KEY_LAST_SECTION, section)
    }

    fun loadStudentsViewMode(defaultMode: String): String =
        settings.getString(KEY_STUDENTS_VIEW_MODE, defaultMode)

    fun saveStudentsViewMode(mode: String) {
        settings.putString(KEY_STUDENTS_VIEW_MODE, mode)
    }

    fun loadThemeMode(defaultMode: String): String =
        settings.getString(KEY_THEME_MODE, defaultMode)

    fun saveThemeMode(mode: String) {
        settings.putString(KEY_THEME_MODE, mode)
    }

    private companion object {
        const val KEY_LAST_SECTION = "ui.lastSection"
        const val KEY_STUDENTS_VIEW_MODE = "ui.students.viewMode"
        const val KEY_THEME_MODE = "ui.theme.mode"
    }
}

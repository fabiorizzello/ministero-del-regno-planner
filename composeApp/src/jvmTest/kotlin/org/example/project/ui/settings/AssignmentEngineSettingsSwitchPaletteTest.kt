package org.example.project.ui.settings

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class AssignmentEngineSettingsSwitchPaletteTest {

    @Test
    fun `switch palette uses explicit high contrast theme colors`() {
        val scheme = darkColorScheme(
            primary = Color(0xFF006B4F),
            onPrimary = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF2C2F36),
            onSurface = Color(0xFFF2F4F8),
            outline = Color(0xFF8E98AB),
        )

        val palette = assignmentSettingsSwitchPalette(scheme)

        assertEquals(scheme.primary, palette.checkedTrackColor)
        assertEquals(scheme.onPrimary, palette.checkedThumbColor)
        assertEquals(scheme.surfaceVariant, palette.uncheckedTrackColor)
        assertEquals(scheme.onSurface, palette.uncheckedThumbColor)
        assertEquals(scheme.outline, palette.uncheckedBorderColor)
    }
}

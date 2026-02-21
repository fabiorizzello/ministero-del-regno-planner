package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val MinisteroLightColorScheme = lightColorScheme(
    primary = Color(0xFF1C63C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF002F62),
    secondary = Color(0xFF197A66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFEFE5),
    onSecondaryContainer = Color(0xFF0B3B30),
    tertiary = Color(0xFF9A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF321A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEAF1F8),
    onBackground = Color(0xFF182332),
    surface = Color(0xFFF5F8FC),
    onSurface = Color(0xFF1B2430),
    surfaceVariant = Color(0xFFDCE6F1),
    onSurfaceVariant = Color(0xFF3F4E61),
    outline = Color(0xFF748091),
    outlineVariant = Color(0xFFC6D0DD),
    inverseSurface = Color(0xFF2A313C),
    inverseOnSurface = Color(0xFFF0F3F7),
    inversePrimary = Color(0xFFA3D1FF),
    surfaceTint = Color(0xFF0F6CBD),
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides AppSpacing()) {
        MaterialTheme(
            colorScheme = MinisteroLightColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

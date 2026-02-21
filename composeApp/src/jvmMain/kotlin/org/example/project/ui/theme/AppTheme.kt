package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val MinisteroLightColorScheme = lightColorScheme(
    primary = Color(0xFF0F6CBD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8EAFE),
    onPrimaryContainer = Color(0xFF002A4A),
    secondary = Color(0xFF2B7A66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6F3E8),
    onSecondaryContainer = Color(0xFF0B3B30),
    tertiary = Color(0xFF9A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF321A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF19232F),
    surface = Color(0xFFFCFDFF),
    onSurface = Color(0xFF1B2430),
    surfaceVariant = Color(0xFFE7EDF6),
    onSurfaceVariant = Color(0xFF475467),
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

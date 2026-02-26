package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MinisteroLightColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    onPrimary = Color(0xFF1A1305),
    primaryContainer = Color(0xFF3B2B10),
    onPrimaryContainer = Color(0xFFFDE7BF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0E1A2B),
    secondaryContainer = Color(0xFF1C2A3F),
    onSecondaryContainer = Color(0xFFD6E6FB),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF072419),
    tertiaryContainer = Color(0xFF143629),
    onTertiaryContainer = Color(0xFFC7F6E4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5E1C1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1722),
    onBackground = Color(0xFFE8EDF6),
    surface = Color(0xFF172233),
    onSurface = Color(0xFFE8EDF6),
    surfaceVariant = Color(0xFF1D2A3D),
    onSurfaceVariant = Color(0xFF9FB0C8),
    outline = Color(0xFF2D3F59),
    outlineVariant = Color(0xFF2A3A52),
    inverseSurface = Color(0xFFE8EDF6),
    inverseOnSurface = Color(0xFF172233),
    inversePrimary = Color(0xFF8BC0FF),
    surfaceTint = Color(0xFFF59E0B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides AppSpacing()) {
        MaterialTheme(
            colorScheme = MinisteroLightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

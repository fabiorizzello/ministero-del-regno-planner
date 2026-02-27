package org.example.project.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MinisteroLightColorScheme = lightColorScheme(
    primary = Color(0xFF4F8DD8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5EBF7),
    onPrimaryContainer = Color(0xFF1E2D46),
    secondary = Color(0xFF2D9A63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDF3E7),
    onSecondaryContainer = Color(0xFF173524),
    tertiary = Color(0xFFCF8A24),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE8CD),
    onTertiaryContainer = Color(0xFF3A2507),
    error = Color(0xFFC43D3D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF571616),
    background = Color(0xFFF0F4FA),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF4F7FB),
    onSurfaceVariant = Color(0xFF66758E),
    outline = Color(0xFF99A8BE),
    outlineVariant = Color(0xFFD8E1EE),
    inverseSurface = Color(0xFF223045),
    inverseOnSurface = Color(0xFFF1F5FA),
    inversePrimary = Color(0xFFB9D0F3),
    surfaceTint = Color(0xFF4F8DD8),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides AppSpacing(),
        LocalWorkspaceTokens provides WorkspaceTokens(),
    ) {
        MaterialTheme(
            colorScheme = MinisteroLightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

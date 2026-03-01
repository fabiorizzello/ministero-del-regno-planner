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
    onPrimaryContainer = Color(0xFF1E293B),
    secondary = Color(0xFF2D9A63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDF2E8),
    onSecondaryContainer = Color(0xFF163A28),
    tertiary = Color(0xFFCF8A24),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E7CF),
    onTertiaryContainer = Color(0xFF412A08),
    error = Color(0xFFBD3E3E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9E5E5),
    onErrorContainer = Color(0xFF5A1717),
    background = Color(0xFFD9E4F8),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF4F7FB),
    onSurfaceVariant = Color(0xFF66758E),
    outline = Color(0xFFD8E1EE),
    outlineVariant = Color(0xFFC7D2E3),
    inverseSurface = Color(0xFF233147),
    inverseOnSurface = Color(0xFFF3F7FD),
    inversePrimary = Color(0xFFB9D0EE),
    surfaceTint = Color(0xFF4F8DD8),
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
    CompositionLocalProvider(
        LocalSpacing provides AppSpacing(),
        LocalWorkspaceTokens provides WorkspaceTokens(),
        LocalWorkspaceSketchPalette provides WorkspaceSketchPalette(),
    ) {
        MaterialTheme(
            colorScheme = MinisteroLightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

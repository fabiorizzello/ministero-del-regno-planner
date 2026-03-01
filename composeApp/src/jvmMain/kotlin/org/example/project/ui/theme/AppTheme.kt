package org.example.project.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MinisteroColorScheme = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1B2740),
    onPrimaryContainer = Color(0xFFD9E8FF),
    secondary = Color(0xFF35D39A),
    onSecondary = Color(0xFF052217),
    secondaryContainer = Color(0xFF123327),
    onSecondaryContainer = Color(0xFFC7F7E5),
    tertiary = Color(0xFFFFB020),
    onTertiary = Color(0xFF2D1B00),
    tertiaryContainer = Color(0xFF3F2D0D),
    onTertiaryContainer = Color(0xFFFFE6B8),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF300A11),
    errorContainer = Color(0xFF45131C),
    onErrorContainer = Color(0xFFFFD9DF),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF111524),
    onSurface = Color(0xFFE8ECFF),
    surfaceVariant = Color(0xFF121A2C),
    onSurfaceVariant = Color(0xFFA8B2D8),
    outline = Color(0xFF24304A),
    outlineVariant = Color(0xFF2F3C59),
    inverseSurface = Color(0xFFE8ECFF),
    inverseOnSurface = Color(0xFF111524),
    inversePrimary = Color(0xFF365988),
    surfaceTint = Color(0xFF6EA8FF),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
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
            colorScheme = MinisteroColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

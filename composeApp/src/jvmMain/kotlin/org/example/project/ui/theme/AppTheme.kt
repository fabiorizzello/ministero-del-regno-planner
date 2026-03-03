package org.example.project.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MinisteroColorScheme = lightColorScheme(
    primary = Color(0xFF2B6DE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBF0FF),
    onPrimaryContainer = Color(0xFF1A3A78),
    secondary = Color(0xFF16A34A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF1A2035),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2035),
    surfaceVariant = Color(0xFFF0F2F7),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1),
    inverseSurface = Color(0xFF1A2035),
    inverseOnSurface = Color(0xFFF5F7FA),
    inversePrimary = Color(0xFF93B4FF),
    surfaceTint = Color(0xFF2B6DE8),
    // Surface container family — override M3 baseline lavender defaults with app grey-blue scale
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFB),
    surfaceContainer = Color(0xFFF5F7FA),
    surfaceContainerHigh = Color(0xFFF0F2F7),
    surfaceContainerHighest = Color(0xFFEAEEF5),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE2E8F0),
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

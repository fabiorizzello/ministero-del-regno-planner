package org.example.project.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MinisteroLightColorScheme = lightColorScheme(
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
    background = Color(0xFFF0F4F8),
    onBackground = Color(0xFF1A2035),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF1A2035),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFDAE2EC),
    outlineVariant = Color(0xFFC3CFDD),
    inverseSurface = Color(0xFF1A2035),
    inverseOnSurface = Color(0xFFF0F4F8),
    inversePrimary = Color(0xFF93B4FF),
    surfaceTint = Color(0xFF2B6DE8),
    // Surface container family — override M3 baseline lavender defaults with app grey-blue scale
    surfaceContainerLowest = Color(0xFFFDFEFF),
    surfaceContainerLow = Color(0xFFF7FAFC),
    surfaceContainer = Color(0xFFF0F4F8),
    surfaceContainerHigh = Color(0xFFEAF0F6),
    surfaceContainerHighest = Color(0xFFE3EAF2),
    surfaceBright = Color(0xFFFDFEFF),
    surfaceDim = Color(0xFFDAE2EC),
)

private val MinisteroDarkColorScheme = darkColorScheme(
    primary = Color(0xFF69A7FF),
    onPrimary = Color(0xFF0E223F),
    primaryContainer = Color(0xFF223456),
    onPrimaryContainer = Color(0xFFD7E8FF),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF10311C),
    secondaryContainer = Color(0xFF183823),
    onSecondaryContainer = Color(0xFFCFF7DA),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF3E2800),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFFFE3B0),
    error = Color(0xFFF87171),
    onError = Color(0xFF4A0D12),
    errorContainer = Color(0xFF5A1A1F),
    onErrorContainer = Color(0xFFFFD8D8),
    background = Color(0xFF111827),
    onBackground = Color(0xFFF3F7FD),
    surface = Color(0xFF1C2636),
    onSurface = Color(0xFFF3F7FD),
    surfaceVariant = Color(0xFF192334),
    onSurfaceVariant = Color(0xFF9FB0C6),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF475569),
    inverseSurface = Color(0xFFF0F4F8),
    inverseOnSurface = Color(0xFF111827),
    inversePrimary = Color(0xFF2B6DE8),
    surfaceTint = Color(0xFF69A7FF),
    surfaceContainerLowest = Color(0xFF17202F),
    surfaceContainerLow = Color(0xFF1A2434),
    surfaceContainer = Color(0xFF1C2636),
    surfaceContainerHigh = Color(0xFF202B3B),
    surfaceContainerHighest = Color(0xFF243042),
    surfaceBright = Color(0xFF293547),
    surfaceDim = Color(0xFF141D2C),
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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides AppSpacing(),
        LocalWorkspaceTokens provides WorkspaceTokens(),
        LocalWorkspaceSketchPalette provides if (darkTheme) darkWorkspaceSketchPalette() else WorkspaceSketchPalette(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) MinisteroDarkColorScheme else MinisteroLightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

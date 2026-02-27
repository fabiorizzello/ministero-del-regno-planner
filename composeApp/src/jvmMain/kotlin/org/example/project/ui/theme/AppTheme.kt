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
    primary = Color(0xFF2B67C8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0ECFF),
    onPrimaryContainer = Color(0xFF102746),
    secondary = Color(0xFF2A9B69),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFF4EA),
    onSecondaryContainer = Color(0xFF0E3023),
    tertiary = Color(0xFF996A18),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE6C2),
    onTertiaryContainer = Color(0xFF312000),
    error = Color(0xFFBB2B2B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE2DF),
    onErrorContainer = Color(0xFF490507),
    background = Color(0xFFF2F4F8),
    onBackground = Color(0xFF182331),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2635),
    surfaceVariant = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFF4E5A6C),
    outline = Color(0xFF7C899D),
    outlineVariant = Color(0xFFC8D0DB),
    inverseSurface = Color(0xFF263243),
    inverseOnSurface = Color(0xFFECF1F8),
    inversePrimary = Color(0xFFA6C7FA),
    surfaceTint = Color(0xFF2B67C8),
)

private val MinisteroDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8CBDFF),
    onPrimary = Color(0xFF022E68),
    primaryContainer = Color(0xFF1C3D67),
    onPrimaryContainer = Color(0xFFDCEBFF),
    secondary = Color(0xFF87DFB8),
    onSecondary = Color(0xFF053724),
    secondaryContainer = Color(0xFF1C5B42),
    onSecondaryContainer = Color(0xFFD8F4E7),
    tertiary = Color(0xFFF1CC91),
    onTertiary = Color(0xFF342100),
    tertiaryContainer = Color(0xFF6C4B10),
    onTertiaryContainer = Color(0xFFFFE7C2),
    error = Color(0xFFFFB4AF),
    onError = Color(0xFF69090B),
    errorContainer = Color(0xFF8D1B22),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1520),
    onBackground = Color(0xFFE0E8F3),
    surface = Color(0xFF19212D),
    onSurface = Color(0xFFE4EBF6),
    surfaceVariant = Color(0xFF242E3D),
    onSurfaceVariant = Color(0xFFB7C3D7),
    outline = Color(0xFF8E9CB3),
    outlineVariant = Color(0xFF3E4B61),
    inverseSurface = Color(0xFFE3EAF5),
    inverseOnSurface = Color(0xFF1A2230),
    inversePrimary = Color(0xFF2B67C8),
    surfaceTint = Color(0xFF8CBDFF),
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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides AppSpacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) MinisteroDarkColorScheme else MinisteroLightColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

package org.example.project.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides AppSpacing()) {
        MaterialTheme(
            colorScheme = lightColorScheme(),
            typography = AppTypography,
            content = content,
        )
    }
}

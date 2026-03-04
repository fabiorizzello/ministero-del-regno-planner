package org.example.project.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import org.example.project.ui.theme.SemanticColors

enum class FeedbackBannerKind {
    SUCCESS,
    WARNING,
    ERROR,
}

data class FeedbackBannerModel(
    val message: String,
    val kind: FeedbackBannerKind,
    val details: String? = null,
)

private data class FeedbackBannerPalette(
    val contentColor: Color,
    val containerColor: Color,
    val icon: ImageVector,
    val iconTint: Color,
)

@Composable
fun FeedbackBanner(
    model: FeedbackBannerModel?,
    modifier: Modifier = Modifier,
    onDismissRequest: (() -> Unit)? = null,
) {
    val isVisible = model != null
    val safeModel = model ?: return

    LaunchedEffect(safeModel) {
        if (onDismissRequest != null && safeModel.kind == FeedbackBannerKind.SUCCESS) {
            delay(6500)
            onDismissRequest()
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successContentColor = if (isDark) SemanticColors.successContentDark else SemanticColors.successContentLight
    val successContainerColor = if (isDark) SemanticColors.successContainerDark else SemanticColors.successContainerLight

    val palette = when (safeModel.kind) {
        FeedbackBannerKind.SUCCESS -> FeedbackBannerPalette(
            contentColor = successContentColor,
            containerColor = successContainerColor,
            icon = Icons.Filled.TaskAlt,
            iconTint = successContentColor,
        )
        FeedbackBannerKind.WARNING -> FeedbackBannerPalette(
            contentColor = SemanticColors.warningContentLight,
            containerColor = SemanticColors.warningContainerLight,
            icon = Icons.Filled.Warning,
            iconTint = SemanticColors.warningContentLight,
        )
        FeedbackBannerKind.ERROR -> FeedbackBannerPalette(
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            icon = Icons.Filled.ErrorOutline,
            iconTint = MaterialTheme.colorScheme.error,
        )
    }

    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(x = -24, y = -24),
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Card(
                modifier = modifier
                    .widthIn(min = 320.dp, max = 560.dp),
                colors = CardDefaults.cardColors(
                    containerColor = palette.containerColor,
                    contentColor = palette.contentColor,
                ),
                border = BorderStroke(1.dp, palette.contentColor.copy(alpha = 0.34f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = palette.icon,
                        contentDescription = null,
                        tint = palette.iconTint,
                        modifier = Modifier.padding(top = 2.dp).size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    SelectionContainer(
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 1.dp, top = 1.dp, end = 4.dp, bottom = 1.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(text = safeModel.message, style = MaterialTheme.typography.bodySmall)
                            if (!safeModel.details.isNullOrBlank()) {
                                Text(
                                    text = safeModel.details,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.contentColor.copy(alpha = 0.92f),
                                )
                            }
                        }
                    }
                    if (onDismissRequest != null) {
                        IconButton(
                            modifier = Modifier.size(28.dp).handCursorOnHover(),
                            onClick = onDismissRequest,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Chiudi notifica",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

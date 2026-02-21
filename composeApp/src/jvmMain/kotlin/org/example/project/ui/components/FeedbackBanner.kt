package org.example.project.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.example.project.ui.theme.SemanticColors

enum class FeedbackBannerKind {
    SUCCESS,
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
    val borderColor: Color,
    val icon: ImageVector,
    val iconTint: Color,
)

@Composable
fun FeedbackBanner(
    model: FeedbackBannerModel?,
    modifier: Modifier = Modifier,
    onDismissRequest: (() -> Unit)? = null,
) {
    if (model == null) return

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successContentColor = if (isDark) SemanticColors.successContentDark else SemanticColors.successContentLight
    val successContainerColor = if (isDark) SemanticColors.successContainerDark else SemanticColors.successContainerLight

    val palette = when (model.kind) {
        FeedbackBannerKind.SUCCESS -> FeedbackBannerPalette(
            contentColor = successContentColor,
            containerColor = successContainerColor,
            borderColor = successContentColor.copy(alpha = 0.45f),
            icon = Icons.Filled.TaskAlt,
            iconTint = successContentColor,
        )
        FeedbackBannerKind.ERROR -> FeedbackBannerPalette(
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
            icon = Icons.Filled.ErrorOutline,
            iconTint = MaterialTheme.colorScheme.error,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.containerColor,
            contentColor = palette.contentColor,
        ),
        border = BorderStroke(1.dp, palette.borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = palette.icon,
                contentDescription = null,
                tint = palette.iconTint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            SelectionContainer(
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = model.message)
                    if (!model.details.isNullOrBlank()) {
                        Text(
                            text = model.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contentColor.copy(alpha = 0.92f),
                        )
                    }
                }
            }
            if (onDismissRequest != null) {
                IconButton(
                    modifier = Modifier.handCursorOnHover(),
                    onClick = onDismissRequest,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Chiudi notifica",
                    )
                }
            }
        }
    }
}

package org.example.project.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val (contentColor, containerColor) = when (model.kind) {
        FeedbackBannerKind.SUCCESS -> successContentColor to successContainerColor
        FeedbackBannerKind.ERROR -> {
            MaterialTheme.colorScheme.onErrorContainer to MaterialTheme.colorScheme.errorContainer
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SelectionContainer(
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 2.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = model.message)
                    if (!model.details.isNullOrBlank()) {
                        Text(
                            text = model.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.92f),
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

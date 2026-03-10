package org.example.project.ui.components.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.feature.output.domain.ProgramDeliverySnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveryBadge(
    snapshot: ProgramDeliverySnapshot,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            snapshot.allDelivered -> {
                DeliveryChip(
                    indicator = "\u2713", // ✓
                    label = "Tutti inviati",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            else -> {
                if (snapshot.pending > 0) {
                    DeliveryChip(
                        indicator = "\u25CF", // ●
                        label = "${snapshot.pending} da inviare",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                    )
                }
                if (snapshot.blocked > 0) {
                    DeliveryChip(
                        indicator = "\u25B2", // ▲
                        label = "${snapshot.blocked} bloccati",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        indicatorColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryChip(
    indicator: String,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    indicatorColor: androidx.compose.ui.graphics.Color = contentColor,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = indicator,
                style = MaterialTheme.typography.labelSmall,
                color = indicatorColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

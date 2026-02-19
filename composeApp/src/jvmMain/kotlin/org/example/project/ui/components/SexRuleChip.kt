package org.example.project.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.ui.theme.SemanticColors

@Composable
fun SexRuleChip(sexRule: SexRule) {
    val (label, chipColor) = when (sexRule) {
        SexRule.UOMO -> "UOMO" to SemanticColors.blue
        SexRule.LIBERO -> "LIBERO" to SemanticColors.grey
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = chipColor.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = chipColor,
        )
    }
}

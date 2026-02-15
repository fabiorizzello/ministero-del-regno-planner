package org.example.project.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class TableColumnSpec(
    val title: String,
    val weight: Float,
)

fun Modifier.standardTableCell(
    lineColor: Color,
    borderWidth: Dp = 0.6.dp,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 4.dp,
): Modifier = border(borderWidth, lineColor).padding(horizontal = horizontalPadding, vertical = verticalPadding)

@Composable
fun StandardTableHeader(
    columns: List<TableColumnSpec>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.outline,
    onColumnClick: ((Int) -> Unit)? = null,
    sortIndicatorText: ((Int) -> String?)? = null,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        columns.forEachIndexed { index, column ->
            val cellModifier = Modifier
                .weight(column.weight)
                .standardTableCell(lineColor)
                .let { base ->
                    if (onColumnClick == null) {
                        base
                    } else {
                        base
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onColumnClick(index) }
                    }
                }
            val indicator = sortIndicatorText?.invoke(index)
            val label = if (indicator.isNullOrBlank()) {
                column.title
            } else {
                "${column.title} $indicator"
            }
            Text(
                text = label,
                modifier = cellModifier,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun StandardTableViewport(
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 0.8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.border(borderWidth, lineColor),
        content = content,
    )
}

@Composable
fun StandardTableEmptyRow(
    message: String,
    totalWeight: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.outline,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(totalWeight)
                .standardTableCell(lineColor, horizontalPadding = 8.dp, verticalPadding = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(message)
        }
    }
}

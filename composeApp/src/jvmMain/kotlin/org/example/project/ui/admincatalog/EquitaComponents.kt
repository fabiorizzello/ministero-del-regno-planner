package org.example.project.ui.admincatalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.feature.assignments.application.RANKING_HISTORY_WEEKS
import org.example.project.feature.diagnostics.domain.EquitaIndicazione
import org.example.project.feature.diagnostics.domain.EquitaProclamatore
import org.example.project.feature.diagnostics.domain.RiepilogoEquita
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch

@Composable
internal fun EquitaSummaryCard(
    riepilogo: RiepilogoEquita,
    righe: List<EquitaProclamatore>,
    modifier: Modifier = Modifier,
) {
    AdminContentCard(
        title = "Riepilogo",
        modifier = modifier,
    ) {
        if (riepilogo.dimenticatiDaTroppo.isNotEmpty() || righe.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                if (riepilogo.dimenticatiDaTroppo.isNotEmpty()) {
                    EquitaForgottenNotice(
                        riepilogo = riepilogo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                EquitaPriorityPanels(
                    righe = righe,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        EquitaStatsRow(
            riepilogo = riepilogo,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EquitaFilterBar(
    filtroRicerca: String,
    soloLiberi: Boolean,
    includiSospesi: Boolean,
    sortMode: EquitaSortMode,
    onSearchChange: (String) -> Unit,
    onToggleSoloLiberi: () -> Unit,
    onToggleIncludiSospesi: () -> Unit,
    onSortChange: (EquitaSortMode) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        OutlinedTextField(
            value = filtroRicerca,
            onValueChange = onSearchChange,
            modifier = Modifier
                .widthIn(min = 220.dp, max = 320.dp)
                .height(52.dp),
            placeholder = { Text("Cerca studente...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary,
                errorCursorColor = MaterialTheme.colorScheme.error,
            ),
        )
        FilterChip(
            selected = soloLiberi,
            onClick = onToggleSoloLiberi,
            label = { Text("Fuori cooldown") },
            modifier = Modifier.handCursorOnHover(),
        )
        FilterChip(
            selected = includiSospesi,
            onClick = onToggleIncludiSospesi,
            label = { Text("Sospesi") },
            modifier = Modifier.handCursorOnHover(),
        )
        EquitaSortSelector(
            sortMode = sortMode,
            onSortChange = onSortChange,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EquitaList(
    righe: List<EquitaProclamatore>,
    riepilogo: RiepilogoEquita,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val listState = rememberLazyListState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(end = MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            stickyHeader(key = "equita-columns") {
                EquitaColumnHeader()
            }
            items(righe, key = { it.proclamatore.id.value }) { row ->
                EquitaRow(
                    row = row,
                    maxTotale = riepilogo.maxTotale.coerceAtLeast(1),
                    medianaTotale = riepilogo.medianaTotale,
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun EquitaRow(
    row: EquitaProclamatore,
    maxTotale: Int,
    medianaTotale: Int,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val status = statusPresentation(row, sketch)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, status.borderColor.copy(alpha = 0.22f)),
        color = sketch.cardSurface,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EquitaStatusDot(status)
            EquitaAvatar(
                nome = row.proclamatore.nome,
                cognome = row.proclamatore.cognome,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${row.proclamatore.cognome} ${row.proclamatore.nome}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.proclamatore.sospeso) {
                        EquitaBadge(
                            text = "Sospeso",
                            containerColor = sketch.bad.copy(alpha = 0.12f),
                            contentColor = sketch.bad,
                        )
                    }
                    when (row.indicazione) {
                        EquitaIndicazione.DA_RECUPERARE -> EquitaBadge(
                            text = "Poco usato",
                            containerColor = sketch.ok.copy(alpha = 0.12f),
                            contentColor = sketch.ok,
                        )
                        EquitaIndicazione.DA_ALLEGGERIRE -> EquitaBadge(
                            text = "Molto usato",
                            containerColor = sketch.warn.copy(alpha = 0.14f),
                            contentColor = sketch.warn,
                        )
                        EquitaIndicazione.EQUILIBRATO -> Unit
                    }
                    if (row.fermoDaMolto) {
                        EquitaBadge(
                            text = "Fermo da molto",
                            containerColor = sketch.warn.copy(alpha = 0.12f),
                            contentColor = sketch.warn,
                        )
                    }
                }
                Text(
                    text = buildContextLine(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EquitaBulletCell(
                total = row.totaleInFinestra,
                maxTotal = maxTotale,
                medianTotal = medianaTotale,
                modifier = Modifier.width(124.dp),
            )
            Text(
                text = row.totaleInFinestra.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.width(22.dp),
            )
            Text(
                text = row.settimaneDallUltima?.let { "${it} sett." } ?: "-",
                style = MaterialTheme.typography.labelMedium,
                color = sketch.inkSoft,
                modifier = Modifier.width(44.dp),
            )
        }
    }
}

@Composable
private fun EquitaForgottenNotice(
    riepilogo: RiepilogoEquita,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    var expanded by remember(riepilogo.dimenticatiDaTroppo) { mutableStateOf(false) }
    val previewCount = 8
    val visibleRows = if (expanded) {
        riepilogo.dimenticatiDaTroppo
    } else {
        riepilogo.dimenticatiDaTroppo.take(previewCount)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = sketch.warn.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, sketch.warn.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = sketch.warn,
                modifier = Modifier.size(16.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${riepilogo.dimenticatiDaTroppo.size} poco usati da coinvolgere",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = sketch.ink,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    visibleRows.forEach { row ->
                        val nome = row.proclamatore.nome.firstOrNull()?.uppercaseChar() ?: '?'
                        EquitaBadge(
                            text = "${row.proclamatore.cognome} $nome.",
                            containerColor = sketch.warn.copy(alpha = 0.12f),
                            contentColor = sketch.warn,
                        )
                    }
                }
                if (riepilogo.dimenticatiDaTroppo.size > previewCount) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.handCursorOnHover(),
                    ) {
                        Text(
                            text = if (expanded) "Mostra meno" else "Mostra tutti",
                            style = MaterialTheme.typography.labelMedium,
                            color = sketch.warn,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquitaStatsRow(
    riepilogo: RiepilogoEquita,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        EquitaStatPill(label = "Attivi", value = riepilogo.totaleAttivi.toString())
        EquitaStatPill(label = "Mai", value = riepilogo.maiAssegnati.toString())
        EquitaStatPill(label = "Poco usati", value = riepilogo.daRecuperare.toString())
        EquitaStatPill(label = "Fermi", value = riepilogo.fermiDaMolto.toString())
        EquitaStatPill(label = "Molto usati", value = riepilogo.daAlleggerire.toString())
        EquitaStatPill(label = "Mediana", value = riepilogo.medianaTotale.toString())
        EquitaStatPill(label = "Cond. cd", value = riepilogo.inCooldownLead.toString())
        EquitaStatPill(label = "Assist. cd", value = riepilogo.inCooldownAssist.toString())
    }
}

@Composable
private fun EquitaPriorityPanels(
    righe: List<EquitaProclamatore>,
    modifier: Modifier = Modifier,
) {
    val activeRows = righe.filterNot { it.proclamatore.sospeso }
    val moltoUsati = activeRows
        .sortedWith(
            compareByDescending<EquitaProclamatore> { it.totaleInFinestra }
                .thenByDescending { it.assegnazioniUltime4Settimane }
                .thenBy { it.proclamatore.cognome.lowercase() },
        )
        .take(5)

    EquitaPriorityNotice(
        title = "Piu carichi",
        rows = moltoUsati,
        accent = MaterialTheme.workspaceSketch.accent,
        modifier = modifier,
    )
}

@Composable
private fun EquitaPriorityNotice(
    title: String,
    rows: List<EquitaProclamatore>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    var expanded by remember(rows) { mutableStateOf(false) }
    val previewCount = 8
    val visibleRows = if (expanded) rows else rows.take(previewCount)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = sketch.ink,
                )
                if (rows.isEmpty()) {
                    Text(
                        text = "Nessuno",
                        style = MaterialTheme.typography.bodySmall,
                        color = sketch.inkMuted,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        visibleRows.forEach { row ->
                            val nome = row.proclamatore.nome.firstOrNull()?.uppercaseChar() ?: '?'
                            EquitaBadge(
                                text = "${row.proclamatore.cognome} $nome. ${row.totaleInFinestra}",
                                containerColor = accent.copy(alpha = 0.10f),
                                contentColor = accent,
                            )
                        }
                    }
                    if (rows.size > previewCount) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.handCursorOnHover(),
                        ) {
                            Text(
                                text = if (expanded) "Mostra meno" else "Mostra tutti",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquitaColumnHeader() {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(34.dp))
            Text(
                text = "Studente e stato",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Carico vs med.",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.inkSoft,
                modifier = Modifier.width(124.dp),
            )
            Text(
                text = "Tot",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.inkSoft,
                modifier = Modifier.width(22.dp),
            )
            Text(
                text = "Ultima",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.inkSoft,
                modifier = Modifier.width(44.dp),
            )
        }
    }
}

@Composable
private fun EquitaStatPill(
    label: String,
    value: String,
) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = sketch.surfaceMuted,
        border = BorderStroke(1.dp, sketch.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.ink,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = sketch.inkMuted,
            )
        }
    }
}

@Composable
private fun EquitaSortSelector(
    sortMode: EquitaSortMode,
    onSortChange: (EquitaSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.handCursorOnHover(),
        ) {
            Text(sortMode.label)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            EquitaSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSortChange(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun EquitaStatusDot(
    status: EquitaStatusPresentation,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(status.dotColor),
        )
        Icon(
            imageVector = status.icon,
            contentDescription = null,
            tint = status.dotColor,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun EquitaAvatar(
    nome: String,
    cognome: String,
) {
    val sketch = MaterialTheme.workspaceSketch
    val initials = "${nome.firstOrNull() ?: ""}${cognome.firstOrNull() ?: ""}".uppercase()

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(sketch.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = sketch.accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun EquitaBadge(
    text: String,
    containerColor: Color = MaterialTheme.workspaceSketch.selectionSurface,
    contentColor: Color = MaterialTheme.workspaceSketch.selectionBorder,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.32f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

@Composable
private fun EquitaBulletCell(
    total: Int,
    maxTotal: Int,
    medianTotal: Int,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    val fillFraction = (total.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)
    val medianFraction = (medianTotal.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(sketch.surfaceMuted),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            sketch.selectionBorder.copy(alpha = 0.55f),
                            sketch.accent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(medianFraction)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(sketch.warn.copy(alpha = 0.9f)),
            )
        }
    }
}

private fun buildContextLine(row: EquitaProclamatore): String {
    if (row.maiAssegnato) return "Mai assegnato"
    if (row.cooldownLeadResiduo > 0) {
        return "Attive ${row.settimaneAssegnate.size}/${RANKING_HISTORY_WEEKS.toInt()} | Cooldown conduzione ${row.cooldownLeadResiduo}w | Ult. ${row.ultimaAssegnazione}"
    }
    if (row.cooldownAssistResiduo > 0) {
        return "Attive ${row.settimaneAssegnate.size}/${RANKING_HISTORY_WEEKS.toInt()} | Cooldown assistenza ${row.cooldownAssistResiduo}w | Ult. ${row.ultimaAssegnazione}"
    }
    val parts = buildList {
        add("Attive ${row.settimaneAssegnate.size}/${RANKING_HISTORY_WEEKS.toInt()}")
        row.ultimaConduzione?.let { add("Conduzione $it") }
        row.ultimaAssistenza?.let { add("Assistenza $it") }
        if (row.assegnazioniUltime4Settimane > 0) add("Ultime 4w ${row.assegnazioniUltime4Settimane}")
    }
    return parts.joinToString(" | ")
}

private data class EquitaStatusPresentation(
    val dotColor: Color,
    val borderColor: Color,
    val icon: ImageVector,
)

private fun statusPresentation(
    row: EquitaProclamatore,
    sketch: org.example.project.ui.theme.WorkspaceSketchPalette,
): EquitaStatusPresentation = when {
    row.cooldownLeadResiduo > 0 -> EquitaStatusPresentation(
        dotColor = sketch.bad,
        borderColor = sketch.bad,
        icon = Icons.Filled.ErrorOutline,
    )

    row.cooldownAssistResiduo > 0 -> EquitaStatusPresentation(
        dotColor = sketch.warn,
        borderColor = sketch.warn,
        icon = Icons.Filled.HourglassTop,
    )

    else -> EquitaStatusPresentation(
        dotColor = sketch.ok,
        borderColor = sketch.ok,
        icon = Icons.Filled.CheckCircle,
    )
}

private val EquitaSortMode.label: String
    get() = when (this) {
        EquitaSortMode.MENO_USATI -> "Meno usati"
        EquitaSortMode.PIU_USATI -> "Piu usati"
        EquitaSortMode.ALFABETICO -> "Alfabetico"
    }

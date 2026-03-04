/**
 * Visual sketch for the Programma workspace — dark mode, v2 redesign.
 *
 * Layout: Toolbar (44dp) | [ Sidebar 210dp | Center flex | Right 248dp ] | StatusBar (24dp)
 * Adapted from docs/sketch-redesign-v2.html using WorkspaceSketchPalette (dark).
 *
 * Swap ProgrammaSketchScreen() → ProgramWorkspaceScreen() once design is validated.
 */
package org.example.project.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Frame
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.theme.workspaceSketch

// ─── Fake model ───────────────────────────────────────────────────────────────

private enum class FkWeekStatus { EMPTY, PARTIAL, COMPLETE, CURRENT, PAST, SKIPPED }

private data class FkMonth(
    val id: String,
    val label: String,        // "Marzo 2026"
    val weekCount: Int,
    val isCurrent: Boolean,
    val assignedSlots: Int,
    val totalSlots: Int,
)

private data class FkWeek(
    val id: String,
    val shortLabel: String,   // "3–9 Mar" — sidebar
    val fullLabel: String,    // "Settimana 3–9 Marzo 2026" — detail header
    val monthSubtext: String, // "Marzo 2026" — detail sub
    val monthId: String,
    val status: FkWeekStatus,
    val parts: List<FkPart>,
)

private data class FkPart(
    val num: Int,
    val title: String,
    val slots: List<String?>,  // null = empty slot
)

private data class FkIssue(
    val week: String,
    val partTitle: String,
    val reason: String,
)

private fun FkWeek.fraction(): Float {
    val slots = parts.flatMap { it.slots }
    if (slots.isEmpty()) return 0f
    return slots.count { it != null }.toFloat() / slots.size
}

private val MESI_IT = listOf(
    "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre",
)

/** Returns e.g. "Crea maggio 2026" — the month after the last in the list. */
private fun nextMonthLabel(months: List<FkMonth>): String {
    val last = months.lastOrNull()?.label ?: return "Crea mese"
    // label is "Marzo 2026" — extract month index and year
    val parts = last.split(" ")
    if (parts.size < 2) return "Crea mese"
    val monthIdx = MESI_IT.indexOfFirst { it.equals(parts[0], ignoreCase = true) }
    val year = parts[1].toIntOrNull() ?: return "Crea mese"
    return if (monthIdx == 11) "Crea ${MESI_IT[0]} ${year + 1}"
    else "Crea ${MESI_IT[monthIdx + 1].lowercase()} $year"
}

private val fakeMonths = listOf(
    FkMonth("m1", "Marzo 2026", 5, true, 10, 24),
    FkMonth("m2", "Aprile 2026", 4, false, 0, 20),
)

private val fakeWeeks = listOf(
    // ── Marzo 2026 ──
    FkWeek(
        "w1", "24 Feb – 2 Mar", "Settimana 24 Feb – 2 Marzo 2026", "Marzo 2026", "m1",
        FkWeekStatus.PAST, listOf(
            FkPart(1, "Lettura biblica", listOf("M. Rossi", "L. Bianchi")),
            FkPart(2, "Iniziare conversazioni", listOf("A. Verdi", "C. Neri")),
            FkPart(3, "Discorso", listOf("F. Blu")),
            FkPart(4, "Studio biblico cong.", listOf("G. Rosa", "E. Viola")),
        ),
    ),
    FkWeek(
        "w2", "3–9 Mar", "Settimana 3–9 Marzo 2026", "Marzo 2026", "m1",
        FkWeekStatus.CURRENT, listOf(
            FkPart(1, "Lettura biblica", listOf("V. Azzurro", "S. Grigio")),
            FkPart(2, "Iniziare conversazioni", listOf("M. Rossi", null)),
            FkPart(3, "Discorso", listOf(null)),
            FkPart(4, "Studio biblico cong.", listOf(null, null)),
        ),
    ),
    FkWeek(
        "w3", "10–16 Mar", "Settimana 10–16 Marzo 2026", "Marzo 2026", "m1",
        FkWeekStatus.EMPTY, listOf(
            FkPart(1, "Lettura biblica", listOf(null, null)),
            FkPart(2, "Iniziare conversazioni", listOf(null, null)),
            FkPart(3, "Discorso", listOf(null)),
            FkPart(4, "Studio biblico cong.", listOf(null, null)),
        ),
    ),
    FkWeek(
        "w4", "17–23 Mar", "Settimana 17–23 Marzo 2026", "Marzo 2026", "m1",
        FkWeekStatus.SKIPPED, emptyList(),
    ),
    FkWeek(
        "w5", "24–30 Mar", "Settimana 24–30 Marzo 2026", "Marzo 2026", "m1",
        FkWeekStatus.EMPTY, listOf(
            FkPart(1, "Lettura biblica", listOf(null, null)),
            FkPart(2, "Discorso", listOf(null)),
        ),
    ),
    // ── Aprile 2026 ──
    FkWeek(
        "a1", "31 Mar – 6 Apr", "Settimana 31 Mar – 6 Aprile 2026", "Aprile 2026", "m2",
        FkWeekStatus.EMPTY, listOf(
            FkPart(1, "Lettura biblica", listOf(null, null)),
            FkPart(2, "Iniziare conversazioni", listOf(null, null)),
            FkPart(3, "Discorso", listOf(null)),
            FkPart(4, "Studio biblico cong.", listOf(null, null)),
        ),
    ),
    FkWeek(
        "a2", "7–13 Apr", "Settimana 7–13 Aprile 2026", "Aprile 2026", "m2",
        FkWeekStatus.EMPTY, listOf(
            FkPart(1, "Lettura biblica", listOf(null, null)),
            FkPart(2, "Iniziare conversazioni", listOf(null, null)),
            FkPart(3, "Discorso", listOf(null)),
            FkPart(4, "Studio biblico cong.", listOf(null, null)),
        ),
    ),
    FkWeek(
        "a3", "14–20 Apr", "Settimana 14–20 Aprile 2026", "Aprile 2026", "m2",
        FkWeekStatus.EMPTY, listOf(
            FkPart(1, "Lettura biblica", listOf(null, null)),
            FkPart(2, "Discorso", listOf(null)),
        ),
    ),
    FkWeek(
        "a4", "21–27 Apr", "Settimana 21–27 Aprile 2026", "Aprile 2026", "m2",
        FkWeekStatus.EMPTY, listOf(
            FkPart(1, "Lettura biblica", listOf(null, null)),
            FkPart(2, "Iniziare conversazioni", listOf(null, null)),
            FkPart(3, "Discorso", listOf(null)),
            FkPart(4, "Studio biblico cong.", listOf(null, null)),
        ),
    ),
)

private val fakeIssues = listOf(
    FkIssue("3–9 Mar", "Discorso", "nessun idoneo disponibile"),
    FkIssue("10–16 Mar", "Lettura biblica", "cooldown violato"),
)

// ─── Top-level screen ─────────────────────────────────────────────────────────

@Composable
fun ProgrammaSketchScreen() {
    val sketch = MaterialTheme.workspaceSketch
    var selectedMonthId by remember { mutableStateOf("m1") }
    var selectedWeekId by remember { mutableStateOf("w2") }

    val selectedMonth = fakeMonths.firstOrNull { it.id == selectedMonthId }
    val selectedWeek = fakeWeeks.firstOrNull { it.id == selectedWeekId }
    val weeksForMonth = fakeWeeks.filter { it.monthId == selectedMonthId }

    Column(modifier = Modifier.fillMaxSize().background(sketch.panelMid)) {
        SketchToolbar()

        Row(modifier = Modifier.weight(1f)) {
            SketchSidebar(
                months = fakeMonths,
                weeks = weeksForMonth,
                selectedMonthId = selectedMonthId,
                selectedWeekId = selectedWeekId,
                onMonthSelect = { selectedMonthId = it },
                onWeekSelect = { selectedWeekId = it },
            )
            // Sidebar / center divider
            Box(Modifier.fillMaxHeight().width(1.dp).background(sketch.lineSoft))

            SketchCenter(
                selectedWeek = selectedWeek,
                modifier = Modifier.weight(1f),
            )

            // Center / right panel divider
            Box(Modifier.fillMaxHeight().width(1.dp).background(sketch.lineSoft))

            SketchRightPanel(
                selectedMonth = selectedMonth,
                issues = fakeIssues,
            )
        }

        SketchStatusBar(
            monthLabel = selectedMonth?.label ?: "—",
            weekLabel = selectedWeek?.shortLabel ?: "—",
            assignedSlots = selectedMonth?.assignedSlots ?: 0,
            totalSlots = selectedMonth?.totalSlots ?: 0,
        )
    }
}

// ─── Toolbar ──────────────────────────────────────────────────────────────────

@Composable
private fun SketchToolbar() {
    val sketch = MaterialTheme.workspaceSketch
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(sketch.toolbarBackground)
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    // Window drag: track press position and move the AWT frame on drag
                    var startX = 0f
                    var startY = 0f
                    var frameOriginX = 0
                    var frameOriginY = 0
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            val window = Frame.getFrames().firstOrNull() ?: continue
                            when (event.type.toString()) {
                                "Press" -> {
                                    startX = pos.x; startY = pos.y
                                    frameOriginX = window.x; frameOriginY = window.y
                                }
                                "Move" -> if (event.changes.any { it.pressed }) {
                                    val dx = (pos.x - startX).toInt()
                                    val dy = (pos.y - startY).toInt()
                                    window.setLocation(frameOriginX + dx, frameOriginY + dy)
                                }
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand logo
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.linearGradient(listOf(sketch.accent, Color(0xFF5480E8)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "M",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                    ),
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                "Ministero del Regno",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                ),
                color = sketch.ink,
            )
            // Separator
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(sketch.lineSoft),
            )
            // Nav tabs
            SketchToolbarTab("Programma", Icons.Filled.Today, active = true)
            SketchToolbarTab("Studenti", Icons.Filled.Groups, active = false)
            SketchToolbarTab("Diagnostica", Icons.Filled.BugReport, active = false)

            Spacer(Modifier.weight(1f))

            // Actions
            SketchToolbarButton("Autoassegna", Icons.Filled.PlayArrow, isPrimary = false)
            Spacer(Modifier.width(4.dp))
            SketchToolbarButton("Stampa", Icons.Filled.Print, isPrimary = true)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft))
    }
}

@Composable
private fun SketchToolbarTab(label: String, icon: ImageVector, active: Boolean) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .hoverable(interactionSource)
            .handCursorOnHover()
            .clip(RoundedCornerShape(5.dp))
            .background(
                when {
                    active -> sketch.accentSoft
                    hovered -> sketch.surface
                    else -> Color.Transparent
                },
            )
            .clickable {}
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) sketch.accent else sketch.inkSoft,
            modifier = Modifier.size(12.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (active) sketch.accent else sketch.inkSoft,
        )
    }
}

@Composable
private fun SketchToolbarButton(label: String, icon: ImageVector, isPrimary: Boolean) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = Modifier.handCursorOnHover().clickable {},
        shape = RoundedCornerShape(5.dp),
        color = if (isPrimary) sketch.accent else sketch.surfaceMuted,
        border = BorderStroke(
            1.dp,
            if (isPrimary) sketch.accent.copy(alpha = 0.7f) else sketch.lineSoft,
        ),
    ) {
        Row(
            modifier = Modifier.height(30.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isPrimary) Color.White else sketch.inkSoft,
                modifier = Modifier.size(13.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = if (isPrimary) Color.White else sketch.inkSoft,
            )
        }
    }
}

// ─── Left sidebar ─────────────────────────────────────────────────────────────

@Composable
private fun SketchSidebar(
    months: List<FkMonth>,
    weeks: List<FkWeek>,
    selectedMonthId: String,
    selectedWeekId: String,
    onMonthSelect: (String) -> Unit,
    onWeekSelect: (String) -> Unit,
) {
    val sketch = MaterialTheme.workspaceSketch

    Column(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(sketch.panelLeft),
    ) {
        // ── Programs section ──────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SketchSectionTitle("PROGRAMMI")
            Spacer(Modifier.height(2.dp))
            months.forEach { month ->
                SketchMonthItem(
                    month = month,
                    selected = selectedMonthId == month.id,
                    onClick = { onMonthSelect(month.id) },
                )
            }
            // + Add month (simplified without dashed border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handCursorOnHover()
                    .clip(RoundedCornerShape(5.dp))
                    .clickable {}
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = sketch.accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    nextMonthLabel(months),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = sketch.accent.copy(alpha = 0.7f),
                )
            }
        }

        // Divider
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.6f)))

        // ── Week list (scrollable) ────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    fakeMonths.firstOrNull { it.id == selectedMonthId }?.label?.uppercase()
                        ?: "SETTIMANE",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).padding(top = 0.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp,
                    ),
                    color = sketch.inkMuted,
                )
            }
            items(weeks, key = { it.id }) { week ->
                SketchWeekListItem(
                    week = week,
                    selected = selectedWeekId == week.id,
                    onClick = { onWeekSelect(week.id) },
                )
            }
        }

        // Divider
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.6f)))

        // ── Footer ────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SketchSidebarFooterButton("Aggiorna schemi", Icons.Filled.Refresh)
        }
    }
}

@Composable
private fun SketchMonthItem(month: FkMonth, selected: Boolean, onClick: () -> Unit) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .handCursorOnHover()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = when {
            selected -> sketch.accentSoft
            hovered -> sketch.surface
            else -> Color.Transparent
        },
        border = if (selected) BorderStroke(1.dp, sketch.accent.copy(alpha = 0.45f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                month.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) sketch.accent else sketch.ink,
            )
            Text(
                "${month.weekCount} sett.",
                style = MaterialTheme.typography.labelSmall,
                color = sketch.inkMuted,
            )
        }
    }
}

@Composable
private fun SketchWeekListItem(week: FkWeek, selected: Boolean, onClick: () -> Unit) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val fraction = week.fraction()

    val dotColor = when (week.status) {
        FkWeekStatus.CURRENT -> sketch.accent
        FkWeekStatus.COMPLETE -> sketch.ok
        FkWeekStatus.PARTIAL -> sketch.warn
        FkWeekStatus.PAST -> sketch.inkMuted
        FkWeekStatus.SKIPPED -> sketch.lineSoft
        FkWeekStatus.EMPTY -> sketch.lineSoft
    }
    val bgColor = when {
        selected -> sketch.accentSoft
        hovered -> sketch.surface
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .handCursorOnHover()
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
    ) {
        // Main row: dot + name + tag
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor),
            )
            Text(
                week.shortLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) sketch.accent else sketch.ink,
                maxLines = 1,
            )
            when (week.status) {
                FkWeekStatus.CURRENT -> SketchWeekTag("CORRENTE", sketch.accent)
                FkWeekStatus.SKIPPED -> SketchWeekTag("SALTATA", sketch.inkMuted)
                else -> {}
            }
        }
        // Mini progress bar (hidden for SKIPPED)
        if (week.status != FkWeekStatus.SKIPPED) {
            val fillColor = when (week.status) {
                FkWeekStatus.COMPLETE -> sketch.ok
                FkWeekStatus.PAST -> sketch.inkMuted
                FkWeekStatus.CURRENT -> sketch.accent
                FkWeekStatus.PARTIAL -> sketch.warn
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(sketch.lineSoft.copy(alpha = 0.5f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceAtMost(1f))
                            .background(fillColor, RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
    }

}

@Composable
private fun SketchWeekTag(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.3.sp,
            ),
            color = color,
        )
    }
}

@Composable
private fun SketchSidebarFooterButton(label: String, icon: ImageVector) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = Modifier.fillMaxWidth().handCursorOnHover().clickable {},
        shape = RoundedCornerShape(5.dp),
        color = sketch.surface,
        border = BorderStroke(1.dp, sketch.lineSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = sketch.inkSoft, modifier = Modifier.size(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = sketch.inkSoft,
            )
        }
    }
}

// ─── Center — week detail ─────────────────────────────────────────────────────

@Composable
private fun SketchCenter(selectedWeek: FkWeek?, modifier: Modifier = Modifier) {
    val sketch = MaterialTheme.workspaceSketch

    Column(modifier = modifier.fillMaxHeight().background(sketch.panelMid)) {
        if (selectedWeek == null) {
            SketchEmptyState("Seleziona una settimana dalla sidebar")
            return@Column
        }

        val allSlots = selectedWeek.parts.flatMap { it.slots }
        val total = allSlots.size
        val assigned = allSlots.count { it != null }
        val fraction = if (total > 0) assigned.toFloat() / total else 0f
        val readOnly = selectedWeek.status == FkWeekStatus.PAST

        // Sticky detail header
        SketchDetailHeader(week = selectedWeek)

        // Coverage strip (not shown for skipped weeks)
        if (selectedWeek.status != FkWeekStatus.SKIPPED && total > 0) {
            SketchCoverageStrip(assigned = assigned, total = total, fraction = fraction)
        }

        // Scrollable body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            when {
                selectedWeek.status == FkWeekStatus.SKIPPED -> {
                    SketchSkippedBody()
                }
                selectedWeek.parts.isEmpty() -> {
                    SketchEmptyState("Nessuna parte configurata per questa settimana")
                }
                else -> {
                    SketchPartsGrid(parts = selectedWeek.parts, readOnly = readOnly)
                }
            }
        }
    }
}

@Composable
private fun SketchDetailHeader(week: FkWeek) {
    val sketch = MaterialTheme.workspaceSketch
    Column(modifier = Modifier.background(sketch.panelLeft)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Week name + sub
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        week.fullLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = sketch.ink,
                    )
                    when (week.status) {
                        FkWeekStatus.CURRENT -> SketchBadge("CORRENTE", sketch.accent)
                        FkWeekStatus.SKIPPED -> SketchBadge("SALTATA", sketch.inkMuted)
                        else -> {}
                    }
                }
                Text(
                    week.monthSubtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkMuted,
                )
            }
            // Action buttons (right)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (week.status) {
                    FkWeekStatus.SKIPPED -> {
                        SketchHdrButton("Riattiva", Icons.Filled.PlayCircle, HdrTone.Ok)
                    }
                    FkWeekStatus.PAST -> {
                        // No actions for past weeks
                    }
                    else -> {
                        SketchHdrButton("Modifica parti", Icons.Filled.Edit, HdrTone.Neutral)
                        SketchHdrButton("Salta settimana", Icons.Filled.Block, HdrTone.Warn)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft))
    }
}

@Composable
private fun SketchCoverageStrip(assigned: Int, total: Int, fraction: Float) {
    val sketch = MaterialTheme.workspaceSketch
    val fillColor = when {
        fraction == 1f -> sketch.ok
        fraction > 0f -> sketch.accent
        else -> Color.Transparent
    }
    val pctColor = when {
        fraction == 1f -> sketch.ok
        fraction > 0f -> sketch.accent
        else -> sketch.inkMuted
    }
    Column(modifier = Modifier.background(sketch.panelLeft)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$assigned / $total slot",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.inkSoft,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(sketch.lineSoft),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceAtMost(1f))
                            .background(fillColor, RoundedCornerShape(999.dp)),
                    )
                }
            }
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = pctColor,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.5f)))
    }
}

@Composable
private fun SketchPartsGrid(parts: List<FkPart>, readOnly: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.chunked(2).forEach { rowParts ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowParts.forEach { part ->
                    SketchPartCard(part = part, readOnly = readOnly, modifier = Modifier.weight(1f))
                }
                if (rowParts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SketchPartCard(part: FkPart, readOnly: Boolean, modifier: Modifier = Modifier) {
    val sketch = MaterialTheme.workspaceSketch
    val assignedCount = part.slots.count { it != null }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = sketch.surface,
        border = BorderStroke(1.dp, sketch.lineSoft),
    ) {
        Column {
            // Card header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sketch.panelMid)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            "PARTE ${part.num}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.6.sp,
                            ),
                            color = sketch.inkMuted,
                        )
                        Text(
                            part.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = sketch.ink,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = sketch.lineSoft.copy(alpha = 0.5f),
                    ) {
                        Text(
                            "$assignedCount/${part.slots.size}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = sketch.inkMuted,
                        )
                    }
                }
            }
            // Divider
            Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft))
            // Card body
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                part.slots.forEachIndexed { idx, name ->
                    val roleLabel = if (part.slots.size > 1) {
                        if (idx == 0) "Cond." else "Asst."
                    } else null
                    SketchSlotRow(
                        roleLabel = roleLabel,
                        name = name,
                        readOnly = readOnly,
                    )
                }
            }
        }
    }
}

@Composable
private fun SketchSlotRow(roleLabel: String?, name: String?, readOnly: Boolean) {
    val sketch = MaterialTheme.workspaceSketch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (roleLabel != null) {
            Text(
                roleLabel,
                modifier = Modifier.width(42.dp),
                style = MaterialTheme.typography.labelSmall,
                color = sketch.inkMuted,
            )
        }
        if (name != null) {
            SketchSlotFilled(name = name, modifier = Modifier.weight(1f))
        } else {
            SketchSlotEmpty(readOnly = readOnly, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SketchSlotFilled(name: String, modifier: Modifier = Modifier) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = modifier.hoverable(interactionSource).handCursorOnHover().clickable {},
        shape = RoundedCornerShape(5.dp),
        color = if (hovered) sketch.ok.copy(alpha = 0.18f) else sketch.ok.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, if (hovered) sketch.ok.copy(alpha = 0.6f) else sketch.ok.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = sketch.ok,
                modifier = Modifier.size(12.dp),
            )
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.ok,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SketchSlotEmpty(readOnly: Boolean, modifier: Modifier = Modifier) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val interactive = !readOnly

    Surface(
        modifier = modifier
            .hoverable(interactionSource)
            .handCursorOnHover(interactive)
            .clickable(enabled = interactive) {},
        shape = RoundedCornerShape(5.dp),
        color = when {
            hovered && interactive -> sketch.accentSoft
            else -> Color.Transparent
        },
        border = BorderStroke(
            1.dp,
            when {
                hovered && interactive -> sketch.accent.copy(alpha = 0.7f)
                interactive -> sketch.lineSoft
                else -> sketch.lineSoft.copy(alpha = 0.4f)
            },
        ),
    ) {
        Text(
            text = if (interactive) "+ assegna" else "—",
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                hovered && interactive -> sketch.accent
                interactive -> sketch.inkMuted
                else -> sketch.inkMuted.copy(alpha = 0.5f)
            },
        )
    }
}

@Composable
private fun SketchSkippedBody() {
    val sketch = MaterialTheme.workspaceSketch
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Settimana saltata",
            style = MaterialTheme.typography.titleSmall,
            color = sketch.inkSoft,
        )
        Text(
            "Questa settimana è stata esclusa dal programma.\nClicca 'Riattiva' per ripristinarla.",
            style = MaterialTheme.typography.bodySmall,
            color = sketch.inkMuted,
        )
    }
}

@Composable
private fun SketchEmptyState(message: String) {
    val sketch = MaterialTheme.workspaceSketch
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = sketch.inkMuted,
        )
    }
}

// ─── Right panel ──────────────────────────────────────────────────────────────

@Composable
private fun SketchRightPanel(selectedMonth: FkMonth?, issues: List<FkIssue>) {
    val sketch = MaterialTheme.workspaceSketch
    var settingsOpen by remember { mutableStateOf(false) }
    var issuesOpen by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.width(296.dp).fillMaxHeight().background(sketch.panelRight),
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Coverage card
            if (selectedMonth != null) {
                SketchCoverageCard(month = selectedMonth)
            }

            // Settings (collapsible)
            SketchCollapsibleSettings(open = settingsOpen, onToggle = { settingsOpen = !settingsOpen })

            // Issues panel
            if (issues.isNotEmpty()) {
                SketchIssuesPanel(
                    issues = issues,
                    open = issuesOpen,
                    onToggle = { issuesOpen = !issuesOpen },
                )
            }
        }

        // Danger zone (pinned to bottom)
        SketchDangerZone()
    }
}

@Composable
private fun SketchCoverageCard(month: FkMonth) {
    val sketch = MaterialTheme.workspaceSketch
    val fraction = if (month.totalSlots > 0) month.assignedSlots.toFloat() / month.totalSlots else 0f
    val empty = (month.totalSlots - month.assignedSlots).coerceAtLeast(0)
    val fillColor = if (fraction == 1f) sketch.ok else sketch.accent

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SketchSectionTitle("COPERTURA")
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = sketch.surfaceMuted,
            border = BorderStroke(1.dp, sketch.lineSoft),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Big number
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${month.assignedSlots}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp,
                        ),
                        color = sketch.ink,
                    )
                    Text(
                        "/ ${month.totalSlots}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = sketch.inkMuted,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    "slot assegnati · ${month.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = sketch.inkMuted,
                )
                Spacer(Modifier.height(4.dp))
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(sketch.lineSoft),
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction.coerceAtMost(1f))
                                .background(fillColor, RoundedCornerShape(999.dp)),
                        )
                    }
                }
                // Pills
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SketchCovPill("${month.assignedSlots} assegnati", isOk = true)
                    if (empty > 0) SketchCovPill("$empty vuoti", isOk = false)
                }
            }
        }
    }
}

@Composable
private fun SketchCovPill(label: String, isOk: Boolean) {
    val sketch = MaterialTheme.workspaceSketch
    val color = if (isOk) sketch.ok else sketch.warn
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
private fun SketchCollapsibleSettings(open: Boolean, onToggle: () -> Unit) {
    val sketch = MaterialTheme.workspaceSketch
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .handCursorOnHover()
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("IMPOSTAZIONI")
            Icon(
                imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = sketch.inkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Column {
                SketchSettingRow("Cooldown conduttore", value = "4 sett.")
                SketchSettingRow("Cooldown assistente", value = "2 sett.")
                SketchSettingRow("Modalità rigorosa", value = "ON", isToggle = true)
                SketchSettingRow("Peso conduttore", value = "2×")
                SketchSettingRow("Peso assistente", value = "1×")
            }
        }
    }
}

@Composable
private fun SketchSettingRow(label: String, value: String, isToggle: Boolean = false) {
    val sketch = MaterialTheme.workspaceSketch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = sketch.inkSoft,
        )
        if (isToggle) {
            // Simple toggle pill (ON state)
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(sketch.ok),
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = sketch.surface,
                border = BorderStroke(1.dp, sketch.lineSoft),
            ) {
                Text(
                    value,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = sketch.ink,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.4f)))
}

@Composable
private fun SketchIssuesPanel(issues: List<FkIssue>, open: Boolean, onToggle: () -> Unit) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = sketch.warn.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, sketch.warn.copy(alpha = 0.4f)),
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handCursorOnHover()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = sketch.warn,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    "PROBLEMI",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = sketch.warn,
                )
                // Badge count
                Box(
                    modifier = Modifier.size(17.dp).clip(CircleShape).background(sketch.warn),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${issues.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                        ),
                        color = Color.White,
                    )
                }
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = sketch.warn,
                    modifier = Modifier.size(12.dp),
                )
            }
            AnimatedVisibility(visible = open) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.warn.copy(alpha = 0.4f)))
                    issues.forEach { issue ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .handCursorOnHover()
                                .clickable {}
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                "${issue.partTitle.uppercase()} · ${issue.week}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 0.4.sp,
                                ),
                                color = sketch.warn,
                            )
                            Text(
                                issue.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = sketch.inkSoft,
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().height(1.dp)
                                .background(sketch.warn.copy(alpha = 0.15f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SketchDangerZone() {
    val sketch = MaterialTheme.workspaceSketch
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(sketch.lineSoft.copy(alpha = 0.5f)))
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SketchDangerButton("Svuota assegnazioni future", Icons.Filled.ClearAll)
            SketchDangerButton("Elimina mese", Icons.Filled.Delete)
        }
    }
}

@Composable
private fun SketchDangerButton(label: String, icon: ImageVector) {
    val sketch = MaterialTheme.workspaceSketch
    Surface(
        modifier = Modifier.fillMaxWidth().handCursorOnHover().clickable {},
        shape = RoundedCornerShape(5.dp),
        color = sketch.surface,
        border = BorderStroke(1.dp, sketch.bad.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = sketch.bad, modifier = Modifier.size(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = sketch.bad,
            )
        }
    }
}

// ─── Status bar ───────────────────────────────────────────────────────────────

@Composable
private fun SketchStatusBar(
    monthLabel: String,
    weekLabel: String,
    assignedSlots: Int,
    totalSlots: Int,
) {
    val sketch = MaterialTheme.workspaceSketch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(sketch.accent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SketchStatusItem(monthLabel, Icons.Filled.Today)
        SketchStatusSep()
        SketchStatusItem("Settimana $weekLabel")
        SketchStatusSep()
        SketchStatusItem("$assignedSlots/$totalSlots slot")
        Spacer(Modifier.weight(1f))
        SketchStatusItem("2 mar 2026")
    }
}

@Composable
private fun SketchStatusItem(label: String, icon: ImageVector? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(11.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun SketchStatusSep() {
    Box(
        Modifier
            .height(12.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    )
}

// ─── Shared primitives ────────────────────────────────────────────────────────

@Composable
private fun SketchSectionTitle(text: String) {
    val sketch = MaterialTheme.workspaceSketch
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 0.7.sp,
        ),
        color = sketch.inkMuted,
    )
}

@Composable
private fun SketchBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
            ),
            color = color,
        )
    }
}

private enum class HdrTone { Neutral, Warn, Ok }

@Composable
private fun SketchHdrButton(label: String, icon: ImageVector, tone: HdrTone) {
    val sketch = MaterialTheme.workspaceSketch
    val (fg, border, bg) = when (tone) {
        HdrTone.Neutral -> Triple(sketch.inkSoft, sketch.lineSoft, Color.Transparent)
        HdrTone.Warn -> Triple(sketch.warn, sketch.warn.copy(alpha = 0.45f), Color.Transparent)
        HdrTone.Ok -> Triple(sketch.ok, sketch.ok.copy(alpha = 0.45f), Color.Transparent)
    }
    Surface(
        modifier = Modifier.handCursorOnHover().clickable {},
        shape = RoundedCornerShape(5.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = fg,
            )
        }
    }
}

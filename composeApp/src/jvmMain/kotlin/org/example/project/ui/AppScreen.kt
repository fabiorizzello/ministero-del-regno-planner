package org.example.project.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import kotlin.math.roundToInt
import org.example.project.ui.components.handCursorOnHover
import org.example.project.ui.diagnostics.DiagnosticsScreen
import org.example.project.ui.proclamatori.ProclamatoriScreen
import org.example.project.ui.theme.AppTheme
import org.example.project.ui.theme.spacing
import org.example.project.ui.theme.workspaceSketch
import org.example.project.ui.theme.workspaceTokens
import org.example.project.ui.updates.UpdateCenterUiState
import org.example.project.ui.updates.UpdateCenterViewModel
import org.example.project.ui.workspace.ProgramWorkspaceScreen
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.koin.core.context.GlobalContext

internal val LocalSectionNavigator = staticCompositionLocalOf<(AppSection) -> Unit> { {} }
private const val UI_SCALE_MIN = 0.85f
private const val UI_SCALE_MAX = 1.25f
private const val UI_SCALE_STEP_PERCENT = 5
private const val UI_SCALE_SLIDER_STEPS = 7
private val UI_SCALE_PRESET_PERCENTAGES = listOf(90, 100, 110, 120)
private const val TAG_TOP_BAR = "top-bar"
private const val TAG_SECTION_PROGRAMMA = "top-section-programma"
private const val TAG_SECTION_PROCLAMATORI = "top-section-proclamatori"
private const val TAG_SECTION_DIAGNOSTICA = "top-section-diagnostica"

internal enum class AppSection(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
) {
    PLANNING("Programma", Icons.Filled.Dashboard, PlanningDashboardSectionScreen),
    PROCLAMATORI("Studenti", Icons.Filled.Groups, ProclamatoriSectionScreen),
    DIAGNOSTICS("Diagnostica", Icons.Filled.BugReport, DiagnosticsSectionScreen),
}

@Composable
fun DecoratedWindowScope.AppScreen(
    initialUiScale: Float = 1f,
    onUiScaleChange: (Float) -> Unit = {},
    onRestartRequested: () -> Unit = {},
) {
    AppTheme {
        val spacing = MaterialTheme.spacing
        val workspaceTokens = MaterialTheme.workspaceTokens
        val sketch = MaterialTheme.workspaceSketch
        var uiScale by rememberSaveable(initialUiScale) {
            mutableFloatStateOf(initialUiScale.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX))
        }
        var draftUiScale by remember { mutableFloatStateOf(uiScale) }
        var isSizeMenuExpanded by rememberSaveable { mutableStateOf(false) }
        var isUpdateMenuExpanded by rememberSaveable { mutableStateOf(false) }
        val updateViewModel = remember { GlobalContext.get().get<UpdateCenterViewModel>() }
        val updateState by updateViewModel.state.collectAsState()
        val baseDensity = LocalDensity.current
        val scaledDensity = remember(baseDensity, uiScale) {
            Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale * uiScale,
            )
        }

        Navigator(AppSection.PLANNING.screen) { navigator ->
            val currentSection = AppSection.entries
                .firstOrNull { it.screen::class == navigator.lastItem::class }
                ?: AppSection.PLANNING
            val navigationSections = remember { AppSection.entries.toList() }

            CompositionLocalProvider(
                LocalSectionNavigator provides { section ->
                    if (currentSection != section) {
                        navigator.replaceAll(section.screen)
                    }
                },
                LocalDensity provides scaledDensity,
            ) {
                val navigateToSection = LocalSectionNavigator.current

                fun applyUiScale(value: Float) {
                    val updatedScale = snapUiScale(value)
                    if (updatedScale != uiScale) {
                        uiScale = updatedScale
                        onUiScaleChange(updatedScale)
                    }
                    draftUiScale = updatedScale
                }

                val windowBackdrop = Brush.radialGradient(
                    colors = listOf(
                        sketch.accent.copy(alpha = 0.2f),
                        sketch.ok.copy(alpha = 0.08f),
                        sketch.windowBackground,
                    ),
                    radius = 1500f,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(workspaceTokens.windowRadius))
                        .background(windowBackdrop),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(workspaceTokens.windowRadius),
                        color = sketch.windowBackground,
                        border = BorderStroke(1.dp, sketch.windowBorder),
                        shadowElevation = 12.dp,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TitleBar(
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag(TAG_TOP_BAR)
                                    .newFullscreenControls(),
                                gradientStartColor = Color.Unspecified,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.Start)
                                        .fillMaxHeight()
                                        .padding(start = spacing.lg),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(workspaceTokens.controlRadius),
                                        color = sketch.toolbarSelectedBg,
                                        border = BorderStroke(1.dp, sketch.toolbarSelectedBorder.copy(alpha = 0.55f)),
                                    ) {
                                        Text(
                                            text = "S",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = sketch.toolbarSelectedInk,
                                        )
                                    }
                                    Text(
                                        text = "Scuola di ministero",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = sketch.toolbarInk,
                                    )
                                }

                                Row(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    navigationSections.filter { it != AppSection.DIAGNOSTICS }.forEach { section ->
                                        TopBarSectionButton(
                                            selected = currentSection == section,
                                            onClick = { navigateToSection(section) },
                                            section = section,
                                            tooltip = "Apri ${section.label}",
                                            tag = when (section) {
                                                AppSection.PLANNING -> TAG_SECTION_PROGRAMMA
                                                AppSection.PROCLAMATORI -> TAG_SECTION_PROCLAMATORI
                                                AppSection.DIAGNOSTICS -> TAG_SECTION_DIAGNOSTICA
                                            },
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(end = spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                ) {
                                    ToolbarIconAction(
                                        onClick = { navigateToSection(AppSection.DIAGNOSTICS) },
                                        icon = Icons.Filled.BugReport,
                                        contentDescription = "Diagnostica",
                                        tooltip = "Apri strumenti diagnostici",
                                        modifier = Modifier.alpha(
                                            if (currentSection == AppSection.DIAGNOSTICS) 0.75f else 0.35f
                                        ),
                                    )
                                    Box {
                                        UpdateToolbarAction(
                                            state = updateState,
                                            onClick = { isUpdateMenuExpanded = true },
                                        )
                                        DropdownMenu(
                                            expanded = isUpdateMenuExpanded,
                                            onDismissRequest = { isUpdateMenuExpanded = false },
                                            properties = PopupProperties(focusable = true),
                                            shape = RoundedCornerShape(12.dp),
                                            containerColor = sketch.surface,
                                            border = BorderStroke(1.dp, sketch.lineSoft),
                                            shadowElevation = 8.dp,
                                        ) {
                                            UpdateCenterMenu(
                                                state = updateState,
                                                onCheckUpdates = updateViewModel::checkUpdates,
                                                onStartUpdate = updateViewModel::startUpdate,
                                                onRestart = {
                                                    isUpdateMenuExpanded = false
                                                    onRestartRequested()
                                                },
                                            )
                                        }
                                    }
                                    Box {
                                        ToolbarIconAction(
                                            onClick = {
                                                draftUiScale = uiScale
                                                isSizeMenuExpanded = true
                                            },
                                            icon = Icons.Filled.FormatSize,
                                            contentDescription = "Dimensione testo",
                                            tooltip = "Regola la scala dell'interfaccia",
                                        )
                                        DropdownMenu(
                                            expanded = isSizeMenuExpanded,
                                            onDismissRequest = {
                                                applyUiScale(draftUiScale)
                                                isSizeMenuExpanded = false
                                            },
                                            properties = PopupProperties(focusable = true),
                                            shape = RoundedCornerShape(12.dp),
                                            containerColor = sketch.surface,
                                            border = BorderStroke(1.dp, sketch.lineSoft),
                                            shadowElevation = 8.dp,
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .width(320.dp)
                                                    .padding(spacing.md),
                                                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                            ) {
                                                val draftPercentage = draftUiScale.toUiScalePercentage()
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text("Dimensione testo", style = MaterialTheme.typography.titleSmall)
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = sketch.accentSoft,
                                                        border = BorderStroke(1.dp, sketch.accent.copy(alpha = 0.52f)),
                                                    ) {
                                                        Text(
                                                            "${draftPercentage}%",
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = sketch.accent,
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "Regola la scala dell'interfaccia",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = sketch.inkMuted,
                                                )
                                                Slider(
                                                    value = draftUiScale,
                                                    onValueChange = { draftUiScale = snapUiScale(it) },
                                                    onValueChangeFinished = { applyUiScale(draftUiScale) },
                                                    valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                                                    steps = UI_SCALE_SLIDER_STEPS,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = sketch.accent,
                                                        activeTrackColor = sketch.accent,
                                                        inactiveTrackColor = sketch.lineSoft,
                                                        activeTickColor = sketch.surface,
                                                        inactiveTickColor = sketch.inkMuted.copy(alpha = 0.35f),
                                                    ),
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                                ) {
                                                    UI_SCALE_PRESET_PERCENTAGES.forEach { preset ->
                                                        val presetScale = preset.toUiScale()
                                                        ScaleMenuButton(
                                                            onClick = { applyUiScale(presetScale) },
                                                            enabled = draftPercentage != preset,
                                                            selected = draftPercentage == preset,
                                                            label = "${preset}%",
                                                            tooltip = "Imposta la scala al ${preset}%",
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    ScaleMenuButton(
                                                        onClick = { applyUiScale((draftPercentage - UI_SCALE_STEP_PERCENT).toUiScale()) },
                                                        enabled = draftPercentage > UI_SCALE_MIN.toUiScalePercentage(),
                                                        label = "-${UI_SCALE_STEP_PERCENT}%",
                                                        tooltip = "Riduci la scala di ${UI_SCALE_STEP_PERCENT} punti",
                                                    )
                                                    ScaleMenuButton(
                                                        onClick = { applyUiScale((draftPercentage + UI_SCALE_STEP_PERCENT).toUiScale()) },
                                                        enabled = draftPercentage < UI_SCALE_MAX.toUiScalePercentage(),
                                                        label = "+${UI_SCALE_STEP_PERCENT}%",
                                                        tooltip = "Aumenta la scala di ${UI_SCALE_STEP_PERCENT} punti",
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                CurrentScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBarSectionButton(
    selected: Boolean,
    onClick: () -> Unit,
    section: AppSection,
    tooltip: String? = null,
    tag: String,
) {
    val sketch = MaterialTheme.workspaceSketch
    val tokens = MaterialTheme.workspaceTokens
    val shape = RoundedCornerShape(tokens.controlRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerColor = when {
        selected -> sketch.toolbarSelectedBg
        isFocused -> sketch.accentSoft.copy(alpha = 0.85f)
        isHovered -> sketch.toolbarSurface
        else -> Color.Transparent
    }
    val border: BorderStroke? = when {
        selected -> BorderStroke(1.dp, sketch.toolbarSelectedBorder)
        isFocused -> BorderStroke(1.dp, sketch.accent.copy(alpha = 0.72f))
        isHovered -> BorderStroke(1.dp, sketch.toolbarBorder)
        else -> null
    }
    val contentColor = when {
        selected -> sketch.toolbarSelectedInk
        isFocused -> sketch.accent
        isHovered -> sketch.toolbarInk
        else -> sketch.toolbarInkMuted
    }

    TooltipWrap(tooltip) {
        Surface(
            modifier = Modifier
                .testTag(tag)
                .handCursorOnHover()
                .hoverable(interactionSource)
                .focusable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = shape,
            color = containerColor,
            border = border,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = section.label,
                    tint = contentColor,
                    modifier = Modifier.width(15.dp),
                )
                Text(
                    text = section.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data object ProclamatoriSectionScreen : Screen {
    @Composable
    override fun Content() {
        ProclamatoriScreen()
    }
}

private data object PlanningDashboardSectionScreen : Screen {
    @Composable
    override fun Content() {
        ProgramWorkspaceScreen()
    }
}

private data object DiagnosticsSectionScreen : Screen {
    @Composable
    override fun Content() {
        DiagnosticsScreen()
    }
}

private fun Float.toUiScalePercentage(): Int = (this * 100f).roundToInt()

private fun Int.toUiScale(): Float {
    val minPercentage = UI_SCALE_MIN.toUiScalePercentage()
    val maxPercentage = UI_SCALE_MAX.toUiScalePercentage()
    return this.coerceIn(minPercentage, maxPercentage) / 100f
}

private fun snapUiScale(value: Float): Float {
    val snappedPercentage = ((value * 100f) / UI_SCALE_STEP_PERCENT).roundToInt() * UI_SCALE_STEP_PERCENT
    return snappedPercentage.toUiScale()
}

@Composable
private fun ScaleMenuButton(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean,
    selected: Boolean = false,
    tooltip: String? = null,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val alpha = if (enabled || selected) 1f else 0.45f

    TooltipWrap(tooltip) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = when {
                selected -> sketch.accentSoft
                (isHovered || isFocused) && enabled -> sketch.surface
                else -> sketch.surfaceMuted
            },
            border = BorderStroke(
                1.dp,
                when {
                    selected -> sketch.accent.copy(alpha = 0.72f)
                    isFocused && enabled -> sketch.accent.copy(alpha = 0.72f)
                    else -> sketch.lineSoft.copy(alpha = alpha)
                },
            ),
            modifier = modifier
                .hoverable(interactionSource)
                .focusable(enabled = enabled && !selected, interactionSource = interactionSource)
                .handCursorOnHover(enabled = enabled && !selected)
                .clickable(
                    enabled = enabled && !selected,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Text(
                text = label,
                color = (if (selected) sketch.accent else sketch.inkSoft).copy(alpha = alpha),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ToolbarIconAction(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tooltip: String? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val alpha = if (enabled) 1f else 0.45f

    val bg = when {
        isDestructive && isHovered -> MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
        isDestructive && isFocused -> MaterialTheme.colorScheme.error.copy(alpha = 0.92f)
        isFocused -> sketch.accentSoft.copy(alpha = 0.8f)
        isHovered -> sketch.toolbarSurface
        else -> Color.Transparent
    }
    val border: BorderStroke? = when {
        isDestructive && (isHovered || isFocused) ->
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.78f))
        isFocused -> BorderStroke(1.dp, sketch.accent.copy(alpha = 0.7f * alpha))
        isHovered -> BorderStroke(1.dp, sketch.toolbarBorder.copy(alpha = alpha))
        else -> null
    }
    val tint = if (isDestructive) {
        if (isHovered || isFocused) Color.White.copy(alpha = alpha)
        else sketch.toolbarInkMuted.copy(alpha = alpha)
    } else {
        sketch.toolbarInkMuted.copy(alpha = alpha)
    }

    TooltipWrap(tooltip) {
        Surface(
            shape = if (fillHeight) RoundedCornerShape(0.dp) else CircleShape,
            color = bg,
            border = border,
            modifier = modifier
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .hoverable(interactionSource)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .handCursorOnHover(enabled = enabled)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Box(
                modifier = if (fillHeight) Modifier.size(48.dp) else Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateToolbarAction(
    state: UpdateCenterUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        state.restartRequired -> sketch.ok.copy(alpha = 0.14f)
        state.updateAvailable -> sketch.warn.copy(alpha = 0.12f)
        state.hasError -> sketch.bad.copy(alpha = 0.1f)
        isFocused -> sketch.accentSoft.copy(alpha = 0.8f)
        isHovered -> sketch.toolbarSurface
        else -> Color.Transparent
    }
    val border = when {
        state.restartRequired -> BorderStroke(1.dp, sketch.ok.copy(alpha = 0.45f))
        state.updateAvailable -> BorderStroke(1.dp, sketch.warn.copy(alpha = 0.45f))
        state.hasError -> BorderStroke(1.dp, sketch.bad.copy(alpha = 0.4f))
        isFocused -> BorderStroke(1.dp, sketch.accent.copy(alpha = 0.7f))
        isHovered -> BorderStroke(1.dp, sketch.toolbarBorder)
        else -> null
    }
    val icon = when {
        state.restartRequired -> Icons.Filled.RestartAlt
        state.hasError -> Icons.Filled.ErrorOutline
        state.updateAvailable -> Icons.Filled.ArrowCircleUp
        else -> Icons.Filled.SystemUpdateAlt
    }
    val tint = when {
        state.restartRequired -> sketch.ok
        state.hasError -> sketch.bad
        state.updateAvailable -> sketch.warn
        state.isBusy -> sketch.accent
        else -> sketch.toolbarInkMuted
    }
    val tooltipText = when {
        state.restartRequired -> "Aggiornamento pronto. Riavvia per installare"
        state.isInstalling -> "Preparazione aggiornamento in corso"
        state.isDownloading -> "Download aggiornamento in corso"
        state.isChecking -> "Verifica aggiornamenti in corso"
        state.updateAvailable -> "Aggiornamento disponibile. Un clic scarica e prepara l'installazione"
        state.hasError -> state.statusText
        else -> "Aggiornamenti applicazione"
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
    ) {
        Surface(
            shape = CircleShape,
            color = bg,
            border = border,
            modifier = modifier
                .hoverable(interactionSource)
                .focusable(interactionSource = interactionSource)
                .handCursorOnHover()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = tint,
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Aggiornamenti applicazione",
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCenterMenu(
    state: UpdateCenterUiState,
    onCheckUpdates: () -> Unit,
    onStartUpdate: () -> Unit,
    onRestart: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    Column(
        modifier = Modifier
            .width(392.dp)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        UpdateHeroCard(state = state)
        if (!state.releaseTitle.isNullOrBlank() && (state.updateAvailable || state.restartRequired)) {
            UpdateReleaseNotesCard(
                title = state.releaseTitle,
                notes = state.releaseNotes,
            )
        }
        when {
            state.restartRequired -> UpdateMenuAction(
                label = "Riavvia per installare",
                icon = Icons.Filled.RestartAlt,
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                emphasized = true,
                tooltip = "Chiude l'app, esegue l'aggiornamento e la riapre automaticamente",
            )
            state.isBusy -> UpdateMenuAction(
                label = when {
                    state.isInstalling -> "Preparazione in corso..."
                    state.isDownloading -> "Download in corso..."
                    else -> "Verifica in corso..."
                },
                icon = Icons.Filled.Refresh,
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            state.updateAvailable && state.updateAsset != null -> {
                UpdateMenuAction(
                    label = "Scarica e prepara ${formatVersion(state.latestVersion)}",
                    icon = Icons.Filled.ArrowCircleUp,
                    onClick = onStartUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    emphasized = true,
                    tooltip = "Scarica la release e prepara l'installazione al riavvio dell'app",
                )
                Text(
                    text = "Scarico ora. Ti chiedo il riavvio solo quando l'installazione e pronta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkMuted,
                )
            }
            else -> UpdateMenuAction(
                label = "Controlla aggiornamenti",
                icon = Icons.Filled.Refresh,
                onClick = onCheckUpdates,
                modifier = Modifier.fillMaxWidth(),
                tooltip = "Controlla se esiste una release più recente",
            )
        }
    }
}

@Composable
private fun UpdateHeroCard(state: UpdateCenterUiState) {
    val spacing = MaterialTheme.spacing
    val sketch = MaterialTheme.workspaceSketch
    val tone = when {
        state.restartRequired -> UpdateHeroTone("Riavvia per installare", sketch.ok, Icons.Filled.RestartAlt)
        state.isInstalling -> UpdateHeroTone("Preparazione in corso", sketch.warn, Icons.Filled.FileOpen)
        state.isDownloading -> UpdateHeroTone("Download in corso", sketch.warn, Icons.Filled.FileOpen)
        state.isChecking -> UpdateHeroTone("Verifica release", sketch.accent, Icons.Filled.Refresh)
        state.updateAvailable -> UpdateHeroTone("Aggiornamento disponibile", sketch.warn, Icons.Filled.ArrowCircleUp)
        state.hasError -> UpdateHeroTone("Riprova aggiornamento", sketch.bad, Icons.Filled.ErrorOutline)
        state.lastCheck != null -> UpdateHeroTone("Nessuna azione richiesta", sketch.ok, Icons.Filled.CheckCircle)
        else -> UpdateHeroTone("Aggiornamenti", sketch.inkSoft, Icons.Filled.SystemUpdateAlt)
    }
    val primaryMessage = updatePrimaryMessage(state)
    val secondaryMessage = updateSecondaryMessage(state)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = sketch.surface,
        border = BorderStroke(1.dp, sketch.lineSoft),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(96.dp)
                    .background(tone.accent.copy(alpha = 0.9f)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = sketch.surfaceMuted,
                        border = BorderStroke(1.dp, sketch.lineSoft),
                    ) {
                        Icon(
                            imageVector = tone.icon,
                            contentDescription = null,
                            tint = tone.accent,
                            modifier = Modifier.padding(8.dp).size(16.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prossima azione",
                            style = MaterialTheme.typography.labelMedium,
                            color = tone.accent,
                        )
                        Text(
                            text = tone.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = sketch.ink,
                        )
                        Text(
                            text = primaryMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = sketch.inkSoft,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = sketch.surfaceMuted,
                        border = BorderStroke(1.dp, sketch.lineSoft),
                    ) {
                        Text(
                            text = normalizedUpdateVersionSummary(state),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = sketch.inkSoft,
                        )
                    }
                }
                if (!secondaryMessage.isNullOrBlank()) {
                    Text(
                        text = secondaryMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = sketch.inkMuted,
                    )
                }
            }
        }
    }
}

private fun normalizedUpdateVersionSummary(state: UpdateCenterUiState): String {
    val current = formatVersion(state.currentVersion)
    val latest = state.latestVersion
    return if (latest.isNullOrBlank() || sameVersion(latest, state.currentVersion)) current else "$current -> ${formatVersion(latest)}"
}

private fun formatVersion(version: String?): String =
    normalizeVersion(version)?.let { "v$it" } ?: "versione sconosciuta"

private fun normalizeVersion(version: String?): String? =
    version
        ?.trim()
        ?.removePrefix("v")
        ?.takeIf(String::isNotBlank)

private fun sameVersion(left: String?, right: String?): Boolean =
    normalizeVersion(left) == normalizeVersion(right)

private fun updatePrimaryMessage(state: UpdateCenterUiState): String = when {
    state.restartRequired -> "L'aggiornamento e pronto. Premi il pulsante qui sotto per completare l'installazione."
    state.isInstalling -> "Sto preparando l'installazione. Non serve fare altro adesso."
    state.isDownloading -> "Sto scaricando il pacchetto di aggiornamento."
    state.isChecking -> "Sto controllando se esiste una release piu recente."
    state.updateAvailable -> "${formatVersion(state.latestVersion)} e pronta. Un clic la scarica e prepara l'installazione."
    state.hasError -> "L'ultimo tentativo non e andato a buon fine."
    state.lastCheck != null -> "Stai gia usando l'ultima versione disponibile."
    else -> "Controlla se esiste una versione piu recente dell'app."
}

private fun updateSecondaryMessage(state: UpdateCenterUiState): String? = when {
    state.restartRequired -> "Dopo il click compare una finestra separata di installazione e l'app si riapre automaticamente."
    state.isInstalling -> "Tra poco ti chiedero il riavvio per installare la nuova versione."
    state.isDownloading -> "Al termine preparo l'installazione e poi ti chiedo il riavvio."
    state.hasError -> state.statusText
    state.updateAvailable -> "Versione corrente ${formatVersion(state.currentVersion)}."
    state.lastCheck != null -> state.lastCheck.let { "Ultimo controllo ${formatUpdateTimestamp(it)}." }
    else -> null
}


@Suppress("unused")
private fun legacyUpdateVersionSummary(state: UpdateCenterUiState): String {
    val current = formatVersion(state.currentVersion)
    val latest = state.latestVersion
    return if (latest.isNullOrBlank() || latest == state.currentVersion) current else "$current → v$latest"
}

private data class UpdateHeroTone(
    val title: String,
    val accent: Color,
    val icon: ImageVector,
)

private enum class UpdateJourneyStatus {
    Pending,
    Ready,
    Active,
    Completed,
}

private data class UpdateJourneyPalette(
    val background: Color,
    val border: Color,
    val content: Color,
    val badgeBackground: Color,
    val badgeContent: Color,
)

@Composable
private fun UpdateCenterInfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val sketch = MaterialTheme.workspaceSketch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(108.dp),
            style = MaterialTheme.typography.labelMedium,
            color = sketch.inkMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = sketch.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UpdateReleaseNotesCard(title: String?, notes: String?) {
    val sketch = MaterialTheme.workspaceSketch
    val spacing = MaterialTheme.spacing
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = sketch.surfaceMuted,
        border = BorderStroke(1.dp, sketch.lineSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Dettagli release",
                style = MaterialTheme.typography.labelMedium,
                color = sketch.inkMuted,
            )
            Text(
                text = title.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = sketch.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!notes.isNullOrBlank()) {
                Text(
                    text = notes,
                    modifier = Modifier
                        .height(96.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    color = sketch.inkSoft,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateMenuAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    tooltip: String? = null,
) {
    val sketch = MaterialTheme.workspaceSketch
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val borderColor = when {
        !enabled -> sketch.lineSoft.copy(alpha = 0.7f)
        emphasized -> sketch.warn.copy(alpha = 0.5f)
        isHovered -> sketch.accent.copy(alpha = 0.45f)
        else -> sketch.lineStrong
    }
    val background = when {
        !enabled -> sketch.surfaceMuted.copy(alpha = 0.65f)
        emphasized -> sketch.warn.copy(alpha = 0.12f)
        isHovered -> sketch.accentSoft.copy(alpha = 0.7f)
        else -> sketch.surface
    }
    val content = when {
        !enabled -> sketch.inkMuted.copy(alpha = 0.7f)
        emphasized -> sketch.warn
        else -> sketch.inkSoft
    }
    val actionContent: @Composable (Modifier) -> Unit = { anchorModifier ->
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = background,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier
                .then(anchorModifier)
                .handCursorOnHover(enabled = enabled)
                .hoverable(interactionSource, enabled = enabled)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = content,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                )
            }
        }
    }

    if (tooltip.isNullOrBlank()) {
        actionContent(Modifier)
    } else {
        TooltipWrap(tooltip) {
            actionContent(Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipWrap(
    tooltip: String?,
    content: @Composable () -> Unit,
) {
    if (tooltip.isNullOrBlank()) {
        content()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
            ),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState(),
        ) {
            content()
        }
    }
}

private fun formatUpdateTimestamp(instant: java.time.Instant): String =
    instant.atZone(java.time.ZoneId.systemDefault())
        .format(org.example.project.ui.components.timestampFormatter)

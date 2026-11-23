package org.example.project.ui.workspace

import androidx.compose.material3.Surface
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.theme.AppTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class ProgramWorkspaceResponsiveLayoutTest {
    @Test
    fun `compact actions float over the week without moving its assignment controls`() {
        val monday = LocalDate.of(2026, 11, 23)
        val program = ProgramMonth(
            id = ProgramMonthId("november"),
            year = 2026,
            month = 11,
            startDate = monday.withDayOfMonth(1),
            endDate = monday.withDayOfMonth(30),
            templateAppliedAt = null,
            createdAt = monday.atStartOfDay(),
        )
        val type = PartType(
            PartTypeId("conversation"), "CONVERSATION", "Iniziare una conversazione",
            2, SexRule.entries.first(), false, 0,
        )
        val part = WeeklyPart(WeeklyPartId("conversation"), type, sortOrder = 0)
        val week = WeekPlan(WeekPlanId("week"), monday, listOf(part), program.id)
        val loaded = ProgramLifecycleUiState(
            today = monday,
            isLoading = false,
            currentProgram = program,
            selectedProgramId = program.id,
            selectedProgramWeeks = listOf(week),
        )
        val lifecycleState = MutableStateFlow(loaded)
        val lifecycle = mockk<ProgramLifecycleViewModel>(relaxed = true)
        val assignment = mockk<AssignmentManagementViewModel>(relaxed = true)
        val schema = mockk<SchemaManagementViewModel>()
        val picker = mockk<PersonPickerViewModel>()
        val editor = mockk<PartEditorViewModel>()
        every { lifecycle.state } returns lifecycleState
        every { assignment.uiState } returns MutableStateFlow(AssignmentManagementUiState())
        every { schema.state } returns MutableStateFlow(SchemaManagementUiState())
        every { picker.state } returns MutableStateFlow(PersonPickerUiState())
        every { editor.state } returns MutableStateFlow(PartEditorUiState())
        startKoin {
            modules(module {
                single { lifecycle }
                single { assignment }
                single { schema }
                single { picker }
                single { editor }
            })
        }
        try {
            for ((width, darkTheme) in listOf(375 to false, 600 to true, 768 to true, 899 to false, 900 to true)) {
                val scene = ImageComposeScene(width = width, height = 760) {
                    AppTheme(darkTheme = darkTheme) { Surface { ProgramWorkspaceScreen() } }
                }
                try {
                    scene.render()
                    val initial = planningBounds(scene)
                    if (width < 900) {
                        assertNull(findByTag(scene, "program-actions-panel"))
                        click(scene, "Azioni programma")
                        assertFloatingPanel(scene, initial)
                        click(scene, "IMPOSTAZIONI")
                        assertNotNull(findText(scene, "Riposo conduzione"))
                        lifecycleState.value = loaded.copy(isLoading = true)
                        scene.render()
                        assertNotNull(findText(scene, "Caricamento programma in corso..."))
                        lifecycleState.value = loaded
                        scene.render()
                        assertFloatingPanel(scene, initial)
                        assertNotNull(findText(scene, "Riposo conduzione"))
                        requestFocus(scene, "Autoassegna")
                        pressEscape(scene)
                        assertNull(findByTag(scene, "program-actions-panel"))
                        assertEquals(initial, planningBounds(scene))
                        val trigger = assertNotNull(findText(scene, "Azioni programma"))
                        assertTrue(generateSequence(trigger) { it.parent }
                            .any { it.config.getOrNull(SemanticsProperties.Focused) == true })

                        click(scene, "Azioni programma")
                        click(scene, "Chiudi azioni")
                        assertNull(findByTag(scene, "program-actions-panel"))
                        assertEquals(initial, planningBounds(scene))
                    } else {
                        val actions = assertNotNull(findByTag(scene, "program-actions-panel")).boundsInRoot
                        assertTrue(initial.week.right <= actions.left)
                        assertNull(findText(scene, "Azioni programma"))
                    }
                } finally {
                    scene.close()
                }
            }
        } finally {
            stopKoin()
        }
    }

    private data class PlanningBounds(val programs: Rect, val week: Rect, val assignmentControls: List<Rect>)

    private fun planningBounds(scene: ImageComposeScene): PlanningBounds {
        val programs = assertNotNull(findByTag(scene, "program-programs-panel")).boundsInRoot
        val week = assertNotNull(findByTag(scene, "program-week-panel")).boundsInRoot
        val assignActions = textNodes(scene, "Assegna").map {
            generateSequence(it) { node -> node.parent }
                .first { node -> node.config.getOrNull(SemanticsActions.OnClick)?.action != null }
                .boundsInRoot
        }.toList()
        assertEquals(2, assignActions.size)
        assignActions.forEach { action ->
            assertTrue(
                action.width >= 64 && action.height >= 30,
                "Assignment control is too small: $action",
            )
        }
        assertTrue(programs.width > 0 && week.width > 0)
        assertTrue(programs.right <= week.left)
        return PlanningBounds(programs, week, assignActions)
    }

    private fun assertFloatingPanel(scene: ImageComposeScene, initial: PlanningBounds) {
        assertEquals(initial, planningBounds(scene))
        val actions = assertNotNull(findByTag(scene, "program-actions-panel")).boundsInRoot
        assertTrue(actions.width > 0 && actions.left > initial.week.left && actions.right <= initial.week.right)
        assertTrue(actions.top < initial.week.bottom && actions.bottom > initial.week.top)
    }

    private fun nodes(scene: ImageComposeScene): Sequence<SemanticsNode> {
        fun descendants(node: SemanticsNode): Sequence<SemanticsNode> =
            sequenceOf(node) + node.children.asSequence().flatMap(::descendants)
        return scene.semanticsOwners.asSequence()
            .flatMap { descendants(it.rootSemanticsNode) }
    }

    private fun findByTag(scene: ImageComposeScene, tag: String): SemanticsNode? =
        nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }

    private fun findText(scene: ImageComposeScene, label: String): SemanticsNode? = textNodes(scene, label).firstOrNull()

    private fun textNodes(scene: ImageComposeScene, label: String): Sequence<SemanticsNode> =
        nodes(scene).filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }

    private fun requestFocus(scene: ImageComposeScene, label: String) {
        val node = assertNotNull(findText(scene, label))
        val focusAction = generateSequence(node) { it.parent }
            .firstNotNullOf { it.config.getOrNull(SemanticsActions.RequestFocus)?.action }
        assertTrue(focusAction())
    }

    @OptIn(InternalComposeUiApi::class)
    private fun pressEscape(scene: ImageComposeScene) {
        assertTrue(scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)))
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    private fun click(scene: ImageComposeScene, label: String) {
        val node = assertNotNull(findText(scene, label))
        val clickAction = generateSequence(node) { it.parent }
            .firstNotNullOf { it.config.getOrNull(SemanticsActions.OnClick)?.action }
        assertTrue(clickAction())
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}

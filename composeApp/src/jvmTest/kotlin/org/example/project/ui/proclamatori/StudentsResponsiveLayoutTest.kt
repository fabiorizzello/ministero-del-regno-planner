package org.example.project.ui.proclamatori

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.ui.theme.AppTheme
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class StudentsResponsiveLayoutTest {
    @Test
    fun `compact cards retain sorting and page selection`() {
        val person = Proclamatore(ProclamatoreId("preview"), "Mario", "Rossi", Sesso.M)
        val events = mockk<ProclamatoriElencoEvents>(relaxed = true)
        var selectedIds = emptyList<ProclamatoreId>()
        var selected = false
        var newSort: ProclamatoriSort? = null
        every { events.onToggleSelectPage } returns { ids, checked -> selectedIds = ids; selected = checked }
        every { events.onSortChange } returns { newSort = it }
        val scene = ImageComposeScene(width = 375, height = 760) {
            AppTheme {
                Surface {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        ProclamatoriElencoContent(
                            state = ProclamatoriListUiState(allItems = listOf(person), isLoading = false),
                            viewMode = ProclamatoriListViewMode.TABLE,
                            onViewModeChange = {},
                            searchFocusRequester = FocusRequester(),
                            tableListState = rememberLazyListState(),
                            events = events,
                        )
                    }
                }
            }
        }
        try {
            scene.render()
            click(scene, "Seleziona pagina")
            assertEquals(listOf(person.id), selectedIds)
            assertTrue(selected)
            click(scene, "Ordina: Cognome")
            scene.render()
            click(scene, "Nome")
            assertEquals(ProclamatoriSort(ProclamatoriSortField.NOME), newSort)
            scene.render()
            save(scene, "students-375")
        } finally { scene.close() }
    }

    @Test
    fun `eligibility history wraps without truncating role labels`() {
        val label = "Iniziare una conversazione"
        for (width in listOf(280, 375, 650)) {
            val scene = ImageComposeScene(width = width, height = 500) {
                AppTheme {
                    Surface {
                        EligibilityTable(
                            options = listOf(LeadEligibilityOptionUi(PartTypeId("preview"), label, SexRule.entries.first(), true, true, true)),
                            onToggle = { _, _ -> }, puoAssistere = true, onPuoAssistereChange = {},
                            onSetAllEligibilityChange = {}, lastAssistantAssignmentDate = LocalDate.of(2026, 1, 1),
                            showHistoryColumns = true, today = LocalDate.of(2026, 10, 21),
                        )
                    }
                }
            }
            try {
                scene.render()
                val text = nodes(scene).first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == label } == true }
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(text.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
                save(scene, "eligibility-$width")
                val layout = layouts.single()
                assertFalse(layout.multiParagraph.didExceedMaxLines)
                assertTrue(layout.multiParagraph.height <= layout.size.height + 1)
                for (line in 0 until layout.lineCount) {
                    assertTrue(layout.getLineRight(line) <= layout.layoutInput.constraints.maxWidth)
                }
                assertTrue(text.boundsInRoot.right <= width)
                save(scene, "eligibility-$width")
            } finally { scene.close() }
        }
    }

    @Test
    fun `compact suggestions preserve two step rest confirmation`() {
        val person = Proclamatore(ProclamatoreId("preview"), "Mario Alessandro", "Rossi Bianchi", Sesso.M)
        var assignments = 0
        val scene = ImageComposeScene(width = 375, height = 760) {
            AppTheme {
                Surface {
                    org.example.project.ui.assignments.PersonPickerDialog(
                        partLabel = "Iniziare una conversazione", slotLabel = "Conduzione",
                        weekLabel = "19-25 ottobre 2026", isHistoricalEdit = false,
                        currentAssigneeName = null, searchTerm = "", strictCooldown = false,
                        suggestions = listOf(org.example.project.feature.assignments.domain.SuggestedProclamatore(
                            person, 2, 2, 2, inCooldown = true, cooldownRemainingWeeks = 3,
                        )),
                        isLoading = false, isAssigning = false,
                        onSearchChange = {}, onStrictCooldownChange = {},
                        onAssign = { assignments++ }, onDismiss = {},
                    )
                }
            }
        }
        try {
            scene.render()
            val name = nodes(scene).first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == person.fullName } == true }
            assertTrue(name.boundsInRoot.width > 150)
            click(scene, "Assegna")
            assertEquals(0, assignments)
            scene.render()
            click(scene, "Conferma")
            assertEquals(1, assignments)
            save(scene, "assignment-375")
        } finally { scene.close() }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
    }

    private fun click(scene: ImageComposeScene, label: String) {
        var node: SemanticsNode? = nodes(scene).first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == label } == true }
        while (node != null) {
            val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            if (action != null) {
                assertTrue(action())
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                scene.render()
                return
            }
            node = node.parent
        }
        error("Missing action for $label")
    }

    private fun save(scene: ImageComposeScene, name: String) {
        val file = File("build/responsive-preview/$name.png")
        file.parentFile.mkdirs()
        val frame = System.nanoTime()
        scene.render(frame)
        file.writeBytes(scene.render(frame + 1_000_000_000).encodeToData()!!.bytes)
    }
}

package org.example.project.ui.workspace

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.nio.file.Files
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.example.project.feature.output.application.AssignmentTicketImage
import org.example.project.feature.output.application.AssignmentTicketLine
import org.example.project.feature.output.application.PartAssignmentWarning
import org.example.project.feature.output.domain.SlipDeliveryInfo
import org.example.project.feature.output.domain.SlipDeliveryStatus.INVIATO
import org.example.project.feature.output.infrastructure.DesktopFileOpener
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.ui.theme.AppTheme

@OptIn(ExperimentalComposeUiApi::class)
class ProgramWorkspaceComponentsTest {
    private val currentWeek = LocalDate.of(2026, 11, 23)

    @Test
    fun `ticket folder action remains visible in a narrow window`() {
        val scene = ImageComposeScene(width = 327, height = 180) {
            AppTheme {
                AssignmentTicketsFolderAction(Path.of("biglietti"))
            }
        }
        try {
            scene.render()
            val button = textNodes(scene, "Apri cartella biglietti").single()
            val buttonAction = generateSequence(button) { it.parent }
                .firstNotNullOfOrNull { it.config.getOrNull(SemanticsActions.OnClick) }
            assertNotNull(buttonAction)
            assertTrue(button.boundsInRoot.width > 0 && button.boundsInRoot.right <= 327)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `opening a ticket folder rejects a file path`() {
        val ticket = Files.createTempFile("biglietto", ".png")
        try {
            assertFailsWith<NotDirectoryException> {
                DesktopFileOpener().openDirectory(ticket)
            }
        } finally {
            Files.deleteIfExists(ticket)
        }
    }

    @Test
    fun `warnings remain visible when no ticket can be generated`() {
        val warning = PartAssignmentWarning(currentWeek, currentWeek.plusDays(6), "Primo contatto", 0, 2)
        val scene = ImageComposeScene(width = 375, height = 400) {
            AppTheme(darkTheme = true) {
                AssignmentTicketsBody(
                    tickets = emptyList(),
                    partWarnings = listOf(warning),
                    historicalAssignmentTickets = emptyList(),
                    historicalAssignmentPartWarnings = emptyList(),
                    deliveryStatus = emptyMap(),
                    isLoading = false,
                    errorMessage = null,
                    markingTicketKey = null,
                    cancellingTicketKey = null,
                    onPreviewRequest = { error("No ticket available") },
                    onMarkAsDelivered = { error("No ticket available") },
                    onCancelDelivery = { error("No ticket available") },
                )
            }
        }
        try {
            scene.render()
            assertEquals(1, textNodes(scene, "Non inviabili").size)
            assertEquals(1, textNodes(scene, "Parte vuota").size)
            assertEquals(1, textNodes(scene, warning.partLabel).size)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `past week archive is collapsed by default and tickets can only be viewed`() {
        val archived = ticket("Mario Rossi", 3, currentWeek.minusWeeks(1))
        var preview: AssignmentTicketImage? = null
        val scene = ImageComposeScene(width = 375, height = 400) {
            AppTheme {
                AssignmentTicketsBody(
                    tickets = emptyList(),
                    partWarnings = emptyList(),
                    historicalAssignmentTickets = listOf(archived),
                    historicalAssignmentPartWarnings = emptyList(),
                    deliveryStatus = mapOf(archived.key() to SlipDeliveryInfo(INVIATO, null, null)),
                    isLoading = false,
                    errorMessage = null,
                    markingTicketKey = null,
                    cancellingTicketKey = null,
                    onPreviewRequest = { preview = it },
                    onMarkAsDelivered = { error("Archive is read-only") },
                    onCancelDelivery = { error("Archive is read-only") },
                )
            }
        }
        try {
            scene.render()
            assertTrue(textNodes(scene, archived.fullName).isEmpty())
            click(scene, "Settimane passate")
            assertEquals(1, textNodes(scene, archived.fullName).size)
            assertEquals(1, textNodes(scene, "Inviato").size)
            assertTrue(textNodes(scene, "Segna come inviato").isEmpty())
            assertTrue(textNodes(scene, "Annulla invio").isEmpty())
            click(scene, "Visualizza")
            assertEquals(archived, preview)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `only the selected card reports delivery progress`() {
        val first = ticket("Mario Rossi", 3)
        val second = ticket("Anna Verdi", 4)
        for (cancelling in listOf(false, true)) {
            val idleLabel = if (cancelling) "Annulla invio" else "Segna come inviato"
            val busyLabel = if (cancelling) "Annullamento…" else "Registrazione…"
            val scene = ImageComposeScene(width = 720, height = 450) {
                AppTheme {
                    AssignmentTicketsBody(
                        tickets = listOf(first, second),
                        partWarnings = emptyList(),
                        historicalAssignmentTickets = emptyList(),
                        historicalAssignmentPartWarnings = emptyList(),
                        deliveryStatus = if (cancelling) {
                            listOf(first, second).associate { it.key() to SlipDeliveryInfo(INVIATO, null, null) }
                        } else emptyMap(),
                        isLoading = false,
                        errorMessage = null,
                        markingTicketKey = if (cancelling) null else first.key(),
                        cancellingTicketKey = if (cancelling) first.key() else null,
                        onPreviewRequest = {},
                        onMarkAsDelivered = {},
                        onCancelDelivery = {},
                    )
                }
            }
            try {
                scene.render()
                val busy = textNodes(scene, busyLabel).single().boundsInRoot
                val idle = textNodes(scene, idleLabel).single().boundsInRoot
                assertTrue(busy.right <= idle.left)
            } finally {
                scene.close()
            }
        }
    }

    private fun ticket(name: String, partNumber: Int, weekStart: LocalDate = currentWeek) = AssignmentTicketImage(
        fullName = name,
        assistantName = null,
        weekStart = weekStart,
        weekEnd = weekStart.plusDays(6),
        imagePath = Path.of("$name.png"),
        assignments = listOf(AssignmentTicketLine("Lettura", null, partNumber)),
        weeklyPartId = WeeklyPartId("part-$partNumber"),
        weekPlanId = WeekPlanId("week-$weekStart"),
    )

    private fun AssignmentTicketImage.key() = weeklyPartId to weekPlanId

    private fun textNodes(scene: ImageComposeScene, label: String): List<SemanticsNode> {
        fun nodes(node: SemanticsNode): Sequence<SemanticsNode> =
            sequenceOf(node) + node.children.asSequence().flatMap(::nodes)
        return scene.semanticsOwners.asSequence()
            .flatMap { nodes(it.rootSemanticsNode) }
            .filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }
            .toList()
    }

    private fun click(scene: ImageComposeScene, label: String) {
        val action = generateSequence(textNodes(scene, label).single()) { it.parent }
            .firstNotNullOf { it.config.getOrNull(SemanticsActions.OnClick)?.action }
        assertTrue(action())
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}

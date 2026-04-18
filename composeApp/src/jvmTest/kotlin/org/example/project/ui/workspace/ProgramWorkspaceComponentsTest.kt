package org.example.project.ui.workspace

import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgramWorkspaceComponentsTest {

    @Test
    fun `buildAssignmentTicketTransferable returns null when ticket file is missing`() {
        val missingPath = Files.createTempDirectory("assignment-ticket-dnd-missing")
            .resolve("missing-ticket.png")

        val transferable = buildAssignmentTicketTransferable(missingPath)

        assertNull(transferable)
    }

    @Test
    fun `buildAssignmentTicketTransferable exposes file list and text payload for existing file`() {
        val tempDir = Files.createTempDirectory("assignment-ticket-dnd")
        val pngPath = tempDir.resolve("ticket.png")
        Files.writeString(pngPath, "png")

        val transferable = buildAssignmentTicketTransferable(pngPath)

        assertNotNull(transferable)
        assertEquals(listOf(pngPath.toFile().absoluteFile), transferable.getTransferData(DataFlavor.javaFileListFlavor))
        assertEquals(pngPath.toFile().absolutePath, transferable.getTransferData(DataFlavor.stringFlavor))
    }
}

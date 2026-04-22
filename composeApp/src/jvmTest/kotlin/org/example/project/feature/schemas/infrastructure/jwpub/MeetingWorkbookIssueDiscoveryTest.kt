package org.example.project.feature.schemas.infrastructure.jwpub

import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingWorkbookIssueDiscoveryTest {

    @Test
    fun `january start returns six issues for the full year`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 1)
        assertEquals(
            listOf("202601", "202603", "202605", "202607", "202609", "202611"),
            result,
        )
    }

    @Test
    fun `april start skips the january-february issue`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 4)
        assertEquals(
            listOf("202603", "202605", "202607", "202609", "202611"),
            result,
        )
    }

    @Test
    fun `december start returns only the last issue`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 12)
        assertEquals(listOf("202611"), result)
    }

    @Test
    fun `mid-bimester month maps to the containing issue`() {
        val result = MeetingWorkbookIssueDiscovery.candidatesForYear(2026, startingFromMonth = 2)
        assertEquals(
            listOf("202601", "202603", "202605", "202607", "202609", "202611"),
            result,
        )
    }
}

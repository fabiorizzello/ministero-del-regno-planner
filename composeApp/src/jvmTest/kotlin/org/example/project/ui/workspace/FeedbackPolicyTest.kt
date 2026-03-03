package org.example.project.ui.workspace

import org.example.project.ui.components.FeedbackBannerKind
import org.example.project.ui.components.errorNotice
import org.example.project.ui.components.shouldShowSuccessNotice
import org.example.project.ui.components.successNoticeIfNeeded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedbackPolicyTest {

    @Test
    fun `success notice is suppressed when immediate feedback already exists and no additional info`() {
        val shouldShow = shouldShowSuccessNotice(
            hasImmediateVisualFeedback = true,
            hasAdditionalInfo = false,
        )

        assertEquals(false, shouldShow)
        assertNull(
            successNoticeIfNeeded(
                details = "Dettaglio",
                hasImmediateVisualFeedback = true,
                hasAdditionalInfo = false,
            ),
        )
    }

    @Test
    fun `success notice is shown when there is no immediate feedback`() {
        val shouldShow = shouldShowSuccessNotice(
            hasImmediateVisualFeedback = false,
            hasAdditionalInfo = false,
        )

        assertEquals(true, shouldShow)
        assertNotNull(
            successNoticeIfNeeded(
                details = "Dettaglio",
                hasImmediateVisualFeedback = false,
                hasAdditionalInfo = false,
            ),
        )
    }

    @Test
    fun `success notice is shown when additional info is relevant`() {
        val shouldShow = shouldShowSuccessNotice(
            hasImmediateVisualFeedback = true,
            hasAdditionalInfo = true,
        )

        assertEquals(true, shouldShow)
        assertNotNull(
            successNoticeIfNeeded(
                details = "Dettaglio",
                hasImmediateVisualFeedback = true,
                hasAdditionalInfo = true,
            ),
        )
    }

    @Test
    fun `error notice remains explicit`() {
        val notice = errorNotice("Errore test")

        assertEquals(FeedbackBannerKind.ERROR, notice.kind)
        assertNotNull(notice.details)
    }
}

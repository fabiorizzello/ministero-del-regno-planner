package org.example.project.feature.output.application

import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StampaProgrammaUseCaseTest {

    @Test
    fun `active week status is mapped to user friendly label`() {
        assertEquals("Attiva", weekPlanStatusLabel(WeekPlanStatus.ACTIVE))
    }

    @Test
    fun `skipped week status is mapped to user friendly label`() {
        assertEquals("Saltata", weekPlanStatusLabel(WeekPlanStatus.SKIPPED))
    }
}

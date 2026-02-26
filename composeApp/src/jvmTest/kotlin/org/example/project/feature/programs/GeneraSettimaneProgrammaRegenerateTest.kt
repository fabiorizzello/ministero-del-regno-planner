package org.example.project.feature.programs

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.example.project.feature.programs.application.GeneraSettimaneProgrammaUseCase
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanStatus
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeneraSettimaneProgrammaRegenerateTest {

    @Test
    fun `regenerate clears old weeks and updates template timestamp once`() = runBlocking {
        val program = fixtureProgramMonth(YearMonth.of(2026, 2), id = "program-regen")
        val oldWeeks = listOf(
            WeekPlan(
                id = WeekPlanId("old-1"),
                weekStartDate = LocalDate.of(2026, 2, 2),
                parts = emptyList(),
                programId = program.id.value,
                status = WeekPlanStatus.ACTIVE,
            ),
        )
        val weekStore = InMemoryWeekPlanStoreGeneration(
            initialWeeksByProgram = mapOf(program.id.value to oldWeeks),
        )
        val programStore = InMemoryProgramStoreGeneration(program)
        val templatePart = partType("tpl")
        val schemaStore = InMemorySchemaTemplateStore(
            mapOf(
                LocalDate.of(2026, 2, 2) to StoredSchemaWeekTemplate(LocalDate.of(2026, 2, 2), listOf(templatePart.id)),
                LocalDate.of(2026, 2, 9) to StoredSchemaWeekTemplate(LocalDate.of(2026, 2, 9), listOf(templatePart.id)),
                LocalDate.of(2026, 2, 16) to StoredSchemaWeekTemplate(LocalDate.of(2026, 2, 16), listOf(templatePart.id)),
                LocalDate.of(2026, 2, 23) to StoredSchemaWeekTemplate(LocalDate.of(2026, 2, 23), listOf(templatePart.id)),
            ),
        )
        val useCase = GeneraSettimaneProgrammaUseCase(
            programStore = programStore,
            weekPlanStore = weekStore,
            schemaTemplateStore = schemaStore,
            partTypeStore = InMemoryPartTypeStore(partTypes = listOf(templatePart), fixedPart = null),
            transactionRunner = ImmediateTransactionRunner(),
        )

        val result = useCase(program.id.value)

        assertIs<Either.Right<Unit>>(result)
        assertEquals(listOf(program.id.value), weekStore.deletedPrograms)
        assertEquals(4, weekStore.createdWeeks.size)
        assertTrue(weekStore.createdWeeks.none { it.id.value == "old-1" })
        assertEquals(1, programStore.templateAppliedUpdates.size)
    }
}

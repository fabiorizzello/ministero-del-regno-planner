package org.example.project.ui.workspace

import arrow.core.Either
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.example.project.core.PassthroughTransactionRunner
import org.example.project.core.domain.DomainError
import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.programs.application.AggiornaProgrammaDaSchemiUseCase
import org.example.project.feature.programs.application.CaricaProgrammiAttiviUseCase
import org.example.project.feature.programs.application.ProgramCreationContext
import org.example.project.feature.programs.application.ProgramSelectionSnapshot
import org.example.project.feature.programs.application.ProgramStore
import org.example.project.feature.programs.application.SchemaRefreshMode
import org.example.project.feature.programs.application.SchemaRefreshReport
import org.example.project.feature.programs.domain.ProgramMonth
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.schemas.application.AggiornaSchemiResult
import org.example.project.feature.schemas.application.AggiornaSchemiUseCase
import org.example.project.feature.schemas.application.RemoteSchemaCatalog
import org.example.project.feature.schemas.application.SchemaCatalogRemoteSource
import org.example.project.feature.schemas.application.SchemaTemplateStore
import org.example.project.feature.schemas.application.SchemaUpdateAnomaly
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyDraft
import org.example.project.feature.schemas.application.SchemaUpdateAnomalyStore
import org.example.project.feature.schemas.application.SkippedPart
import org.example.project.feature.schemas.application.StoredSchemaWeekTemplate
import org.example.project.feature.weeklyparts.application.PartTypeStore
import org.example.project.feature.weeklyparts.application.PartTypeRevisionRow
import org.example.project.feature.weeklyparts.application.PartTypeWithStatus
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanAggregate
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs
import java.time.LocalDateTime
import org.example.project.feature.programs.application.WeekRefreshDetail
import org.example.project.feature.programs.application.SchemaRefreshPreview
import org.example.project.feature.programs.application.hasEffectiveChanges

class SchemaManagementViewModelTest {

    // ---- Existing pure-function tests preserved ----

    private val importTime = LocalDateTime.of(2026, 2, 15, 10, 0)

    private fun programMonth(
        createdAt: LocalDateTime,
        templateAppliedAt: LocalDateTime? = null,
    ) = ProgramMonth(
        id = ProgramMonthId("test-id"),
        year = 2026,
        month = 3,
        startDate = LocalDate.of(2026, 3, 2),
        endDate = LocalDate.of(2026, 3, 29),
        templateAppliedAt = templateAppliedAt,
        createdAt = createdAt,
    )

    @Test
    fun `new program created after import does not need refresh`() {
        val program = programMonth(createdAt = importTime.plusHours(1))
        assertFalse(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `old program created before import needs refresh`() {
        val program = programMonth(createdAt = importTime.minusDays(1))
        assertTrue(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `template applied after import does not need refresh`() {
        val program = programMonth(
            createdAt = importTime.minusDays(5),
            templateAppliedAt = importTime.plusHours(2),
        )
        assertFalse(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `template applied before import needs refresh`() {
        val program = programMonth(
            createdAt = importTime.minusDays(5),
            templateAppliedAt = importTime.minusHours(1),
        )
        assertTrue(isSchemaRefreshNeeded(importTime, program))
    }

    @Test
    fun `no future program returns false`() {
        assertFalse(isSchemaRefreshNeeded(importTime, null))
    }

    @Test
    fun `no lastSchemaImport returns false`() {
        val program = programMonth(createdAt = importTime.minusDays(1))
        assertFalse(isSchemaRefreshNeeded(null, program))
    }

    @Test
    fun `schema refresh reference date includes current week`() {
        assertEquals(
            LocalDate.of(2026, 3, 2),
            schemaRefreshReferenceDate(LocalDate.of(2026, 3, 4)),
        )
    }

    @Test
    fun `report with no part delta and no assignment removals has no effective changes`() {
        val report = SchemaRefreshReport(
            weeksUpdated = 1,
            assignmentsPreserved = 2,
            assignmentsRemoved = 0,
            weekDetails = listOf(
                WeekRefreshDetail(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partsAdded = 0,
                    partsRemoved = 0,
                    partsKept = 2,
                    assignmentsPreserved = 2,
                    assignmentsRemoved = 0,
                ),
            ),
        )

        assertFalse(report.hasEffectiveChanges())
    }

    @Test
    fun `preview with effective change on any mode requires confirmation`() {
        val unchanged = SchemaRefreshReport(
            weeksUpdated = 1,
            assignmentsPreserved = 2,
            assignmentsRemoved = 0,
            weekDetails = listOf(
                WeekRefreshDetail(
                    weekStartDate = LocalDate.of(2026, 3, 2),
                    partsAdded = 0,
                    partsRemoved = 0,
                    partsKept = 2,
                    assignmentsPreserved = 2,
                    assignmentsRemoved = 0,
                ),
            ),
        )
        val changed = SchemaRefreshReport(
            weeksUpdated = 1,
            assignmentsPreserved = 1,
            assignmentsRemoved = 1,
            weekDetails = listOf(
                WeekRefreshDetail(
                    weekStartDate = LocalDate.of(2026, 3, 9),
                    partsAdded = 1,
                    partsRemoved = 1,
                    partsKept = 1,
                    assignmentsPreserved = 1,
                    assignmentsRemoved = 1,
                ),
            ),
        )

        val preview = SchemaRefreshPreview(
            allChanges = unchanged,
            onlyUnassignedChanges = changed,
        )

        assertTrue(preview.hasEffectiveChanges())
    }

    // ---- New Task 19 ViewModel tests ----

    private fun fakeAggiornaSchemi(result: AggiornaSchemiResult): AggiornaSchemiUseCase =
        object : AggiornaSchemiUseCase(
            remoteSource = StubRemoteSource,
            partTypeStore = StubPartTypeStore,
            eligibilityStore = StubEligibilityStore,
            schemaTemplateStore = StubSchemaTemplateStore,
            schemaUpdateAnomalyStore = StubSchemaUpdateAnomalyStore,
            transactionRunner = PassthroughTransactionRunner,
            settings = PreferencesSettings(
                Preferences.userRoot().node("schema-mgmt-vm-test-${UUID.randomUUID()}"),
            ),
        ) {
            override suspend fun invoke(): Either<DomainError, AggiornaSchemiResult> =
                Either.Right(result)
        }

    private fun fakeAggiornaProgramma(): AggiornaProgrammaDaSchemiUseCase =
        object : AggiornaProgrammaDaSchemiUseCase(
            programStore = StubProgramStore,
            weekPlanStore = StubWeekPlanStore,
            schemaTemplateStore = StubSchemaTemplateStore,
            partTypeStore = StubPartTypeStore,
            transactionRunner = PassthroughTransactionRunner,
        ) {
            override suspend fun invoke(
                programId: ProgramMonthId,
                referenceDate: LocalDate,
                dryRun: Boolean,
                mode: SchemaRefreshMode,
            ): Either<DomainError, SchemaRefreshReport> =
                Either.Right(SchemaRefreshReport(0, 0, 0, emptyList()))
        }

    private fun fakeCaricaProgrammiAttivi(): CaricaProgrammiAttiviUseCase =
        object : CaricaProgrammiAttiviUseCase(programStore = StubProgramStore) {
            override suspend fun invoke(referenceDate: LocalDate): Either<DomainError, ProgramSelectionSnapshot> =
                Either.Right(ProgramSelectionSnapshot(previous = null, current = null, futures = emptyList()))
        }

    private val sampleSkipped = SkippedPart(
        weekStartDate = "2026-03-02",
        mepsDocumentId = 123L,
        label = "Parte sconosciuta",
        detailLine = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `result with unknown parts and no program selected opens result dialog`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = SchemaManagementViewModel(
            scope = scope,
            aggiornaSchemi = fakeAggiornaSchemi(
                AggiornaSchemiResult(
                    version = "v1",
                    partTypesImported = 0,
                    weekTemplatesImported = 0,
                    eligibilityAnomalies = 0,
                    skippedUnknownParts = listOf(sampleSkipped),
                    downloadedIssues = emptyList(),
                ),
            ),
            aggiornaProgrammaDaSchemi = fakeAggiornaProgramma(),
            caricaProgrammiAttivi = fakeCaricaProgrammiAttivi(),
        )

        vm.refreshSchemasAndProgram(selectedProgramId = null)
        scope.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.showRefreshResultDialog)
        assertEquals(1, state.pendingUnknownParts.size)
        assertTrue(state.pendingDownloadedIssues.isEmpty())
        assertNull(state.pendingRefreshPreview)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `nothing to report does not open dialog`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = SchemaManagementViewModel(
            scope = scope,
            aggiornaSchemi = fakeAggiornaSchemi(
                AggiornaSchemiResult(
                    version = "v1",
                    partTypesImported = 0,
                    weekTemplatesImported = 0,
                    eligibilityAnomalies = 0,
                    skippedUnknownParts = emptyList(),
                    downloadedIssues = emptyList(),
                ),
            ),
            aggiornaProgrammaDaSchemi = fakeAggiornaProgramma(),
            caricaProgrammiAttivi = fakeCaricaProgrammiAttivi(),
        )

        vm.refreshSchemasAndProgram(selectedProgramId = null)
        scope.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.showRefreshResultDialog)
        assertTrue(state.pendingUnknownParts.isEmpty())
        assertTrue(state.pendingDownloadedIssues.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `dismiss refresh result dialog clears state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val vm = SchemaManagementViewModel(
            scope = scope,
            aggiornaSchemi = fakeAggiornaSchemi(
                AggiornaSchemiResult(
                    version = "v1",
                    partTypesImported = 0,
                    weekTemplatesImported = 0,
                    eligibilityAnomalies = 0,
                    skippedUnknownParts = listOf(sampleSkipped),
                    downloadedIssues = listOf("issue-a"),
                ),
            ),
            aggiornaProgrammaDaSchemi = fakeAggiornaProgramma(),
            caricaProgrammiAttivi = fakeCaricaProgrammiAttivi(),
        )

        vm.refreshSchemasAndProgram(selectedProgramId = null)
        scope.advanceUntilIdle()
        assertTrue(vm.state.value.showRefreshResultDialog)

        vm.dismissRefreshResultDialog()

        val state = vm.state.value
        assertFalse(state.showRefreshResultDialog)
        assertTrue(state.pendingUnknownParts.isEmpty())
        assertTrue(state.pendingDownloadedIssues.isEmpty())
        assertNull(state.pendingRefreshPreview)
        assertNull(state.pendingRefreshProgramId)
    }
}

// ---- Throw-on-call stub stores (only constructor wiring; methods must never be called) ----

private object StubRemoteSource : SchemaCatalogRemoteSource {
    override suspend fun fetchCatalog(): Either<DomainError, RemoteSchemaCatalog> =
        error("StubRemoteSource.fetchCatalog should not be called")
}

private object StubPartTypeStore : PartTypeStore {
    override suspend fun all(): List<PartType> = error("not used")
    override suspend fun allWithStatus(): List<PartTypeWithStatus> = error("not used")
    override suspend fun findById(id: PartTypeId): PartType? = error("not used")
    override suspend fun findByCode(code: String): PartType? = error("not used")
    override suspend fun findFixed(): PartType? = error("not used")
    context(tx: TransactionScope)
    override suspend fun upsertAll(partTypes: List<PartType>) = error("not used")
    override suspend fun getLatestRevisionId(partTypeId: PartTypeId): String? = error("not used")
    override suspend fun allRevisionsForPartType(partTypeId: PartTypeId): List<PartTypeRevisionRow> = error("not used")
    context(tx: TransactionScope)
    override suspend fun deactivateMissingCodes(codes: Set<String>) = error("not used")
}

private object StubEligibilityStore : EligibilityStore {
    context(tx: TransactionScope)
    override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) = error("not used")
    context(tx: TransactionScope)
    override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) = error("not used")
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = error("not used")
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> = error("not used")
    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> = error("not used")
    context(tx: TransactionScope)
    override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) = error("not used")
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = error("not used")
}

private object StubSchemaTemplateStore : SchemaTemplateStore {
    context(tx: TransactionScope)
    override suspend fun replaceAll(templates: List<StoredSchemaWeekTemplate>) = error("not used")
    override suspend fun listAll(): List<StoredSchemaWeekTemplate> = error("not used")
    override suspend fun findByWeekStartDate(weekStartDate: LocalDate): StoredSchemaWeekTemplate? = error("not used")
    override suspend fun isEmpty(): Boolean = error("not used")
}

private object StubSchemaUpdateAnomalyStore : SchemaUpdateAnomalyStore {
    context(tx: TransactionScope)
    override suspend fun append(items: List<SchemaUpdateAnomalyDraft>) = error("not used")
    override suspend fun listOpen(): List<SchemaUpdateAnomaly> = error("not used")
    context(tx: TransactionScope)
    override suspend fun dismissAllOpen() = error("not used")
}

private object StubProgramStore : ProgramStore {
    override suspend fun listCurrentAndFuture(referenceDate: LocalDate): List<ProgramMonth> = error("not used")
    override suspend fun findMostRecentPast(referenceDate: LocalDate): ProgramMonth? = error("not used")
    override suspend fun findById(id: ProgramMonthId): ProgramMonth? = error("not used")
    context(tx: TransactionScope)
    override suspend fun save(program: ProgramMonth) = error("not used")
    context(tx: TransactionScope)
    override suspend fun delete(id: ProgramMonthId) = error("not used")
    context(tx: TransactionScope)
    override suspend fun updateTemplateAppliedAt(id: ProgramMonthId, templateAppliedAt: LocalDateTime) = error("not used")
    override suspend fun loadCreationContext(referenceDate: LocalDate): ProgramCreationContext = error("not used")
}

private object StubWeekPlanStore : WeekPlanStore {
    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? = error("not used")
    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = error("not used")
    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = error("not used")
    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlan? = error("not used")
    override suspend fun listByProgram(programId: ProgramMonthId): List<WeekPlan> = error("not used")
    override suspend fun loadAggregateByDate(weekStartDate: LocalDate): WeekPlanAggregate? = error("not used")
    override suspend fun loadAggregateById(weekPlanId: WeekPlanId): WeekPlanAggregate? = error("not used")
    override suspend fun loadAggregateByDateAndProgram(weekStartDate: LocalDate, programId: ProgramMonthId): WeekPlanAggregate? = error("not used")
    override suspend fun listAggregatesByProgram(programId: ProgramMonthId): List<WeekPlanAggregate> = error("not used")
    context(tx: TransactionScope)
    override suspend fun saveAggregate(aggregate: WeekPlanAggregate) = error("not used")
    context(tx: TransactionScope)
    override suspend fun replaceProgramAggregates(programId: ProgramMonthId, aggregates: List<WeekPlanAggregate>) = error("not used")
    context(tx: TransactionScope)
    override suspend fun deleteByProgram(programId: ProgramMonthId) = error("not used")
}

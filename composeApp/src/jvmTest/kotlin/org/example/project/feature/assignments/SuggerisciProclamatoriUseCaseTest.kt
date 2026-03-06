package org.example.project.feature.assignments

import kotlinx.coroutines.runBlocking
import org.example.project.feature.assignments.application.AssignmentSettings
import org.example.project.feature.assignments.application.SuggerisciProclamatoriUseCase
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.PartType
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.SexRule
import org.example.project.feature.weeklyparts.domain.WeekPlan
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import org.example.project.feature.weeklyparts.domain.WeekPlanSummary
import org.example.project.feature.weeklyparts.domain.WeeklyPart
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.programs.domain.ProgramMonthId as PMId
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test per SuggerisciProclamatoriUseCase.
 *
 * Nota architetturale: il filtro `sospeso=true` avviene a livello SQL
 * (query `allAssignableProclaimers WHERE suspended=0`), non nell'use case.
 * Di conseguenza, i test sul sospeso verificano che una persona sospesa
 * non compaia nei suggerimenti quando non è inclusa nell'AssignmentRanking.
 */
class SuggerisciProclamatoriUseCaseTest {

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------

    private val partTypeId = PartTypeId("pt-standard")
    private val weeklyPartId = WeeklyPartId("wp-1")
    private val weekStart = LocalDate.of(2026, 3, 9)
    private val programId = ProgramMonthId("program-1")

    private val partType = PartType(
        id = partTypeId,
        code = "STD",
        label = "Discorso",
        peopleCount = 2,
        sexRule = SexRule.STESSO_SESSO,
        fixed = false,
        sortOrder = 0,
    )

    private val weeklyPart = WeeklyPart(
        id = weeklyPartId,
        partType = partType,
        sortOrder = 0,
    )

    private val week = WeekPlan(
        id = WeekPlanId("week-1"),
        weekStartDate = weekStart,
        parts = listOf(weeklyPart),
        programId = programId,
    )

    private val weekPlanQueries = StaticWeekPlanQueriesForSuggest(week)

    // -----------------------------------------------------------------------
    // Test 1 — candidato con cooldown attivo appare in fondo (strictCooldown=false)
    // -----------------------------------------------------------------------

    @Test
    fun `candidato in cooldown con strictCooldown=false appare ma con score peggiore`() = runBlocking {
        // cooldown attivo: lastGlobalWeeks=1 < assistCooldownWeeks=2
        val personInCooldown = person(id = "p-cooldown", nome = "Carlo", cognome = "Verdi", sesso = Sesso.M)
        // nessun cooldown: lastGlobalWeeks=10
        val personNoCooldown = person(id = "p-ok", nome = "Mario", cognome = "Rossi", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personInCooldown,
                lastGlobalWeeks = 1,
                lastForPartTypeWeeks = 1,
                lastConductorWeeks = null,
            ),
            SuggestedProclamatore(
                proclamatore = personNoCooldown,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 8,
                lastConductorWeeks = null,
            ),
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(eligible = emptySet()), // slot=2, idoneità non rilevante
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 2, // assistente: puoAssistere basta
        )

        // Entrambi devono apparire
        assertEquals(2, result.size)
        // personNoCooldown deve essere primo (score più alto)
        assertEquals(personNoCooldown.id, result.first().proclamatore.id)
        // personInCooldown deve essere secondo (score penalizzato)
        assertEquals(personInCooldown.id, result.last().proclamatore.id)
        // il candidato in cooldown ha inCooldown=true annotato
        assertTrue(result.last().inCooldown)
    }

    // -----------------------------------------------------------------------
    // Test 2 — strictCooldown=true: candidato in cooldown viene escluso
    // -----------------------------------------------------------------------

    @Test
    fun `candidato in cooldown con strictCooldown=true viene escluso dalla lista`() = runBlocking {
        val personInCooldown = person(id = "p-cooldown", nome = "Carlo", cognome = "Verdi", sesso = Sesso.M)
        val personNoCooldown = person(id = "p-ok", nome = "Mario", cognome = "Rossi", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personInCooldown,
                lastGlobalWeeks = 1,
                lastForPartTypeWeeks = 1,
                lastConductorWeeks = null,
            ),
            SuggestedProclamatore(
                proclamatore = personNoCooldown,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 8,
                lastConductorWeeks = null,
            ),
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(eligible = emptySet()),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = true)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 2,
        )

        // Solo personNoCooldown deve apparire
        assertEquals(1, result.size)
        assertEquals(personNoCooldown.id, result.first().proclamatore.id)
        assertFalse(result.first().inCooldown)
    }

    // -----------------------------------------------------------------------
    // Test 3 — candidato in cooldown con strictCooldown=false ha score peggiore
    //           anche rispetto a candidato con meno settimane ma fuori cooldown
    // -----------------------------------------------------------------------

    @Test
    fun `score del candidato in cooldown e' peggiore di quello senza cooldown anche con meno settimane`() = runBlocking {
        // personInCooldown: lastGlobalWeeks=1 (in cooldown), lastForPartTypeWeeks=1
        // personNoCooldown: lastGlobalWeeks=3 (fuori cooldown, assistCooldownWeeks=2)
        // Score slot=2 (assistWeight=1):
        //   personInCooldown: 1*1 + 1 - COOLDOWN_PENALTY = 2 - 10000 = -9998
        //   personNoCooldown: 3*1 + 2 = 5
        val personInCooldown = person(id = "p-cool", nome = "Luca", cognome = "Bianchi", sesso = Sesso.M)
        val personNoCooldown = person(id = "p-free", nome = "Anna", cognome = "Rossi", sesso = Sesso.F)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personInCooldown,
                lastGlobalWeeks = 1,
                lastForPartTypeWeeks = 1,
                lastConductorWeeks = null,
            ),
            SuggestedProclamatore(
                proclamatore = personNoCooldown,
                lastGlobalWeeks = 3,
                lastForPartTypeWeeks = 2,
                lastConductorWeeks = null,
            ),
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(eligible = emptySet()),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 2,
        )

        assertEquals(2, result.size)
        // personNoCooldown prima nonostante lastGlobalWeeks minore
        assertEquals(personNoCooldown.id, result.first().proclamatore.id)
        assertEquals(personInCooldown.id, result.last().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Test 4 — persona sospesa NON compare tra i suggeriti
    //
    // La sospensione è filtrata a livello SQL (allAssignableProclaimers WHERE suspended=0).
    // In questo test simuliamo il comportamento corretto: la persona sospesa
    // non è presente nel ranking restituito dall'AssignmentRanking.
    // -----------------------------------------------------------------------

    @Test
    fun `persona non inclusa nel ranking SQL non compare nei suggeriti`() = runBlocking {
        val personSospesa = person(id = "p-sospeso", nome = "Giulia", cognome = "Neri", sesso = Sesso.F, sospeso = true)
        val personAttiva = person(id = "p-attivo", nome = "Marco", cognome = "Blu", sesso = Sesso.M)

        // Il ranking SQL filtra sospesi: solo personAttiva arriva all'use case
        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personAttiva,
                lastGlobalWeeks = 5,
                lastForPartTypeWeeks = 3,
                lastConductorWeeks = null,
            ),
            // personSospesa non è mai nel ranking (filtrata da SQL)
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(eligible = emptySet()),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 2,
        )

        assertEquals(1, result.size)
        assertEquals(personAttiva.id, result.first().proclamatore.id)
        assertTrue(result.none { it.proclamatore.id == personSospesa.id })
    }

    // -----------------------------------------------------------------------
    // Test 5 — slot=1 (studente): solo chi ha idoneità conduzione appare
    // -----------------------------------------------------------------------

    @Test
    fun `slot 1 filtra i candidati senza idoneita' conduzione per il partType`() = runBlocking {
        val personEligible = person(id = "p-lead", nome = "Fabio", cognome = "Brun", sesso = Sesso.M)
        val personNotEligible = person(id = "p-noLead", nome = "Sara", cognome = "Galli", sesso = Sesso.F)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personEligible,
                lastGlobalWeeks = 6,
                lastForPartTypeWeeks = 4,
                lastConductorWeeks = null,
            ),
            SuggestedProclamatore(
                proclamatore = personNotEligible,
                lastGlobalWeeks = 8,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
            ),
        )

        // Solo personEligible è lead-eligible per questo partType
        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(
                eligible = setOf(EligibilityCleanupCandidate(personEligible.id, partTypeId)),
            ),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 1,
        )

        assertEquals(1, result.size)
        assertEquals(personEligible.id, result.first().proclamatore.id)
        assertTrue(result.none { it.proclamatore.id == personNotEligible.id })
    }
}

// ---------------------------------------------------------------------------
// Local fake: WeekPlanQueries con una settimana fissa
// ---------------------------------------------------------------------------

private class StaticWeekPlanQueriesForSuggest(
    private val week: WeekPlan,
) : WeekPlanQueries {
    override suspend fun findByDate(weekStartDate: LocalDate): WeekPlan? =
        if (week.weekStartDate == weekStartDate) week else null

    override suspend fun listInRange(startDate: LocalDate, endDate: LocalDate): List<WeekPlanSummary> = emptyList()

    override suspend fun totalSlotsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()

    override suspend fun findByDateAndProgram(weekStartDate: LocalDate, programId: PMId): WeekPlan? =
        if (week.weekStartDate == weekStartDate && week.programId == programId) week else null

    override suspend fun listByProgram(programId: PMId): List<WeekPlan> =
        if (week.programId == programId) listOf(week) else emptyList()
}

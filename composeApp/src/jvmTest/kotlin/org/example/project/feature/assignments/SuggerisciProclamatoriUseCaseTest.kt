package org.example.project.feature.assignments

import kotlinx.coroutines.test.runTest
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
    fun `candidato in cooldown con strictCooldown=false appare ma con score peggiore`() = runTest {
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
    fun `candidato in cooldown con strictCooldown=true viene escluso dalla lista`() = runTest {
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
    fun `score del candidato in cooldown e' peggiore di quello senza cooldown anche con meno settimane`() = runTest {
        // personInCooldown: lastGlobalWeeks=1 (in cooldown), lastForPartTypeWeeks=1
        // personNoCooldown: lastGlobalWeeks=3 (fuori cooldown, assistCooldownWeeks=2)
        // Score (slot=2, lastConductorWeeks=null → slotRepeatPenalty=4 per entrambi):
        //   personInCooldown: 1 - 4 - COOLDOWN_PENALTY = -10003
        //   personNoCooldown: 3 - 4 = -1
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
    fun `persona sospesa viene esclusa anche se presente nel ranking`() = runTest {
        val personSospesa = person(id = "p-sospeso", nome = "Giulia", cognome = "Neri", sesso = Sesso.F, sospeso = true)
        val personAttiva = person(id = "p-attivo", nome = "Marco", cognome = "Blu", sesso = Sesso.M)

        // Anche se il ranking includesse una persona sospesa, l'use case deve escluderla.
        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personSospesa,
                lastGlobalWeeks = 9,
                lastForPartTypeWeeks = 6,
                lastConductorWeeks = null,
            ),
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
    // Test 6 — candidato con più assegnazioni nella finestra ottiene score peggiore
    // -----------------------------------------------------------------------

    @Test
    fun `candidato con piu' assegnazioni nella finestra ottiene score peggiore`() = runTest {
        val personManyAssignments = person(id = "p-many", nome = "Mario", cognome = "Rossi", sesso = Sesso.M)
        val personFewAssignments = person(id = "p-few", nome = "Luigi", cognome = "Verdi", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personManyAssignments,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                totalAssignmentsInWindow = 8,
            ),
            SuggestedProclamatore(
                proclamatore = personFewAssignments,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                totalAssignmentsInWindow = 2,
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
        assertEquals(personFewAssignments.id, result.first().proclamatore.id)
        assertEquals(personManyAssignments.id, result.last().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Test 7 — candidato che ripete lo stesso slot ottiene penalità slot repeat
    // -----------------------------------------------------------------------

    @Test
    fun `candidato che ripete lo stesso slot ottiene penalita' slot repeat`() = runTest {
        val personRepeat = person(id = "p-repeat", nome = "Carlo", cognome = "Bianchi", sesso = Sesso.M)
        val personNoRepeat = person(id = "p-norepeat", nome = "Marco", cognome = "Verdi", sesso = Sesso.M)

        // personRepeat: lastConductorWeeks == lastGlobalWeeks → last was conductor, target is slot=1 → penalty
        // personNoRepeat: lastConductorWeeks = null → last was assistant → no penalty for slot=1
        // Score: personRepeat = 10 - 4 = 6, personNoRepeat = 10
        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personRepeat,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = 10,  // == lastGlobalWeeks → last was conductor
            ),
            SuggestedProclamatore(
                proclamatore = personNoRepeat,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,  // last was assistant (or never assigned as conductor)
            ),
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(
                eligible = setOf(
                    EligibilityCleanupCandidate(personRepeat.id, partTypeId),
                    EligibilityCleanupCandidate(personNoRepeat.id, partTypeId),
                ),
            ),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 1,  // target = conductor
        )

        assertEquals(2, result.size)
        assertEquals(personNoRepeat.id, result.first().proclamatore.id)
        assertEquals(personRepeat.id, result.last().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Test 8 — candidato mai assegnato (lastGlobalWeeks=null) ottiene score massimo
    // -----------------------------------------------------------------------

    @Test
    fun `candidato mai assegnato ottiene score massimo e appare primo`() = runTest {
        val personNeverAssigned = person(id = "p-never", nome = "Luca", cognome = "Alpi", sesso = Sesso.M)
        val personAssigned = person(id = "p-assigned", nome = "Marco", cognome = "Zeta", sesso = Sesso.M)

        // personNeverAssigned: lastGlobalWeeks=null → safeGlobalWeeks=Int.MAX_VALUE
        //   lastWasConductor=false, slot=2 → sameSlotType=false (lastGlobalWeeks is null) → slotRepeatPenalty=0
        //   score = Int.MAX_VALUE = 2147483647
        // personAssigned: lastGlobalWeeks=10
        //   lastWasConductor=false, slot=2 → sameSlotType=true → slotRepeatPenalty=4
        //   score = 10 - 4 = 6
        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personAssigned,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
            ),
            SuggestedProclamatore(
                proclamatore = personNeverAssigned,
                lastGlobalWeeks = null,
                lastForPartTypeWeeks = null,
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
        assertEquals(personNeverAssigned.id, result.first().proclamatore.id)
        assertEquals(personAssigned.id, result.last().proclamatore.id)
        assertFalse(result.first().inCooldown)
    }

    // -----------------------------------------------------------------------
    // Test 9 — count penalty e cooldown si sommano correttamente
    // -----------------------------------------------------------------------

    @Test
    fun `count penalty e cooldown si sommano nello score`() = runTest {
        // personBoth: in cooldown (lastGlobalWeeks=1 < assistCooldown=2) + 5 assegnazioni in finestra
        //   lastWasConductor=false, slot=2 → sameSlotType=true → slotRepeatPenalty=4
        //   score = 1 - 5 - 4 - 10000 = -10008
        // personClean: fuori cooldown (lastGlobalWeeks=5), 0 assegnazioni
        //   sameSlotType=true → slotRepeatPenalty=4
        //   score = 5 - 0 - 4 - 0 = 1
        val personBoth = person(id = "p-both", nome = "Carlo", cognome = "Bianchi", sesso = Sesso.M)
        val personClean = person(id = "p-clean", nome = "Mario", cognome = "Rossi", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personBoth,
                lastGlobalWeeks = 1,
                lastForPartTypeWeeks = 1,
                lastConductorWeeks = null,
                totalAssignmentsInWindow = 5,
            ),
            SuggestedProclamatore(
                proclamatore = personClean,
                lastGlobalWeeks = 5,
                lastForPartTypeWeeks = 3,
                lastConductorWeeks = null,
                totalAssignmentsInWindow = 0,
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
        assertEquals(personClean.id, result.first().proclamatore.id)
        assertEquals(personBoth.id, result.last().proclamatore.id)
        assertTrue(result.last().inCooldown)
    }

    // -----------------------------------------------------------------------
    // Test 10 — assistente che ripete assistente ottiene slot repeat penalty
    // -----------------------------------------------------------------------

    @Test
    fun `assistente che ripete assistente ottiene slot repeat penalty`() = runTest {
        // personRepeatAssist: lastConductorWeeks=null → lastWasConductor=false, target slot=2
        //   sameSlotType = (!false && !false && 10!=null) = true → slotRepeatPenalty=4
        //   score = 10 - 4 = 6
        // personSwitchRole: lastConductorWeeks=10 == lastGlobalWeeks → lastWasConductor=true, target slot=2
        //   sameSlotType = (true && false) = false → slotRepeatPenalty=0
        //   score = 10 - 0 = 10
        val personRepeatAssist = person(id = "p-repeat-a", nome = "Luigi", cognome = "Verdi", sesso = Sesso.M)
        val personSwitchRole = person(id = "p-switch", nome = "Anna", cognome = "Blu", sesso = Sesso.F)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personRepeatAssist,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
            ),
            SuggestedProclamatore(
                proclamatore = personSwitchRole,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = 10,
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
        // personSwitchRole primo: conduttore→assistente = nessuna penalita' repeat (score 10)
        assertEquals(personSwitchRole.id, result.first().proclamatore.id)
        // personRepeatAssist secondo: assistente→assistente = penalita' repeat (score 6)
        assertEquals(personRepeatAssist.id, result.last().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Fairness test F1 — equità per-parte: chi ha già condotto questa parte scende
    // -----------------------------------------------------------------------

    @Test
    fun `equita' per-parte penalizza chi ha gia' condotto lo stesso tipo di parte`() = runTest {
        // Entrambi identici su tutto tranne leadCountsByPartType per questo specifico partType.
        // personHeavy: ha già condotto questa parte 2 volte nella finestra (penalità 2*2=4 settimane)
        // personLight: mai condotta questa parte (penalità 0)
        // Score (slot=1, lastConductorWeeks=null → nessun slotRepeatPenalty):
        //   personHeavy: 10 - 0 - 0 - 4 - 0 = 6
        //   personLight: 10 - 0 - 0 - 0 - 0 = 10
        val personHeavy = person(id = "p-heavy", nome = "Carlo", cognome = "Bianchi", sesso = Sesso.M)
        val personLight = person(id = "p-light", nome = "Marco", cognome = "Verdi", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personHeavy,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                leadCountsByPartType = mapOf(partTypeId to 2),
            ),
            SuggestedProclamatore(
                proclamatore = personLight,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                leadCountsByPartType = emptyMap(),
            ),
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(
                eligible = setOf(
                    EligibilityCleanupCandidate(personHeavy.id, partTypeId),
                    EligibilityCleanupCandidate(personLight.id, partTypeId),
                ),
            ),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 1, // conduttore → partTypeLeadPenalty attivo
        )

        assertEquals(2, result.size)
        // personLight prima: nessuna storia su questa parte
        assertEquals(personLight.id, result.first().proclamatore.id)
        // personHeavy seconda: ha già condotto questa parte
        assertEquals(personHeavy.id, result.last().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Fairness test F2 — equità assistenza: chi ha assistito molto scende come assistente
    // -----------------------------------------------------------------------

    @Test
    fun `equita' assistenza penalizza chi ha molte assistenze quando target e' assistente`() = runTest {
        // Entrambi identici su tutto tranne assistCountInWindow.
        // personManyAssists: 5 assistenze → penalità 5*1 = 5
        // personFewAssists: 0 assistenze → penalità 0
        // Score (slot=2, lastConductorWeeks=null → slotRepeatPenalty=4 per entrambi):
        //   personManyAssists: 10 - 0 - 4 - 0 - 5 = 1
        //   personFewAssists:  10 - 0 - 4 - 0 - 0 = 6
        val personManyAssists = person(id = "p-many", nome = "Luigi", cognome = "Blu", sesso = Sesso.M)
        val personFewAssists = person(id = "p-few", nome = "Anna", cognome = "Rosa", sesso = Sesso.F)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personManyAssists,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                assistCountInWindow = 5,
            ),
            SuggestedProclamatore(
                proclamatore = personFewAssists,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                assistCountInWindow = 0,
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
            slot = 2, // assistente → assistRolePenalty attivo
        )

        assertEquals(2, result.size)
        assertEquals(personFewAssists.id, result.first().proclamatore.id)
        assertEquals(personManyAssists.id, result.last().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Fairness test F3 — le penalità di equità non bloccano mai il candidato
    // -----------------------------------------------------------------------

    @Test
    fun `fairness e' soft penalty - candidato penalizzato resta nei risultati`() = runTest {
        // Unico candidato per slot=1, con una storia pesante su questa parte.
        // Anche se la penalità lo sposta molto in basso, deve comunque apparire nei risultati.
        val personHeavy = person(id = "p-heavy", nome = "Carlo", cognome = "Bianchi", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personHeavy,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                leadCountsByPartType = mapOf(partTypeId to 10), // storia pesantissima
                assistCountInWindow = 20,                       // e anche molte assistenze
                totalAssignmentsInWindow = 30,                  // e conteggio globale altissimo
            ),
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(
                eligible = setOf(EligibilityCleanupCandidate(personHeavy.id, partTypeId)),
            ),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 1,
        )

        // Appare comunque: fairness e' soft penalty, non filtro
        assertEquals(1, result.size)
        assertEquals(personHeavy.id, result.first().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Fairness test F4 — la penalità per-parte e' specifica al tipo di parte
    // -----------------------------------------------------------------------

    @Test
    fun `penalita' per-parte e' limitata al tipo di parte specifico`() = runTest {
        // personHeavyOnOther: ha condotto molte volte un ALTRO tipo di parte, mai questo
        // personClean: nessuna storia
        // Quando target = partTypeId corrente, personHeavyOnOther NON deve essere penalizzato.
        // Score atteso identico → ordine alfabetico secondario (cognome asc).
        val otherPartTypeId = PartTypeId("pt-other")
        val personHeavyOnOther = person(id = "p-heavy-other", nome = "Carlo", cognome = "Alpi", sesso = Sesso.M)
        val personClean = person(id = "p-clean", nome = "Marco", cognome = "Zeta", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personHeavyOnOther,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
                leadCountsByPartType = mapOf(otherPartTypeId to 5), // storia su ALTRO partType
            ),
            SuggestedProclamatore(
                proclamatore = personClean,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
            ),
        )

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(suggestions),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(
                eligible = setOf(
                    EligibilityCleanupCandidate(personHeavyOnOther.id, partTypeId),
                    EligibilityCleanupCandidate(personClean.id, partTypeId),
                ),
            ),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 1,
        )

        // Stesso score → sort secondario per cognome asc: "Alpi" < "Zeta"
        assertEquals(2, result.size)
        assertEquals(personHeavyOnOther.id, result.first().proclamatore.id)
        assertEquals(personClean.id, result.last().proclamatore.id)
    }

    // -----------------------------------------------------------------------
    // Test 5 — slot=1 (studente): solo chi ha idoneità conduzione appare
    // -----------------------------------------------------------------------

    @Test
    fun `slot 1 filtra i candidati senza idoneita' conduzione per il partType`() = runTest {
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

    @Test
    fun `a parita' di score l'ordinamento resta alfabetico e stabile`() = runTest {
        val personAlpi = person(id = "p-a", nome = "Mario", cognome = "Alpi", sesso = Sesso.M)
        val personBianchi = person(id = "p-b", nome = "Luca", cognome = "Bianchi", sesso = Sesso.M)

        val suggestions = listOf(
            SuggestedProclamatore(
                proclamatore = personBianchi,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
                lastConductorWeeks = null,
            ),
            SuggestedProclamatore(
                proclamatore = personAlpi,
                lastGlobalWeeks = 10,
                lastForPartTypeWeeks = 5,
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

        assertEquals(listOf(personAlpi.id, personBianchi.id), result.map { it.proclamatore.id })
    }

    @Test
    fun `cooldown annotation espone settimane rimanenti quando strict cooldown e' disattivato`() = runTest {
        val personInCooldown = person(id = "p-cooldown", nome = "Carlo", cognome = "Verdi", sesso = Sesso.M)

        val useCase = SuggerisciProclamatoriUseCase(
            weekPlanStore = weekPlanQueries,
            assignmentStore = StaticAssignmentRanking(
                listOf(
                    SuggestedProclamatore(
                        proclamatore = personInCooldown,
                        lastGlobalWeeks = 1,
                        lastForPartTypeWeeks = 1,
                        lastConductorWeeks = null,
                    ),
                ),
            ),
            assignmentRepository = EmptyAssignmentsRepository,
            eligibilityStore = StaticEligibilityStore(eligible = emptySet()),
            assignmentSettingsStore = FixedSettingsStore(AssignmentSettings(strictCooldown = false, assistCooldownWeeks = 3)),
        )

        val result = useCase(
            weekStartDate = weekStart,
            weeklyPartId = weeklyPartId,
            slot = 2,
        )

        assertEquals(1, result.size)
        assertTrue(result.single().inCooldown)
        assertEquals(2, result.single().cooldownRemainingWeeks)
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

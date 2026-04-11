package org.example.project.feature.assignments

import org.example.project.core.persistence.TransactionScope
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.AssignmentSettings
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import org.example.project.feature.assignments.application.SuggestionRankingCache
import org.example.project.feature.assignments.domain.Assignment
import org.example.project.feature.assignments.domain.AssignmentId
import org.example.project.feature.assignments.domain.AssignmentWithPerson
import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.application.EligibilityCleanupCandidate
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.LeadEligibility
import org.example.project.feature.people.domain.Proclamatore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.people.domain.Sesso
import org.example.project.feature.programs.domain.ProgramMonthId
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeekPlanId
import java.time.LocalDate

// ---------------------------------------------------------------------------
// AssignmentRepository fakes
// ---------------------------------------------------------------------------

internal object EmptyAssignmentsRepository : AssignmentRepository {
    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> = emptyList()
    override suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>> = emptyMap()
    context(tx: TransactionScope) override suspend fun save(assignment: Assignment) {}
    context(tx: TransactionScope) override suspend fun remove(assignmentId: AssignmentId) {}
    context(tx: TransactionScope) override suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId) {}
    override suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int = 0
    override suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()
    context(tx: TransactionScope) override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
    override suspend fun findWeekPlanIdByAssignmentId(assignmentId: AssignmentId): WeekPlanId? = null
}

internal class StaticAssignmentRepository(
    private val assignments: List<AssignmentWithPerson>,
) : AssignmentRepository {
    override suspend fun listByWeek(weekPlanId: WeekPlanId): List<AssignmentWithPerson> = assignments
    override suspend fun listByWeekPlanIds(weekPlanIds: Set<WeekPlanId>): Map<WeekPlanId, List<AssignmentWithPerson>> =
        weekPlanIds.associateWith { assignments }
    context(tx: TransactionScope) override suspend fun save(assignment: Assignment) {}
    context(tx: TransactionScope) override suspend fun remove(assignmentId: AssignmentId) {}
    context(tx: TransactionScope) override suspend fun removeAllByWeekPlan(weekPlanId: WeekPlanId) {}
    override suspend fun countAssignmentsForWeek(weekPlanId: WeekPlanId): Int = assignments.size
    override suspend fun countAssignmentsByWeekInRange(startDate: LocalDate, endDate: LocalDate): Map<WeekPlanId, Int> = emptyMap()
    context(tx: TransactionScope) override suspend fun deleteByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
    override suspend fun countByProgramFromDate(programId: ProgramMonthId, fromDate: LocalDate): Int = 0
    override suspend fun findWeekPlanIdByAssignmentId(assignmentId: AssignmentId): WeekPlanId? = null
}

// ---------------------------------------------------------------------------
// EligibilityStore fakes
// ---------------------------------------------------------------------------

/** Eligibility store that treats a given set of (personId, partTypeId) pairs as lead-eligible. */
internal class StaticEligibilityStore(
    private val eligible: Set<EligibilityCleanupCandidate>,
) : EligibilityStore {
    context(tx: TransactionScope) override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    context(tx: TransactionScope) override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> =
        eligible.filter { it.partTypeId in partTypeIds }
    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> =
        eligible.groupBy({ it.partTypeId }, { it.personId }).mapValues { (_, ids) -> ids.toSet() }
    context(tx: TransactionScope) override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

/** Eligibility store that marks exactly one (personId, partTypeId) pair as lead-eligible. */
internal class SingleCandidateEligibilityStore(
    private val personId: ProclamatoreId,
    private val partTypeId: PartTypeId,
) : EligibilityStore {
    context(tx: TransactionScope) override suspend fun setCanAssist(personId: ProclamatoreId, canAssist: Boolean) {}
    context(tx: TransactionScope) override suspend fun setCanLead(personId: ProclamatoreId, partTypeId: PartTypeId, canLead: Boolean) {}
    override suspend fun listLeadEligibility(personId: ProclamatoreId): List<LeadEligibility> = emptyList()
    override suspend fun listLeadEligibilityCandidatesForPartTypes(partTypeIds: Set<PartTypeId>): List<EligibilityCleanupCandidate> =
        if (partTypeId in partTypeIds) listOf(EligibilityCleanupCandidate(personId, partTypeId)) else emptyList()
    override suspend fun preloadLeadEligibilityByPartType(partTypeIds: Set<PartTypeId>): Map<PartTypeId, Set<ProclamatoreId>> =
        mapOf(partTypeId to setOf(personId))
    context(tx: TransactionScope) override suspend fun deleteLeadEligibilityForPartTypes(partTypeIds: Set<PartTypeId>) {}
    override suspend fun listFutureAssignmentWeeks(personId: ProclamatoreId, fromDate: LocalDate): List<LocalDate> = emptyList()
}

// ---------------------------------------------------------------------------
// AssignmentRanking fakes
// ---------------------------------------------------------------------------

/** Ranking that always returns the same static list of suggestions, regardless of partTypeId/slot/date. */
internal class StaticAssignmentRanking(
    private val suggestions: List<SuggestedProclamatore>,
) : AssignmentRanking {
    override suspend fun suggestedProclamatori(
        partTypeId: PartTypeId,
        slot: Int,
        referenceDate: LocalDate,
        rankingCache: SuggestionRankingCache?,
    ): List<SuggestedProclamatore> = suggestions

    override suspend fun preloadSuggestionRanking(
        referenceDates: Set<LocalDate>,
        partTypeIds: Set<PartTypeId>,
    ): SuggestionRankingCache = SuggestionRankingCache(
        globalLast = emptyMap(),
        conductorLast = emptyMap(),
        allActive = emptyList(),
        globalBeforeByDate = emptyMap(),
        globalAfterByDate = emptyMap(),
        conductorBeforeByDate = emptyMap(),
        conductorAfterByDate = emptyMap(),
        nearestAssignmentWasConductorByDate = emptyMap(),
        partTypeLastByType = emptyMap(),
        partTypeBeforeByTypeAndDate = emptyMap(),
        partTypeAfterByTypeAndDate = emptyMap(),
        assignmentCountInWindow = emptyMap(),
    )
}

// ---------------------------------------------------------------------------
// AssignmentSettingsStore fakes
// ---------------------------------------------------------------------------

internal class FixedSettingsStore(
    private val settings: AssignmentSettings,
) : AssignmentSettingsStore {
    override suspend fun load(): AssignmentSettings = settings
    context(tx: TransactionScope) override suspend fun save(settings: AssignmentSettings) {}
}

internal object StaticSettingsStore : AssignmentSettingsStore {
    override suspend fun load(): AssignmentSettings = AssignmentSettings(strictCooldown = true)
    context(tx: TransactionScope) override suspend fun save(settings: AssignmentSettings) {}
}

// ---------------------------------------------------------------------------
// Domain helpers
// ---------------------------------------------------------------------------

internal fun person(
    id: String,
    nome: String,
    cognome: String,
    sesso: Sesso,
    puoAssistere: Boolean = true,
    sospeso: Boolean = false,
): Proclamatore = Proclamatore(
    id = ProclamatoreId(id),
    nome = nome,
    cognome = cognome,
    sesso = sesso,
    puoAssistere = puoAssistere,
    sospeso = sospeso,
)

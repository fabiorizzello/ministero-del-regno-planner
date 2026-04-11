package org.example.project.feature.assignments.application

import org.example.project.feature.assignments.domain.SuggestedProclamatore
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.domain.ProclamatoreId
import org.example.project.feature.weeklyparts.application.WeekPlanQueries
import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.WeeklyPartId
import org.example.project.feature.weeklyparts.domain.allowsCandidate
import org.example.project.feature.weeklyparts.domain.isMismatch
import java.time.LocalDate

class SuggerisciProclamatoriUseCase(
    private val weekPlanStore: WeekPlanQueries,
    private val assignmentStore: AssignmentRanking,
    private val assignmentRepository: AssignmentRepository,
    private val eligibilityStore: EligibilityStore,
    private val assignmentSettingsStore: AssignmentSettingsStore,
) {
    suspend operator fun invoke(
        weekStartDate: LocalDate,
        weeklyPartId: WeeklyPartId,
        slot: Int,
        /**
         * IDs of people assigned **in-progress** (not yet persisted to DB) during the current
         * operation. The use case always loads persisted assignments from the repository
         * internally; callers only need to pass IDs of assignments made within the same
         * in-flight transaction that are not yet visible to the repository.
         *
         * For interactive (ViewModel) callers this should be left as the default emptySet().
         */
        additionalExcludedIds: Set<ProclamatoreId> = emptySet(),
        rankingCache: SuggestionRankingCache? = null,
        eligibilityCache: Map<PartTypeId, Set<ProclamatoreId>>? = null,
    ): List<SuggestedProclamatore> {
        val plan = weekPlanStore.findByDate(weekStartDate) ?: return emptyList()
        val part = plan.parts.find { it.id == weeklyPartId } ?: return emptyList()
        val settings = assignmentSettingsStore.load()

        val suggestions = assignmentStore.suggestedProclamatori(
            partTypeId = part.partType.id,
            slot = slot,
            referenceDate = weekStartDate,
            rankingCache = rankingCache,
        )
        val leadEligiblePersonIds: Set<ProclamatoreId> = if (eligibilityCache != null) {
            eligibilityCache[part.partType.id] ?: emptySet()
        } else {
            eligibilityStore
                .listLeadEligibilityCandidatesForPartTypes(setOf(part.partType.id))
                .map { it.personId }
                .toSet()
        }

        // Load assignments for this week from the repository in a single query.
        // This derives both requiredSex (for STESSO_SESSO rule) and the set of
        // already-assigned person IDs, eliminating the N+1 and the fragile caller contract.
        val weekAssignments = assignmentRepository.listByWeek(plan.id)
        val existingPartAssignments = weekAssignments
            .filter { it.weeklyPartId == weeklyPartId && it.slot != slot }
        val requiredSex = existingPartAssignments.firstOrNull()?.sex
        val alreadyAssignedInWeek: Set<ProclamatoreId> = weekAssignments.map { it.personId }.toSet()
        val excludedIds = alreadyAssignedInWeek + additionalExcludedIds

        // Filtri hard: regola sesso UOMO, idoneita', gia' assegnato nella stessa settimana.
        // STESSO_SESSO = stesso sesso preferito (soft): non filtra, ma annota sexMismatch.
        val eligible = suggestions
            .map { suggestion ->
                val p = suggestion.proclamatore
                val passaSesso = part.partType.sexRule.allowsCandidate(p.sesso)
                val isSexMismatch = part.partType.sexRule.isMismatch(
                    candidateSex = p.sesso,
                    requiredSex = requiredSex,
                )
                val passaIdoneita = if (slot == 1) {
                    p.id in leadEligiblePersonIds
                } else {
                    p.puoAssistere
                }
                val globalWeeks = suggestion.lastGlobalWeeks ?: Int.MAX_VALUE
                val cooldownWeeks = if (suggestion.lastWasConductor && slot == 1) settings.leadCooldownWeeks
                                    else settings.assistCooldownWeeks
                val isInCooldown = cooldownWeeks > 0 && globalWeeks < cooldownWeeks
                val remaining = if (isInCooldown) cooldownWeeks - globalWeeks else 0
                val annotated = suggestion.copy(
                    inCooldown = isInCooldown,
                    cooldownRemainingWeeks = remaining.coerceAtLeast(0),
                    sexMismatch = isSexMismatch,
                )
                annotated to (passaSesso && passaIdoneita && !p.sospeso && p.id !in excludedIds)
            }
            .filter { (_, allowed) -> allowed }
            .map { (suggestion, _) -> suggestion to weightedScore(suggestion, part.partType.id, slot) }
            .filter { (suggestion, _) -> !settings.strictCooldown || !suggestion.inCooldown }

        return eligible
            .sortedWith(
                compareByDescending<Pair<SuggestedProclamatore, Long>> { it.second }
                    .thenByDescending { it.first.lastGlobalWeeks ?: Int.MAX_VALUE }
                    .thenBy { it.first.proclamatore.cognome.lowercase() }
                    .thenBy { it.first.proclamatore.nome.lowercase() },
            )
            .map { (suggestion, _) -> suggestion }
    }

    private fun weightedScore(
        suggestion: SuggestedProclamatore,
        targetPartTypeId: PartTypeId,
        targetSlot: Int,
    ): Long {
        val safeGlobalWeeks = suggestion.lastGlobalWeeks ?: Int.MAX_VALUE
        val cooldownPenalty = if (suggestion.inCooldown) COOLDOWN_PENALTY else 0
        val countPenalty = suggestion.totalAssignmentsInWindow * COUNT_PENALTY_WEIGHT

        val targetIsConductor = targetSlot == 1
        val sameSlotType = (suggestion.lastWasConductor && targetIsConductor) ||
            (!suggestion.lastWasConductor && !targetIsConductor && suggestion.lastGlobalWeeks != null)
        val slotRepeatPenalty = if (sameSlotType) SLOT_REPEAT_PENALTY else 0

        // Equità per-parte: penalizza chi ha già condotto questo specifico tipo di parte nella
        // finestra di equità. Applicata solo se il target e' il ruolo di conduttore (slot 1),
        // altrimenti non ha senso — lo slot di assistenza non e' per-parte.
        val partTypeLeadPenalty = if (targetIsConductor) {
            (suggestion.leadCountsByPartType[targetPartTypeId] ?: 0) * PART_TYPE_LEAD_WEIGHT
        } else {
            0
        }
        // Equità per assistenza: penalizza chi ha accumulato molte assistenze nella finestra di
        // equità. Applicata solo se il target e' un ruolo di assistente (slot >= 2).
        val assistRolePenalty = if (!targetIsConductor) {
            suggestion.assistCountInWindow * ASSIST_ROLE_WEIGHT
        } else {
            0
        }

        return safeGlobalWeeks.toLong() -
            countPenalty -
            slotRepeatPenalty -
            partTypeLeadPenalty -
            assistRolePenalty -
            cooldownPenalty
    }
}

/** True when the most recent assignment (any part type) was in slot 1 (conductor). */
private val SuggestedProclamatore.lastWasConductor: Boolean
    get() = lastAssignmentWasConductor ?: (
        lastConductorWeeks != null &&
            lastGlobalWeeks != null &&
            lastConductorWeeks == lastGlobalWeeks
        )

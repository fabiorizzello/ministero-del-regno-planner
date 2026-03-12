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
                val lastWasConductor = suggestion.lastConductorWeeks != null &&
                    suggestion.lastGlobalWeeks != null &&
                    suggestion.lastConductorWeeks == suggestion.lastGlobalWeeks
                val cooldownWeeks = if (lastWasConductor && slot == 1) settings.leadCooldownWeeks
                                    else settings.assistCooldownWeeks
                val isInCooldown = cooldownWeeks > 0 && globalWeeks < cooldownWeeks
                val remaining = if (isInCooldown) cooldownWeeks - globalWeeks else 0
                val annotated = suggestion.copy(
                    inCooldown = isInCooldown,
                    cooldownRemainingWeeks = remaining.coerceAtLeast(0),
                    sexMismatch = isSexMismatch,
                )
                Triple(annotated, passaSesso && passaIdoneita && p.id !in excludedIds, Unit)
            }
            .filter { (_, allowed, _) -> allowed }
            .map { (suggestion, _, _) -> suggestion to weightedScore(suggestion, slot) }
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

    private fun weightedScore(suggestion: SuggestedProclamatore, targetSlot: Int): Long {
        val safeGlobalWeeks = suggestion.lastGlobalWeeks ?: 999
        val cooldownPenalty = if (suggestion.inCooldown) COOLDOWN_PENALTY else 0
        val countPenalty = suggestion.totalAssignmentsInWindow * COUNT_PENALTY_WEIGHT

        val lastWasConductor = suggestion.lastConductorWeeks != null &&
            suggestion.lastGlobalWeeks != null &&
            suggestion.lastConductorWeeks == suggestion.lastGlobalWeeks
        val targetIsConductor = targetSlot == 1
        val sameSlotType = (lastWasConductor && targetIsConductor) ||
            (!lastWasConductor && !targetIsConductor && suggestion.lastGlobalWeeks != null)
        val slotRepeatPenalty = if (sameSlotType) SLOT_REPEAT_PENALTY else 0

        return safeGlobalWeeks.toLong() - countPenalty - slotRepeatPenalty - cooldownPenalty
    }
}

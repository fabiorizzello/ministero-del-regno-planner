package org.example.project.feature.planning.application

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.feature.assignments.application.AssignmentRanking
import org.example.project.feature.assignments.application.AssignmentRepository
import org.example.project.feature.assignments.application.AssignmentSettingsStore
import org.example.project.feature.people.application.EligibilityStore
import org.example.project.feature.people.application.ProclamatoriAggregateStore
import org.example.project.feature.planning.domain.AlertType
import org.example.project.feature.planning.domain.PlanningAlert
import org.example.project.feature.weeklyparts.application.WeekPlanStore
import java.time.LocalDate

class GeneraAlertValidazioneAssegnazioni(
    private val weekPlanStore: WeekPlanStore,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentRanking: AssignmentRanking,
    private val assignmentSettingsStore: AssignmentSettingsStore,
    private val eligibilityStore: EligibilityStore,
    private val proclamatoriStore: ProclamatoriAggregateStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<PlanningAlert> = withContext(dispatcher) {
        val settings = assignmentSettingsStore.load()
        val alerts = mutableListOf<PlanningAlert>()

        val weekPlans = weekPlanStore.listInRange(startDate, endDate)

        for (weekSummary in weekPlans) {
            val weekPlan = weekPlanStore.findByDate(weekSummary.weekStartDate) ?: continue
            val assignments = assignmentRepository.listByWeek(weekPlan.id)

            // Check for duplicate assignments (same person multiple times in week)
            val assignmentsByPerson = assignments.groupBy { it.personId }
            for ((_, personAssignments) in assignmentsByPerson) {
                if (personAssignments.size > 1) {
                    alerts.add(
                        PlanningAlert(
                            type = AlertType.DUPLICATE_ASSIGNMENT,
                            weekKeys = listOf(weekPlan.weekStartDate.toString()),
                            personName = personAssignments.first().fullName,
                            partTypeName = null,
                        ),
                    )
                }
            }

            // Check cooldown and eligibility for each assignment
            for (assignment in assignments) {
                val part = weekPlan.parts.find { it.id == assignment.weeklyPartId } ?: continue

                // Check cooldown violation
                val cooldownWeeks = if (assignment.slot == 1) {
                    settings.leadCooldownWeeks
                } else {
                    settings.assistCooldownWeeks
                }

                if (cooldownWeeks > 0) {
                    val suggestions = assignmentRanking.suggestedProclamatori(
                        partTypeId = part.partType.id,
                        slot = assignment.slot,
                        referenceDate = weekPlan.weekStartDate,
                    )
                    val personSuggestion = suggestions.find { it.proclamatore.id == assignment.personId }
                    val globalWeeks = personSuggestion?.lastGlobalWeeks ?: Int.MAX_VALUE

                    if (globalWeeks < cooldownWeeks) {
                        alerts.add(
                            PlanningAlert(
                                type = AlertType.COOLDOWN_VIOLATION,
                                weekKeys = listOf(weekPlan.weekStartDate.toString()),
                                personName = assignment.fullName,
                                partTypeName = part.partType.label,
                            ),
                        )
                    }
                }

                // Check eligibility
                if (assignment.slot == 1) {
                    // Lead role - check lead eligibility
                    val eligibilities = eligibilityStore.listLeadEligibility(assignment.personId)
                    val canLead = eligibilities.any {
                        it.partTypeId == part.partType.id && it.canLead
                    }
                    if (!canLead) {
                        alerts.add(
                            PlanningAlert(
                                type = AlertType.INELIGIBLE_ASSIGNMENT,
                                weekKeys = listOf(weekPlan.weekStartDate.toString()),
                                personName = assignment.fullName,
                                partTypeName = part.partType.label,
                            ),
                        )
                    }
                } else {
                    // Assist role - check puoAssistere
                    val proclamatore = proclamatoriStore.load(assignment.personId)
                    if (proclamatore != null && !proclamatore.puoAssistere) {
                        alerts.add(
                            PlanningAlert(
                                type = AlertType.INELIGIBLE_ASSIGNMENT,
                                weekKeys = listOf(weekPlan.weekStartDate.toString()),
                                personName = assignment.fullName,
                                partTypeName = part.partType.label,
                            ),
                        )
                    }
                }
            }
        }

        alerts
    }
}

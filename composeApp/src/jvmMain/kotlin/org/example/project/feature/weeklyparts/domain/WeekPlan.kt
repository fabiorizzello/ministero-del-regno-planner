package org.example.project.feature.weeklyparts.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.time.DayOfWeek
import java.time.LocalDate
import org.example.project.core.domain.DomainError
import org.example.project.feature.programs.domain.ProgramMonthId

@JvmInline
value class WeekPlanId(val value: String)

enum class WeekPlanStatus {
    ACTIVE,
    SKIPPED,
}

data class WeekPlan internal constructor(
    val id: WeekPlanId,
    val weekStartDate: LocalDate,
    val parts: List<WeeklyPart>,
    val programId: ProgramMonthId? = null,
    val status: WeekPlanStatus = WeekPlanStatus.ACTIVE,
) {
    companion object {
        fun of(
            id: WeekPlanId,
            weekStartDate: LocalDate,
            parts: List<WeeklyPart> = emptyList(),
            programId: ProgramMonthId? = null,
            status: WeekPlanStatus = WeekPlanStatus.ACTIVE,
        ): Either<DomainError, WeekPlan> = either {
            ensure(id.value.isNotBlank()) {
                DomainError.Validation("WeekPlanId non può essere vuoto")
            }
            ensure(weekStartDate.dayOfWeek == DayOfWeek.MONDAY) {
                DomainError.DataSettimanaNonLunedi
            }
            WeekPlan(id = id, weekStartDate = weekStartDate, parts = parts, programId = programId, status = status)
        }
    }

    fun nextSortOrder(): Int =
        (parts.maxOfOrNull { it.sortOrder } ?: -1) + 1

    fun findPart(partId: WeeklyPartId): WeeklyPart? =
        parts.find { it.id == partId }
}

fun WeekPlan.canBeMutated(referenceDate: LocalDate): Boolean =
    canBeEditedManually() && weekStartDate >= referenceDate

fun WeekPlan.canBeEditedManually(): Boolean =
    status == WeekPlanStatus.ACTIVE

/** Restituisce la domenica della settimana che inizia il lunedì [monday]. */
fun sundayOf(monday: LocalDate): LocalDate = monday.plusDays(6)

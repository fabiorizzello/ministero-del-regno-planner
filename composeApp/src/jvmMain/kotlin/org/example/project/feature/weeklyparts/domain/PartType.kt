package org.example.project.feature.weeklyparts.domain

import arrow.core.Either
import arrow.core.raise.either
import io.konform.validation.Validation
import io.konform.validation.constraints.minimum
import org.example.project.core.domain.DomainError
import org.example.project.core.domain.validate

@JvmInline
value class PartTypeId(val value: String)

private data class PartTypeValidationInput(
    val code: String,
    val label: String,
    val peopleCount: Int,
)

private val partTypeValidator = Validation<PartTypeValidationInput> {
    PartTypeValidationInput::code {
        constrain("code non puo' essere vuoto") { it.trim().isNotEmpty() }
    }
    PartTypeValidationInput::label {
        constrain("label non puo' essere vuoto") { it.trim().isNotEmpty() }
    }
    PartTypeValidationInput::peopleCount {
        minimum(1)
    }
}

data class PartType internal constructor(
    val id: PartTypeId,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
    val sortOrder: Int,
) {
    companion object {
        fun of(
            id: PartTypeId,
            code: String,
            label: String,
            peopleCount: Int,
            sexRule: SexRule,
            fixed: Boolean,
            sortOrder: Int,
        ): Either<DomainError, PartType> = either {
            partTypeValidator.validate(
                value = PartTypeValidationInput(code = code, label = label, peopleCount = peopleCount),
                context = "PartType non valido",
            ).bind()
            PartType(
                id = id,
                code = code,
                label = label,
                peopleCount = peopleCount,
                sexRule = sexRule,
                fixed = fixed,
                sortOrder = sortOrder,
            )
        }
    }

    fun isValidSlot(slot: Int): Boolean = slot in 1..peopleCount
}

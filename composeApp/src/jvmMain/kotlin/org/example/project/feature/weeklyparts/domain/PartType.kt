package org.example.project.feature.weeklyparts.domain

import io.konform.validation.Validation
import io.konform.validation.constraints.minimum
import org.example.project.core.domain.requireValid

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

data class PartType(
    val id: PartTypeId,
    val code: String,
    val label: String,
    val peopleCount: Int,
    val sexRule: SexRule,
    val fixed: Boolean,
    val sortOrder: Int,
) {
    init {
        partTypeValidator.requireValid(
            value = PartTypeValidationInput(code = code, label = label, peopleCount = peopleCount),
            context = "PartType non valido",
        )
    }

    fun isValidSlot(slot: Int): Boolean = slot in 1..peopleCount
}

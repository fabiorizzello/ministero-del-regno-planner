package org.example.project.core.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.konform.validation.Invalid
import io.konform.validation.Validation

internal fun <T> Validation<T>.requireValid(value: T, context: String) {
    when (val result = this(value)) {
        is Invalid -> {
            val details = result.errors.joinToString("; ") { it.message }
            throw IllegalArgumentException("$context: $details")
        }

        else -> Unit
    }
}

internal fun <T> Validation<T>.validate(value: T, context: String): Either<DomainError.Validation, T> {
    return when (val result = this(value)) {
        is Invalid -> {
            val details = result.errors.joinToString("; ") { it.message }
            DomainError.Validation("$context: $details").left()
        }

        else -> value.right()
    }
}

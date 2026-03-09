package org.example.project.core.domain

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

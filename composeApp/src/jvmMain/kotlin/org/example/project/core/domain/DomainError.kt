package org.example.project.core.domain

sealed interface DomainError {
    data class Validation(val message: String) : DomainError
    data class NotImplemented(val area: String) : DomainError
}

fun DomainError.toMessage(): String = when (this) {
    is DomainError.Validation -> message
    is DomainError.NotImplemented -> "Non implementato: $area"
}

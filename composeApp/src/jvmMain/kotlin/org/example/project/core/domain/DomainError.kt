package org.example.project.core.domain

sealed interface DomainError {
    data class Validation(val message: String) : DomainError
    data class NotImplemented(val area: String) : DomainError
}

package org.example.project.feature.updates.application

import arrow.core.Either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.core.domain.DomainError

class UpdateStatusStore {
    private val _state = MutableStateFlow<Either<DomainError, UpdateCheckResult>?>(null)
    val state: StateFlow<Either<DomainError, UpdateCheckResult>?> = _state.asStateFlow()

    fun update(result: Either<DomainError, UpdateCheckResult>) {
        _state.value = result
    }
}

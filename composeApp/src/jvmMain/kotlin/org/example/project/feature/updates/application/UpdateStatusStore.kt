package org.example.project.feature.updates.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UpdateStatusStore {
    private val _state = MutableStateFlow<UpdateCheckResult?>(null)
    val state: StateFlow<UpdateCheckResult?> = _state.asStateFlow()

    fun update(result: UpdateCheckResult) {
        _state.value = result
    }
}

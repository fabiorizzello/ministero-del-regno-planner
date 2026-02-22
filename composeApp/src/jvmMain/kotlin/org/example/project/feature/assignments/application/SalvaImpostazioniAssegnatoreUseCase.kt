package org.example.project.feature.assignments.application

class SalvaImpostazioniAssegnatoreUseCase(
    private val store: AssignmentSettingsStore,
) {
    suspend operator fun invoke(settings: AssignmentSettings) {
        store.save(settings.normalized())
    }
}

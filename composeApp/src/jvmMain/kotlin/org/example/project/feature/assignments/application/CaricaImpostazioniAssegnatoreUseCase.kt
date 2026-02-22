package org.example.project.feature.assignments.application

class CaricaImpostazioniAssegnatoreUseCase(
    private val store: AssignmentSettingsStore,
) {
    suspend operator fun invoke(): AssignmentSettings = store.load()
}

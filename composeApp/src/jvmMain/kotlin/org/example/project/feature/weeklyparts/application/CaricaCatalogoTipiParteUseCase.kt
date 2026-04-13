package org.example.project.feature.weeklyparts.application

class CaricaCatalogoTipiParteUseCase(
    private val partTypeStore: PartTypeStore,
) {
    suspend operator fun invoke(): List<PartTypeWithStatus> =
        partTypeStore.allWithStatus()
}

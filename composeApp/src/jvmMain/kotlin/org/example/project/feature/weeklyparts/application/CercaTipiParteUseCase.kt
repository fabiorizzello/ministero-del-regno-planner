package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartType

class CercaTipiParteUseCase(
    private val partTypeStore: PartTypeStore,
) {
    suspend operator fun invoke(): List<PartType> {
        return partTypeStore.all()
    }
}

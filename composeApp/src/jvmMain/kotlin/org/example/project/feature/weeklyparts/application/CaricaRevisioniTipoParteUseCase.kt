package org.example.project.feature.weeklyparts.application

import org.example.project.feature.weeklyparts.domain.PartTypeId
import org.example.project.feature.weeklyparts.domain.PartTypeRevisionView
import org.example.project.feature.weeklyparts.domain.PartTypeSnapshot
import org.example.project.feature.weeklyparts.domain.computePartTypeDelta

class CaricaRevisioniTipoParteUseCase(
    private val partTypeStore: PartTypeStore,
) {
    suspend operator fun invoke(partTypeId: PartTypeId): List<PartTypeRevisionView> {
        val rows = partTypeStore.allRevisionsForPartType(partTypeId)
        if (rows.isEmpty()) return emptyList()
        val maxRevisionNumber = rows.maxOf { it.revisionNumber }
        var previous: PartTypeSnapshot? = null
        val views = rows.map { row ->
            val view = PartTypeRevisionView(
                revisionNumber = row.revisionNumber,
                createdAt = row.createdAt,
                isCurrent = row.revisionNumber == maxRevisionNumber,
                snapshot = row.snapshot,
                deltaFromPrevious = computePartTypeDelta(previous, row.snapshot),
            )
            previous = row.snapshot
            view
        }
        return views.sortedByDescending { it.revisionNumber }
    }
}
